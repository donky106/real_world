/*
 * Check usage in the `README.md` file.
 * Dependencies' versions are defined in the `gradle.properties` file.
 */

plugins {
    application
    war
}

apply(from = "${extra["gradleScripts"]}/kotlin.gradle")
apply(from = "${extra["gradleScripts"]}/dokka.gradle")
apply(from = "${extra["gradleScripts"]}/service.gradle")
apply(from = "${extra["gradleScripts"]}/junit.gradle")

application {
    mainClassName = "com.hexagonkt.realworld.ApplicationKt"
    applicationDefaultJvmArgs = listOf("-XX:+UseNUMA", "-XX:+UseParallelGC", "-XX:+AggressiveOpts")
}

tasks.war {
    archiveFileName.set("ROOT.war")
}

task("doc") {
    dependsOn("dokka", "jacocoTestReport")
}

task("all") {
    dependsOn("installDist", "jarAll", "doc")
}

dependencies {
    implementation("com.hexagonkt:http_server_jetty:${project.extra["hexagonVersion"]}")
    implementation("com.hexagonkt:store_mongodb:${project.extra["hexagonVersion"]}")
    implementation("com.auth0:java-jwt:${project.extra["javaJwtVersion"]}")

    testImplementation("com.hexagonkt:port_http_client:${project.extra["hexagonVersion"]}")
}
