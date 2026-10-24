package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;


public class AuthenticationProxy extends ClassInvocationStub {
    public static final String TAG = "AuthenticationProxy";

    public AuthenticationProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        return null;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("signIn")
    public static class SignIn extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "Authentication: Handling signIn call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Authentication: SignIn error, returning null (CloneSpace: never fabricate auth results)", e);
                return null;
            }
        }
    }

    @ProxyMethod("signOut")
    public static class SignOut extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "Authentication: Handling signOut call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Authentication: SignOut error, returning success", e);
                return null;
            }
        }
    }

    @ProxyMethod("isSignedIn")
    public static class IsSignedIn extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "Authentication: Handling isSignedIn call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Authentication: IsSignedIn error, returning false", e);
                return false;
            }
        }
    }

    @ProxyMethod("getCurrentUser")
    public static class GetCurrentUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "Authentication: Handling getCurrentUser call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Authentication: GetCurrentUser error, returning null", e);
                return null;
            }
        }
    }

    @ProxyMethod("getAccessToken")
    public static class GetAccessToken extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "Authentication: Handling getAccessToken call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Authentication: GetAccessToken error, returning null (never fabricate tokens)", e);
                return null;
            }
        }
    }

    @ProxyMethod("refreshToken")
    public static class RefreshToken extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "Authentication: Handling refreshToken call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Authentication: RefreshToken error, returning null", e);
                return null;
            }
        }
    }

    @ProxyMethod("validateToken")
    public static class ValidateToken extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "Authentication: Handling validateToken call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Authentication: ValidateToken error, returning false", e);
                return false;
            }
        }
    }

    @ProxyMethod("login")
    public static class Login extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "Authentication: Handling login call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Authentication: Login error, returning null", e);
                return null;
            }
        }
    }

    @ProxyMethod("logout")
    public static class Logout extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "Authentication: Handling logout call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Authentication: Logout error, returning success", e);
                return null;
            }
        }
    }

    @ProxyMethod("isLoggedIn")
    public static class IsLoggedIn extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Slog.d(TAG, "Authentication: Handling isLoggedIn call");
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Authentication: IsLoggedIn error, returning false", e);
                return false;
            }
        }
    }

    





}
