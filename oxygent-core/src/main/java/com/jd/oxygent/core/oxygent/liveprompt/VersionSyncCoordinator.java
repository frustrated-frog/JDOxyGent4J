package com.jd.oxygent.core.oxygent.liveprompt;

import com.jd.oxygent.core.Config;
import com.jd.oxygent.core.oxygent.infra.databases.BaseEs;
import com.jd.oxygent.core.oxygent.infra.impl.databases.es.LocalEs;
import com.jd.oxygent.core.oxygent.samples.server.masprovider.factory.impl.platform.spring.ApplicationContextHolder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author OxyGent Team
 * @version 1.0.10.4
 * @since 1.0.10.4
 */
@Slf4j
public class VersionSyncCoordinator {
    private static VersionSyncCoordinator instance;

    private PromptManager promptManager;
    private BaseEs esClient;

    private int pollingInterval;
    private boolean useEsPolling;
    private ScheduledExecutorService pollingExecutor;

    private boolean isRunning;
    private Map<String, Integer> localVersions = new ConcurrentHashMap<>();  // Track local versions
    private Map<String, Set<Integer>> pendingUpdates = new ConcurrentHashMap<>();  // Track pending updates: {promptKey: {version, ...}}

    public VersionSyncCoordinator(PromptManager promptManager, Integer pollingInterval) {
        this.promptManager = promptManager;
        esClient = ApplicationContextHolder.getBean(BaseEs.class);
        if (esClient == null) {
            esClient = new LocalEs();
        }
        // Read polling interval from config, or use default value
        if (pollingInterval == null) {
            this.pollingInterval = Config.getLivePrompt().getEsPollingInterval();
        } else {
            this.pollingInterval = pollingInterval;
        }

        this.useEsPolling = false;
        this.isRunning = false;
        this.detectSyncMechanisms();
    }

    private void detectSyncMechanisms() {
        if (esClient instanceof LocalEs) {
            log.info("Local ES detected, polling disabled for multi-instance sync");
        } else {
            this.useEsPolling = true;
            log.info("ES polling enabled for remote hosts");
        }
    }

    public void start() {
        if (isRunning) {
            log.warn("Version sync coordinator is already running");
            return;
        }

        isRunning = true;

        // Initialize local versions from current cache
        initializeLocalVersions();

        // Start ES polling if enabled
        if (useEsPolling) {
            startEsPolling();
        }

        log.info("Version sync coordinator started");
    }

    public void stop() {
        if (!isRunning) {
            return;
        }

        isRunning = false;

        // Stop ES polling
        if (useEsPolling && pollingExecutor != null) {
            stopEsPolling();
        }

        log.info("Version sync coordinator stopped");
    }

    private void initializeLocalVersions() {
        // Initialize local version tracking from current cache
        Map<String, Map<String, Object>> cacheSnapshot = promptManager.getPromptCache();
        for (Map.Entry<String, Map<String, Object>> entry : cacheSnapshot.entrySet()) {
            String promptKey = entry.getKey();
            Map<String, Object> promptData = entry.getValue();
            int version = ((Long) promptData.getOrDefault("version", 1)).intValue();
            localVersions.put(promptKey, version);
        }
    }

    private void startEsPolling() {
        log.info(
                "Starting ES polling with {}s interval "
                        + "(configured in live_prompt.es_polling_interval)",
                pollingInterval
        );

        pollingExecutor = new ScheduledThreadPoolExecutor(1);
        pollingExecutor.scheduleAtFixedRate(
                this::esPoller,
                0,
                pollingInterval,
                TimeUnit.SECONDS
        );
    }

    private void stopEsPolling() {
        if (pollingExecutor != null) {
            pollingExecutor.shutdownNow();
            try {
                if (!pollingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("ES polling executor did not terminate properly");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("ES polling stopped");
        }
    }

    private void esPoller() {
        if (isRunning && useEsPolling) {
            try {
                checkEsVersions();
            } catch (Exception e) {
                log.error("ES polling error: {}", e);
            }
        }
    }

    private void checkEsVersions() {
        try {
            // Get all prompts from ES
            Map<String, Object> searchBody = new HashMap<>();
            Map<String, Object> query = new HashMap<>();
            query.put("match_all", Map.of());
            searchBody.put("query", query);
            searchBody.put("size", 1000);
            searchBody.put("_source", List.of("version", "updated_at"));

            Map<String, Object> response = promptManager.getEsClient().search(promptManager.getIndexName(), searchBody);

            if (response == null) {
                return;
            }

            Map<String, Object> hits = (Map<String, Object>) response.get("hits");
            if (hits != null) {
                List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
                for (Map<String, Object> hit : hitList) {
                    String promptKey = (String) hit.get("_id");
                    Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                    if (source != null) {
                        int remoteVersion = ((Long) source.getOrDefault("version", 1)).intValue();

                        // Check if local version is behind
                        int localVersion = localVersions.getOrDefault(promptKey, 0);
                        if (remoteVersion > localVersion) {
                            handleVersionUpdate(promptKey, remoteVersion);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking ES versions: {}", e);
        }
    }

    private void handleVersionUpdate(String promptKey, int newVersion) {
        try {
            // Prevent duplicate updates for the same version
            synchronized (pendingUpdates) {
                Set<Integer> versions = pendingUpdates.getOrDefault(promptKey, new HashSet<>());
                if (versions.contains(newVersion)) {
                    log.debug("Skipping duplicate update for {} v{}", promptKey, newVersion);
                    return;
                }

                // Prevent version rollback: only accept if newVersion > current version
                int currentVersion = localVersions.getOrDefault(promptKey, 0);
                if (newVersion <= currentVersion) {
                    log.debug(
                            "Ignoring old version for {}: new={}, current={}",
                            promptKey, newVersion, currentVersion
                    );
                    return;
                }

                // Mark as pending to prevent concurrent updates
                versions.add(newVersion);
                pendingUpdates.put(promptKey, versions);
            }

            try {
                // Fetch from ES with retry logic
                fetchFromEsWithRetry(promptKey, newVersion, 3);
            } finally {
                // Remove from pending after processing (with cleanup of old versions)
                synchronized (pendingUpdates) {
                    Set<Integer> versions = pendingUpdates.get(promptKey);
                    if (versions != null) {
                        versions.remove(newVersion);
                        // Clean up old versions to prevent memory leak (keep last 10)
                        if (versions.size() > 10) {
                            // Remove oldest versions (keep higher version numbers)
                            List<Integer> versionsList = versions.stream().sorted().toList();
                            List<Integer> versionsToRemove = versionsList.subList(0, versionsList.size() - 10);
                            for (int v : versionsToRemove) {
                                versions.remove(v);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle version update for {}: {}", promptKey, e);
        }
    }

    private void fetchFromEsWithRetry(String promptKey, int newVersion, int maxRetries) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // Force fetch from ES (bypass cache)
                Map<String, Object> promptData = promptManager.getPrompt(promptKey, false);

                if (promptData != null) {
                    int actualVersion = ((Long) promptData.getOrDefault("version", 1)).intValue();
                    if (actualVersion == newVersion) {
                        // Update local version tracker
                        localVersions.put(promptKey, newVersion);

                        log.debug(
                                "Cache updated for {} from ES "
                                        + "(version {}, attempt {}/{})",
                                promptKey, newVersion, attempt + 1, maxRetries
                        );

                        // Trigger hot-reload for agents using this prompt
                        DynamicAgentManager dynamicAgentManager = DynamicAgentManager.getInstance();
                        dynamicAgentManager.updatePromptByKey(promptKey);
                        return;
                    } else {
                        log.debug(
                                "Version mismatch for {} on attempt {}: "
                                        + "expected={}, got={}",
                                promptKey, attempt + 1, newVersion, actualVersion
                        );
                        if (actualVersion > newVersion) {
                            // Newer version already exists, update our tracker and skip
                            localVersions.put(promptKey, actualVersion);
                            log.info("Found newer version {} for {}, skipping update", actualVersion, promptKey);
                            return;
                        }
                        // If actualVersion < newVersion, continue retrying
                    }
                } else {
                    log.debug(
                            "Prompt {} not found in ES during version sync "
                                    + "(attempt {}/{})",
                            promptKey, attempt + 1, maxRetries
                    );
                }

            } catch (Exception e) {
                log.error(
                        "Error fetching {} from ES (attempt {}): {}",
                        promptKey, attempt + 1, e
                );
            }

            // Wait before retry (exponential backoff: 0.5s, 1s, 2s)
            if (attempt < maxRetries - 1) {
                long waitTime = (long) (0.5 * Math.pow(2, attempt));
                log.debug("Retrying {} after {}s...", promptKey, waitTime);
                try {
                    Thread.sleep(waitTime * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // All retries failed, log warning
        log.warn(
                "Failed to fetch {} v{} from ES after {} attempts. "
                        + "Will sync on next ES polling cycle.",
                promptKey, newVersion, maxRetries
        );
    }

    public void updateLocalVersion(String promptKey, int version) {
        localVersions.put(promptKey, version);
        log.debug("Updated local version tracker: {} v{}", promptKey, version);
    }

    // Static methods for global instance management
    public static VersionSyncCoordinator getInstance(PromptManager promptManager) {
        synchronized (VersionSyncCoordinator.class) {
            if (instance == null) {
                instance = new VersionSyncCoordinator(promptManager, null);
            }
            return instance;
        }
    }

    public static VersionSyncCoordinator getInstance() {
        synchronized (VersionSyncCoordinator.class) {
            if (instance == null) {
                PromptManager promptManager = PromptManager.getInstance();
                instance = new VersionSyncCoordinator(promptManager, null);
            }
            return instance;
        }
    }
}