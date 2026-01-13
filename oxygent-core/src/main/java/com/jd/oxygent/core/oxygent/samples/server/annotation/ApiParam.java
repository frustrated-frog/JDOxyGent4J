package com.jd.oxygent.core.oxygent.samples.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API参数注解，用于描述接口参数信息
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiParam {

    /**
     * 参数名称
     */
    String name() default "";

    /**
     * 参数描述
     */
    String description() default "";

    /**
     * 是否必需参数
     */
    boolean required() default true;

    /**
     * 参数示例值
     */
    String example() default "";

    /**
     * 参数默认值
     */
    String defaultValue() default "";
}