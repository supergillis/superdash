plugins {
    id("superdash.android.library")
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.superdash.esphome"
}

// Inject Java codegen options into a build-time copy of api.proto.
// The checked-in protocol file stays pristine.
val prepareEsphomeProto by tasks.registering {
    val source = file("src/main/proto-pristine/api.proto")
    val target = file("src/main/proto/api.proto")
    inputs.file(source)
    outputs.file(target)
    doLast {
        target.parentFile.mkdirs()
        val original = source.readText()
        val firstNewline = original.indexOf('\n')
        check(firstNewline > 0) { "api.proto has no newline?" }
        val syntaxLine = original.substring(0, firstNewline + 1)
        val rest = original.substring(firstNewline + 1)
        target.writeText(
            syntaxLine +
                "// Options injected at build time by :esphome-server:prepareEsphomeProto;\n" +
                "// the pristine source copy is src/main/proto-pristine/api.proto.\n" +
                "option java_package = \"org.esphome.api\";\n" +
                "option java_multiple_files = true;\n" +
                rest,
        )
    }
}

dependencies {
    implementation(project(":packages:core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.ktor.network)
    implementation(libs.tink.android)
    implementation(libs.protobuf.kotlin.lite)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.35.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.dependsOn(prepareEsphomeProto)
            // src/main/proto-include/ provides api_options.proto on the protoc
            // include path so api.proto's `import "api_options.proto"` resolves.
            task.addIncludeDir(project.files("src/main/proto-include"))
            task.builtins {
                create("java") { option("lite") }
                create("kotlin") { option("lite") }
            }
        }
    }
}

// Fix for implicit dependency warning in newer Gradle/Protobuf versions
tasks.configureEach {
    if (name.startsWith("process") && name.endsWith("ProtoResources")) {
        dependsOn(prepareEsphomeProto)
    }
}
