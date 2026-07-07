plugins {
    id("superdash.android.library")
}

android {
    namespace = "com.superdash.camera"
}

dependencies {
    implementation(project(":packages:core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.jdk8)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
