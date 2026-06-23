// Root build script. Holds project-wide tooling that must be applied to the root
// project — currently the binary-compatibility-validator, which dumps the public
// API surface to `<module>/api/<module>.api` and fails the build on undocumented
// drift (`apiCheck`). Per-module build logic lives in the `buildSrc` convention
// plugin and each module's own `build.gradle.kts`.
plugins {
    alias(libs.plugins.binaryCompatibilityValidator)
}
