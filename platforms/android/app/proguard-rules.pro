# NomadMark ProGuard 配置
# 用于 Release 构建时的代码混淆和优化

# ============================================
# Markwon - Markdown 渲染引擎
# ============================================
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# ============================================
# JLatexMath - 数学公式渲染
# ============================================
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn ru.noties.jlatexmath.**

# ============================================
# AndroidX 库
# ============================================
-keep class androidx.** { *; }
-dontwarn androidx.**

# ============================================
# Kotlin Coroutines
# ============================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ============================================
# 原生库 (JNI)
# ============================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================
# Supernote 相关 (如果使用 aar 库)
# ============================================
# 取消注释以下内容以支持 SupernoteUiLib, dialoglib, nativeEventlib
# -keep class com.ratta.supernote.** { *; }
# -keep class com.ratta.ui.** { *; }
# -keep class com.ratta.dialog.** { *; }
# -keep class com.ratta.nativeevent.** { *; }
# -dontwarn com.ratta.**

# ============================================
# 通用优化
# ============================================
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
