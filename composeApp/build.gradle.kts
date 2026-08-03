import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
        iosTarget.compilations["main"].cinterops {
            create("CommonCrypto") {
                defFile = File(projectDir, "src/iosMain/cinterop/CommonCrypto.def")
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-android:0.23.0")
            implementation("com.journeyapps:zxing-android-embedded:4.3.0")
            implementation("androidx.biometric:biometric:1.1.0")
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)

            // Ktor
            implementation(libs.ktor.client.core)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // Navigation
            implementation(libs.navigation.compose)
            // Bitcoin
            implementation(libs.bitcoin.kmp)
            // BigNum for multiplatform BigDecimal support
            implementation("com.ionspin.kotlin:bignum:0.3.10")
            // Ed25519 cryptography (pure Kotlin)
            implementation(libs.curve25519.kotlin)
            // Multiplatform Icons (replacement for androidx.compose.material.icons)
            implementation("br.com.devsrsouza.compose.icons:feather:1.1.1")

            // QR Code generation
            implementation(libs.qr)

            // Date/time and human-readable formatting
            implementation(libs.kotlinx.datetime)
            implementation(libs.human.readable)

            // SQLDelight
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)

            implementation("dev.whyoleg.cryptography:cryptography-core:0.6.0")
            implementation("dev.whyoleg.cryptography:cryptography-provider-optimal:0.6.0")

            // Cryptographically secure random bytes
            implementation("org.kotlincrypto.random:crypto-rand:0.6.0")
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.coroutines.test)
        }

        androidUnitTest.dependencies {
            val currentOs = org.gradle.internal.os.OperatingSystem.current()
            val target = when {
                currentOs.isLinux -> "linux"
                currentOs.isMacOsX -> "darwin"
                currentOs.isWindows -> "mingw"
                else -> error("Unsupported OS $currentOs")
            }
            implementation("fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-$target:0.23.0")
        }
        iosArm64Test.dependencies {
            implementation("fr.acinq.secp256k1:secp256k1-kmp-iosarm64:0.23.0")
        }

        iosX64Test.dependencies {
            implementation("fr.acinq.secp256k1:secp256k1-kmp-iosx64:0.23.0")
        }

        iosSimulatorArm64Test.dependencies {
            implementation("fr.acinq.secp256k1:secp256k1-kmp-iossimulatorarm64:0.23.0")
        }
    }
}

android {
    namespace = "com.ultrabytecoder.kryptakeep"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ultrabytecoder.kryptakeep"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "dimension"

    productFlavors {
        create("productionTestnet") {
            dimension = "dimension"
            applicationIdSuffix = ".testnet"
            versionNameSuffix = "-prod-testnet"
            buildConfigField("boolean", "IS_TESTNET", "true")
            buildConfigField("String", "ETHERSCAN_API_KEY", "\"${localProperties.getProperty("etherscan.testnet.api.key", "")}\"")
        }
        create("productionMainnet") {
            dimension = "dimension"
            applicationIdSuffix = ".mainnet"
            versionNameSuffix = "-prod-mainnet"
            buildConfigField("boolean", "IS_TESTNET", "false")
            buildConfigField("String", "ETHERSCAN_API_KEY", "\"${localProperties.getProperty("etherscan.mainnet.api.key", "")}\"")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

sqldelight {
    databases {
        create("KryptaKeepDatabase") {
            packageName.set("com.ultrabytecoder.kryptakeep.db")
        }
    }
}

