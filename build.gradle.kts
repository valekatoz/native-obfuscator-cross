plugins {
    `maven-publish`
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "java")

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString() + ".nativeobfuscator"
                artifactId = project.name
                version = project.version.toString()
            }
        }
    }

    // Make sure to compile with UTF-8 encoding
    tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

    tasks.withType<Test> { defaultCharacterEncoding = "UTF-8" }

    tasks.withType<Javadoc> { options.encoding = "UTF-8" }
}