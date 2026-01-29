package com.jd.oxygent.core.oxygent.samples.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API endpoint annotation, used to mark methods that need to be exposed as HTTP interfaces
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiEndpoint {

    /**
     * Interface path
     */
    String path();

    /**
     * HTTP method, default is POST
     */
    HttpMethod method() default HttpMethod.POST;

    /**
     * Interface description
     */
    String description() default "";

    /**
     * Tags/categories
     */
    String[] tags() default {};

    enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH
    }
}