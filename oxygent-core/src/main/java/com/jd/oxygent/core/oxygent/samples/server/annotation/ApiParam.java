package com.jd.oxygent.core.oxygent.samples.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API parameter annotation, used to describe interface parameter information
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiParam {

    /**
     * Parameter name
     */
    String name() default "";

    /**
     * Parameter description
     */
    String description() default "";

    /**
     * Whether the parameter is required
     */
    boolean required() default true;

    /**
     * Parameter example value
     */
    String example() default "";

    /**
     * Parameter default value
     */
    String defaultValue() default "";
}