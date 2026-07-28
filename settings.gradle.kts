plugins {
    // Lets Gradle auto-download a matching JDK when the toolchain requested in
    // build.gradle.kts (17) isn't already installed on a team member's machine.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Guardians_of_Angkor"