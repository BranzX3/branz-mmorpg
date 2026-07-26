plugins { `java-library` }

dependencies {
    api(project(":mmorpg-quest-api"))
    implementation(project(":mmorpg-quest-core"))
    implementation(project(":mmorpg-quest-storage"))
    implementation(project(":mmorpg-storage"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}
