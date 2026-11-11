plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// A versão (= a tag do GitHub) decide a app: "beta-…" é a Cyclo Beta, outra app no Android
// (com.gaebriu.cyclo.beta: dados, preferências, widgets, notificações e atualizações à parte, instala-se
// ao lado da estável); "release-…" é a Cyclo estável (com.gaebriu.cyclo, nunca muda). O namespace
// (classes, R) é o mesmo nas duas.
val cycloVersionName = "beta-3.0.1"
val cycloBeta = cycloVersionName.startsWith("beta-")

android {
    namespace = "com.gaebriu.cyclo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = if (cycloBeta) "com.gaebriu.cyclo.beta" else "com.gaebriu.cyclo"
        minSdk = 33
        targetSdk = 37
        // versionName = a tag do GitHub ("beta-3.0", prerelease; as estáveis são "release-x.x"). Mostra-se "Cyclo Beta 3.0".
        // versionCode 42 (beta-3.0.1; a release-2.4 republicada é o 41, a beta-3.0 refeita o 40, a primeira release-2.4 o 39 e a beta-3.0 antiga, em com.gaebriu.cyclo, foi o 38; a release-2.3 foi o 37, a release-2.2 o 36, a release-2.1 o 35, a release-2.0 o 34, a beta-2.4 o 33; a beta-2.2 o 31, saltando o 30 de uma build release-1.2 de testes).
        versionCode = 42
        versionName = cycloVersionName
        // O nome no launcher (e nos widgets e nas definições do Android): "Cyclo Beta" na beta.
        manifestPlaceholders["appLabel"] = if (cycloBeta) "Cyclo Beta" else "Cyclo"
        // O canal do atualizador (CycloUpdates): a beta só anuncia betas, a estável só estáveis.
        buildConfigField("boolean", "BETA_APP", cycloBeta.toString())

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
        buildConfig = true
    }
}

// O ficheiro do APK chama-se sempre "<applicationId>.apk" (com.gaebriu.cyclo.apk), em debug e em
// release; é este o ficheiro que vai para as releases do GitHub. (O AGP não tem API pública para
// o nome: VariantOutputImpl.outputFileName é a forma suportada na prática; rever ao subir o AGP.)
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName
                ?.set(variant.applicationId.map { "$it.apk" })
        }
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