-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.yourapp.**$$serializer { *; }
-keepclassmembers class com.yourapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.yourapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
