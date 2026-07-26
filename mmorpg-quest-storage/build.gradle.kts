plugins { `java-library` }

dependencies {
    api(project(":mmorpg-quest-api"))
    implementation(project(":mmorpg-storage"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.0")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:mysql:1.21.3")
    testRuntimeOnly("com.mysql:mysql-connector-j:9.4.0")
}
