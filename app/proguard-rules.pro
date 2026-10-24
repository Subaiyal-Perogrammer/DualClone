# DualClone release rules. The debug build (what CI ships) does not minify;
# keep the BlackBox engine and Room-generated code intact if release is used.
-keep class top.niunaijun.blackbox.** { *; }
-keep class com.dualclone.app.data.** { *; }
-dontwarn top.niunaijun.blackbox.**
