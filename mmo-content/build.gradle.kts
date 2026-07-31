plugins {
    application
}

dependencies {
    api(project(":mmo-api"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0")
}

application {
    applicationName = "mmo-content"
    mainClass.set("com.branz.mmorpg.content.cli.ContentCli")
}

tasks.register<JavaExec>("generateContentSchemas") {
    group = "content"
    description = "Generate editor JSON Schemas from runtime schema metadata."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.branz.mmorpg.content.cli.ContentCli")
    args("schema", rootProject.layout.projectDirectory.dir("schemas/generated").asFile.absolutePath)
}
