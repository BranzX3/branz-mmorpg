plugins {
    `java-library`
}

dependencies {
    api(project(":mmorpg-api"))
    implementation(project(":mmorpg-content"))
    implementation(project(":mmorpg-storage"))
}
