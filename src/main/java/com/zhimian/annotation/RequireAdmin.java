package com.zhimian.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 * 标记在 Controller 方法上,表示该接口仅 admin 角色可访问
 * 由 JwtInterceptor 读取并校验
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
