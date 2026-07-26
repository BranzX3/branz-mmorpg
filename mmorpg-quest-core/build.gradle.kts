plugins { `java-library` }

dependencies {
    api(project(":mmorpg-quest-api"))
    implementation(project(":mmorpg-content"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0")
}
