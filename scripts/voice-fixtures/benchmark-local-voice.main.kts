#!/usr/bin/env kotlin

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.streams.toList

data class VoiceFixture(
    val source: String,
    val wakeFile: String,
    val commandFile: String,
    val wakeWord: String,
    val expectedText: String,
    val action: String,
    val target: String,
    val silenceMs: Int,
)

fun argValue(name: String): String? {
    val index = args.indexOf(name)
    return if (index >= 0 && index + 1 < args.size) {
        args[index + 1]
    } else {
        null
    }
}

fun hasFlag(name: String): Boolean = args.contains(name)

val adb = argValue("--adb") ?: "adb"
val manifest = Path.of(argValue("--manifest") ?: "packages/app/src/androidTest/assets/voice/commands/manifest.tsv")
val recordings = argValue("--recordings")?.let { Path.of(it) }
val providers = (argValue("--providers") ?: "ha_assist,whisper,moonshine").split(",").filter { it.isNotBlank() }
val sttModel = argValue("--stt-model")?.takeIf { it.isNotBlank() }
val device = argValue("--device") ?: "emulator-5554"
val waitMs = argValue("--wait-ms")?.toLong() ?: 65_000L
val dryRun = hasFlag("--dry-run")
val fixtureRoot = manifest.parent

fun loadManifestFixtures(path: Path): List<VoiceFixture> =
    Files
        .readAllLines(path)
        .filter { line -> line.isNotBlank() && !line.startsWith("#") }
        .map { line ->
            val columns = line.split("\t")
            check(columns.size >= 7) { "bad manifest row: $line" }
            VoiceFixture(
                source = "generated",
                wakeFile = fixtureRoot.resolve(columns[0]).toString(),
                commandFile = fixtureRoot.resolve(columns[1]).toString(),
                wakeWord = columns[2],
                expectedText = columns[3],
                action = columns[4],
                target = columns[5],
                silenceMs = columns[6].toInt(),
            )
        }

fun jsonString(text: String, name: String): String? {
    val pattern = Regex("\"${Regex.escape(name)}\"\\s*:\\s*(null|\"((?:\\\\.|[^\"])*)\")")
    val match = pattern.find(text) ?: return null
    if (match.groupValues[1] == "null") {
        return null
    }
    return match.groupValues[2]
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

fun recordingInputDir(path: Path): Path =
    if (Files.isDirectory(path.resolve("recordings"))) {
        path.resolve("recordings")
    } else {
        path
    }

fun loadRecordingFixtures(path: Path): List<VoiceFixture> {
    val inputDir = recordingInputDir(path)
    if (!Files.exists(inputDir)) {
        return emptyList()
    }
    return Files
        .list(inputDir)
        .use { stream -> stream.filter { file -> file.extension == "json" }.toList() }
        .sortedBy { file -> file.name }
        .mapNotNull { metadataFile ->
            val wavFile = metadataFile.resolveSibling(metadataFile.nameWithoutExtension + ".wav")
            if (!Files.exists(wavFile)) {
                null
            } else {
                val metadata = metadataFile.readText()
                VoiceFixture(
                    source = "recording",
                    wakeFile = fixtureRoot.resolve("wakeword_hey_jarvis.wav").toString(),
                    commandFile = wavFile.toString(),
                    wakeWord = "Hey Jarvis",
                    expectedText =
                        jsonString(metadata, "expectedText")
                            ?: jsonString(metadata, "transcript")
                            ?: "",
                    action = "",
                    target = "",
                    silenceMs = 250,
                )
            }
        }
}

fun secondaryFor(provider: String): String =
    when (provider) {
        "ha_assist" -> {
            "none"
        }
        else -> {
            "ha_assist"
        }
    }

fun runCommand(command: List<String>): String {
    val process =
        ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "command failed: ${command.joinToString(" ")}" }
    return output
}

fun adb(vararg adbArgs: String): String = runCommand(listOf(adb, "-s", device) + adbArgs)

fun safeName(value: String): String =
    value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .takeLast(80)

val recordingFixtures = recordings?.let(::loadRecordingFixtures).orEmpty()
if (recordings != null && recordingFixtures.isEmpty()) {
    error("No usable recording fixtures found in $recordings")
}
val fixtures = loadManifestFixtures(manifest) + recordingFixtures

if (dryRun) {
    println("provider\tsource\tfixture\texpected")
    providers.forEach { provider ->
        fixtures.forEach { fixture ->
            println("$provider\t${fixture.source}\t${Path.of(fixture.commandFile).name}\t${fixture.expectedText}")
        }
    }
    kotlin.system.exitProcess(0)
}

providers.forEach { provider ->
    fixtures.forEach { fixture ->
        val fixtureName = safeName(Path.of(fixture.commandFile).name)
        val wakeName = "bench-wake-$fixtureName"
        val commandName = "bench-command-$fixtureName"
        adb("logcat", "-c")
        adb("push", fixture.wakeFile, "/data/local/tmp/$wakeName")
        adb("push", fixture.commandFile, "/data/local/tmp/$commandName")
        adb("shell", "run-as", "com.superdash", "cp", "/data/local/tmp/$wakeName", "files/$wakeName")
        adb("shell", "run-as", "com.superdash", "cp", "/data/local/tmp/$commandName", "files/$commandName")
        if (sttModel != null) {
            adb(
                "shell",
                "am",
                "broadcast",
                "-a",
                "com.superdash.DEBUG_VOICE_SETTINGS",
                "--es",
                "stt_model",
                sttModel,
                "-p",
                "com.superdash",
            )
        }
        adb(
            "shell",
            "am",
            "broadcast",
            "-a",
            "com.superdash.DEBUG_WAKE_ASSIST_TEST",
            "--es",
            "provider",
            provider,
            "--es",
            "secondary_provider",
            secondaryFor(provider),
            "--es",
            "wake_name",
            wakeName,
            "--es",
            "command_name",
            commandName,
            "--es",
            "fixture_name",
            fixtureName,
            "--es",
            "fixture_source",
            fixture.source,
            "--es",
            "expected_text",
            fixture.expectedText,
            "--es",
            "word",
            fixture.wakeWord.lowercase().replace(" ", "_"),
            "--ei",
            "silence_ms",
            fixture.silenceMs.toString(),
            "-p",
            "com.superdash",
        )
        Thread.sleep(waitMs)
        val results =
            adb("logcat", "-d", "-v", "time")
                .lineSequence()
                .filter { line -> line.contains("WakeAssistTest") && line.contains("benchmark result") }
                .toList()
        check(results.size == 1) {
            "Expected exactly one benchmark result for ${fixture.commandFile} with $provider, got ${results.size}"
        }
        val result = results.single()
        check(result.contains("completed=true") || result.contains("\"completed\":true")) {
            "Benchmark did not complete for ${fixture.commandFile} with $provider: $result"
        }
        println(result)
    }
}
