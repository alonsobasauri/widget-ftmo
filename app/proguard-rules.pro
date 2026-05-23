-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx.serialization — both our data layer and the update DTOs.
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class com.basauri.ftmowidget.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.basauri.ftmowidget.update.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.basauri.ftmowidget.data.**$$serializer { *; }
-keep class com.basauri.ftmowidget.update.**$$serializer { *; }

# Glance — receiver looked up by name in AndroidManifest, no harm keeping the package.
-keep class androidx.glance.appwidget.** { *; }
-keep class com.basauri.ftmowidget.widget.** { *; }
