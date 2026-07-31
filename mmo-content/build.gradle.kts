plugins {
    application
}

dependencies {
    api(project(":mmo-api"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
}

application {
    applicationName = "mmo-content"
    mainClass.set("com.branz.mmorpg.content.cli.ContentCli")
}
