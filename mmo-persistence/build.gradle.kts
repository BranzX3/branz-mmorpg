dependencies {
    api(project(":mmo-api"))
    api(project(":mmo-progression"))
    api(project(":mmo-lifeskills"))
    runtimeOnly("org.postgresql:postgresql:42.7.10")

    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
}
