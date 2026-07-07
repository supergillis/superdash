plugins {
    id("superdash.android.library")
}

android {
    namespace = "com.superdash.kiosk.bus"
}

dependencies {
    implementation(project(":packages:core"))
    implementation(libs.kotlinx.coroutines.jdk8)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
