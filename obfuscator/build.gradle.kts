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

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

dependencies {
    implementation(project(":annotations"))

    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")

    implementation("info.picocli:picocli:4.6.3")

    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.apache.logging.log4j:log4j-core:2.12.4")

    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.12.4")
    implementation("org.apache.logging.log4j:log4j-api:2.12.4")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.4.2")
}

// Define default main class
if (!hasProperty("mainClass")) {
    extra["mainClass"] = "dev.lennoxlotl.obfuscator.Main"
}

// Define test sources
if (hasProperty("ide.eclipse")) {
    tasks.processTestResources {
        from("test_data")
    }
} else {
    val sourceSet = sourceSets.getByName("test")
    sourceSet.resources.srcDir(file("test_data"))
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

tasks.withType<Test> {
    useJUnitPlatform()

    maxParallelForks = max(Runtime.getRuntime().availableProcessors(), 32)

    testLogging {
        setEvents(emptyList<Any>())
    }

    addTestOutputListener(object : TestOutputListener {
        override fun onOutput(descriptor: TestDescriptor, event: TestOutputEvent) {
            if (!testOutputs.containsKey(descriptor.name)) {
                testOutputs[descriptor.name] = StringBuilder()
            }

            testOutputs[descriptor.name]!!.append("[")
                    .append(event.destination.toString().uppercase()).append("] ")
                    .append(event.message)
        }
    })

    addTestListener(object : TestListener {
        private val ci = System.getenv("CI")?.toBoolean() ?: false

        override fun beforeSuite(suite: TestDescriptor) {
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        }

        override fun beforeTest(descriptor: TestDescriptor) {
            if (ci) {
                print("\u001b[1;33m")
            }
            testOutputs[descriptor.name] = StringBuilder()
            print("Running test \"${descriptor.displayName}\" -> ")
            System.out.flush()
        }

        override fun afterTest(descriptor: TestDescriptor, result: TestResult) {
            if (ci) {
                when (result.resultType) {
                    TestResult.ResultType.SUCCESS -> print("\u001b[1;32m")
                    TestResult.ResultType.FAILURE -> print("\u001b[1;31m")
                    TestResult.ResultType.SKIPPED -> print("\u001b[1;34m")
                    null -> {}
                }
            }
            print(result.resultType.toString())
            if (ci) {
                print("\u001b[1;33m")
            }
            println(" in ${result.endTime - result.startTime}ms")
            if (result.resultType == TestResult.ResultType.FAILURE) {
                if (ci) {
                    print("\u001b[1;31m")
                }
                println("Test fail log")
                print(testOutputs[descriptor.name].toString())
                testOutputs.remove(descriptor.name)
            }
        }

    })
}