import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Firma para publicar en Google Play.
//
// Las credenciales viven en "keystore.properties" (raiz del proyecto), que esta en
// .gitignore; "keystore.properties.example" documenta como se genera. Si el fichero
// no existe, la variante release sigue compilando SIN firmar, para que el repositorio
// se pueda clonar y compilar en un equipo sin secretos.
//
// OJO: esta es la clave de SUBIDA. La app que se distribuye la firma Google con su
// propia clave. Se necesita para subir cada .aab nuevo; si se pierde, se pide el
// restablecimiento en Play Console y se sube el siguiente bundle con la clave nueva.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val hasPlaySigning = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.mfreya.misgastos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mfreya.misgastos"
        minSdk = 26
        targetSdk = 36
        // Play exige targetSdk 36 para publicar apps nuevas o actualizar. No bajar.
        //
        // OJO con versionCode: Google Play NO admite dos bundles con el mismo
        // versionCode, asi que hay que subirlo en CADA publicacion (1, 2, 3...).
        // El tope es 2.100.000.000.
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/kotlin")
        }
    }

    signingConfigs {
        if (hasPlaySigning) {
            create("play") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Si no hay keystore.properties, release se genera sin firmar (util para
            // comprobar que compila). Para Play hace falta la firma de arriba.
            signingConfig = if (hasPlaySigning) signingConfigs.getByName("play") else null
            // Play no obliga a ofuscar: isMinifyEnabled = false es valido. Con R8
            // activado, el mapping lo asocia Play solo al subir el bundle.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // Cifrado de la base de datos local (AES-256). La clave vive en el Android Keystore.
    implementation("net.zetetic:sqlcipher-android:4.20.0@aar")
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}