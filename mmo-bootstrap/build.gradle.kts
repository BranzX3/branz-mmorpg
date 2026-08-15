plugins {
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

dependencies {
    implementation(project(":mmo-api"))
    implementation(project(":mmo-content"))
    implementation(project(":mmo-combat"))
    implementation(project(":mmo-magic"))
    implementation(project(":mmo-items"))
    implementation(project(":mmo-progression"))
    implementation(project(":mmo-persistence"))
    implementation(project(":mmo-scenes"))
    implementation(project(":mmo-social"))
    implementation(project(":mmo-worldloop"))
    implementation(project(":mmo-integrations"))
    implementation(project(":mmo-integrations:integration-oraxen"))
    implementation(project(":mmo-integrations:integration-mythicmobs"))
    implementation(project(":mmo-integrations:integration-packetevents"))
    implementation(project(":mmo-integrations:integration-worldguard"))
    implementation(project(":mmo-integrations:integration-wallet"))
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation("io.zonky.test:embedded-postgres:2.2.2")
    runtimeOnly("org.postgresql:postgresql:42.7.10")
    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    minecraftVersion("26.2")
    val configuredContent = providers.gradleProperty("contentPath").orNull
    val contentFixture =
        when {
            providers.gradleProperty("smokeInvalidContent").orNull == "true" ->
                rootProject.layout.projectDirectory
                    .dir("example-content/milestone-1-invalid")
                    .asFile
            configuredContent != null -> rootProject.file(configuredContent)
            else ->
                rootProject.layout.projectDirectory.dir("example-content/milestone-1").asFile
        }
    jvmArgs("-Dmmo.content.path=${contentFixture.absolutePath}")

    val physicalPrimaryInputAcceptance =
        providers.gradleProperty("physicalPrimaryInputAcceptance").orNull == "true"
    if (physicalPrimaryInputAcceptance) {
        jvmArgs("-Dmmo.physical-primary-input-acceptance=true")
    }
    val physicalPrimaryBrokenAcceptance =
        providers.gradleProperty("physicalPrimaryBrokenAcceptance").orNull == "true"
    if (physicalPrimaryBrokenAcceptance) {
        jvmArgs("-Dmmo.physical-broken-acceptance=true")
    }
    val physicalHotbarAcceptance =
        providers.gradleProperty("physicalHotbarAcceptance").orNull == "true"
    if (physicalHotbarAcceptance) {
        jvmArgs("-Dmmo.physical-hotbar-acceptance=true")
    }
    val physicalConsumableLotAcceptance =
        providers.gradleProperty("physicalConsumableLotAcceptance").orNull == "true"
    val physicalConsumableUseAcceptance =
        providers.gradleProperty("physicalConsumableUseAcceptance").orNull == "true"
    if (physicalConsumableLotAcceptance || physicalConsumableUseAcceptance) {
        // The existing consumable-lot flag controls physical inventory diagnostics only.
        // Reuse it for C3 so pickup/place ordering is visible without changing gameplay policy.
        jvmArgs("-Dmmo.physical-consumable-lot-acceptance=true")
    }

    val smokeTest = providers.gradleProperty("smokeTest").orNull == "true"
    if (smokeTest) {
        val smokeDatabaseDirectory =
            layout.projectDirectory.dir("run/plugins/BranzMMO/smoke-embedded-postgres").asFile
        val smokeFailureMarker =
            layout.projectDirectory.file("run/plugins/BranzMMO/smoke-startup-failure.marker").asFile
        doFirst {
            project.delete(smokeDatabaseDirectory)
            project.delete(smokeFailureMarker)
        }
        jvmArgs(
            "-Dmmo.bootstrap.smoke-test=true",
        )
        doLast {
            if (smokeFailureMarker.isFile) {
                throw GradleException(
                    "Bootstrap smoke startup failed: ${smokeFailureMarker.readText().trim()}",
                )
            }
        }
    }
}