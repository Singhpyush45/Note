# ==============================================================================
# NOTEflow / Papriva — Production R8 Full-Mode Rules
# ==============================================================================

# 1. Readable Crash Stack Traces (keep line numbers, strip local file paths)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 2. Kotlin Metadata & Intrinsics (required for reflection/serialization/Compose)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# 3. Kotlinx Serialization (keep generated $$serializer & Companion serializers)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.appname.notes.**$$serializer { *; }
-keepclassmembers class com.appname.notes.** {
    *** Companion;
}
-keepclasseswithmembers class com.appname.notes.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# 4. Room Database (keep @Database, @Entity, @Dao & generated _Impl classes)
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class com.appname.notes.**_Impl { *; }
-dontwarn androidx.room.paging.**

# 5. Hilt & Dagger Dependency Injection
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# 6. Jetpack Compose & Navigation / SavedState
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers enum com.appname.notes.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 7. Kotlinx Coroutines (prevent R8 full-mode stripping of debug/fast-service loader)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# 8. Strip verbose/debug/info logs from Release APKs (preserve Log.w and Log.e)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# PDFBox-Android optional JPEG2000 (JP2) codec references not bundled
-dontwarn com.gemalto.jp2.**
