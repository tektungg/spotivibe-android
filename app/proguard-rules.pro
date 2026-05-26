# Spotivibe ProGuard / R8 rules for release build.
# Enabled via isMinifyEnabled = true in app/build.gradle.kts.

# Keep source file + line numbers untuk crash stacktrace readable
-keepattributes SourceFile,LineNumberTable

# ── Spotify SDK ──────────────────────────────────────────────────
# Spotify pakai reflection internal di App Remote + Auth. Keep all classes.
-keep class com.spotify.** { *; }
-keep interface com.spotify.** { *; }
-dontwarn com.spotify.**

# ── Kuromoji ─────────────────────────────────────────────────────
# Kuromoji pakai reflection untuk load dict dari embedded resources.
-keep class com.atilika.kuromoji.** { *; }
-keep class com.atilika.** { *; }
-dontwarn com.atilika.**
# Dict resources jangan di-process
-keep class com.atilika.kuromoji.ipadic.compile.** { *; }

# ── Pinyin4j ─────────────────────────────────────────────────────
-keep class net.sourceforge.pinyin4j.** { *; }
-dontwarn net.sourceforge.pinyin4j.**

# ── Aromanize (Korean romanization, included as Kotlin/Java port) ─
-keep class net.fjar.aromanize.** { *; }
-dontwarn net.fjar.aromanize.**

# ── Moshi (reflection-based JSON adapters) ───────────────────────
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}
# DTO classes — keep all fields for Moshi reflection
-keep class com.tglabs.spotivibe.data.LrclibDto { *; }
-keep class com.tglabs.spotivibe.data.LrclibDto$** { *; }
-keep class com.tglabs.spotivibe.data.LyricsCache$** { *; }
-dontwarn com.squareup.moshi.**

# ── Retrofit ─────────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions, *Annotation*, InnerClasses, EnclosingMethod

# ── OkHttp ───────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Kotlin coroutines ───────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Compose (mostly handled by AGP default rules, but keep stability) ──
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── Application domain models (used by Compose with @Immutable) ──
-keep class com.tglabs.spotivibe.domain.** { *; }

# ── R8 strict checks ─────────────────────────────────────────────
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
