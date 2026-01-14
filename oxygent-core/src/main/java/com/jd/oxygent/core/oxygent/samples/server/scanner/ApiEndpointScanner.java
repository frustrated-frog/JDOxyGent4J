package com.jd.oxygent.core.oxygent.samples.server.scanner;
import com.jd.oxygent.core.oxygent.samples.server.annotation.ApiEndpoint;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * API endpoint scanner that uses JDK native reflection API to scan all methods marked with @ApiEndpoint annotation
 */
@Slf4j
public class ApiEndpointScanner {

    private static final Map<String, EndpointInfo> ENDPOINT_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Object> SERVICE_INSTANCES = new ConcurrentHashMap<>();

    /**
     * Endpoint information class
     */
    public static class EndpointInfo {
        private final String path;
        private final ApiEndpoint.HttpMethod httpMethod;
        private final Method method;
        private final Object serviceInstance;
        private final String description;
        private final String[] tags;

        public EndpointInfo(String path, ApiEndpoint.HttpMethod httpMethod, Method method,
                            Object serviceInstance, String description, String[] tags) {
            this.path = path;
            this.httpMethod = httpMethod;
            this.method = method;
            this.serviceInstance = serviceInstance;
            this.description = description;
            this.tags = tags;
        }

        // Getters
        public String getPath() { return path; }
        public ApiEndpoint.HttpMethod getHttpMethod() { return httpMethod; }
        public Method getMethod() { return method; }
        public Object getServiceInstance() { return serviceInstance; }
        public String getDescription() { return description; }
        public String[] getTags() { return tags; }

        /**
         * Get parameter information
         */
        public List<ParamInfo> getParamInfos() {
            List<ParamInfo> params = new ArrayList<>();
            Class<?>[] paramTypes = method.getParameterTypes();
            String[] paramNames = Arrays.stream(method.getParameters())
                    .map(p -> p.getName())
                    .toArray(String[]::new);

            for (int i = 0; i < paramTypes.length; i++) {
                params.add(new ParamInfo(paramNames[i], paramTypes[i]));
            }
            return params;
        }
    }

    /**
     * 参数信息类
     */
    public static class ParamInfo {
        private final String name;
        private final Class<?> type;

        public ParamInfo(String name, Class<?> type) {
            this.name = name;
            this.type = type;
        }

        public String getName() { return name; }
        public Class<?> getType() { return type; }
    }

    /**
     * Scan all API endpoints under the specified package
     */
    public static void scan(String... basePackages) {
        try {
            log.info("Starting API endpoint scanning...");

            // Clear previous registrations
            ENDPOINT_REGISTRY.clear();
            SERVICE_INSTANCES.clear();

            // 扫描每个包或类
            for (String name : basePackages) {
                try {
                    // 尝试直接作为类加载
                    Class<?> clazz = Class.forName(name);
                    // 如果加载成功，说明是完整类名
                    log.debug("Processing as class: {}", name);
                    processClass(clazz);
                } catch (ClassNotFoundException e) {
                    // 如果不是类名，当作包名处理
                    log.debug("Processing as package: {}", name);
                    if(!name.isEmpty()){
                        scanPackage(name);
                    }
                }
            }

            log.info("API endpoint scanning completed. Found {} endpoints.", ENDPOINT_REGISTRY.size());

        } catch (Exception e) {
            log.error("Failed to scan API endpoints", e);
        }
    }

    /**
     * Scan specified package
     */
    private static void scanPackage(String basePackage) throws Exception {
        String packagePath = basePackage.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        Enumeration<URL> resources = classLoader.getResources(packagePath);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();

            if (resource.getProtocol().equals("file")) {
                // 文件系统（开发环境）
                scanFileSystemClasses(packagePath, resource, basePackage);
            } else if (resource.getProtocol().equals("jar")) {
                // JAR文件（生产环境）
                scanJarClasses(packagePath, resource, basePackage);
            }
        }
    }

    /**
     * Scan classes in file system
     */
    private static void scanFileSystemClasses(String packagePath, URL resource, String basePackage) throws Exception {
        String filePath = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8);
        if (filePath.startsWith("/") && filePath.contains(":")) {
            // Windows path: remove leading slash
            filePath = filePath.substring(1);
        }
        Path directory = Paths.get(filePath);

        if (Files.exists(directory) && Files.isDirectory(directory)) {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".class")) {
                        // Get class name
                        String relativePath = directory.relativize(file).toString();
                        String className = basePackage + "." +
                                relativePath.replace(File.separatorChar, '.')
                                        .replace(".class", "");

                        try {
                            processClass(Class.forName(className));
                        } catch (ClassNotFoundException e) {
                            log.warn("Class not found: {}", className);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Scan classes in JAR file
     */
    private static void scanJarClasses(String packagePath, URL resource, String basePackage) throws Exception {
        String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
        jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);

        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // 检查是否是指定包下的类文件
                if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                    // 转换为类名
                    String className = entryName.replace('/', '.')
                            .replace(".class", "");

                    try {
                        processClass(Class.forName(className));
                    } catch (ClassNotFoundException e) {
                        log.warn("Class not found: {}", className);
                    }
                }
            }
        }
    }

    /**
     * Process single class, scan for @ApiEndpoint annotated methods
     */
    private static void processClass(Class<?> clazz) {
        try {
            // 检查类是否需要处理（可选的过滤条件）
            if (!shouldProcessClass(clazz)) {
                log.debug("Skipping class: {}", clazz.getName());
                return;
            }

            log.debug("Processing class: {}", clazz.getName());

            // 获取类实例
            Object instance = getOrCreateServiceInstance(clazz);

            // 扫描类中的所有方法
            boolean foundEndpoints = false;
            for (Method method : clazz.getDeclaredMethods()) {
                ApiEndpoint annotation = method.getAnnotation(ApiEndpoint.class);
                if (annotation != null) {
                    registerEndpoint(annotation, method, instance);
                    foundEndpoints = true;
                }
            }

            if (!foundEndpoints) {
                log.debug("Class {} has no @ApiEndpoint methods", clazz.getName());
            }

        } catch (Exception e) {
            log.warn("Failed to process class: {}", clazz.getName(), e);
        }
    }

    /**
     * Determine if this class needs to be processed
     */
    private static boolean shouldProcessClass(Class<?> clazz) {
        try {
            String className = clazz.getName();

            // 1. 排除接口和抽象类
            if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                log.trace("Skipping interface/abstract class: {}", className);
                return false;
            }

            // 2. 排除所有内部类（包含$符号的类）
            if (className.contains("$")) {
                log.trace("Skipping inner class: {}", className);
                return false;
            }

            // 3. 排除匿名内部类、局部内部类、成员内部类
            if (clazz.isAnonymousClass() || clazz.isLocalClass()) {
                log.trace("Skipping anonymous/local class: {}", className);
                return false;
            }

            // 4. 排除枚举类型
            if (clazz.isEnum()) {
                log.trace("Skipping enum class: {}", className);
                return false;
            }

            // 5. 排除注解类型
            if (clazz.isAnnotation()) {
                log.trace("Skipping annotation class: {}", className);
                return false;
            }

            // 6. 检查是否有无参构造函数
            try {
                clazz.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                log.trace("Class {} has no default constructor, skipping", className);
                return false;
            }

            // 7. 检查是否是合成类（编译器生成的）
            if (clazz.isSynthetic()) {
                log.trace("Skipping synthetic class: {}", className);
                return false;
            }

            // 8. 只处理特定包下的类（可选，如果需要的话）
            // String packageName = clazz.getPackage().getName();
            // if (!packageName.startsWith("com.jd.oxygent.core.oxygent.samples.server.service")) {
            //     log.trace("Skipping class not in target package: {}", className);
            //     return false;
            // }

            // 9. 检查是否有@ApiEndpoint注解的方法
            boolean hasApiEndpoint = false;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(ApiEndpoint.class)) {
                    hasApiEndpoint = true;
                    break;
                }
            }

            if (!hasApiEndpoint) {
                log.trace("Class {} has no @ApiEndpoint methods, skipping", className);
                return false;
            }

            // 10. 确保类可以被实例化（公共类或静态内部类）
            if (!java.lang.reflect.Modifier.isPublic(clazz.getModifiers())) {
                log.trace("Class {} is not public, skipping", className);
                return false;
            }

            log.debug("Class {} passed all filters, will process", className);
            return true;

        } catch (Exception e) {
            log.debug("Error checking class {}: {}", clazz.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Get or create service instance
     */
    private static synchronized Object getOrCreateServiceInstance(Class<?> serviceClass) throws Exception {
        String className = serviceClass.getName();
        if (SERVICE_INSTANCES.containsKey(className)) {
            // Instance already created, return singleton
            return SERVICE_INSTANCES.get(className);
        }

        // 创建新实例并缓存
        Object instance = serviceClass.getDeclaredConstructor().newInstance();
        SERVICE_INSTANCES.put(className, instance);
        log.debug("Created instance for class: {}", className);
        return instance;
    }

    /**
     * Register endpoint
     */
    private static void registerEndpoint(ApiEndpoint annotation, Method method, Object serviceInstance) {
        try {
            String path = annotation.path();
            ApiEndpoint.HttpMethod httpMethod = annotation.method();
            String key = generateKey(path, httpMethod);

            // Check if endpoint with same path and method already exists
            if (ENDPOINT_REGISTRY.containsKey(key)) {
                log.warn("Duplicate endpoint found: {} {}, ignoring...", httpMethod, path);
                return;
            }

            EndpointInfo endpointInfo = new EndpointInfo(
                    path,
                    httpMethod,
                    method,
                    serviceInstance,
                    annotation.description(),
                    annotation.tags()
            );

            ENDPOINT_REGISTRY.put(key, endpointInfo);
            log.info("Registered API endpoint: {} {} -> {}.{}",
                    httpMethod, path, serviceInstance.getClass().getSimpleName(), method.getName());

        } catch (Exception e) {
            log.error("Failed to register endpoint: {}.{}",
                    serviceInstance.getClass().getSimpleName(), method.getName(), e);
        }
    }

    /**
     * Generate endpoint unique key
     */
    private static String generateKey(String path, ApiEndpoint.HttpMethod method) {
        return method.name() + ":" + normalizePath(path);
    }

    /**
     * Normalize path (ensure starts with /, does not end with /)
     */
    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        // 确保以/开头
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // 移除末尾的/
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    /**
     * Get all registered endpoints
     */
    public static Map<String, EndpointInfo> getEndpoints() {
        return new HashMap<>(ENDPOINT_REGISTRY);
    }

    /**
     * Get endpoint by path and method
     */
    public static EndpointInfo getEndpoint(String path, String method) {
        String key = method.toUpperCase() + ":" + normalizePath(path);
        return ENDPOINT_REGISTRY.get(key);
    }

    /**
     * Get endpoint list by specified tag
     */
    public static List<EndpointInfo> getEndpointsByTag(String tag) {
        List<EndpointInfo> result = new ArrayList<>();

        for (EndpointInfo endpoint : ENDPOINT_REGISTRY.values()) {
            if (endpoint.getTags() != null && Arrays.asList(endpoint.getTags()).contains(tag)) {
                result.add(endpoint);
            }
        }

        return result;
    }

    /**
     * Clear registry
     */
    public static void clear() {
        ENDPOINT_REGISTRY.clear();
        SERVICE_INSTANCES.clear();
    }

    /**
     * Get all service instances (for debugging and management)
     */
    public static Map<String, Object> getServiceInstances() {
        return new HashMap<>(SERVICE_INSTANCES);
    }
}