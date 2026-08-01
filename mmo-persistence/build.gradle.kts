dependencies {
    api(project(":mmo-api"))
    api(project(":mmo-progression"))
    runtimeOnly("org.postgresql:postgresql:42.7.10")

    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
}
