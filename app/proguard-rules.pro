# R8 / ProGuard rules for release builds.
#
# Retrofit, OkHttp, Media3 and Hilt ship their own consumer rules, so the main
# thing we add is kotlinx.serialization (R8 can't see the synthetic serializers
# otherwise). Adapted from the official kotlinx.serialization guidance.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep `serializer()` on companion objects of @Serializable types.
-keepclassmembers class eu.junak.baton.** {
    *** Companion;
}
-keepclasseswithmembers class eu.junak.baton.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep the generated serializers themselves.
-keep,includedescriptorclasses class eu.junak.baton.**$$serializer { *; }

# Models are referenced reflectively by name in a few logs/diagnostics; keep
# their names readable in stack traces without keeping the whole class.
-keepnames class eu.junak.baton.core.model.** { *; }
