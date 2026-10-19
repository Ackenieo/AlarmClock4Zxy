plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.alarmclock4jacw"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.alarmclock4jacw"
        // 26 起步：覆盖绝大多数在用机型，且低于此版本无法可靠使用前台服务/通知渠道
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.core)

    // 相机 + 实时图像分析
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // 骨骼识别
    implementation(libs.mlkit.pose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
