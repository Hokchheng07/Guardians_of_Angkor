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