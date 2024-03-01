plugins {
    `maven-publish`
    java
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString() + ".nativeobfuscator"
                artifactId = project.name
                version = project.version.toString()
            }
        }
    }

    dependencies {
        compileOnly("org.projectlombok:lombok:1.18.30")
        annotationProcessor("org.projectlombok:lombok:1.18.30")

        testCompileOnly("org.projectlombok:lombok:1.18.30")
        testAnnotationProcessor("org.projectlombok:lombok:1.18.30")
    }

    // Make sure to compile with UTF-8 encoding
    tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

    tasks.withType<Test> { defaultCharacterEncoding = "UTF-8" }

    tasks.withType<Javadoc> { options.encoding = "UTF-8" }
}