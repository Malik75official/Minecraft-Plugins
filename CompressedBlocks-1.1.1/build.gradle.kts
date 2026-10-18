plugins {
    java
}

group = "dev.codex"
version = "1.1.1"

base {
    archivesName.set("CompressedBlocks")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Pin a stable 26.2 API while remaining forward-compatible with newer 26.2 builds.
    val paperApi = "io.papermc.paper:paper-api:26.2.build.111-stable"
    compileOnly(paperApi)
    testImplementation(paperApi)

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.processResources {
    val projectVersion = project.version.toString()
    inputs.property("version", projectVersion)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to projectVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    // A forked javac also avoids Windows ZipFS keeping dependency JARs locked.
    options.isFork = true
    options.isIncremental = false
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Implementation-Title"] = "CompressedBlocks"
        attributes["Implementation-Version"] = project.version
    }
}
