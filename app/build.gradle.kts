plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.nvm.traplink"
    compileSdk =37

    defaultConfig {
        applicationId = "com.nvm.traplink"
        minSdk = 24
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    //Retrofit para las peticiones HTTP
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    //Converter Gson para convertir Json a objetos de Kotlin
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}