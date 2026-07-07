# Voice

Voice handles wake word, VAD, Assist, and TTS.

## Files

Runtime:

- `packages/voice/src/main/kotlin/com/superdash/voice/VoiceCaptureLoop.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/VoicePipelineCoordinator.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/VoiceRun.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/VoiceProviderRunner.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/VoiceProviderRegistry.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/VoiceProviderWithFallback.kt`

Capture and speech:

- `packages/voice/src/main/kotlin/com/superdash/voice/WakeWordModel.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/MicroWakeWordRunner.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/LocalSttEngine.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/LocalTranscriptDecision.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/TranscriptActionExecutor.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/HaTextActionExecutor.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/LocalTranscriptActionPipeline.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/HaTtsPlayer.kt`

Recording:

- `packages/voice/src/main/kotlin/com/superdash/voice/VoiceRecordingComponent.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/VoiceCommandRecordingRepository.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/VoiceCommandRecordingService.kt`

## Flow

```text
AppGraph
  -> VoiceSubgraph
  -> VoiceCaptureLoop
  -> MicroWakeWordRunner
  -> VadGated
  -> VoiceRunContext
  -> VoicePipelineCoordinator
  -> VoiceProviderRunner
  -> VoiceProviderRegistry
  -> VoiceActionFlow
  -> VoiceRunResult
  -> VoiceRecordingComponent
  -> HaTtsPlayer
```

External interrupts:

```text
DoorbellWatcher
  -> KioskEventBus.emit(KioskEvent.DoorbellRingStarted)
  -> VoicePipelineCoordinator.stopAll()
```

## Pipeline Boundaries

- Voice run control flow is composed through typed `Flow` pipelines.
- The app-wide event bus is for app notifications, not request-response voice execution.
- Pipeline stages return scoped results to their caller.
- Fallback branches must preserve full command audio for downstream stages.
- Local STT providers use `LocalTranscriptActionPipeline`.
- Accepted local transcripts run through `HaTextActionExecutor`.
- Rejected or unavailable local transcripts fall back to HA audio with full replayed audio.

## Inter-Component Communication

Each component owns its state and exposes it as `StateFlow` or `SharedFlow`.
Components do not call each other's methods to react to state changes.

| Direction | Mechanism |
| --- | --- |
| Component state out | `StateFlow<T>` or `SharedFlow<T>` |
| Caller input | `suspend` function or `Channel` |
| Cross-component notification | `KioskEventBus` with typed `KioskEvent` subclasses |

Rules:

- `KioskEventBus` is for app-wide notifications such as `DoorbellRingStarted`,
  `WakeWordDetected`, and `UserTouched`.
- Voice execution stays inside the voice subgraph as typed `Flow` pipelines.
- `VoiceRunResult` is published on `VoicePipelineCoordinator.runResults`.
- Components requiring a bus take it as a required constructor parameter.

### Principles

1. **Bus events are domain facts, not commands.** `DoorbellRingStarted`
   (happened), not `StopVoice` (do this). Naming uses past-tense or
   noun-event form.
2. **One-way only.** Producers emit and forget; nothing replies on the bus.
3. **Notification fan-out, not control flow.** If a caller waits for a
   result, use a `suspend`/`Flow` direct call. If state has a current value,
   use `StateFlow`. The bus is only for "this thing happened, N components
   may independently care."
4. **Typed and sealed.** All events extend the existing `KioskEvent` sealed
   hierarchy. No string keys, no untyped payloads.
5. **No optional bus parameters.** If a component emits or subscribes, the
   bus is a required constructor dependency. Never `bus: KioskEventBus? = null`.
   Enforced by review and a `rg "bus: KioskEventBus\?"` scan; see the
   stale-symbol scan in Task 3.
6. **No actor mailboxes.** No `Channel`-as-inbox, no sealed `Command` types,
   no per-component message loops. Keep `suspend`/synchronous methods for
   inputs that need synchronous semantics.
7. **Detector ≠ presenter.** A class that watches a domain input and emits a
   fact on the bus does not also own a UI-shaped `MutableStateFlow`. Split
   into a detector (entity watch + bus emit) and a presenter (bus subscribe +
   UI `StateFlow`). The Compose layer (`<Foo>Screen`/`<Foo>Content`, see
   `AGENTS.md:144-169`) is a third concern and does not enter the split — it
   simply points at the presenter for its state source.

## Providers

Voice has two STT settings:

| Setting | Default | Choices |
| --- | --- |
| Primary STT | `ha_assist` | `ha_assist`, `whisper`, `moonshine` |
| Secondary STT | `none` | `none`, `ha_assist`, `whisper`, `moonshine` |

| Key | Behavior |
| --- | --- |
| `ha_assist` | Streams audio to Home Assistant Assist. |
| `whisper` | Runs local Whisper batch STT, then sends text to HA. |
| `moonshine` | Runs local Moonshine batch STT, then sends text to HA. |
| `none` | Disables secondary fallback. |

Debug commands still accept legacy key `whisper_stt_ha` and translate it to
`VoiceSttProvider.Whisper` in
`packages/app/src/debug/kotlin/com/superdash/voice/DebugVoiceArgs.kt`. The
legacy keys `sherpa` and `sherpa_stt_ha` map to `VoiceSttProvider.Moonshine`
as a stored-value migration shim (see `SettingsRepositoryVoiceSettings`).

Native Whisper is disabled by default. Build it only with
`-PsuperdashWhisperNative=true` after placing `whisper.cpp` at
`packages/voice/src/main/cpp/whisper.cpp/`; the build fails if the property is
true and the sources are missing.

## Voice Models

- Model catalog code lives in `packages/voice/src/main/kotlin/com/superdash/voice/models/`.
- Downloaded models install under app-private `files/voice-models/<model-id>/`.
- Downloads stage under app-private `files/voice-model-downloads/<model-id>/`.
- Installed metadata lives in `files/voice-models/installed-models.json`.
- STT model selection is separate from STT provider selection.
- The bundled `moonshine-tiny-en` model remains the default STT model and offline fallback.
- `moonshine-base-en` downloads from `https://download.moonshine.ai/model/base-en/quantized/base-en`.
- Downloaded Moonshine models are checksum verified before they become selectable.
- Intent embedding model selection currently defaults to `intent-embedding-none`.

## Response Modes

| Mode | Behavior |
| --- | --- |
| `speak` | Runs HA Assist through TTS and plays the returned media URL. |
| `silent` | Runs HA Assist through intent only, then returns to idle without audio. |
| `visual` | Runs HA Assist through intent only and holds the Done overlay briefly. |

Direct HA service execution uses the shared `HaWebSocketClient` through
`HaServiceCallClient`.
It is an execution primitive for local intent work, not a local recognizer.

## Local Intent

Local intent matches a transcript against a generated phrase registry and, on
match, dispatches an action without consulting Home Assistant Assist. Falls
back to HA conversation on every miss, error, or stale-registry condition.

Files:

- `packages/voice/src/main/kotlin/com/superdash/voice/LocalIntentCatalog.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/LocalIntentRegistryBuilder.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/LocalIntentRegistryCache.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/LocalIntentRecognizer.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/LocalIntentAction.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/LocalIntentActionDispatcher.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/HaServiceCallExecutor.kt`
- `packages/voice/src/main/kotlin/com/superdash/voice/LocalIntentTranscriptActionExecutor.kt`

Flow:

```text
transcript
  -> LocalIntentTranscriptActionExecutor
  -> LocalIntentRecognizer
       (LocalIntentRegistryRecognizer: exact phrase match today;
        EmbeddingLocalIntentRecognizer planned)
  -> LocalIntentRecognitionResult(action: LocalIntentAction?)
  -> LocalIntentActionDispatcher
       is LocalIntentAction.ServiceCall    -> HaServiceCallExecutor
       is LocalIntentAction.SkillInvocation -> SkillExecutor
  -> VoiceActionEvent.ActionComplete | Error
  on Error -> fall back to HaTextActionExecutor
```

### `LocalIntentAction`

Sealed type produced by recognizers, consumed by the dispatcher.

| Variant | Payload | Producer | Executor |
| --- | --- | --- | --- |
| `ServiceCall` | `HaServiceCall` | `LocalIntentRegistryBuilder` (HA-entity actuation phrases) | `HaServiceCallExecutor` |
| `SkillInvocation` | `skillId` | None today (reserved for skill intents: weather, time, calendar) | `UnsupportedSkillExecutor` returns `Error("skill_not_implemented")` |

### `LocalIntentActionDispatcher`

The dispatcher is the only place that knows the variant set. Recognizers and
the transcript executor are variant-agnostic. To add a new action variant: add
the variant to `LocalIntentAction`, add a branch to
`DefaultLocalIntentActionDispatcher.dispatch`, and (typically) add an executor.

### Open question: skill response surface

`VoiceActionEvent.ActionComplete.response: JsonObject` is currently shaped like
Home Assistant's `{response_type: "action_done", result: ...}` reply. Skill
intents (weather, time, calendar) need to produce spoken text and optionally a
visual hint. **Before the first skill ships**, decide whether
`VoiceActionEvent.ActionComplete` grows typed fields (e.g.
`spokenText: String?`, `uiHint: ScreenTarget?`) or whether skills emit a new
`SkillResponse` event variant. The risk of deferring this decision is that
HA's JSON response schema becomes the app-wide contract by accretion.

## Local Voice Benchmark

Use a debug build on the tablet or emulator:

```bash
just bench-local-voice
```

To review and backtest real commands, enable command recordings in Settings, then export and replay:

```bash
just export-voice-recordings
just bench-local-voice-recordings
```

Selection rule:

- Keep `ha_assist` as default unless a local provider matches at least 3 of 4 command fixtures.
- Use `ha_assist` as secondary STT for the selected local provider.
- Prefer lower median `elapsedMs` when local providers tie on matches.

Settings policy:

- App defaults stay `primary_stt_provider = ha_assist` and `secondary_stt_provider = none`.
- Command recording defaults to disabled.
- Daily-use tablet setup may use the measured local winner as primary.
- Daily-use tablet setup must use `ha_assist` as secondary for local primary providers.

## State

`VoicePipelineCoordinator` owns voice state.

- `Idle`
- `WakeFired`
- `Recording`
- `Processing`
- `Speaking`
- `Failed`

## Voice Run Sessions

- Each wake creates one `VoiceRunContext`.
- All runtime events, provider attempts, recordings, and benchmark rows carry the same run id.
- UI still observes `VoiceState`.
- Recordings and benchmarks consume `VoiceRunResult`.
- No code saves command metadata by polling global coordinator state.

## Wake Word Invariants

Keep these intact:

- Supported models live in `WakeWordModel`.
- Runtime assets live under `packages/app/src/main/assets/models/wakeword/`.
- The app currently ships only `hey_jarvis`.
- Model licensing and source notes are in `docs/architecture/wake-word-models.md`.
- Model output is `uint8`.
- Read bytes with `.toInt() and 0xFF`.
- Inference stride is `FRAMES_PER_INFERENCE`.

## Native Frontend

- `MicroWakeWordRunner` uses `com.superdash.voice.features.AudioFeatureExtractor`.
- `libsuperdash_audio_features.so` is built by Gradle/CMake.
- TensorFlow microfrontend and KISS FFT sources are vendored under `packages/voice/src/main/cpp/third_party/`.
- Supported ABIs are `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`.
