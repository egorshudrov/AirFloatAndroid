plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {

    namespace = "com.airfloat.app"

    compileSdk = 34

    defaultConfig {

        applicationId = "com.airfloat.app"

        minSdk = 26

        targetSdk = 34

        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {

        viewBinding = false
        dataBinding = false

    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

    }

    kotlinOptions {

        jvmTarget = "17"

    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")

    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    testImplementation("junit:junit:4.13.2")

}
