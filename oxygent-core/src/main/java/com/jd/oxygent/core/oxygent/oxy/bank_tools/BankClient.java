package com.jd.oxygent.core.oxygent.oxy.bank_tools;

import com.jd.oxygent.core.Mas;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyRequest;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyResponse;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyState;
import com.jd.oxygent.core.oxygent.utils.JsonUtils;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import com.jd.oxygent.core.Mas;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyRequest;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyResponse;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BankClient - 银行客户端，用于发现和注册远程银行工具
 */
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@Data
public class BankClient extends BaseBank {
    @Builder.Default
    private String serverUrl = "";
    @Builder.Default
    private List<String> includedBankNameList = new ArrayList<>();
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /**
     * 初始化方法，发现远程银行工具
     */
    @Override
    public void init() {
        super.init();

        try {
            String url = buildUrl(serverUrl, "list_banks");
            log.info("BankClient '{}': 从 {} 发现银行工具", getName(), url);

            // 构建请求头
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds((long)getTimeout()));

            // 添加headers
            if (headers != null && !headers.isEmpty()) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpRequest request = requestBuilder.build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 解析JSON响应
                String json = response.body();
                List<Map<String, Object>> tools = JsonUtils.readValue(json, List.class);
                addTools(tools);
            }

        } catch (Exception e) {
            log.error("BankClient初始化失败", e);
        }
    }

    /**
     * 添加工具到MAS
     */
    public void addTools(List<Map<String, Object>> toolsResponse) {
        if (toolsResponse == null) return;

        Mas mas = getMas();
        if (mas == null) return;

        // 排除的字段集合
        Set<String> excludeFields = new HashSet<>(Arrays.asList(
                "sseUrl",        // sse_url
                "serverUrl",     // server_url
                "includedBankNameList", // included_bank_name_list
                "name",
                "desc",
                "serverName",    // server_name
                "inputSchema",    // input_schema
                "className",
                "category",
                "semaphore",
                "descForLlm",
                "systemArgs",
                "isEntrance",
                "permittedToolNameList",
                "mas",
                "semaphoreCount",
                "extraPermittedToolNameList",
                "isMaster",
                "precedingPlaceholder",
                "funcProcessInput",
                "funcProcessOutput",
                "funcFormatInput",
                "funcFormatOutput",
                "_funcExecute",
                "funcInterceptor",
                "isRetrievable"
        ));

        // 获取排除字段后的参数
        Map<String, Object> params = modelDump(excludeFields);

        for (Map<String, Object> item : toolsResponse) {
            String toolName = getString(item, "name");
            String description = getString(item, "description");
            String endpoint = getString(item, "endpoint");

            if (toolName == null || endpoint == null) continue;

            includedBankNameList.add(toolName);

            String toolUrl = buildUrl(serverUrl, endpoint);

            // 创建BankTool
            BankTool bankTool = BankTool.builder()
                    .name(toolName)
                    .desc(description)
                    .serverName(getName())
                    .serverUrl(toolUrl)
                    .inputSchema((Map<String, Object>) item.get("inputSchema"))
                    .isRetrievable("retrieve".equals(item.get("type")))
                    .funcProcessInput(this.getFuncProcessInput())
                    .funcProcessOutput(this.getFuncProcessOutput())
                    .funcFormatInput(this.getFuncFormatInput())
                    .funcFormatOutput(this.getFuncFormatOutput())
                    ._funcExecute(this.get_funcExecute())
                    .funcInterceptor(this.getFuncInterceptor())
                    .build();

            // 应用参数
            applyParams(bankTool, params);

            bankTool.setMas(mas);
            mas.addOxy(bankTool);
        }
    }

    /**
     * 应用参数到目标对象
     */
    private void applyParams(Object target, Map<String, Object> params) {
        if (target == null || params == null || params.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            try {
                // 获取字段
                Field field = null;
                Class<?> clazz = target.getClass();

                // 查找字段（包括父类）
                while (clazz != null && field == null) {
                    try {
                        field = clazz.getDeclaredField(fieldName);
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                }

                if (field != null) {
                    field.setAccessible(true);
                    field.set(target, value);
                }

            } catch (Exception e) {
                log.debug("无法设置字段 {}: {}", fieldName, e.getMessage());
            }
        }
    }

    /**
     * 构建URL
     */
    private String buildUrl(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isEmpty()) return path;
        if (path == null || path.isEmpty()) return baseUrl;

        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        path = path.startsWith("/") ? path.substring(1) : path;

        return baseUrl + "/" + path;
    }

    /**
     * 解析JSON数组字符串
     */
    private List<Map<String, Object>> parseJsonArray(String json) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            // 简单JSON解析，实际应该使用Jackson等库
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1).trim();
                if (json.isEmpty()) return result;

                // 这里简化处理，实际应该完整解析
                String[] items = json.split("\\},\\{");
                for (String item : items) {
                    Map<String, Object> map = new HashMap<>();
                    item = item.replace("{", "").replace("}", "").trim();

                    String[] pairs = item.split(",");
                    for (String pair : pairs) {
                        String[] kv = pair.split(":");
                        if (kv.length == 2) {
                            String key = kv[0].trim().replace("\"", "");
                            String value = kv[1].trim().replace("\"", "");
                            map.put(key, value);
                        }
                    }

                    result.add(map);
                }
            }
        } catch (Exception e) {
            log.error("解析JSON失败", e);
        }

        return result;
    }

    /**
     * 从Map中获取字符串值
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 模型转储方法 - 对应Python的model_dump
     */
    public Map<String, Object> modelDump(Set<String> excludeFields) {
        Map<String, Object> result = new HashMap<>();

        try {
            Class<?> clazz = this.getClass();

            // 遍历所有字段（包括继承的）
            while (clazz != null && clazz != Object.class) {
                Field[] fields = clazz.getDeclaredFields();

                for (Field field : fields) {
                    // 跳过静态字段
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }

                    field.setAccessible(true);
                    String fieldName = field.getName();

                    // 跳过排除的字段
                    if (excludeFields != null && excludeFields.contains(fieldName)) {
                        continue;
                    }

                    try {
                        Object value = field.get(this);
                        result.put(fieldName, value);
                    } catch (IllegalAccessException e) {
                        log.warn("无法访问字段: {}", fieldName, e);
                    }
                }

                // 处理父类
                clazz = clazz.getSuperclass();
            }

        } catch (Exception e) {
            log.error("modelDump失败", e);
        }

        return result;
    }

    @Override
    protected OxyResponse _execute(OxyRequest oxyRequest) {
        return new OxyResponse(OxyState.FAILED, "This method is not yet implemented");
    }
}