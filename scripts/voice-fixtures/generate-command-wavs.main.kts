#!/usr/bin/env kotlin

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import kotlin.io.path.createDirectories

data class CommandFixture(
    val commandFileName: String,
    val wakeWord: String,
    val text: String,
    val action: String,
    val target: String,
) {
    val wakeFileName: String = "wakeword_${wakeWord.lowercase().replace(' ', '_')}.wav"
}

val fixtures =
    listOf(
        CommandFixture(
            commandFileName = "turn_on_kitchen_lights.wav",
            wakeWord = "Hey Jarvis",
            text = "Turn on the kitchen lights.",
            action = "light.turn_on",
            target = "light.kitchen",
        ),
        CommandFixture(
            commandFileName = "turn_on_desk_lights.wav",
            wakeWord = "Hey Jarvis",
            text = "Turn on the desk lights.",
            action = "light.turn_on",
            target = "light.desk",
        ),
        CommandFixture(
            commandFileName = "turn_on_office_lights.wav",
            wakeWord = "Hey Jarvis",
            text = "Turn on office lights.",
            action = "light.turn_on",
            target = "light.office",
        ),
        CommandFixture(
            commandFileName = "turn_off_office_lights.wav",
            wakeWord = "Hey Jarvis",
            text = "Turn off office lights.",
            action = "light.turn_off",
            target = "light.office",
        ),
        CommandFixture(
            commandFileName = "turn_off_desk_lights.wav",
            wakeWord = "Hey Jarvis",
            text = "Turn off the desk lights.",
            action = "light.turn_off",
            target = "light.desk",
        ),
        CommandFixture(
            commandFileName = "turn_off_living_room_lights.wav",
            wakeWord = "Hey Jarvis",
            text = "Turn off the living room lights.",
            action = "light.turn_off",
            target = "light.living_room",
        ),
        CommandFixture(
            commandFileName = "set_hallway_brightness_20.wav",
            wakeWord = "Hey Jarvis",
            text = "Set the hallway brightness to twenty percent.",
            action = "light.turn_on",
            target = "light.hallway",
        ),
        CommandFixture(
            commandFileName = "pause_250ms_turn_on_kitchen_lights.wav",
            wakeWord = "Hey Jarvis",
            text = "Turn on the kitchen... lights.",
            action = "light.turn_on",
            target = "light.kitchen",
        ),
    )

val outputDir =
    argValue("--out")
        ?.let(Path::of)
        ?: Path.of("packages/app/src/androidTest/assets/voice/commands")
val onlyCommandFiles =
    argValue("--only-command-file")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        .orEmpty()
val selectedFixtures =
    if (onlyCommandFiles.isEmpty()) {
        fixtures
    } else {
        fixtures.filter { fixture -> fixture.commandFileName in onlyCommandFiles }
    }
require(selectedFixtures.isNotEmpty()) {
    "No fixtures matched --only-command-file ${onlyCommandFiles.joinToString(",")}"
}
val force = hasArg("--force")
val onlyWake = hasArg("--only-wake")
val wakeCommandSilenceMs = argValue("--wake-command-silence-ms")?.toInt() ?: 250
val provider = argValue("--provider") ?: "openai"
val model =
    argValue("--model")
        ?: when (provider) {
            "gemini" -> "gemini-2.5-flash-preview-tts"
            "openai" -> "gpt-4o-mini-tts"
            else -> error("Unsupported provider: $provider")
        }
val voice =
    argValue("--voice")
        ?: when (provider) {
            "gemini" -> "Kore"
            "openai" -> "marin"
            else -> error("Unsupported provider: $provider")
        }
val apiKeyEnvName =
    when (provider) {
        "gemini" -> "GEMINI_API_KEY"
        "openai" -> "OPENAI_API_KEY"
        else -> error("Unsupported provider: $provider")
    }
val defaultApiKeyFiles =
    when (provider) {
        "gemini" ->
            listOf(
                Path.of(System.getProperty("user.home"), ".gemini-key"),
                Path.of(System.getProperty("user.home"), "gemini-key"),
            )
        "openai" ->
            listOf(
                Path.of(System.getProperty("user.home"), ".openai-key"),
                Path.of(System.getProperty("user.home"), "openai-key"),
            )
        else -> error("Unsupported provider: $provider")
    }
val apiKeyFile =
    argValue("--api-key-file")
        ?.let(Path::of)
val instructions =
    argValue("--instructions")
        ?: "Speak clearly as a nearby smart-home user saying the wake phrase and command. Natural pace, no extra words."
val apiKey =
    System.getenv(apiKeyEnvName)
        ?: apiKeyFile?.takeIf(Files::exists)?.let { Files.readString(it).trim() }
        ?: defaultApiKeyFiles.firstOrNull(Files::exists)?.let { Files.readString(it).trim() }
        ?: error(
            "Set $apiKeyEnvName or pass --api-key-file <path>. Checked: " +
                defaultApiKeyFiles.joinToString(", "),
        )
val client =
    HttpClient
        .newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

outputDir.createDirectories()

val wakeFixtures = selectedFixtures.distinctBy { it.wakeWord }
for (fixture in wakeFixtures) {
    generateSpeech(
        fileName = fixture.wakeFileName,
        input = fixture.wakeWord,
        provider = provider,
        model = model,
        voice = voice,
        instructions = instructions,
        client = client,
        apiKey = apiKey,
        outputDir = outputDir,
    )
}

if (!onlyWake) {
    for (fixture in selectedFixtures) {
        generateSpeech(
            fileName = fixture.commandFileName,
            input = fixture.text,
            provider = provider,
            model = model,
            voice = voice,
            instructions = instructions,
            client = client,
            apiKey = apiKey,
            outputDir = outputDir,
        )
    }

    val manifest =
        buildString {
            appendLine("# wake_file\tcommand_file\twake_word\ttext\taction\ttarget\twake_command_silence_ms")
            for (fixture in selectedFixtures) {
                appendLine(
                    "${fixture.wakeFileName}\t${fixture.commandFileName}\t${fixture.wakeWord}\t" +
                        "${fixture.text}\t${fixture.action}\t${fixture.target}\t$wakeCommandSilenceMs",
                )
            }
        }
    Files.writeString(outputDir.resolve("manifest.tsv"), manifest)
    println("wrote ${outputDir.resolve("manifest.tsv")}")
}

fun argValue(name: String): String? {
    val index = args.indexOf(name)
    if (index < 0) {
        return null
    }
    return args.getOrNull(index + 1) ?: error("$name requires a value")
}

fun hasArg(name: String): Boolean = args.contains(name)

fun String.jsonString(): String =
    buildString {
        append('"')
        for (char in this@jsonString) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

fun generateSpeech(
    fileName: String,
    input: String,
    provider: String,
    model: String,
    voice: String,
    instructions: String,
    client: HttpClient,
    apiKey: String,
    outputDir: Path,
) {
    when (provider) {
        "gemini" ->
            generateGeminiSpeech(
                fileName = fileName,
                input = input,
                model = model,
                voice = voice,
                instructions = instructions,
                client = client,
                apiKey = apiKey,
                outputDir = outputDir,
            )
        "openai" ->
            generateOpenAiSpeech(
                fileName = fileName,
                input = input,
                model = model,
                voice = voice,
                instructions = instructions,
                client = client,
                apiKey = apiKey,
                outputDir = outputDir,
            )
        else -> error("Unsupported provider: $provider")
    }
}

fun generateOpenAiSpeech(
    fileName: String,
    input: String,
    model: String,
    voice: String,
    instructions: String,
    client: HttpClient,
    apiKey: String,
    outputDir: Path,
) {
    val temporaryFile = Files.createTempFile(fileName.substringBeforeLast('.'), ".openai.wav")
    val outputFile = outputDir.resolve(fileName)
    if (Files.exists(outputFile) && !force) {
        println("skipped existing $outputFile")
        return
    }
    val request =
        HttpRequest
            .newBuilder(URI.create("https://api.openai.com/v1/audio/speech"))
            .timeout(Duration.ofMinutes(2))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {
                      "model": ${model.jsonString()},
                      "voice": ${voice.jsonString()},
                      "input": ${input.jsonString()},
                      "instructions": ${instructions.jsonString()},
                      "response_format": "wav"
                    }
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                ),
            ).build()
    val response = sendWithRetry(client, request)
    if (response.statusCode() !in 200..299) {
        error(
            "OpenAI speech request failed for $fileName: HTTP ${response.statusCode()}: " +
                response.body().toString(StandardCharsets.UTF_8),
        )
    }
    Files.write(temporaryFile, response.body())
    convertToSuperdashWav(temporaryFile, outputFile)
    Files.deleteIfExists(temporaryFile)
    println("generated $outputFile")
}

fun generateGeminiSpeech(
    fileName: String,
    input: String,
    model: String,
    voice: String,
    instructions: String,
    client: HttpClient,
    apiKey: String,
    outputDir: Path,
) {
    val temporaryFile = Files.createTempFile(fileName.substringBeforeLast('.'), ".gemini.pcm")
    val outputFile = outputDir.resolve(fileName)
    if (Files.exists(outputFile) && !force) {
        println("skipped existing $outputFile")
        return
    }
    val prompt =
        """
        $instructions

        Say exactly this phrase and nothing else:
        "$input"
        """.trimIndent()
    val request =
        HttpRequest
            .newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"))
            .timeout(Duration.ofMinutes(2))
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
                    {
                      "contents": [
                        {
                          "parts": [
                            {
                              "text": ${prompt.jsonString()}
                            }
                          ]
                        }
                      ],
                      "generationConfig": {
                        "responseModalities": ["AUDIO"],
                        "speechConfig": {
                          "voiceConfig": {
                            "prebuiltVoiceConfig": {
                              "voiceName": ${voice.jsonString()}
                            }
                          }
                        }
                      }
                    }
                    """.trimIndent(),
                    StandardCharsets.UTF_8,
                ),
            ).build()
    val response = sendWithRetry(client, request)
    val body = response.body().toString(StandardCharsets.UTF_8)
    if (response.statusCode() !in 200..299) {
        error("Gemini speech request failed for $fileName: HTTP ${response.statusCode()}: $body")
    }
    val audioBase64 = extractGeminiAudioBase64(body)
    Files.write(temporaryFile, Base64.getDecoder().decode(audioBase64))
    convertRawPcm24kToSuperdashWav(temporaryFile, outputFile)
    Files.deleteIfExists(temporaryFile)
    println("generated $outputFile")
}

fun sendWithRetry(
    client: HttpClient,
    request: HttpRequest,
): HttpResponse<ByteArray> {
    repeat(3) { attempt ->
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 429 || attempt == 2) {
            return response
        }
        val retryMs = retryDelayMs(response.body().toString(StandardCharsets.UTF_8)) ?: 2_000L
        println("rate limited; retrying in ${retryMs}ms")
        Thread.sleep(retryMs)
    }
    error("unreachable")
}

fun retryDelayMs(body: String): Long? {
    val retryInfoMatch = Regex(""""retryDelay"\s*:\s*"(\d+)s"""").find(body)
    if (retryInfoMatch != null) {
        return (retryInfoMatch.groupValues[1].toLong() + 1L) * 1_000L
    }
    val messageMatch = Regex("""retry in (\d+(?:\.\d+)?)s""").find(body)
    if (messageMatch != null) {
        return ((messageMatch.groupValues[1].toDouble() + 1.0) * 1_000.0).toLong()
    }
    return null
}

fun convertToSuperdashWav(input: Path, output: Path) {
    val process =
        ProcessBuilder(
            "ffmpeg",
            "-y",
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            input.toAbsolutePath().toString(),
            "-ac",
            "1",
            "-ar",
            "16000",
            "-sample_fmt",
            "s16",
            output.toAbsolutePath().toString(),
        ).inheritIO()
            .start()
    val code = process.waitFor()
    require(code == 0) { "ffmpeg failed with exit code $code for $input" }
}

fun convertRawPcm24kToSuperdashWav(input: Path, output: Path) {
    val process =
        ProcessBuilder(
            "ffmpeg",
            "-y",
            "-hide_banner",
            "-loglevel",
            "error",
            "-f",
            "s16le",
            "-ar",
            "24000",
            "-ac",
            "1",
            "-i",
            input.toAbsolutePath().toString(),
            "-ac",
            "1",
            "-ar",
            "16000",
            "-sample_fmt",
            "s16",
            output.toAbsolutePath().toString(),
        ).inheritIO()
            .start()
    val code = process.waitFor()
    require(code == 0) { "ffmpeg failed with exit code $code for $input" }
}

fun extractGeminiAudioBase64(body: String): String {
    val dataKey = """"data""""
    var searchFrom = 0
    while (true) {
        val keyIndex = body.indexOf(dataKey, searchFrom)
        if (keyIndex < 0) {
            error("Gemini response did not contain inline audio data")
        }
        var index = keyIndex + dataKey.length
        while (index < body.length && body[index].isWhitespace()) {
            index += 1
        }
        if (index >= body.length || body[index] != ':') {
            searchFrom = keyIndex + dataKey.length
            continue
        }
        index += 1
        while (index < body.length && body[index].isWhitespace()) {
            index += 1
        }
        if (index >= body.length || body[index] != '"') {
            searchFrom = keyIndex + dataKey.length
            continue
        }
        return readJsonStringLiteral(body, index)
    }
}

fun readJsonStringLiteral(
    body: String,
    quoteIndex: Int,
): String {
    val out = StringBuilder()
    var index = quoteIndex + 1
    while (index < body.length) {
        val char = body[index]
        if (char == '"') {
            return out.toString()
        }
        if (char != '\\') {
            out.append(char)
            index += 1
            continue
        }
        val escaped = body.getOrNull(index + 1) ?: error("Bad JSON escape in Gemini response")
        when (escaped) {
            '\\' -> out.append('\\')
            '"' -> out.append('"')
            '/' -> out.append('/')
            'b' -> out.append('\b')
            'f' -> out.append('\u000C')
            'n' -> out.append('\n')
            'r' -> out.append('\r')
            't' -> out.append('\t')
            'u' -> {
                val hex = body.substring(index + 2, index + 6)
                out.append(hex.toInt(16).toChar())
                index += 4
            }
            else -> error("Unsupported JSON escape: \\$escaped")
        }
        index += 2
    }
    error("Unterminated JSON string in Gemini response")
}
