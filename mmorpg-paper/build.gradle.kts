plugins {
    java
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

dependencies {
    implementation(project(":mmorpg-api"))
    implementation(project(":mmorpg-content"))
    implementation(project(":mmorpg-storage"))
    implementation(project(":mmorpg-core"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("BranzMMORPG")
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "com.branz.mmorpg.libs.hikari")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    minecraftVersion("26.2")
    jvmArgs("-Dcom.mojang.eula.agree=true", "--enable-native-access=ALL-UNNAMED")
}
