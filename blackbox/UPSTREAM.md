# Vendored virtualization engine — provenance

**Engine:** BlackBox (Bcore) — a VirtualApp-derived virtual engine
**Fork used:** "NewBlackbox" by ALEX5402
**GitHub:** https://github.com/ALEX5402/NewBlackbox
**Pinned commit:** `89b59836c66f173756a4ae258cf379a957649820` (2026-07-19, "Merge pull request #54 from ALEX5402/dev")
**License:** Apache License 2.0 (see `../LICENSE-blackbox.txt` — copy of the fork's LICENSE;
the original BlackBox code is Copyright 2022 BlackBox contributors)
**Upstream history:** The original BlackBox repository (FBlackBox/BlackBox) deleted all of
its code in 2024; this fork is a maintained, modernized continuation of that codebase.

**Supported Android versions (fork's claim, in its README):** Android 5.0 to 14.0+.
Its build config: minSdk 21, compileSdk 35, arm64-v8a + armeabi-v7a native ABIs.
CloneSpace requires Android 8.0+ (minSdk 26) and targets SDK 34.
**These version claims are the fork's — they have NOT been verified on a device by us.**

**What was vendored (source modules, copied 2026-10-22):**
- `../Bcore/` — the engine: 501 Java files, native hooking code in `src/main/cpp`
  (Dobby-based, built with ndkBuild / `Android.mk`, NDK 29.0.13846066), a stub
  activity/service/provider pool (~300 `:p*` process entries in its manifest),
  virtual PackageManager/ActivityManager/account/notification services, and the
  virtual file layout under `<host-data>/blackbox/data/user/<userId>/<package>/`.
- `../black-reflection/` — its BlackReflection code generator library (java-library).
- `../compiler/` — its annotation processor (java-library).

**Adaptations made for CloneSpace (project build files only — engine source untouched):**
- CloneSpace's toolchain was aligned UP to the fork's: Gradle 8.14.5, AGP 8.13.2,
  Kotlin 1.9.23, KSP 1.9.23-1.0.19, JDK 21, Compose compiler 1.5.11.
- The root project provides `rootProject.ext.compileSdkVersion = 34` and
  `rootProject.ext.minSdk = 21`, which `Bcore/build.gradle` reads. (The fork itself
  used compileSdk 35; CloneSpace compiles the engine against 34 to match its own
  targetSdk 34 — a deviation CI must verify.)
- JitPack was added to the project's repositories for the engine's
  `com.github.tiann:FreeReflection` dependency.
- The app module declares `abiFilters arm64-v8a, armeabi-v7a` and depends on
  `project(":Bcore")`. The engine's NDK version (29.0.13846066, ndkBuild) is unchanged,
  so CI must install exactly that NDK (see `.github/workflows/build.yml`).

## CloneSpace patches to the vendored engine (2026-10-23)

The upstream engine's installer only ever parses ONE apk file (the base APK)
and never guarantees split APKs reach the guest: `PackageManagerCompat`
built the guest ApplicationInfo from `baseCodePath` alone, its
`getResources` added only the base path, and `BActivityThread`'s fallback
classloader used `sourceDir` only. On split-installed apps (e.g. Instagram
from the Play Store) the clone installed and opened, but split-delivered
code/resources were missing at runtime — observed on-device as a black
screen after login. Three minimal, commented patches ("CloneSpace patch
(split APK support)") fix the passthrough; the split files themselves stay
in place in the original install directory, exactly like the base APK does
for FLAG_SYSTEM installs:

1. `core/system/pm/PackageManagerCompat.generateApplicationInfo` — copies
   `splitSourceDirs`/`splitNames` live from the real installed app into the
   guest ApplicationInfo, so the framework LoadedApk builds the guest
   classloader and resources from base + all splits.
2. `core/system/pm/PackageManagerCompat.getResources` — registers every
   split APK as an asset path alongside the base APK.
3. `app/BActivityThread.getClassLoader` — the fallback PathClassLoader now
   includes all splits in its dex path and receives the native library dir.
4. `fake/frameworks/BPackageManager.createFallbackApplicationInfo` — the
   fallback ApplicationInfo previously forced `splitNames` to an empty
   array; it now also carries the real app's `splitSourceDirs`/`splitNames`.
   (Audit note: these are the only two ApplicationInfo construction sites
   in the engine; both now pass splits through.)

No other engine logic was changed. These patches are UNVERIFIED on a device
until the Instagram/Telegram retest passes; the app's Engine log (with
logcat capture of the stub processes) is the evidence channel.

## CloneSpace de-fabrication patches (2026-10-23, second round)

The fork added a layer of "proxy" classes that FABRICATED data for guest
apps on error paths: fake Google accounts (mock.user@gmail.com,
virtual.user@gmail.com), a fake password ("mock_google_password"), fake
auth tokens ("mock_google_auth_token_…", "mock_gms_token_…",
"mock_access_token_…"), fake add-account results, and false "signed in /
token valid" answers. Fabricated credentials can poison real auth flows
inside clones and are unacceptable in CloneSpace. All fabrication return
sites in the following files now return the honest value instead (the real
result when the underlying call succeeds; an empty list / null / false on
failure), and every createMock* factory was deleted:

- `fake/service/GoogleAccountManagerProxy.java`
- `fake/service/AuthenticationProxy.java`
- `fake/service/GmsProxy.java` (auth result + token sites)

This matches the direction of the fork's own still-open PR #62, re-derived
independently from the vendored code. Audited and deliberately left alone:
`AndroidIdProxy` (hands guests a virtual Android ID — identifier
virtualization is core sandbox behaviour, not a credential) and
`IConnectivityManagerProxy`'s Network fallback shim (connectivity, not a
credential; its requestNetwork fallback already returns null).

Also in this round, `BPackageManager.createFallbackApplicationInfo` no
longer hands a guest the REAL app's data directory (`/data/data/<pkg>`);
it now returns the virtual environment's per-user data dir, and copies the
real `nativeLibraryDir` when available.

Stub pool note: Bcore declares 50 stub processes (:p0–:p49). The reference
app declares ~100. Fifty concurrent clone processes exceeds what a 4–6 GB
phone can hold in RAM anyway; the pool is not an observed limitation, so it
was left unchanged rather than inflated speculatively.

## CloneSpace run-9 patches (2026-10-23): resilient GMS install + log flood fix

On-device evidence (moto g54 5G, Android 13, run-8 build): after login the
Instagram guest hung (black screen, then an ANR dialog), and the captured
guest logcat showed `com.instagram.android requires Google Play services,
but they are missing` — GMS was NOT installed in the virtual environment
even though CloneSpace offers an "Install Google Services" action backed
by `GmsCore.installGApps`. A second guest (Bard) showed the same missing-
GMS retry loop. Whether the user pressed the button could not be told
from the log — which was itself the second defect (below).

- `core/GmsCore.java` — `installGApps` rewritten (CloneSpace patch comment
  in the file): upstream aborted on the FIRST failed package and then
  uninstalled everything it had just installed, so one optional Google
  package refusing to install (split APK, OEM stub, signature quirk)
  silently removed Play services itself. Now GMS+GSF install first, all
  other packages are best-effort, per-package outcomes are logged, and
  success = every core package present on the device is installed for
  the user at the end. No rollback. `uninstallGApps` unchanged.
- App-side logging (not engine, recorded here for completeness):
  `EngineLog` ring raised 200 → 600 lines; `LogcatCapture` now writes
  captured lines quietly (no re-emission to logcat — the echo loop where
  each capture re-read its own previous capture), skips previous-capture
  echo lines, and collapses consecutive duplicate messages with a repeat
  count. Two device pastes were ~90% this flood, which had evicted the
  install/launch result lines both times.

## CloneSpace run-10 patches (2026-10-23): LocaleManager proxy + Google app in GMS set

On-device evidence (run-9 build, moto g54 5G / Android 13): the ChatGPT
guest installed with all 3 splits served correctly (log-verified), launched,
then died on TWO uncaught SecurityExceptions from the system LocaleManager
service — `setApplicationLocales … has android.permission.CHANGE_CONFIGURATION`
(~1s after launch) and `getApplicationLocales … READ_APP_SPECIFIC_LOCALES`
(~5s). The system service checks the permission against the calling
package, but inside the virtual space the binder caller is the HOST, not
the guest package the call names, so modern apps using AndroidX AppCompat
per-app locales crash on startup. This is the fork's open issue #37.

- NEW `fake/service/ILocaleManagerProxy.java`, registered in
  `HookManager.init()`: intercepts the "locale" service in guest
  processes. `setApplicationLocales`/`setSystemLocales` are acknowledged
  and discarded (per-app locale is a UX preference); `getApplicationLocales`
  forwards to the real service and falls back to an empty LocaleList when
  it refuses (a guest with no per-app override genuinely has an empty
  list). Adopted from the fork's own still-open PR #62 (same Apache-2.0
  codebase), with the null→empty-LocaleList change noted in the file
  header because android.app.LocaleManager does not expect null.
- `core/GmsCore.java`: `com.google.android.googlequicksearchbox` (the
  Google app) added to the GOOGLE_APP install set — the Gemini guest
  renders but dead-ends on "Download or enable the Google app" without
  it (on-device screenshot, 2026-10-23).
