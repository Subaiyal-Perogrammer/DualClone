package top.niunaijun.blackbox.fake.service;

import android.os.LocaleList;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;

/**
 * Swallows locale updates made from inside the virtual space.
 *
 * Modern apps (Play Store, ChatGPT, anything using AndroidX AppCompat
 * per-app locales) call LocaleManager.setApplicationLocales() /
 * getApplicationLocales() during init. Those calls land on the system
 * ILocaleManager service, which enforces the signature-level
 * android.permission.CHANGE_CONFIGURATION / READ_APP_SPECIFIC_LOCALES
 * against the calling package — but inside the virtual space the binder
 * caller is the HOST app, not the guest package the call names, so the
 * check fails:
 *
 *   SecurityException: setApplicationLocales: Neither user <uid> nor
 *   current process has android.permission.CHANGE_CONFIGURATION.
 *
 * The exception is uncaught in the guest and takes the whole stub process
 * down (seen on-device 2026-10-23, moto g54 5G / Android 13, ChatGPT
 * guest: two fatal crashes ~1s and ~5s after launch). Per-app locale is
 * a pure UX preference, so acknowledging writes and answering reads with
 * the real result — or an empty LocaleList when the real service refuses
 * (a guest with no per-app override genuinely has an empty list) — is
 * honest and unblocks startup.
 *
 * CloneSpace note: adopted from the fork's own still-open PR #62 (same
 * Apache-2.0 codebase this engine is vendored from), with one change —
 * the getApplicationLocales fallback returns an empty LocaleList instead
 * of null, because android.app.LocaleManager does not expect null.
 */
public class ILocaleManagerProxy extends BinderInvocationStub {

    public static final String TAG = "ILocaleManagerProxy";

    public ILocaleManagerProxy() {
        super(BRServiceManager.get().getService("locale"));
    }

    @Override
    protected Object getWho() {
        return BRServiceManager.get().getService("locale");
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("locale");
    }

    @Override
    public boolean isBadEnv() {
        return BRServiceManager.get().getService("locale") == null;
    }

    @ProxyMethod("setApplicationLocales")
    public static class SetApplicationLocales extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.d(TAG, "setApplicationLocales intercepted; ignored for virtual process");
            return null;
        }
    }

    @ProxyMethod("getApplicationLocales")
    public static class GetApplicationLocales extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Object result = method.invoke(who, args);
                return result != null ? result : LocaleList.getEmptyLocaleList();
            } catch (Throwable t) {
                Slog.w(TAG, "getApplicationLocales failed, returning empty LocaleList", t);
                return LocaleList.getEmptyLocaleList();
            }
        }
    }

    @ProxyMethod("setSystemLocales")
    public static class SetSystemLocales extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Slog.d(TAG, "setSystemLocales intercepted; ignored for virtual process");
            return null;
        }
    }
}
