# R8 rules for :app (release). AndroidX/Compose/DataStore ship their own consumer rules,
# so the app only needs to protect its JNI surface.

# Keep this OSS app readable: shrink/optimise, but don't rename symbols. Obfuscation adds
# little for an MIT-licensed app and keeps crash stack traces useful.
-dontobfuscate

# JNI: the native methods are resolved by their fully-qualified C names
# (Java_io_github_..._LameEncoder_nativeInit, …). R8 must not remove them or their class.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class io.github.entewurzelauskuh.mp4tomp3.engine.jni.LameEncoder {
    *;
}
