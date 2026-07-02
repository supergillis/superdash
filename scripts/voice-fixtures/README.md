# Voice Fixtures

Generate high-quality wake-word + command WAVs for voice flow tests.

```sh
kotlin scripts/voice-fixtures/generate-command-wavs.main.kts
```

Defaults:

| Option | Value |
| --- | --- |
| `--provider` | `openai` |
| `--out` | `packages/app/src/androidTest/assets/voice/commands` |
| `--wake-command-silence-ms` | `250` |
| `--model` | `gpt-4o-mini-tts` for OpenAI, `gemini-2.5-flash-preview-tts` for Gemini |
| `--voice` | `marin` for OpenAI, `Kore` for Gemini |
| `--api-key-file` | `~/.openai-key` or `~/openai-key` for OpenAI, `~/.gemini-key` or `~/gemini-key` for Gemini |
| `--only-wake` | Generate only wake-word WAVs |

Gemini example:

```sh
kotlin scripts/voice-fixtures/generate-command-wavs.main.kts --provider gemini
```

Wake-only example:

```sh
kotlin scripts/voice-fixtures/generate-command-wavs.main.kts --provider gemini --voice Puck --only-wake
```

Current wake-word positives use generated Gemini voices:

| File | Voice |
| --- | --- |
| `heyjarvis_aoede.wav` | `Aoede` |
| `heyjarvis_charon.wav` | `Charon` |
| `heyjarvis_fenrir.wav` | `Fenrir` |
| `heyjarvis_kore.wav` | `Kore` |
| `heyjarvis_puck.wav` | `Puck` |

The script writes:

- `wakeword_hey_jarvis.wav`
- command WAVs without the wake word
- `manifest.tsv`

All WAVs are normalized to 16 kHz mono PCM-16.

The JVM fixture test reads this asset directory from disk. The Android
instrumented fixture test reads the same files from the test APK assets. Tests
concatenate the wake-word WAV, manifest silence, and command WAV at runtime.

Run fixture checks:

```sh
./gradlew ktlintCheck :packages:app:testDebugUnitTest --tests com.superdash.voice.VoiceCommandAudioFixtureTest --tests com.superdash.voice.WakeWordAudioFixtureTest
./gradlew :packages:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.superdash.voice.WakeWordPipelineTest
./gradlew :packages:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.superdash.voice.VoiceCommandWakeAssistAudioFixtureTest
```

Run local voice benchmark:

```sh
just bench-local-voice
just bench-local-voice moonshine moonshine-tiny-en
just bench-wake-audio moonshine hey_jarvis turn_off_office_lights
```

Generate one command WAV into the asset folder:

```sh
just generate-voice-command turn_off_office_lights "Turn off office lights."
```

Run saved recording backtests:

```sh
just export-voice-recordings
just bench-local-voice-recordings
```

Export layout:

```text
build/voice-recordings/
  recordings/
    <id>.wav
    <id>.json
```

Use `build/voice-recordings/recordings` as benchmark input.

The benchmark uses `manifest.tsv` and logs:

| Field | Meaning |
| --- | --- |
| `provider` | Primary STT provider. |
| `secondary` | Fallback STT provider. |
| `source` | `generated` or `recording`. |
| `fixture` | Command WAV from the manifest. |
| `matched` | Normalized transcript match. |
| `completed` | Debug run reached a terminal completed state. |
| `elapsedMs` | Injected audio start to final voice state. |
| `fallbackUsed` | Local provider did not produce the HA text path. |
| `json` | Structured benchmark result with run id and provider trace. |
