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
    val combatAcceptance = providers.gradleProperty("combatAcceptance").orNull == "true"
    val physicalLmbAcceptance = providers.gradleProperty("physicalLmbAcceptance").orNull == "true"
    if (physicalLmbAcceptance) {
        val acceptanceMarker =
            project.layout.buildDirectory.file("physical-lmb-ingress-acceptance.pass").get().asFile
        jvmArgs(
            "-Dmmo.bootstrap.physical-lmb-acceptance-test=true",
            "-Dmmo.bootstrap.physical-lmb-acceptance-marker=${acceptanceMarker.absolutePath}",
        )
        doFirst {
            acceptanceMarker.delete()
        }
        doLast {
            check(
                acceptanceMarker.isFile &&
                    acceptanceMarker.readText().trim() == "PHYSICAL_LMB_INGRESS_ACCEPTANCE_PASS",
            ) {
                "Physical LMB ingress acceptance marker was not produced."
            }
        }
    } else if (combatAcceptance) {
        val acceptanceMarker =
            project.layout.buildDirectory.file("combat-runtime-acceptance.pass").get().asFile
        jvmArgs(
            "-Dmmo.bootstrap.smoke-test=true",
            "-Dmmo.bootstrap.combat-acceptance-test=true",
            "-Dmmo.bootstrap.combat-acceptance-marker=${acceptanceMarker.absolutePath}",
        )
        doFirst {
            acceptanceMarker.delete()
        }
        doLast {
            check(
                acceptanceMarker.isFile &&
                    acceptanceMarker.readText().trim() == "COMBAT_RUNTIME_ACCEPTANCE_PASS",
            ) {
                "Paper combat runtime acceptance marker was not produced."
            }
        }
    } else if (providers.gradleProperty("smokeTest").orNull == "true") {
        jvmArgs(
            "-Dmmo.bootstrap.smoke-test=true",
        )
    }
}
