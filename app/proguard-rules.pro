# Shizuku is accessed through its binder + reflection (newProcess).
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Kadb uses reflection inside its cert/TLS helpers.
-keep class com.flyfishxu.kadb.** { *; }
-dontwarn com.flyfishxu.kadb.**

# BouncyCastle (bundled by Kadb) is used reflectively for certificates.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# The RSA KeyFactory provider is instantiated by class name.
-keep class dev.oss.adbcontrol.RsaCrtFix$* { *; }
-keep class dev.oss.adbcontrol.RsaCrtFix { *; }
