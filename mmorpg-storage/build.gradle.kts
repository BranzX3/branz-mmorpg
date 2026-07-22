plugins {
    `java-library`
}

dependencies {
    api(project(":mmorpg-api"))
    implementation("com.zaxxer:HikariCP:6.3.1")
    implementation("com.mysql:mysql-connector-j:9.4.0")
    implementation("org.flywaydb:flyway-core:11.11.2")
    implementation("org.flywaydb:flyway-mysql:11.11.2")
    implementation("org.slf4j:slf4j-api:2.0.17")
}
