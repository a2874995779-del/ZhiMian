package com.zhimian.interceptor;

import com.zhimian.annotation.Public;
import com.zhimian.annotation.RequireAdmin;
import com.zhimian.common.ErrorCode;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录态我用拦截器统一处理。preHandle 里先判断 handler 是不是 HandlerMethod,过滤掉静态资源;
 * 然后从 Authorization 头取 Bearer token 交给 JwtUtil 验签,过期和非法分开抛不同错误码,
 * 由全局异常处理器统一返回——拦截器在 DispatcherServlet 内部,它抛的异常 ControllerAdvice 是能接住的,
 * 这也是我不用 Filter 的原因之一。验签通过后把 userId 和 role 存进 ThreadLocal 封装的 UserContext,
 * 后面 Service 层随取随用,不用层层传参。权限用自定义注解 @RequireAdmin 声明在方法上,拦截器反射读取做校验。
 * ThreadLocal 我特别注意了清理:afterCompletion 里 remove 兜正常路径;但 preHandle 自己抛异常时它的 afterCompletion
 * 不会回调,所以权限不足抛异常前我手动remove 了一次——不清理的话 Tomcat 线程复用,下个请求可能读到上个用户的身份
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {
    private static final String TOKEN_PREFIX = "Bearer ";
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,Object handler){
        if(request.getDispatcherType() == DispatcherType.ASYNC){
            // SSE 接口(如 chat)靠 Servlet 异步机制收尾,同一个请求会在流结束时再触发一次分发,
            // 登录态已经在最初那次分发里校验过了,这次不用再验一遍
            return true;
        }
        if(!(handler instanceof HandlerMethod handlerMethod)){
            return true;
        }

        if(handlerMethod.hasMethodAnnotation(Public.class)){
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if(!StringUtils.hasText(authHeader) || !authHeader.startsWith(TOKEN_PREFIX)){
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        String token = authHeader.substring(TOKEN_PREFIX.length());

        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        }catch (ExpiredJwtException e){
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }catch (JwtException | IllegalArgumentException e){
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        //看redis里是否存有登出记录的key，如果有，就说明这个token已经过期了，就抛出异常
        String jti = claims.getId();
        if(Boolean.TRUE.equals(redisTemplate.hasKey(JwtUtil.BLACKLIST_KEY_PREFIX + jti))){
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role",String.class);
        UserContext.set(userId,role,jti,claims.getExpiration().getTime());

        boolean requireAdmin = handlerMethod.hasMethodAnnotation(RequireAdmin.class);
        if(requireAdmin && !"admin".equals(role)){
            // 注意:必须在抛异常前手动 remove!
            // preHandle 一旦抛异常,本拦截器的 afterCompletion 不会被调用
            // 如果不在这里清理,ThreadLocal 会带着这次请求的 userId 一直留在线程里
            UserContext.remove();
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // preHandle 返回 true 之后,不管 Controller 内部是否抛异常,afterCompletion 都会执行
        // 这里是清理 ThreadLocal 的正确位置
        UserContext.remove();
    }
}
