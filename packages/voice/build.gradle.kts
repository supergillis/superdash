plugins {
    id("superdash.android.library")
    alias(libs.plugins.kotlin.serialization)
}

val whisperNativePropertyValue = providers.gradleProperty("superdashWhisperNative").orNull
val whisperNativeEnabled =
    when (whisperNativePropertyValue?.lowercase()) {
        null,
        "false",
        -> {
            false
        }
        "true" -> {
            true
        }
        else -> {
            throw GradleException(
                "superdashWhisperNative must be true or false, got '$whisperNativePropertyValue'.",
            )
        }
    }
val whisperNativeCMakeFile = file("src/main/cpp/whisper.cpp/CMakeLists.txt")

if (whisperNativeEnabled && !whisperNativeCMakeFile.exists()) {
    throw GradleException(
        "Whisper native support was requested with -PsuperdashWhisperNative=true, " +
            "but packages/voice/src/main/cpp/whisper.cpp/CMakeLists.txt is missing.",
    )
}

android {
    namespace = "com.superdash.voice"

    defaultConfig {
        buildConfigField(
            "boolean",
            "WHISPER_NATIVE_ENABLED",
            whisperNativeEnabled.toString(),
        )
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DSUPERDASH_WHISPER_SOURCE_PRESENT=$whisperNativeEnabled"
            }
        }
        ndk {
            abiFilters +=
                listOf(
                    "arm64-v8a",
                    "armeabi-v7a",
                    "x86_64",
                    "x86",
                )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(project(":packages:core"))
    implementation(project(":packages:kiosk-bus"))
    implementation(project(":packages:ha-client"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.tensorflow.lite)
    implementation(libs.konovalov.vad.webrtc)
    implementation(libs.moonshine.voice)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
