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
        // מספר גרסה עולה אוטומטית בכל בנייה ב-GitHub
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()
        versionCode = 100 + run
        versionName = "1.$run"
    }

    // מפתח קבוע לשימוש אישי (לא ל-Google Play), כדי שכל עדכון יותקן מעל הגרסה הקודמת
    signingConfigs {
        create("personal") {
            storeFile = rootProject.file("signing/flightcompass.jks")
            storePassword = "flightcompass"
            keyAlias = "flightcompass"
            keyPassword = "flightcompass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
        debug {
            signingConfig = signingConfigs.getByName("personal")
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
dependencies {
    // רק לבדיקות על מחשב (org.json האמיתי במקום הגרסה הריקה של android.jar)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

android.testOptions.unitTests.isReturnDefaultValues = true
