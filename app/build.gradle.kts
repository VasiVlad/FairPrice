plugins {
    id("com.android.application")
}

android {
    namespace = "ru.vlad.unitprice"
    compileSdk = 33

    defaultConfig {
        applicationId = "ru.vlad.unitprice"
        minSdk = 26
        targetSdk = 33
        versionCode = 17
        versionName = "0.17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
