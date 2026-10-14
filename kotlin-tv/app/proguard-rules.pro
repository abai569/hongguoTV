-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
-dontwarn javax.mail.**
-dontwarn javax.activation.**

# 崩溃日志里保留我们自己的类名、源文件名与行号，方便用户拍照定位。
-keepattributes SourceFile,LineNumberTable
-keep class com.hongguotv.** { *; }

# 部分电视盒子（如 Allwinner MagicBox_M20A，Android 7）的固件把一份很旧的 Guava 放进了
# boot classpath（/system/framework/listing.jar）。Android 的父优先类加载会让它压过我们打进
# APK 的 Guava，于是 Media3 调用 Guava 31.1+ 才有的 ImmutableMap.Builder.buildOrThrow() 时
# 直接抛 NoSuchMethodError（崩在 AudioCapabilities.<clinit>，创建 ExoPlayer 就挂）。
# 强制对 Guava 做重命名（混淆后类名不再与系统那份同名），冲突即消失。
-keep,allowobfuscation,allowshrinking class com.google.common.** { *; }
