plugins {
    id("superdash.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.superdash.core"

    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation(libs.kotlinx.serialization.json)

    testFixturesImplementation(libs.kotlinx.coroutines.jdk8)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
