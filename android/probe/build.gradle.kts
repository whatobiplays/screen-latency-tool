import org.gradle.api.tasks.testing.Test

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    systemProperty("repositoryRoot", rootProject.projectDir.parentFile.absolutePath)
}
