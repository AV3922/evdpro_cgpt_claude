plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val smtpHost = System.getenv("EVDOCTOR_SMTP_HOST") ?: "smtp.zoho.com"
val smtpPort = System.getenv("EVDOCTOR_SMTP_PORT") ?: "465"
val smtpProtocol = System.getenv("EVDOCTOR_SMTP_PROTOCOL") ?: "SSL"
val smtpUser = System.getenv("EVDOCTOR_SMTP_USER") ?: "data@evegalabs.com"
val smtpPassword = System.getenv("EVDOCTOR_SMTP_PASSWORD") ?: ""
val smtpSenderName = System.getenv("EVDOCTOR_SMTP_SENDER_NAME") ?: "E-VEGA LABS"
val smtpRecipients = System.getenv("EVDOCTOR_SMTP_RECIPIENTS") ?: "data@evegalabs.com"

android {
    namespace = "com.batteryok.evdoctor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.batteryok.evdoctor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SMTP_HOST", "\"$smtpHost\"")
        buildConfigField("int", "SMTP_PORT", smtpPort)
        buildConfigField("String", "SMTP_PROTOCOL", "\"$smtpProtocol\"")
        buildConfigField("String", "SMTP_USER", "\"$smtpUser\"")
        buildConfigField("String", "SMTP_PASSWORD", "\"$smtpPassword\"")
        buildConfigField("String", "SMTP_SENDER_NAME", "\"$smtpSenderName\"")
        buildConfigField("String", "SMTP_RECIPIENTS", "\"$smtpRecipients\"")
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
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE.md"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.mpandroidchart)
    implementation(libs.viewpager2)
    implementation(libs.lottie)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.poi.ooxml)
    implementation(libs.android.mail)
    implementation(libs.android.activation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
