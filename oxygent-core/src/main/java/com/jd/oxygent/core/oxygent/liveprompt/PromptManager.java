package com.jd.oxygent.core.oxygent.liveprompt;

import com.jd.oxygent.core.Config;
import com.jd.oxygent.core.oxygent.infra.databases.BaseEs;
import com.jd.oxygent.core.oxygent.infra.impl.databases.es.LocalEs;
import com.jd.oxygent.core.oxygent.samples.server.masprovider.factory.impl.platform.spring.ApplicationContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Live Prompt Management Service for OxyGent framework.
 *
 * This module provides dynamic prompt management capabilities, supporting storage and
 * real-time updates through Elasticsearch or LocalEs backends. It enables hot-swapping
 * of prompts during runtime and maintains version history for all prompt changes.
 * The system automatically falls back to LocalEs when Elasticsearch is unavailable.
 *
 * Prompt management system with automatic ES/LocalEs fallback.
 *
 *     This class provides comprehensive prompt management capabilities including
 *     storage, retrieval, versioning, and hot-reloading. It automatically switches
 *     between Elasticsearch and LocalEs backends based on availability.
 *
 * @author OxyGent Team
 * @version 1.0.10.4
 * @since 1.0.10.4
 */
@Data
@Slf4j
public class PromptManager {
    private static PromptManager instance;
    private static boolean versionSyncStarted = false;

    private String indexName;
    private Map<String, Map<String, Object>> promptCache = new HashMap<>();
    private ReentrantLock cacheLock = new ReentrantLock();

    private BaseEs esClient;

    public PromptManager(String indexName) {
        if (indexName == null) {
            this.indexName = Config.getAppName() + "_prompt";
        } else {
            this.indexName = indexName;
        }
        esClient = ApplicationContextHolder.getBean(BaseEs.class);
        if (esClient == null) {
            esClient = new LocalEs();
        }
    }

    public PromptManager() {
        this(null);
    }

    public boolean savePrompt(
            String promptKey,
            String promptContent,
            String description,
            String category,
            String agentType,
            int version,
            boolean isActive,
            List<String> tags,
            String createdBy
    ) {
        try {
            // Check if prompt already exists
            Map<String, Object> existing = getPrompt(promptKey, false);

            Map<String, Object> doc = new HashMap<>();
            doc.put("prompt_key", promptKey);
            doc.put("prompt_content", promptContent);
            doc.put("description", description);
            doc.put("category", category);
            doc.put("agent_type", agentType);
            doc.put("is_active", isActive);
            doc.put("updated_at", Instant.now().toString());
            doc.put("tags", tags != null ? tags : new ArrayList<>());

            if (existing != null) {
                // For updates, always increment based on current version in ES
                int currentVersion = Integer.parseInt(existing.getOrDefault("version", "1").toString());
                doc.put("version", currentVersion + 1);

                // Verify cache hasn't drifted from ES
                cacheLock.lock();
                try {
                    Map<String, Object> cachedPrompt = promptCache.get(promptKey);
                    if (cachedPrompt != null) {
                        int cachedVersion = Integer.parseInt(cachedPrompt.getOrDefault("version", "0").toString());
                        if (cachedVersion != currentVersion) {
                            log.warn("Cache version mismatch for {}: cache={}, ES={}. Syncing cache...",
                                    promptKey, cachedVersion, currentVersion);
                        }
                    }
                } finally {
                    cacheLock.unlock();
                }

                // Save current version to history
                String historyId = promptKey + "_v" + currentVersion;
                Map<String, Object> historyDoc = new HashMap<>(existing);
                historyDoc.put("is_history", true);
                historyDoc.put("history_id", historyId);
                historyDoc.put("archived_at", Instant.now().toString());

                try {
                    esClient.index(indexName + "_history", historyId, historyDoc);
                } catch (Exception e) {
                    log.warn("Failed to save history for {}: {}", promptKey, e.getMessage());
                }

                // Update existing record
                doc.put("created_at", existing.get("created_at"));
                doc.put("created_by", existing.getOrDefault("created_by", createdBy));
            } else {
                // Create new record
                doc.put("version", version);
                doc.put("created_at", Instant.now().toString());
                doc.put("created_by", createdBy);
            }

            // Update cache (for immediate read availability)
            Map<String, Object> oldCacheValue = null;
            cacheLock.lock();
            try {
                oldCacheValue = promptCache.get(promptKey);
                promptCache.put(promptKey, doc);
                log.info("Cache now contains {} keys: {}", promptCache.size(), promptCache.keySet());
            } finally {
                cacheLock.unlock();
            }

            // Persist to ES
            try {
                esClient.index(indexName, promptKey, doc);
                log.info("✓ Persisted to ES: {} (phase 2)", promptKey);
            } catch (Exception esError) {
                // ES write failed - rollback cache to maintain consistency
                log.error("ES write failed for {}: {}", promptKey, esError);
                log.warn("Rolling back cache to previous state");

                cacheLock.lock();
                try {
                    if (oldCacheValue != null) {
                        promptCache.put(promptKey, oldCacheValue);
                        log.info("Cache rolled back to previous version");
                    } else {
                        promptCache.remove(promptKey);
                        log.info("Cache rolled back (removed new key)");
                    }
                } finally {
                    cacheLock.unlock();
                }
                throw esError;
            }

            log.info("Saved prompt: {} (two-phase commit completed)", promptKey);

            // Update local version tracker for ES polling sync
            int newVersion = (int) doc.get("version");
            VersionSyncCoordinator.getInstance(this).handleVersionUpdate(promptKey, newVersion);
            updateLocalVersionTracker(promptKey, newVersion);
            return true;
        } catch (Exception e) {
            log.error("Failed to save prompt {}: {}", promptKey, e);
            return false;
        }
    }

    public Map<String, Object> getPrompt(String promptKey, boolean useCache) {
        try {
            // Check cache first if enabled
            if (useCache) {
                cacheLock.lock();
                try {
                    Map<String, Object> cachedPrompt = promptCache.get(promptKey);
                    if (cachedPrompt != null) {
                        log.debug("Using cached prompt for: {}", promptKey);
                        return new HashMap<>(cachedPrompt);
                    }
                } finally {
                    cacheLock.unlock();
                }
            }

            // Cache miss, fetch from database
            Map<String, Object> term = Map.of("_id", promptKey);
            Map<String, Object> query = Map.of("term", term);
            Map<String, Object> searchBody = Map.of(
                    "query", query,
                    "size", 1
            );

            try {
                Map<String, Object> response = esClient.search(indexName, searchBody);
                if (response == null) {
                    return null;
                }

                Map<String, Object> hits = (Map<String, Object>) response.get("hits");
                if (hits != null) {
                    List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
                    if (!hitList.isEmpty()) {
                        Map<String, Object> hit = hitList.get(0);
                        Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                        if (source != null) {
                            // Update cache with full document
                            cacheLock.lock();
                            try {
                                promptCache.put(promptKey, source);
                            } finally {
                                cacheLock.unlock();
                            }
                            return new HashMap<>(source);
                        }
                    }
                }
                return null;
            } catch (Exception searchError) {
                String errorMsg = searchError.getMessage();
                if (errorMsg != null && (errorMsg.contains("index_not_found") || errorMsg.contains("no such index"))) {
                    log.debug("Index {} not found, will be created on first save", indexName);
                    return null;
                } else {
                    throw searchError;
                }
            }
        } catch (Exception e) {
            log.error("Failed to get prompt {}: {}", promptKey, e);
            return null;
        }
    }

    public void clearCache(String promptKey) {
        cacheLock.lock();
        try {
            if (promptKey != null) {
                if (promptCache.containsKey(promptKey)) {
                    promptCache.remove(promptKey);
                    log.debug("Cleared cache for: {}", promptKey);
                }
            } else {
                promptCache.clear();
                log.info("Cleared all prompt cache");
            }
        } finally {
            cacheLock.unlock();
        }
    }

    public String getPromptContent(String promptKey, String fallbackContent, boolean useCache) {
        try {
            Map<String, Object> promptData = getPrompt(promptKey, useCache);
            if (promptData != null && (boolean) promptData.getOrDefault("is_active", true)) {
                return (String) promptData.get("prompt_content");
            }
            return fallbackContent;
        } catch (Exception e) {
            log.error("Failed to get prompt content {}: {}", promptKey, e);
            return fallbackContent;
        }
    }

    public List<Map<String, Object>> getPromptHistory(String promptKey) {
        try {
            // Search history records
            List<Map<String, Object>> must = new ArrayList<>();

            Map<String, Object> promptKeyTerm = Map.of("prompt_key", promptKey);
            Map<String, Object> term1 = Map.of("term", promptKeyTerm);
            must.add(term1);

            Map<String, Object> isHistoryTerm = Map.of("is_history", true);
            Map<String, Object> term2 = Map.of("term", isHistoryTerm);
            must.add(term2);

            Map<String, Object> bool = Map.of("must", must);
            Map<String, Object> query = Map.of("bool", bool);

            Map<String, Object> sort = Map.of("order", "desc");
            Map<String, Object> versionSort = Map.of("version", sort);
            List<Map<String, Object>> sorts = new ArrayList<>();
            sorts.add(versionSort);

            Map<String, Object> searchBody = Map.of(
                    "query", query,
                    "sort", sorts,
                    "size", 50
            );

            Map<String, Object> response = esClient.search(indexName + "_history", searchBody);
            List<Map<String, Object>> histories = new ArrayList<>();

            Map<String, Object> hits = (Map<String, Object>) response.get("hits");
            if (hits != null) {
                List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
                for (Map<String, Object> hit : hitList) {
                    Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                    if (source != null) {
                        histories.add(source);
                    }
                }
            }

            // If term query yields nothing and we're on ES, try match fallback
            if (histories.isEmpty() && !(esClient instanceof LocalEs)) {
                List<Map<String, Object>> mustFallback = new ArrayList<>();

                Map<String, Object> promptKeyMatch = Map.of("prompt_key", promptKey);
                Map<String, Object> match = Map.of("match", promptKeyMatch);
                mustFallback.add(match);

                mustFallback.add(term2); // Same is_history term as before

                Map<String, Object> boolFallback = Map.of("must", mustFallback);
                Map<String, Object> queryFallback = Map.of("bool", boolFallback);

                Map<String, Object> searchBodyFallback = Map.of(
                        "query", queryFallback,
                        "sort", sorts,
                        "size", 50
                );

                Map<String, Object> responseFallback = esClient.search(indexName + "_history", searchBodyFallback);

                Map<String, Object> hitsFallback = (Map<String, Object>) responseFallback.get("hits");
                if (hitsFallback != null) {
                    List<Map<String, Object>> hitListFallback = (List<Map<String, Object>>) hitsFallback.get("hits");
                    for (Map<String, Object> hit : hitListFallback) {
                        Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                        if (source != null) {
                            histories.add(source);
                        }
                    }
                }
            }

            return histories;
        } catch (Exception e) {
            log.error("Failed to get prompt history for {}: {}", promptKey, e);
            return new ArrayList<>();
        }
    }

    public boolean revertToVersion(String promptKey, int targetVersion) {
        try {
            // Get target version from history
            String historyId = promptKey + "_v" + targetVersion;
            log.info("Attempting to revert {} to version {}", promptKey, targetVersion);

            try {
                // Use search instead of get method
                Map<String, Object> term = Map.of("_id", historyId);
                Map<String, Object> query = Map.of("term", term);
                Map<String, Object> searchBody = Map.of(
                        "query", query,
                        "size", 1
                );

                Map<String, Object> response = esClient.search(indexName + "_history", searchBody);

                Map<String, Object> hits = (Map<String, Object>) response.get("hits");
                if (hits != null) {
                    List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
                    if (hitList.isEmpty()) {
                        log.error("Version {} not found for {}", targetVersion, promptKey);
                        return false;
                    }

                    Map<String, Object> hit = hitList.get(0);
                    Map<String, Object> historyData = (Map<String, Object>) hit.get("_source");
                    log.debug("Found history version {} for {}", targetVersion, promptKey);

                    // Clear cache before reverting to ensure fresh data
                    clearCache(promptKey);

                    // Create new version using historical data
                    log.debug("Creating new version from history data for {}", promptKey);
                    boolean success = savePrompt(
                            promptKey,
                            (String) historyData.get("prompt_content"),
                            (String) historyData.getOrDefault("description", ""),
                            (String) historyData.getOrDefault("category", "custom"),
                            (String) historyData.getOrDefault("agent_type", ""),
                            1, // version is ignored for updates
                            (boolean) historyData.getOrDefault("is_active", true),
                            (List<String>) historyData.getOrDefault("tags", new ArrayList<>()),
                            "reverted_from_v" + targetVersion
                    );

                    if (success) {
                        log.info("Successfully reverted {} to version {}", promptKey, targetVersion);
                    } else {
                        log.error("Failed to save reverted version for {}", promptKey);
                    }

                    return success;
                }
                return false;
            } catch (Exception e) {
                log.error("Version {} not found for {}: {}", targetVersion, promptKey, e);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to revert {} to version {}: {}", promptKey, targetVersion, e);
            return false;
        }
    }

    public List<Map<String, Object>> listPrompts(
            String category,
            String agentType,
            Boolean isActive,
            List<String> tags
    ) {
        try {
            // If no filters and cache is populated, return from cache directly
            boolean hasFilters = category != null || agentType != null || isActive != null || tags != null;

            cacheLock.lock();
            try {
                if (!hasFilters && !promptCache.isEmpty()) {
                    List<Map<String, Object>> results = new ArrayList<>();
                    // Create a snapshot to avoid holding lock during iteration
                    Map<String, Map<String, Object>> cacheSnapshot = new HashMap<>(promptCache);
                    // Release lock before processing
                    cacheLock.unlock();

                    for (Map.Entry<String, Map<String, Object>> entry : cacheSnapshot.entrySet()) {
                        String promptKey = entry.getKey();
                        Map<String, Object> promptData = entry.getValue();
                        Map<String, Object> result = new HashMap<>(promptData);
                        result.put("id", promptKey);
                        results.add(result);
                    }
                    // Sort by updated_at descending
                    results.sort((a, b) -> {
                        String updatedAtA = (String) a.getOrDefault("updated_at", "");
                        String updatedAtB = (String) b.getOrDefault("updated_at", "");
                        return updatedAtB.compareTo(updatedAtA);
                    });
                    return results;
                }
            } finally {
                if (cacheLock.isHeldByCurrentThread()) {
                    cacheLock.unlock();
                }
            }

            // Build query for ES search
            Map<String, Object> query = Map.of("match_all", Map.of());
            List<Map<String, Object>> filters = new ArrayList<>();

            if (category != null) {
                Map<String, Object> categoryTerm = Map.of("category", category);
                Map<String, Object> categoryFilter = Map.of("term", categoryTerm);
                filters.add(categoryFilter);
            }
            if (agentType != null) {
                Map<String, Object> agentTypeTerm = Map.of("agent_type", agentType);
                Map<String, Object> agentTypeFilter = Map.of("term", agentTypeTerm);
                filters.add(agentTypeFilter);
            }
            if (isActive != null) {
                Map<String, Object> isActiveTerm = Map.of("is_active", isActive);
                Map<String, Object> isActiveFilter = Map.of("term", isActiveTerm);
                filters.add(isActiveFilter);
            }
            if (tags != null) {
                for (String tag : tags) {
                    Map<String, Object> tagTerm = Map.of("tags", tag);
                    Map<String, Object> tagFilter = Map.of("term", tagTerm);
                    filters.add(tagFilter);
                }
            }

            if (!filters.isEmpty()) {
                List<Map<String, Object>> must = new ArrayList<>();
                must.add(Map.of("match_all", Map.of()));
                Map<String, Object> boolQuery = Map.of(
                        "must", must,
                        "filter", filters
                );
                query = Map.of("bool", boolQuery);
            }

            // Run search
            Map<String, Object> sort = Map.of("order", "desc");
            Map<String, Object> updatedAtSort = Map.of("updated_at", sort);
            List<Map<String, Object>> sorts = new ArrayList<>();
            sorts.add(updatedAtSort);

            Map<String, Object> searchBody = Map.of(
                    "query", query,
                    "sort", sorts,
                    "size", 1000
            );

            Map<String, Object> response = esClient.search(indexName, searchBody);

            List<Map<String, Object>> results = new ArrayList<>();
            Map<String, Object> hits = (Map<String, Object>) response.get("hits");
            if (hits != null) {
                List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
                for (Map<String, Object> hit : hitList) {
                    Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                    Map<String, Object> result = new HashMap<>(source);
                    result.put("id", hit.get("_id"));
                    results.add(result);

                    // Update cache with fetched data (if no filters, refresh full cache)
                    if (!hasFilters) {
                        cacheLock.lock();
                        try {
                            promptCache.put((String) hit.get("_id"), source);
                        } finally {
                            cacheLock.unlock();
                        }
                    }
                }
            }

            return results;
        } catch (Exception e) {
            log.error("Failed to list prompts: {}", e);
            return new ArrayList<>();
        }
    }

    public boolean deletePrompt(String promptKey) {
        try {
            // If ES delete fails, cache is not touched, avoiding inconsistency
            try {
                esClient.delete(indexName, promptKey);
                log.info("Deleted from ES: {}", promptKey);
            } catch (Exception esError) {
                log.error("ES delete failed for {}: {}", promptKey, esError.getMessage());
                // Don't clear cache if ES delete fails
                // This prevents data resurrection on restart
                throw esError;
            }

            // ES delete successful, now clear cache
            cacheLock.lock();
            try {
                if (promptCache.containsKey(promptKey)) {
                    promptCache.remove(promptKey);
                    log.info("Cache cleared for {} (after ES delete)", promptKey);
                }
            } finally {
                cacheLock.unlock();
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to delete prompt {}: {}", promptKey, e);
            // Cache remains unchanged - consistent with ES state
            return false;
        }
    }

    public List<Map<String, Object>> searchPrompts(String keyword, String category) {
        try {
            // Build search query
            List<String> fields = Arrays.asList("prompt_key^2", "description^1.5", "prompt_content", "tags^1.2");
            Map<String, Object> multiMatch = Map.of(
                    "query", keyword,
                    "fields", fields,
                    "type", "best_fields"
            );

            Map<String, Object> multiMatchQuery = Map.of("multi_match", multiMatch);

            List<Map<String, Object>> mustQueries = new ArrayList<>();
            mustQueries.add(multiMatchQuery);

            List<Map<String, Object>> filters = new ArrayList<>();
            if (category != null) {
                Map<String, Object> categoryTerm = Map.of("category", category);
                Map<String, Object> categoryFilter = Map.of("term", categoryTerm);
                filters.add(categoryFilter);
            }

            Map<String, Object> bool = Map.of(
                    "must", mustQueries,
                    "filter", filters
            );
            Map<String, Object> query = Map.of("bool", bool);

            // Execute search
            Map<String, Object> descriptionHighlight = Map.of();
            Map<String, Object> promptContentHighlight = Map.of("fragment_size", 150);
            Map<String, Object> highlight = Map.of(
                    "description", descriptionHighlight,
                    "prompt_content", promptContentHighlight
            );

            Map<String, Object> sort = Map.of("order", "desc");
            Map<String, Object> scoreSort = Map.of("_score", sort);
            List<Map<String, Object>> sorts = new ArrayList<>();
            sorts.add(scoreSort);

            Map<String, Object> searchBody = Map.of(
                    "query", query,
                    "highlight", highlight,
                    "sort", sorts,
                    "size", 50
            );

            Map<String, Object> response = esClient.search(indexName, searchBody);

            List<Map<String, Object>> results = new ArrayList<>();
            Map<String, Object> hits = (Map<String, Object>) response.get("hits");
            if (hits != null) {
                List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
                for (Map<String, Object> hit : hitList) {
                    Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                    Map<String, Object> result = new HashMap<>(source);
                    result.put("id", hit.get("_id"));
                    result.put("score", hit.get("_score"));
                    if (hit.containsKey("highlight")) {
                        result.put("highlight", hit.get("highlight"));
                    }
                    results.add(result);
                }
            }

            return results;
        } catch (Exception e) {
            log.error("Failed to search prompts: {}", e);
            return new ArrayList<>();
        }
    }

    public void updateLocalVersionTracker(String promptKey, int version) {
        try {
            // Lazy import to avoid circular dependency
            VersionSyncCoordinator.getInstance(this).updateLocalVersion(promptKey, version);
        } catch (Exception e) {
            log.debug("Failed to update local version tracker: {}", e);
        }
    }

    public void startVersionSync() {
        try {
            VersionSyncCoordinator.getInstance(this).start();
            log.info("Version synchronization started");
        } catch (Exception e) {
            log.error("Failed to start version synchronization: {}", e);
        }
    }

    public void stopVersionSync() {
        try {
            VersionSyncCoordinator.getInstance(this).stop();
            log.info("Version synchronization stopped");
        } catch (Exception e) {
            log.error("Error stopping version synchronization: {}", e);
        }
    }

    public void close() {
        // Stop version sync first
        stopVersionSync();

        // Close database connection
        esClient.close();
    }

    // Static methods for global instance management
    public static PromptManager getInstance() {
        synchronized (PromptManager.class) {
            if (instance == null) {
                instance = new PromptManager();
                // Start version sync on first initialization
                try {
                    instance.startVersionSync();
                    versionSyncStarted = true;
                    log.info("Version sync auto-started with PromptManager");
                } catch (Exception e) {
                    log.error("Failed to auto-start version sync: {}", e);
                }
            }
            return instance;
        }
    }

    public static void closePromptManager() {
        synchronized (PromptManager.class) {
            if (instance != null) {
                try {
                    // Stop version sync if it was started
                    if (versionSyncStarted) {
                        instance.stopVersionSync();
                        versionSyncStarted = false;
                        log.info("Version sync stopped during shutdown");
                    }

                    instance.close();
                    log.info("Prompt manager closed successfully");
                } catch (Exception e) {
                    log.error("Error closing prompt manager: {}", e);
                } finally {
                    instance = null;
                }
            }
        }
    }

    public static String getDynamicPrompt(String promptKey, String fallbackContent, boolean useCache) {
        try {
            PromptManager manager = getInstance();
            return manager.getPromptContent(promptKey, fallbackContent, useCache);
        } catch (Exception e) {
            log.error("Failed to get dynamic prompt {}: {}", promptKey, e);
            return fallbackContent;
        }
    }

    public static String resolvePromptFromEs(String promptKey, String defaultPrompt, boolean useCache) {
        try {
            // Use the exact prompt key provided
            String promptContent = getDynamicPrompt(promptKey, defaultPrompt, useCache);

            if (promptContent != null && !promptContent.equals(defaultPrompt)) {
                log.info("Loaded hot prompt from ES: {}", promptKey);
                return promptContent;
            }

            // If no dynamic prompt found, use default or empty string
            if (defaultPrompt != null && !defaultPrompt.strip().isEmpty()) {
                log.info("Using default prompt for {}", promptKey);
                return defaultPrompt;
            } else {
                log.info("No prompt found for {}, using system default", promptKey);
                return "";
            }
        } catch (Exception e) {
            log.error("Failed to resolve hot prompt for {}: {}", promptKey, e);
            // Return default prompt or empty string on error
            return defaultPrompt != null ? defaultPrompt : "";
        }
    }

    // Getters for testing
    public String getIndexName() {
        return indexName;
    }

    public Map<String, Map<String, Object>> getPromptCache() {
        return promptCache;
    }
}