import de.undercouch.gradle.tasks.download.Download

plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version "9.6.0"
    id("de.undercouch.download") version "5.7.0"
    id("jacoco")
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
    // Reporting only, never a build gate: running `test` also refreshes the
    // coverage report, but a low-coverage class never fails the build.
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// ---------------------------------------------------------------------------
// Fonts (see src/main/resources/fonts/README.md)
//
// Automates the manual "open the Google Fonts page, download the zip, unzip
// it here" step that README describes. Entirely optional — FontManager
// already falls back gracefully when a font is missing, so skipping this
// task, or running it offline, changes nothing except which face gets drawn.
// Run with: ./gradlew fetchFonts
// ---------------------------------------------------------------------------

val fontsDir = layout.projectDirectory.dir("src/main/resources/fonts")

// Destination filename (a candidate FontManager.java already looks for) ->
// public-domain (OFL) source in the Google Fonts repository. Kantumruy Pro,
// Cinzel and EB Garamond now ship upstream as a single variable-weight file
// rather than separate static weights. Font.createFont loads a variable
// font's default instance the same way it loads a static one, so each is
// saved under the plain "-Regular" name FontManager already treats as an
// accepted candidate.
val fontSources = mapOf(
    "Suwannaphum-Regular.ttf" to
        "https://raw.githubusercontent.com/google/fonts/main/ofl/suwannaphum/Suwannaphum-Regular.ttf",
    "KantumruyPro-Regular.ttf" to
        "https://raw.githubusercontent.com/google/fonts/main/ofl/kantumruypro/KantumruyPro%5Bwght%5D.ttf",
    "CinzelDecorative-Black.ttf" to
        "https://raw.githubusercontent.com/google/fonts/main/ofl/cinzeldecorative/CinzelDecorative-Black.ttf",
    "CinzelDecorative-Bold.ttf" to
        "https://raw.githubusercontent.com/google/fonts/main/ofl/cinzeldecorative/CinzelDecorative-Bold.ttf",
    "Cinzel-Regular.ttf" to
        "https://raw.githubusercontent.com/google/fonts/main/ofl/cinzel/Cinzel%5Bwght%5D.ttf",
    "EBGaramond-Regular.ttf" to
        "https://raw.githubusercontent.com/google/fonts/main/ofl/ebgaramond/EBGaramond%5Bwght%5D.ttf"
)

val fetchFontTasks = fontSources.map { (fileName, url) ->
    tasks.register<Download>("fetchFont-" + fileName.substringBeforeLast(".")) {
        src(url)
        dest(fontsDir.file(fileName).asFile)
        // Never clobber a file someone already put here by hand.
        overwrite(false)
    }
}

tasks.register("fetchFonts") {
    group = "guardians of angkor"
    description = "Downloads the optional bundled fonts listed in " +
        "src/main/resources/fonts/README.md (skips any file already present)."
    dependsOn(fetchFontTasks)
}
