# Default Android optimize rules apply.
# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# Room
-keep class androidx.room.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.goodyaoshi.parentsgames.**$$serializer { *; }
-keepclassmembers class com.goodyaoshi.parentsgames.** {
    *** Companion;
}
-keepclasseswithmembers class com.goodyaoshi.parentsgames.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Mantém as classes @Serializable e seus campos (estados de jogo salvos)
-keep @kotlinx.serialization.Serializable class com.goodyaoshi.parentsgames.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.goodyaoshi.parentsgames.** {
    <fields>;
}
# Enums (serializados por nome) — preserva values()/valueOf()
-keepclassmembers enum com.goodyaoshi.parentsgames.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
