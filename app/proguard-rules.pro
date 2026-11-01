# ProGuard rules for PrivateAI

# Keep LiteRT-LM classes
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.privateai.**$$serializer { *; }
-keepclassmembers class com.privateai.** {
    *** Companion;
}
-keepclasseswithmembers class com.privateai.** {
    kotlinx.serialization.KSerializer serializer(...);
}
