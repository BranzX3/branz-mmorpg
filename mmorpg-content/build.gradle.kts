plugins {
    `java-library`
}

dependencies {
    api(project(":mmorpg-api"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.2")
}
