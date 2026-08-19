# Reglas oficiales de kotlinx.serialization 1.9.0 (rules/common.pro, tag v1.9.0).
# Necesarias porque kotlinx-serialization no se distribuye como AAR y por tanto
# no trae "consumer rules" propias aplicadas automaticamente por AGP.
#
# Nos afecta de verdad (no es solo teorico): Screen (ScreenNavigator.kt) y StudyScope
# son @Serializable y Navigation Compose las usa por debajo para sus rutas tipadas.

# Keep `Companion` object field of serializable classes.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
}

# Keep names for named companion object from obfuscation
-if @kotlinx.serialization.internal.NamedCompanion class *
-keepclassmembers class * {
    static <1> *;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

-keepclassmembers public class **$$serializer {
    private ** descriptor;
}
