plugins {
    // Use explicit plugin ids instead of version-catalog aliases to avoid "Unresolved reference: library"
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.opensource.svgaplayer"
    compileSdk = 36

    defaultConfig {
        // align minSdk with app module
        minSdk = 28
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
    api(libs.wire)
    api(libs.coroutines)

    implementation(platform(libs.okhttpPlatformBom)) // 提供版本
    implementation(libs.okhttp3) // 只引入核心

}
