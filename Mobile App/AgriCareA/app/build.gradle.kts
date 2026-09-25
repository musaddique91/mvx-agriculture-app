import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
}

// The NVIDIA key lives in local.properties (gitignored), not in the repo.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val nvidiaApiKey: String = localProperties.getProperty("NVIDIA_API_KEY") ?: ""
// Free key from https://data.gov.in — optional; without it the Mandi screen says so.
val dataGovApiKey: String = localProperties.getProperty("DATA_GOV_API_KEY") ?: ""

android {
    namespace = "com.mvx.agriculture"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mvx.agriculture"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "NVIDIA_API_KEY", "\"$nvidiaApiKey\"")
        buildConfigField("String", "NVIDIA_BASE_URL", "\"https://integrate.api.nvidia.com/v1/chat/completions\"")
        buildConfigField("String", "NVIDIA_MODEL", "\"meta/llama-3.2-11b-vision-instruct\"")
        buildConfigField("String", "NVIDIA_VISION_MODEL", "\"meta/llama-3.2-11b-vision-instruct\"")
        buildConfigField("String", "DATA_GOV_API_KEY", "\"$dataGovApiKey\"")
        buildConfigField("String", "OPEN_METEO_URL", "\"https://api.open-meteo.com/v1/forecast\"")
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
    androidResources {
        // The models barely compress, and storing them as-is keeps loading quick.
        noCompress += "onnx"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures{
        viewBinding = true
        dataBinding=true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    // OpenStreetMap: field mapping with no API key and no billing account
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    testImplementation(libs.junit)
    // A real JSON parser for JVM tests; android.jar's org.json is only a stub there.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation ("androidx.work:work-runtime-ktx:2.7.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation ("com.squareup.okhttp3:okhttp:4.9.3")
    implementation ("com.google.code.gson:gson:2.8.6")
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")

    // 2.17 ships 16 KB page-aligned .so files, which Android 15+ devices require.
    // Its API classes now come from litert-api, so keep the support library from
    // dragging the old tensorflow-lite-api back in and duplicating them.
    // MobileSAM (MIT) finds a field's border from one tap, on the phone.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0")
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4") {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite")
    }
}
