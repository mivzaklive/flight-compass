plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "biz.dotan.flightcompass"
    compileSdk = 35

    defaultConfig {
        applicationId = "biz.dotan.flightcompass"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // חתימה בדיבאג כדי שאפשר יהיה להתקין ישר על הטלפון לשימוש אישי
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// בלי ספריות חיצוניות: רק Android ו-Kotlin
dependencies {}
