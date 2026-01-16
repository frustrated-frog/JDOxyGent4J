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
 * Annotation-driven API Servlet
 * Handles all interfaces registered via @ApiEndpoint annotation
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
     * Unified request handling
     */
    private void handleRequest(HttpServletRequest req, HttpServletResponse resp, String httpMethod) throws IOException {
        String path = req.getPathInfo();

        // Get endpoint information
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
     * Parse request parameters
     */
    private Map<String, Object> parseParameters(HttpServletRequest req,
                                                ApiEndpointScanner.EndpointInfo endpoint) throws IOException {
        Map<String, Object> params = new HashMap<>();

        // Parse path parameters
        // TODO: 这里可以根据需要实现路径参数解析

        // Parse query parameters
        req.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) {
                params.put(key, values[0]);
            }
        });

        // Parse JSON request body (for POST/PUT, etc.)
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
     * Invoke endpoint method
     */
    private Object invokeEndpoint(ApiEndpointScanner.EndpointInfo endpoint,
                                  Map<String, Object> params) throws Exception {
        Method method = endpoint.getMethod();
        Object serviceInstance = endpoint.getServiceInstance();

        // Prepare method parameters
        Class<?>[] paramTypes = method.getParameterTypes();
        String[] paramNames = Arrays.stream(method.getParameters())
                .map(p -> p.getName())
                .toArray(String[]::new);

        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = paramNames[i];
            Class<?> paramType = paramTypes[i];

            // Get value from parameter map
            Object paramValue = params.get(paramName);

            if (paramValue == null) {
                // Parameter type conversion (set default value based on type)
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
                // Type conversion
                args[i] = convertType(paramValue, paramType);
            }
        }

        // 调用方法
        return method.invoke(serviceInstance, args);
    }

    /**
     * Type conversion
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
     * Send JSON response
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