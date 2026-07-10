plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.overlaymanager.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.overlaymanager.app"
        // minSdk 19 = Android 4.4 (KitKat), enforced for real this time.
        //
        // No AndroidX (or any other external) library is used anywhere in this
        // module on purpose. androidx.appcompat, material and even recent
        // core-ktx releases all silently require minSdk 21+ internally, which
        // is exactly what broke the previous build even though this file said
        // minSdk = 19. Every screen, widget and the notification are built
        // with plain android.* framework APIs only, each SDK-version-gated
        // by hand where the framework itself changed behaviour across
        // versions (see MainActivity, LayerEditActivity and OverlayService).
        minSdk = 19
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

// No dependencies block: zero external libraries, zero minSdk surprises.
