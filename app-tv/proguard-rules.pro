# CalmSource IPTV ProGuard Rules

# Keep Room entities, DAOs, and databases
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * implements @androidx.room.Dao * { *; }
-keep class * extends androidx.room.RoomDatabase {
    <init>();
    *;
}
-keep class com.example.calmsource.core.database.entity.** { *; }
-keep class com.example.calmsource.core.database.dao.** { *; }
-keep class com.example.calmsource.core.discoveryengine.database.**Entity { *; }
-keep class com.example.calmsource.core.discoveryengine.database.PlaybackCount { *; }

# Keep kotlinx.serialization & general @Serializable classes
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.calmsource.**$$serializer { *; }
-keepclassmembers class com.example.calmsource.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.calmsource.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Ktor client and OkHttp/Okio transitive dependencies
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# Keep Hilt / Dependency Injection
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep ExoPlayer / Media3 JNI and media playback bindings
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep model classes used in JSON parsing and API client responses
-keep class com.example.calmsource.core.model.** { *; }
-keep class com.example.calmsource.core.discoveryengine.models.** { *; }
-keep class com.example.calmsource.feature.debrid.** { *; }
-keep class com.example.calmsource.feature.iptv.xtream.** { *; }

# Remove verbose logging in release, but keep error logs for crash debugging
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# SLF4J is brought in transitively (e.g. by ExoPlayer) but no implementation
# is shipped at runtime, so its StaticLoggerBinder is missing. R8 just needs
# to know not to warn about it.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Keep VLC LibVLC JNI
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }
-keep class org.videolan.libvlc.interfaces.** { *; }

# Keep Coil image loading internals
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Firebase Crashlytics symbolication
-keep class com.google.firebase.crashlytics.** { *; }
-keepattributes SourceFile, LineNumberTable

# Keep DataStore Preferences / Protobuf
-keep class androidx.datastore.preferences.** { *; }
-keep class androidx.datastore.** { *; }
-keep class com.google.protobuf.** { *; }

# Keep Navigation3
-keep class androidx.navigation3.** { *; }

# Keep WorkManager workers
-keep class * extends androidx.work.Worker
