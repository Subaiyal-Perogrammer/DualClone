package top.niunaijun.blackbox.core;

import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;


public class GmsCore {
    private static final String TAG = "GmsCore";

    private static final HashSet<String> GOOGLE_APP = new HashSet<>();
    private static final HashSet<String> GOOGLE_SERVICE = new HashSet<>();
    public static final String GMS_PKG = "com.google.android.gms";
    public static final String GSF_PKG = "com.google.android.gsf";
    public static final String VENDING_PKG = "com.android.vending";

    static {
        GOOGLE_APP.add(VENDING_PKG);
        GOOGLE_APP.add("com.google.android.play.games");
        // CloneSpace addition (run 10): the Google app — Gemini and other
        // Google guests hard-require it inside the virtual environment and
        // dead-end on a "Download or enable the Google app" screen without
        // it (seen on-device 2026-10-23).
        GOOGLE_APP.add("com.google.android.googlequicksearchbox");
        GOOGLE_APP.add("com.google.android.wearable.app");
        GOOGLE_APP.add("com.google.android.wearable.app.cn");

        
        GOOGLE_SERVICE.add(GMS_PKG);
        GOOGLE_SERVICE.add(GSF_PKG);
        GOOGLE_SERVICE.add("com.google.android.gsf.login");
        GOOGLE_SERVICE.add("com.google.android.backuptransport");
        GOOGLE_SERVICE.add("com.google.android.backup");
        GOOGLE_SERVICE.add("com.google.android.configupdater");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.contacts");
        GOOGLE_SERVICE.add("com.google.android.feedback");
        GOOGLE_SERVICE.add("com.google.android.onetimeinitializer");
        GOOGLE_SERVICE.add("com.google.android.partnersetup");
        GOOGLE_SERVICE.add("com.google.android.setupwizard");
        GOOGLE_SERVICE.add("com.google.android.syncadapters.calendar");
    }

    public static boolean isGoogleService(String packageName) {
        return GOOGLE_SERVICE.contains(packageName);
    }

    public static boolean isGoogleAppOrService(String str) {
        return GOOGLE_APP.contains(str) || GOOGLE_SERVICE.contains(str);
    }

    // CloneSpace patch (2026-10-23, run 9): resilient GMS install.
    // Upstream aborted on the FIRST failed package and installGApps then
    // UNINSTALLED everything it had just installed — so a single optional
    // Google package that refuses to install inside the virtual environment
    // (split APK, OEM stub, signature quirk) silently removed Google Play
    // services itself, and guests kept reporting "Google Play services are
    // missing" (seen on-device: Instagram and Bard guests, Android 13).
    // Now: core packages (GMS, GSF) install first; every other package is
    // best-effort; failures are logged per package and collected into the
    // result message, never rolled back. Success means every core package
    // that exists on the device is installed for the user at the end.
    public static InstallResult installGApps(int userId) {
        BlackBoxCore blackBoxCore = BlackBoxCore.get();
        List<String> order = new ArrayList<>();
        order.add(GMS_PKG);
        order.add(GSF_PKG);
        Set<String> rest = new HashSet<>(GOOGLE_SERVICE);
        rest.addAll(GOOGLE_APP);
        rest.removeAll(order);
        order.addAll(rest);

        List<String> failures = new ArrayList<>();
        for (String packageName : order) {
            if (blackBoxCore.isInstalled(packageName, userId)) {
                continue;
            }
            try {
                BlackBoxCore.getContext().getPackageManager().getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.i(TAG, "installGApps: " + packageName + " not present on device, skipping");
                continue;
            }
            InstallResult r = blackBoxCore.installPackageAsUser(packageName, userId);
            if (r.success) {
                Log.i(TAG, "installGApps: installed " + packageName + " for user " + userId);
            } else {
                Log.w(TAG, "installGApps: FAILED " + packageName + " for user " + userId + ": " + r.msg);
                failures.add(packageName + " (" + r.msg + ")");
            }
        }
        boolean coreOk = corePresent(blackBoxCore, GMS_PKG, userId) && corePresent(blackBoxCore, GSF_PKG, userId);
        InstallResult result = new InstallResult();
        result.success = coreOk;
        if (coreOk) {
            result.msg = failures.isEmpty()
                    ? "All Google packages installed."
                    : "Core Google services installed; optional packages failed: " + failures;
        } else {
            result.msg = "Core Google services (GMS/GSF) not installed after attempt. Failures: " + failures;
        }
        return result;
    }

    /** True when [pkg] is absent from the device entirely, or installed for [userId]. */
    private static boolean corePresent(BlackBoxCore core, String pkg, int userId) {
        try {
            BlackBoxCore.getContext().getPackageManager().getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
        return core.isInstalled(pkg, userId);
    }

    private static void uninstallPackages(Set<String> list, int userId) {
        BlackBoxCore blackBoxCore = BlackBoxCore.get();
        for (String packageName : list) {
            blackBoxCore.uninstallPackageAsUser(packageName, userId);
        }
    }

    public static void uninstallGApps(int userId) {
        uninstallPackages(GOOGLE_SERVICE, userId);
        uninstallPackages(GOOGLE_APP, userId);
    }

    public static void remove(String packageName) {
        GOOGLE_SERVICE.remove(packageName);
        GOOGLE_APP.remove(packageName);
    }


    public static boolean isSupportGms() {
        try {
            BlackBoxCore.getPackageManager().getPackageInfo(GMS_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return false;
    }

    public static boolean isInstalledGoogleService(int userId) {
        return BlackBoxCore.get().isInstalled(GMS_PKG, userId);
    }
}