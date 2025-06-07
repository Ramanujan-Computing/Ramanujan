import com.android.utils.cxx.os.quoteCommandLineArgument

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "in.ramanujan.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "in.ramanujan.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
//    externalNativeBuild {
//        cmake {
//            path = file("src/main/cpp1/native/CMakeLists.txt")
//            version = "3.30.3"
//
//
//        }
//    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.ramanujan)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}