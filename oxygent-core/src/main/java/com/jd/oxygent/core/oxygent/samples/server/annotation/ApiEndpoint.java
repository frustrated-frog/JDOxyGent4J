package com.jd.oxygent.core.oxygent.samples.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API端点注解，用于标记需要暴露为HTTP接口的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiEndpoint {

    /**
     * 接口路径
     */
    String path();

    /**
     * HTTP方法，默认为POST
     */
    HttpMethod method() default HttpMethod.POST;

    /**
     * 接口描述
     */
    String description() default "";

    /**
     * 标签/分类
     */
    String[] tags() default {};

    enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH
    }
}