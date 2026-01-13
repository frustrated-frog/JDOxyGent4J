package com.jd.oxygent.core.oxygent.samples.server.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.oxygent.core.oxygent.samples.server.scanner.ApiEndpointScanner;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 注解驱动的API Servlet
 * 处理所有通过@ApiEndpoint注解注册的接口
 */
@Slf4j
public class AnnotationApiServlet extends HttpServlet {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void init() throws ServletException {
        super.init();
        log.info("AnnotationApiServlet initialized");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp, "GET");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp, "POST");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp, "PUT");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleRequest(req, resp, "DELETE");
    }

    /**
     * 统一处理请求
     */
    private void handleRequest(HttpServletRequest req, HttpServletResponse resp, String httpMethod) throws IOException {
        String path = req.getPathInfo();

        // 获取端点信息
        ApiEndpointScanner.EndpointInfo endpoint = ApiEndpointScanner.getEndpoint(path, httpMethod);

        if (endpoint == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            sendJsonResponse(resp, Map.of(
                    "error", "Endpoint not found",
                    "path", path,
                    "method", httpMethod
            ));
            return;
        }

        try {
            // 解析请求参数
            Map<String, Object> params = parseParameters(req, endpoint);

            // 调用目标方法
            Object result = invokeEndpoint(endpoint, params);

            // 返回结果
            sendJsonResponse(resp, result);

        } catch (Exception e) {
            log.error("Failed to process API request: {} {}", httpMethod, path, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJsonResponse(resp, Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 解析请求参数
     */
    private Map<String, Object> parseParameters(HttpServletRequest req,
                                                ApiEndpointScanner.EndpointInfo endpoint) throws IOException {
        Map<String, Object> params = new HashMap<>();

        // 解析路径参数
        // TODO: 这里可以根据需要实现路径参数解析

        // 解析查询参数
        req.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) {
                params.put(key, values[0]);
            }
        });

        // 解析JSON请求体（针对POST/PUT等）
        if ("POST".equals(req.getMethod()) || "PUT".equals(req.getMethod()) || "PATCH".equals(req.getMethod())) {
            String contentType = req.getContentType();
            if (contentType != null && contentType.contains("application/json")) {
                BufferedReader reader = req.getReader();
                String body = reader.lines().collect(Collectors.joining());
                if (!body.isEmpty()) {
                    try {
                        Map<String, Object> bodyParams = objectMapper.readValue(body, Map.class);
                        params.putAll(bodyParams);
                    } catch (Exception e) {
                        log.warn("Failed to parse JSON body", e);
                    }
                }
            }
        }

        return params;
    }

    /**
     * 调用端点方法
     */
    private Object invokeEndpoint(ApiEndpointScanner.EndpointInfo endpoint,
                                  Map<String, Object> params) throws Exception {
        Method method = endpoint.getMethod();
        Object serviceInstance = endpoint.getServiceInstance();

        // 准备方法参数
        Class<?>[] paramTypes = method.getParameterTypes();
        String[] paramNames = Arrays.stream(method.getParameters())
                .map(p -> p.getName())
                .toArray(String[]::new);

        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = paramNames[i];
            Class<?> paramType = paramTypes[i];

            // 从参数映射中获取值
            Object paramValue = params.get(paramName);

            if (paramValue == null) {
                // 参数类型转换（根据类型设置默认值）
                if (paramType == String.class) {
                    args[i] = "";
                } else if (paramType == Integer.class || paramType == int.class) {
                    args[i] = 0;
                } else if (paramType == Long.class || paramType == long.class) {
                    args[i] = 0L;
                } else if (paramType == Double.class || paramType == double.class) {
                    args[i] = 0.0;
                } else if (paramType == Boolean.class || paramType == boolean.class) {
                    args[i] = false;
                } else {
                    args[i] = null;
                }
            } else {
                // 类型转换
                args[i] = convertType(paramValue, paramType);
            }
        }

        // 调用方法
        return method.invoke(serviceInstance, args);
    }

    /**
     * 类型转换
     */
    private Object convertType(Object value, Class<?> targetType) {
        if (value == null) return null;

        try {
            if (targetType == String.class) {
                return value.toString();
            } else if (targetType == Integer.class || targetType == int.class) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                return Integer.parseInt(value.toString());
            } else if (targetType == Long.class || targetType == long.class) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                return Long.parseLong(value.toString());
            } else if (targetType == Double.class || targetType == double.class) {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return Double.parseDouble(value.toString());
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                if (value instanceof Boolean) {
                    return value;
                }
                return Boolean.parseBoolean(value.toString());
            }
        } catch (Exception e) {
            log.warn("Type conversion failed: {} to {}", value, targetType, e);
        }

        return value;
    }

    /**
     * 发送JSON响应
     */
    private void sendJsonResponse(HttpServletResponse resp, Object data) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        PrintWriter writer = resp.getWriter();
        String json = objectMapper.writeValueAsString(data);
        writer.write(json);
        writer.flush();
    }
}