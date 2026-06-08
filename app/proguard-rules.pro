-keep class com.exitsense.app.data.local.entities.** { *; }
-keep class com.exitsense.app.domain.model.** { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-dontwarn kotlinx.serialization.**
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.exitsense.app.**$$serializer { *; }
-keepclassmembers class com.exitsense.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.exitsense.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
