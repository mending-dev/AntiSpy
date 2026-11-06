val velocityApiVersion: String by project

dependencies {
    implementation(project(":core"))
    implementation("org.yaml:snakeyaml:2.4")

    compileOnly("com.velocitypowered:velocity-api:$velocityApiVersion")
    // Generates velocity-plugin.json from the @Plugin annotation
    annotationProcessor("com.velocitypowered:velocity-api:$velocityApiVersion")
}