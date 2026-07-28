plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.6.0"
}

group = "com.guardiansofangkor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Pins the compiler for every team machine. Without this, Gradle silently uses
// whatever JDK each person happens to have, and code using records or pattern
// matching compiles for some of the team and not others.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    // TODO: set once the entry point class exists, e.g.
    // mainClass.set("com.guardiansofangkor.engine.Main")
    mainClass.set("com.guardiansofangkor.Main")
}

tasks.test {
    useJUnitPlatform()
}