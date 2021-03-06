
plugins {
    id("kotlin2js")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-html-js:${project.extra["kotlinxHtmlVersion"]}")
}

apply(from = "${extra["gradleScripts"]}/kotlin_js.gradle")
