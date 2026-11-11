plugins {
    id("com.android.application")
}

android {
    namespace = "io.screenlatency.tool"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.screenlatency.tool"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":probe"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
