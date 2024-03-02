import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.lang.Closure
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

val testOutputs = ConcurrentHashMap<String, StringBuilder>()

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("eclipse")
    id("idea")
}

repositories {
    mavenLocal()
    mavenCentral()
}

java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

dependencies {
    implementation(project(":annotations"))

    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")

    implementation("info.picocli:picocli:4.6.3")

    implementation("org.tomlj:tomlj:1.1.1")

    implementation("me.tongfei:progressbar:0.10.0")

    implementation("org.tinylog:tinylog-api:2.7.0")
    implementation("org.tinylog:tinylog-impl:2.7.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.4.2")
}

// Define default main class
if (!hasProperty("mainClass")) {
    extra["mainClass"] = "dev.lennoxlotl.obfuscator.Main"
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.assemble {
    dependsOn("shadowJar")
}

tasks.jar {
    manifest.attributes(mapOf(Pair("Main-Class", properties["mainClass"])))
}