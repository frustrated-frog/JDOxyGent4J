package com.jd.oxygent.core;

import com.jd.oxygent.core.oxygent.infra.databases.BaseEs;
import com.jd.oxygent.core.oxygent.infra.impl.databases.es.LocalEs;
import com.jd.oxygent.core.oxygent.samples.server.masprovider.factory.impl.platform.spring.ApplicationContextHolder;
import com.jd.oxygent.core.oxygent.schemas.evaluation.ConversationRating;
import com.jd.oxygent.core.oxygent.schemas.evaluation.RatingRequest;
import com.jd.oxygent.core.oxygent.schemas.evaluation.RatingResponse;
import com.jd.oxygent.core.oxygent.schemas.evaluation.RatingStats;
import com.jd.oxygent.core.oxygent.schemas.evaluation.RatingType;
import com.jd.oxygent.core.oxygent.utils.CommonUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class EvaluationManager {
    private static EvaluationManager instance;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private BaseEs esClient;

    private String appName;
    private String ratingIndex;
    private String ratingStatsIndex;

    public static EvaluationManager getInstance() {
        synchronized (EvaluationManager.class) {
            if (instance == null) {
                instance = new EvaluationManager();
            }
        }
        instance.esClient = ApplicationContextHolder.getBean(BaseEs.class);
        if (instance.esClient == null) {
            instance.esClient = new LocalEs();
        }
        instance.appName = Config.getAppName();
        instance.ratingIndex = instance.appName + "_rating";
        instance.ratingStatsIndex = instance.appName + "_rating_stats";
        return instance;
    }

    private EvaluationManager() {
    }

    private RatingStats createEmptyStats(String traceId) {
        return new RatingStats(
                traceId,
                0,
                0,
                0,
                0.0,
                LocalDateTime.now().format(DATETIME_FORMATTER)
        );
    }

    private int getHitsTotal(Map<String, Object> response) {
        if (response == null) {
            return 0;
        }
        Map<String, Object> hits = (Map<String, Object>) response.getOrDefault("hits", Map.of());
        Object total = hits.get("total");
        if (total != null) {
            if (total instanceof Map) {
                return (Integer) ((Map) total).getOrDefault("value", 0);
            } else {
                return (Integer) total;
            }
        }
        List<?> hitsList = (List<?>) hits.getOrDefault("hits", List.of());
        return hitsList.size();
    }

    private void refreshIndex(String indexName) {
        try {
            esClient.refreshIndex(indexName);
        } catch (Exception e) {
            log.warn("Failed to refresh index " + indexName + ": " + e.getMessage());
        }
    }

    public RatingResponse createRating(RatingRequest ratingRequest, HttpServletRequest request, Optional<String> userId) {
        try {
            boolean traceExists = checkTraceExists(ratingRequest.getTraceId());
            if (!traceExists) {
                log.warn("Trace does not exist: " + ratingRequest.getTraceId() + ", but allowing rating to continue");
            }
            String currentTime = LocalDateTime.now().format(DATETIME_FORMATTER);
            String ratingId = UUID.randomUUID().toString();
            String userIp = CommonUtils.getClientIp(request);
            ConversationRating rating = new ConversationRating(
                    ratingId,
                    ratingRequest.getTraceId(),
                    ratingRequest.getRatingType(),
                    userId.orElse(null),
                    userIp,
                    ratingRequest.getComment(),
                    ratingRequest.getErp(),
                    currentTime
            );
            esClient.index(ratingIndex, ratingId, rating.toMap());
            refreshIndex(ratingIndex);
            RatingStats stats = updateRatingStats(ratingRequest.getTraceId(), Optional.ofNullable(ratingRequest.getRatingType()));
            return new RatingResponse(true, ratingId, stats, "Rating successful");
        } catch (Exception e) {
            log.error("Failed to create/update rating: " + e.getMessage(), e);
            return new RatingResponse(false, null, null, "Rating failed: " + e.getMessage());
        }
    }

    private boolean checkTraceExists(String traceId) {
        try {
            String traceIndex = appName + "_trace";

            Map<String, Object> query = new HashMap<>();
            Map<String, Object> termQuery = new HashMap<>();
            termQuery.put("trace_id", traceId);
            Map<String, Object> innerQuery = new HashMap<>();
            innerQuery.put("term", termQuery);
            query.put("query", innerQuery);
            query.put("size", 1);

            Map<String, Object> response = esClient.search(traceIndex, query);

            boolean exists = getHitsTotal(response) > 0;

            if (!exists) {
                log.warn("Trace record not found: " + traceId + ", but allowing rating to continue (possible data delay)");
            }

            return exists;
        } catch (Exception e) {
            log.warn("Failed to check trace existence: " + e.getMessage(), e);
            return true;
        }
    }

    public RatingStats updateRatingStats(String traceId, Optional<RatingType> knownRatingType) {
        try {
            int likeCount = 0;
            int dislikeCount = 0;
            int totalRatings = 0;

            Map<String, Object> query = new HashMap<>();
            Map<String, Object> termQuery = new HashMap<>();
            termQuery.put("trace_id", traceId);
            Map<String, Object> innerQuery = new HashMap<>();
            innerQuery.put("term", termQuery);
            query.put("query", innerQuery);
            query.put("size", 1000);

            Map<String, Object> response = esClient.search(ratingIndex, query);

            if (response == null) {
                log.warn("No rating data found for trace_id " + traceId + " (index may not exist)");
                return createEmptyStats(traceId);
            }

            totalRatings = getHitsTotal(response);

            Map<String, Object> hits = (Map<String, Object>) response.getOrDefault("hits", Map.of());
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.getOrDefault("hits", List.of());

            for (Map<String, Object> hit : hitsList) {
                Map<String, Object> source = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
                String ratingType = (String) source.getOrDefault("rating_type", "");

                if (RatingType.LIKE.toString().equals(ratingType)) {
                    likeCount++;
                } else if (RatingType.DISLIKE.toString().equals(ratingType)) {
                    dislikeCount++;
                }
            }

            double satisfactionRate = totalRatings > 0 ? (likeCount / (double) totalRatings) * 100.0 : 0.0;

            RatingStats stats = new RatingStats(
                    traceId,
                    likeCount,
                    dislikeCount,
                    totalRatings,
                    satisfactionRate,
                    LocalDateTime.now().format(DATETIME_FORMATTER)
            );
            esClient.index(ratingStatsIndex, traceId, stats.toMap());
            refreshIndex(ratingStatsIndex);
            return stats;
        } catch (Exception e) {
            log.error("Failed to update rating stats for trace_id=" + traceId + ": " + e.getMessage(), e);
            return createEmptyStats(traceId);
        }
    }

    public Optional<RatingStats> getRatingStats(String traceId) {
        try {
            Map<String, Object> query = new HashMap<>();
            Map<String, Object> termQuery = new HashMap<>();
            termQuery.put("trace_id", traceId);
            Map<String, Object> innerQuery = new HashMap<>();
            innerQuery.put("term", termQuery);
            query.put("query", innerQuery);
            query.put("size", 1);

            Map<String, Object> response = esClient.search(ratingStatsIndex, query);

            if (response == null) {
                log.warn("Rating stats index not found for trace_id " + traceId);
                return Optional.empty();
            }

            if (getHitsTotal(response) > 0) {
                Map<String, Object> hits = (Map<String, Object>) response.getOrDefault("hits", Map.of());
                List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.getOrDefault("hits", List.of());
                Map<String, Object> source = (Map<String, Object>) hitsList.get(0).getOrDefault("_source", Map.of());
                return Optional.of(RatingStats.fromMap(source));
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get rating stats for " + traceId + ": " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Map<String, RatingStats> getRatingsForTraces(List<String> traceIds) {
        try {
            if (traceIds == null || traceIds.isEmpty()) {
                return Map.of();
            }

            Map<String, Object> query = new HashMap<>();
            Map<String, Object> termsQuery = new HashMap<>();
            termsQuery.put("trace_id", traceIds);
            Map<String, Object> innerQuery = new HashMap<>();
            innerQuery.put("terms", termsQuery);
            query.put("query", innerQuery);
            query.put("size", 10000);

            Map<String, Object> response = esClient.search(ratingStatsIndex, query);

            if (response == null) {
                log.warn("Rating stats index not found when fetching batch stats");
                return Map.of();
            }

            Map<String, RatingStats> result = new HashMap<>();
            Map<String, Object> hits = (Map<String, Object>) response.getOrDefault("hits", Map.of());
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.getOrDefault("hits", List.of());

            for (Map<String, Object> hit : hitsList) {
                Map<String, Object> source = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
                String traceId = (String) source.getOrDefault("trace_id", "");
                result.put(traceId, RatingStats.fromMap(source));
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to get ratings for traces: " + e.getMessage(), e);
            return Map.of();
        }
    }

    public List<ConversationRating> getRatingHistory(String traceId, Optional<String> erp) {
        try {
            Map<String, Object> query = new HashMap<>();
            Map<String, Object> boolQuery = new HashMap<>();
            List<Map<String, Object>> mustClauses = new ArrayList<>();

            Map<String, Object> traceIdTerm = new HashMap<>();
            traceIdTerm.put("trace_id", traceId);
            Map<String, Object> traceIdQuery = new HashMap<>();
            traceIdQuery.put("term", traceIdTerm);
            mustClauses.add(traceIdQuery);

            if (erp.isPresent()) {
                Map<String, Object> erpTerm = new HashMap<>();
                erpTerm.put("erp", erp.get());
                Map<String, Object> erpQuery = new HashMap<>();
                erpQuery.put("term", erpTerm);
                mustClauses.add(erpQuery);
            }

            boolQuery.put("must", mustClauses);
            Map<String, Object> innerQuery = new HashMap<>();
            innerQuery.put("bool", boolQuery);
            query.put("query", innerQuery);
            query.put("size", 1000);

            Map<String, Object> response = esClient.search(ratingIndex, query);

            if (response == null) {
                log.warn("No rating data found for trace_id " + traceId + " (index may not exist)");
                return List.of();
            }

            List<ConversationRating> ratings = new ArrayList<>();
            Map<String, Object> hits = (Map<String, Object>) response.getOrDefault("hits", Map.of());
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.getOrDefault("hits", List.of());

            for (Map<String, Object> hit : hitsList) {
                Map<String, Object> source = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
                ratings.add(ConversationRating.fromMap(source));
            }

            ratings.sort((r1, r2) -> r2.getCreateTime().compareTo(r1.getCreateTime()));

            return ratings;
        } catch (Exception e) {
            log.error("Failed to get rating history for " + traceId + ": " + e.getMessage(), e);
            return List.of();
        }
    }

    public List<ConversationRating> getRatingHistory(String traceId) {
        return getRatingHistory(traceId, Optional.empty());
    }

    public Map<String, List<ConversationRating>> getRatingHistoriesForTraces(List<String> traceIds) {
        try {
            if (traceIds == null || traceIds.isEmpty()) {
                return Map.of();
            }

            Map<String, Object> searchBody = new HashMap<>();
            searchBody.put("query", Map.of("terms", Map.of("trace_id", traceIds)));
            searchBody.put("size", 10000);
            searchBody.put("sort", List.of(Map.of("create_time", Map.of("order", "desc"))));

            Map<String, Object> response = esClient.search(ratingIndex, searchBody);

            if (response == null) {
                log.warn("No rating data found when fetching batch histories");
                return Map.of();
            }

            Map<String, List<ConversationRating>> result = new HashMap<>();
            Map<String, Object> hits = (Map<String, Object>) response.getOrDefault("hits", Map.of());
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.getOrDefault("hits", List.of());

            for (Map<String, Object> hit : hitsList) {
                Map<String, Object> source = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
                String traceId = (String) source.getOrDefault("trace_id", "");
                ConversationRating rating = ConversationRating.fromMap(source);

                result.computeIfAbsent(traceId, k -> new ArrayList<>()).add(rating);
            }

            result.values().forEach(list -> list.sort((r1, r2) -> r2.getCreateTime().compareTo(r1.getCreateTime())));

            return result;
        } catch (Exception e) {
            log.error("Failed to get rating histories for traces: " + e.getMessage(), e);
            return Map.of();
        }
    }

    public boolean deleteRating(String ratingId) {
        try {
            Map<String, Object> query = new HashMap<>();
            Map<String, Object> termQuery = new HashMap<>();
            termQuery.put("rating_id", ratingId);
            Map<String, Object> innerQuery = new HashMap<>();
            innerQuery.put("term", termQuery);
            query.put("query", innerQuery);
            query.put("size", 1);

            Map<String, Object> response = esClient.search(ratingIndex, query);

            if (response == null || getHitsTotal(response) == 0) {
                termQuery.put("trace_id", ratingId);
                innerQuery.put("term", termQuery);
                query.put("query", innerQuery);

                response = esClient.search(ratingIndex, query);
            }

            if (response == null || getHitsTotal(response) == 0) {
                return false;
            }

            Map<String, Object> hits = (Map<String, Object>) response.getOrDefault("hits", Map.of());
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.getOrDefault("hits", List.of());
            Map<String, Object> source = (Map<String, Object>) hitsList.get(0).getOrDefault("_source", Map.of());
            String traceId = (String) source.getOrDefault("trace_id", "");
            String docId = (String) hitsList.get(0).getOrDefault("_id", "");

            esClient.delete(ratingIndex, docId);

            updateRatingStats(traceId, Optional.empty());

            log.info("Deleted rating for trace " + traceId);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete rating " + ratingId + ": " + e.getMessage(), e);
            return false;
        }
    }

    public Map<String, Object> getOverallRatingStats(int days) {
        try {
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusDays(days);
            String startDateStr = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd 00:00:00.000000"));
            String endDateStr = endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd 23:59:59.999999"));

            Map<String, Object> query = new HashMap<>();
            Map<String, Object> rangeQuery = new HashMap<>();
            Map<String, Object> createTimeRange = new HashMap<>();
            createTimeRange.put("gte", startDateStr);
            createTimeRange.put("lte", endDateStr);
            rangeQuery.put("create_time", createTimeRange);
            Map<String, Object> innerQuery = new HashMap<>();
            innerQuery.put("range", rangeQuery);
            query.put("query", innerQuery);
            query.put("size", 10000);

            Map<String, Object> response = esClient.search(ratingIndex, query);

            if (response == null) {
                log.warn("Rating index not found when generating trend report");
                Map<String, Object> result = new HashMap<>();
                result.put("total_ratings", 0);
                result.put("like_count", 0);
                result.put("dislike_count", 0);
                result.put("like_rate", 0.0);
                result.put("daily_stats", new HashMap<>());
                return result;
            }

            int totalRatings = 0;
            int likeCount = 0;
            int dislikeCount = 0;
            Map<String, Map<String, Integer>> dailyStats = new HashMap<>();

            Map<String, Object> hits = (Map<String, Object>) response.getOrDefault("hits", Map.of());
            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hits.getOrDefault("hits", List.of());

            for (Map<String, Object> hit : hitsList) {
                Map<String, Object> source = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
                totalRatings++;

                String ratingType = (String) source.getOrDefault("rating_type", "");
                if (RatingType.LIKE.toString().equals(ratingType)) {
                    likeCount++;
                } else {
                    dislikeCount++;
                }

                String createTime = (String) source.getOrDefault("create_time", "");
                String dateStr = createTime.split(" ")[0];
                Map<String, Integer> daily = dailyStats.computeIfAbsent(dateStr, k -> new HashMap<>());
                daily.put(ratingType, daily.getOrDefault(ratingType, 0) + 1);
            }

            double overallSatisfaction = totalRatings > 0 ? (likeCount / (double) totalRatings * 100) : 0.0;

            Map<String, Object> result = new HashMap<>();
            result.put("total_ratings", totalRatings);
            result.put("like_count", likeCount);
            result.put("dislike_count", dislikeCount);
            result.put("satisfaction_rate", Math.round(overallSatisfaction * 100.0) / 100.0);
            result.put("daily_stats", dailyStats);

            Map<String, Object> timeRange = new HashMap<>();
            timeRange.put("start_date", startDateStr);
            timeRange.put("end_date", endDateStr);
            timeRange.put("days", days);
            result.put("time_range", timeRange);

            return result;
        } catch (Exception e) {
            log.error("Failed to get overall rating stats: " + e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("total_ratings", 0);
            result.put("like_count", 0);
            result.put("dislike_count", 0);
            result.put("satisfaction_rate", 0.0);
            result.put("daily_stats", new HashMap<>());
            result.put("error", e.getMessage());
            return result;
        }
    }

    public Map<String, Object> clearAllRatingData() {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("deleted_ratings", 0);
            result.put("deleted_stats", 0);
            List<String> errors = new ArrayList<>();

            Map<String, Object> query = new HashMap<>();
            Map<String, Object> matchAll = new HashMap<>();
            matchAll.put("match_all", new HashMap<>());
            query.put("query", matchAll);
            query.put("size", 1000);

            Map<String, Object> ratingResponse = esClient.search(ratingIndex, query);

            int ratingCount = getHitsTotal(ratingResponse);

            if (ratingCount > 0) {
                try {
                    esClient.deleteIndex(ratingIndex);
                } catch (Exception e) {
                    log.error("Failed to delete rating records", e);
                }
            }

            Map<String, Object> statsResponse = esClient.search(ratingStatsIndex, query);

            int statsCount = getHitsTotal(statsResponse);

            if (statsCount > 0) {
                try {
                    esClient.deleteIndex(ratingStatsIndex);
                } catch (Exception e) {
                    log.error("Failed to delete rating statistics", e);
                }
            }

            if (!errors.isEmpty()) {
                result.put("success", false);
            }
            result.put("errors", errors);

            return result;
        } catch (Exception e) {
            log.error("Failed to clear rating data", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("deleted_ratings", 0);
            result.put("deleted_stats", 0);
            List<String> errors = new ArrayList<>();
            errors.add("Clear failed: " + e.getMessage());
            result.put("errors", errors);
            return result;
        }
    }

    public Map<String, Object> ensureRatingIndicesWithCorrectMapping() {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("rating_index_created", false);
            result.put("rating_stats_index_created", false);
            List<String> errors = new ArrayList<>();

            Map<String, Object> ratingMapping = new HashMap<>();
            Map<String, Object> settings = new HashMap<>();
            settings.put("number_of_shards", 1);
            settings.put("number_of_replicas", 0);
            ratingMapping.put("settings", settings);

            Map<String, Object> mappings = new HashMap<>();
            Map<String, Object> properties = new HashMap<>();

            Map<String, Object> ratingIdProp = new HashMap<>();
            ratingIdProp.put("type", "keyword");
            properties.put("rating_id", ratingIdProp);

            Map<String, Object> traceIdProp = new HashMap<>();
            traceIdProp.put("type", "keyword");
            properties.put("trace_id", traceIdProp);

            Map<String, Object> ratingTypeProp = new HashMap<>();
            ratingTypeProp.put("type", "keyword");
            properties.put("rating_type", ratingTypeProp);

            Map<String, Object> userIdProp = new HashMap<>();
            userIdProp.put("type", "keyword");
            properties.put("user_id", userIdProp);

            Map<String, Object> userIpProp = new HashMap<>();
            userIpProp.put("type", "ip");
            properties.put("user_ip", userIpProp);

            Map<String, Object> commentProp = new HashMap<>();
            commentProp.put("type", "text");
            properties.put("comment", commentProp);

            Map<String, Object> erpProp = new HashMap<>();
            erpProp.put("type", "keyword");
            properties.put("erp", erpProp);

            Map<String, Object> createTimeProp = new HashMap<>();
            createTimeProp.put("type", "keyword");
            properties.put("create_time", createTimeProp);

            Map<String, Object> updateTimeProp = new HashMap<>();
            updateTimeProp.put("type", "keyword");
            properties.put("update_time", updateTimeProp);

            mappings.put("properties", properties);
            ratingMapping.put("mappings", mappings);

            Map<String, Object> ratingStatsMapping = new HashMap<>();
            ratingStatsMapping.put("settings", settings);

            Map<String, Object> statsMappings = new HashMap<>();
            Map<String, Object> statsProperties = new HashMap<>();

            Map<String, Object> statsTraceIdProp = new HashMap<>();
            statsTraceIdProp.put("type", "keyword");
            statsProperties.put("trace_id", statsTraceIdProp);

            Map<String, Object> likeCountProp = new HashMap<>();
            likeCountProp.put("type", "integer");
            statsProperties.put("like_count", likeCountProp);

            Map<String, Object> dislikeCountProp = new HashMap<>();
            dislikeCountProp.put("type", "integer");
            statsProperties.put("dislike_count", dislikeCountProp);

            Map<String, Object> totalRatingsProp = new HashMap<>();
            totalRatingsProp.put("type", "integer");
            statsProperties.put("total_ratings", totalRatingsProp);

            Map<String, Object> satisfactionRateProp = new HashMap<>();
            satisfactionRateProp.put("type", "float");
            statsProperties.put("satisfaction_rate", satisfactionRateProp);

            Map<String, Object> lastUpdatedProp = new HashMap<>();
            lastUpdatedProp.put("type", "keyword");
            statsProperties.put("last_updated", lastUpdatedProp);

            statsMappings.put("properties", statsProperties);
            ratingStatsMapping.put("mappings", statsMappings);

            try {
                Map<String, Object> ratingResult = esClient.createIndex(ratingIndex, ratingMapping);

                if (!Boolean.TRUE.equals(ratingResult.getOrDefault("already_exists", false))) {
                    result.put("rating_index_created", true);
                    log.info("Created rating record index: " + ratingIndex);
                } else {
                    log.info("Rating record index already exists: " + ratingIndex);
                }
            } catch (Exception e) {
                String errorMsg = "Failed to create rating record index: " + e.getMessage();
                errors.add(errorMsg);
                log.error(errorMsg);
            }

            try {
                Map<String, Object> statsResult = esClient.createIndex(ratingIndex, ratingMapping);

                if (!Boolean.TRUE.equals(statsResult.getOrDefault("already_exists", false))) {
                    result.put("rating_stats_index_created", true);
                    log.info("Created rating statistics index: " + ratingStatsIndex);
                } else {
                    log.info("Rating statistics index already exists: " + ratingStatsIndex);
                }
            } catch (Exception e) {
                String errorMsg = "Failed to create rating statistics index: " + e.getMessage();
                errors.add(errorMsg);
                log.error(errorMsg);
            }

            if (!errors.isEmpty()) {
                result.put("success", false);
            }
            result.put("errors", errors);

            return result;
        } catch (Exception e) {
            log.error("Failed to ensure index mapping: " + e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("rating_index_created", false);
            result.put("rating_stats_index_created", false);
            List<String> errors = new ArrayList<>();
            errors.add("Operation failed: " + e.getMessage());
            result.put("errors", errors);
            return result;
        }
    }
}