#!/usr/bin/env kotlin

import java.nio.file.Path
import kotlin.io.path.createDirectories

fun argValue(name: String): String? {
    val index = args.indexOf(name)
    return if (index >= 0 && index + 1 < args.size) {
        args[index + 1]
    } else {
        null
    }
}

val adb = argValue("--adb") ?: "adb"
val device = argValue("--device") ?: "emulator-5554"
val output = Path.of(argValue("--output") ?: "build/voice-recordings")
val recordings = output.resolve("recordings")
output.createDirectories()
recordings.toFile().deleteRecursively()
recordings.createDirectories()

val remote = "/data/data/com.superdash/files/voice-recordings"
val process =
    ProcessBuilder(adb, "-s", device, "exec-out", "run-as", "com.superdash", "tar", "-C", remote, "-cf", "-", ".")
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
val extract =
    ProcessBuilder("tar", "-xf", "-", "-C", recordings.toString())
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

process.inputStream.use { input ->
    extract.outputStream.use { outputStream ->
        input.copyTo(outputStream)
    }
}
check(process.waitFor() == 0) { "adb export failed" }
check(extract.waitFor() == 0) { "recording extract failed" }
println("recordings\t$recordings")
