plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.workbuddy.bookreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.workbuddy.bookreader"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // Kotlin 2.0 用内置 Compose 插件管理编译器扩展版本
    composeOptions { kotlinCompilerExtensionVersion = "2.0.0" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Kotlin 协程库（与 Kotlin 2.0.20 兼容）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // EPUB 解析
    implementation("org.jsoup:jsoup:1.17.2")
    // PDF 渲染：已改用系统 PdfRenderer，无需引入 barteksc:pdfium-android
    // PDF OCR（ML Kit 文字识别）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    // 翻译（ML Kit 离线/在线翻译）
    implementation("com.google.mlkit:translate:17.0.2")
    // MOBI 解析辅助（zip/编码检测）
    implementation("com.googlecode.juniversalchardet:juniversalchardet:1.0.3")
}
