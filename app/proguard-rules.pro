-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx.serialization
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class com.basauri.ftmowidget.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.basauri.ftmowidget.data.**$$serializer { *; }
