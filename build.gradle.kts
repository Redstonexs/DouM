plugins {
    java
}

group = "io.github.doum"
// Override at build time with -PreleaseVersion=<x.y.z> (used by the release CI job to stamp the tag).
version = (findProperty("releaseVersion") as String?)?.takeIf { it.isNotBlank() } ?: "0.1.0-SNAPSHOT"

// Paper/Folia 26.2 line (currently alpha-only; dynamic build selector picks the latest 26.2 build).
val paperApi = "io.papermc.paper:paper-api:26.2.build.+"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(paperApi)

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(paperApi)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    filteringCharset = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
