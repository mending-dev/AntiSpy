val paperApiVersion: String by project

repositories {
    maven("https://repo.helpch.at/releases/")
}

dependencies {
    implementation(project(":core"))
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}