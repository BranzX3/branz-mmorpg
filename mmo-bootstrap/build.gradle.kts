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
        // The existing consumable-lot flag controls the exact Body Tonic physical acceptance path.
        jvmArgs("-Dmmo.physical-consumable-lot-acceptance=true")
    }
    val physicalConsumableC4Acceptance =
        providers.gradleProperty("physicalConsumableC4Acceptance").orNull == "true"
    if (physicalConsumableC4Acceptance) {
        // C4 opens only the exact signed training coating projection in the test quarantine.
        jvmArgs(
            "-Dmmo.physical-consumable-c4-acceptance=true",
            // Reuse existing inventory diagnostic markers so the harness proves the invalid clicks
            // reached the production physical move controller. Quarantine admission is still C4-exact.
            "-Dmmo.physical-consumable-lot-acceptance=true",
        )
    }
    val physicalShieldD13Acceptance =
        providers.gradleProperty("physicalShieldD13Acceptance").orNull == "true"
    if (physicalShieldD13Acceptance) {
        // D1-D3 opens only the exact signed Training Shield projection for inventory/F-key input.
        jvmArgs("-Dmmo.physical-shield-d13-acceptance=true")
    }
    val physicalShieldD46Acceptance =
        providers.gradleProperty("physicalShieldD46Acceptance").orNull == "true"
    if (physicalShieldD46Acceptance) {
        // D4/D6 uses this only for physical-client acceptance diagnostics.
        jvmArgs("-Dmmo.physical-shield-d46-acceptance=true")
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
