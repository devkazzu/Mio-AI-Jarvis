# Mio AI — release shrink rules.
-keep class com.mio.ai.accessibility.MioAccessibilityService { *; }
-keep class com.mio.ai.voice.WakeWordService { *; }
-keep class com.mio.ai.core.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
