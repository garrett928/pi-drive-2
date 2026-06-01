# Pi Drive — R8/ProGuard rules for release builds
#
# These rules are applied when building with isMinifyEnabled = true (release variant).
# The goal is to shrink and obfuscate as aggressively as possible while keeping all
# classes that are accessed via reflection, DI, or serialization.

# ── Source maps (for crash report symbolication) ──────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlinx Serialization ──────────────────────────────────────────────────────
# Serializable classes are accessed via reflection by the KSerializer generated code.
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod

-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-dontwarn kotlinx.serialization.**

# ── OkHttp ───────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Room (entity classes, DAOs, and the database) ─────────────────────────────
# Room generates code at compile time, but the entity and DAO interfaces must survive
# minification so Room's generated implementations can instantiate them at runtime.
-keep class ghart.space.pi_drive.shared.data.db.** { *; }
-keepclassmembers class ghart.space.pi_drive.shared.data.db.** { *; }

# ── Hilt / Dagger ────────────────────────────────────────────────────────────
# Hilt generates components, modules, and member injectors. The generated class names
# follow predictable patterns that R8 must preserve.
-keep class **_HiltComponents { *; }
-keep class **_HiltModules { *; }
-keep class **_MembersInjector { *; }
-keep class **_Factory { *; }
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.**

# ── WorkManager ───────────────────────────────────────────────────────────────
# Workers are instantiated by WorkManager via reflection; their constructors must
# be preserved, including the custom factory in DelegatingWorkerFactory.
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Car App Library ──────────────────────────────────────────────────────────
-keep class androidx.car.app.** { *; }
-dontwarn androidx.car.app.**

# ── Kotlin coroutines & reflection ────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Keep all app data models ──────────────────────────────────────────────────
# VehicleSnapshot, DrivingEvent, and other shared models are passed across module
# boundaries and serialized to Room and JSON — keep all fields.
-keep class ghart.space.pi_drive.shared.data.model.** { *; }
-keepclassmembers class ghart.space.pi_drive.shared.data.model.** { *; }
