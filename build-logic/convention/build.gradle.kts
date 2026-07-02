plugins {
    `kotlin-dsl`
}

group = "com.superdash.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ktlint.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "superdash.android.library"
            implementationClass = "SuperdashAndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "superdash.android.library.compose"
            implementationClass = "SuperdashAndroidLibraryComposeConventionPlugin"
        }
    }
}
