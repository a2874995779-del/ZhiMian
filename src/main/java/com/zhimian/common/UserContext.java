package com.zhimian.common;

public class UserContext {
    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    public static void set(Long userId,String role,String jti,long expireAt){
        CONTEXT.set(new LoginUser(userId,role,jti,expireAt));
    }

    public static Long getUserId(){
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.userId();
    }

    public static String getRole(){
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.role();
    }

    public static String getJti(){
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.jti();
    }

    public static long getExpireAt(){
        LoginUser user = CONTEXT.get();
        return user == null ? 0 : user.expireAt();
    }

    public static void remove(){
        CONTEXT.remove();
    }

    private record LoginUser(Long userId,String role,String jti,long expireAt){

    }
}
