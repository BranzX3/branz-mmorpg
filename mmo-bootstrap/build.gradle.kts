plugins {
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

dependencies {
    implementation(project(":mmo-api"))
    implementation(project(":mmo-content"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    minecraftVersion("26.2")
    if (providers.gradleProperty("smokeTest").orNull == "true") {
        jvmArgs("-Dmmo.bootstrap.smoke-test=true")
    }
}
