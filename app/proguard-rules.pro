# R8 / ProGuard rules for release builds.
#
# Release shrinking (isMinifyEnabled) is still off. This file exists because app/build.gradle.kts
# already references it; add keep rules here when shrinking is turned on. Libraries used so far
# (Retrofit, kotlinx.serialization, Play services, Maps) ship their own consumer rules.
