-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx.serialization — keep generated serializers + companions for our models.
-keep,includedescriptorclasses class dev.cremin.gitops.**$$serializer { *; }
-keepclassmembers class dev.cremin.gitops.** {
    *** Companion;
}
-keepclasseswithmembers class dev.cremin.gitops.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Tink (androidx.security-crypto) references errorprone annotations that are
# compile-only and absent at runtime — safe to ignore.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
