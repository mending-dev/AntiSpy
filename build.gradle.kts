plugins {
    java
    // Use the latest 9.x version
    id("com.gradleup.shadow") version "9.0.0"
}

allprojects {
    apply(plugin = "java")

    group = "dev.mending"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
    }
}

dependencies {
    // Only runtime dependencies end up in the final jar (compileOnly APIs are excluded automatically)
    implementation(project(":paper"))
    implementation(project(":velocity"))
}

tasks.shadowJar {
    archiveBaseName.set("AntiSpy")
    archiveClassifier.set("")

    // Relocate SnakeYAML (only used on Velocity) so it cannot clash with other plugins
    relocate("org.yaml.snakeyaml", "dev.mending.antispy.libs.snakeyaml")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}