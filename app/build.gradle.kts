plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gaebriu.cyclo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gaebriu.cyclo"
        minSdk = 33
        targetSdk = 37
        // versionName = a tag do GitHub ("beta-1.0.0"; final: "1.0.0"). Mostra-se "Cyclo Beta 1.0".
        versionCode = 16
        versionName = "beta-1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Chave das releases: o keystore e as palavras-passe ficam FORA do repositório, no
    // ~/.gradle/gradle.properties (CYCLO_RELEASE_*). Sem elas, o release sai sem assinatura.
    // Todas as releases têm de usar a mesma chave: o Android só aceita atualizações assinadas igual.
    val releaseStore = providers.gradleProperty("CYCLO_RELEASE_STORE_FILE").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("CYCLO_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("CYCLO_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("CYCLO_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
            proguardFiles("proguard-rules.pro")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    // O org.json do Android é só um esboço nos testes locais (lança exceção): usa-se o verdadeiro.
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}