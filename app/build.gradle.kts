plugins {
    id("com.android.application")
}

android {
    namespace = "com.lzq.shigangquiz"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lzq.shigangquiz"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "2.2.0"
    }

    buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true

        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.google.android.material:material:1.12.0")
}
