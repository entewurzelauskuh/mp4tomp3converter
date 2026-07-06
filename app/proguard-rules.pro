# ProGuard/R8 rules for :app.
#
# Minification is currently OFF for release builds (see app/build.gradle.kts).
# When it is enabled — after the JNI/native engine lands in Phase 2 — add keep
# rules for the JNI entry points here (native methods and the classes that own
# them must not be renamed/removed).
