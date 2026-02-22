plugins {
    java
}

group = "dev.patric.dungeonsreborn"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://nexus.frengor.com/repository/public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly(files("lib/UltimateAdvancementAPI-Plugin-2.7.2.jar"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.register<JavaExec>("dslLint") {
    group = "verification"
    description = "Lint a DSL script file (self-contained parser)."
    classpath = sourceSets.main.get().compileClasspath + sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.patric.dungeonsreborn.tools.DslLint")
}

tasks.register<JavaExec>("dslSnapshot") {
    group = "verification"
    description = "Create a normalized DSL snapshot for diffs."
    classpath = sourceSets.main.get().compileClasspath + sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.patric.dungeonsreborn.tools.DslSnapshot")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
