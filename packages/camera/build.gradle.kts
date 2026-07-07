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
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
