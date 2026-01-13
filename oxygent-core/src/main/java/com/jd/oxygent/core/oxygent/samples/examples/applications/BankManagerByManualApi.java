package com.jd.oxygent.core.oxygent.samples.examples.applications;

import com.jd.oxygent.core.Config;
import com.jd.oxygent.core.oxygent.samples.server.ServerApp;
import com.jd.oxygent.core.oxygent.samples.server.annotation.ApiEndpoint;
import com.jd.oxygent.core.oxygent.samples.server.utils.GlobalDefaultOxySpaceMapping;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

/**
 * 银行管理器手动API - Python转换版
 */
@Slf4j
public class BankManagerByManualApi {

    static {
        Config.getServer().setPort(8997);
    }

    private Map<String, String> userProfiles = new HashMap<>();

    public BankManagerByManualApi() {
        // 初始化一些示例数据
        userProfiles.put("001", "Arlen, a student, likes music");
        userProfiles.put("002", "Tom, a programmer, likes sports");
    }

    /**
     * 用户档案检索请求类
     */
    @Data
    public static class RetrievalRequest {
        private String query;
        private String agent_pin;
        private String user_pin;
    }

    @ApiEndpoint(
            path = "/user_profile_retrieve",
            method = ApiEndpoint.HttpMethod.POST,
            description = "A tool for querying user profile",
            tags = {"bank"}
    )
    public String userProfileRetrieve(RetrievalRequest request) {
        log.info("Querying user profile - user_pin: {}, agent_pin: {}, query: {}",
                request.getUser_pin(), request.getAgent_pin(), request.getQuery());

        String portrait = userProfiles.getOrDefault(request.getUser_pin(), "Nothing");
        return String.format("The current user profile is: %s", portrait);
    }

    /**
     * 用户档案存储请求类
     */
    @Data
    public static class DepositRequest {
        private String content;
        private String agent_pin;
        private String user_pin;
    }

    @ApiEndpoint(
            path = "/user_profile_deposit",
            method = ApiEndpoint.HttpMethod.POST,
            description = "A tool for updating user profile",
            tags = {"bank"}
    )
    public String userProfileDeposit(DepositRequest request) {
        log.info("Updating user profile - user_pin: {}, agent_pin: {}, content: {}",
                request.getUser_pin(), request.getAgent_pin(), request.getContent());

        // 这里可以添加实际的存储逻辑
        userProfiles.put(request.getUser_pin(), request.getContent());
        return "updated user_profile";
    }

    @ApiEndpoint(
            path = "/list_banks",
            method = ApiEndpoint.HttpMethod.GET,
            description = "List all bank API endpoints",
            tags = {"bank"}
    )
    public Object[] listBanks() {
        return new Object[] {
                Map.of(
                        "name", "user_profile_retrieve",
                        "endpoint", "/user_profile_retrieve",
                        "type", "retrieve",
                        "description", "A tool for querying user profile",
                        "inputSchema", Map.of(
                                "properties", Map.of(
                                        "query", Map.of("description", "query", "type", "str"),
                                        "agent_pin", Map.of("description", "SystemArg.agent_pin", "type", "str"),
                                        "user_pin", Map.of("description", "SystemArg.user_pin", "type", "str")
                                ),
                                "required", new String[]{"query", "agent_pin", "user_pin"},
                                "type", "object"
                        )
                ),
                Map.of(
                        "name", "user_profile_deposit",
                        "endpoint", "/user_profile_deposit",
                        "type", "deposit",
                        "description", "A tool for updating user profile",
                        "inputSchema", Map.of(
                                "properties", Map.of(
                                        "content", Map.of("description", "content", "type", "str"),
                                        "agent_pin", Map.of("description", "SystemArg.agent_pin", "type", "str"),
                                        "user_pin", Map.of("description", "SystemArg.user_pin", "type", "str")
                                ),
                                "required", new String[]{"content", "agent_pin", "user_pin"},
                                "type", "object"
                        )
                )
        };
    }

    public static void main(String[] args) throws Exception {
        GlobalDefaultOxySpaceMapping.searchCurrentThreadStackAnnotationOxySpaceName(Thread.currentThread().getStackTrace()[1].getClassName());
        ServerApp.main(args);
    }
}