emulator := "emulator-5554"
avd := "superdash-tablet"
adb := env_var_or_default("ADB", "adb")
emulator_bin := env_var_or_default("EMULATOR", "emulator")
apk := "packages/app/build/outputs/apk/debug/app-debug.apk"
whisper_model := "ggml-tiny.en-q5_1.bin"
whisper_model_dir := "packages/app/src/main/assets/models/whisper"
whisper_version := "v1.8.1"
whisper_src_dir := "packages/app/src/main/cpp/whisper.cpp"
whisper_model_url := "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" + whisper_model
whisper_source_commit := "a91dd3be72f70dd1b3cb6e252f35fa17b93f596c"
voice_commands_dir := "packages/app/src/androidTest/assets/voice/commands"

start-emulator:
    {{emulator_bin}} @{{avd}} -allow-host-audio

deploy-emulator:
    ./gradlew :packages:app:assembleDebug
    {{adb}} -s {{emulator}} install -r {{apk}}
    {{adb}} -s {{emulator}} shell am start -n com.superdash/.MainActivity
    {{adb}} -s {{emulator}} emu avd hostmicon

send-audio file provider="":
    {{adb}} -s {{emulator}} push "{{file}}" /data/local/tmp/superdash-audio.wav
    {{adb}} -s {{emulator}} shell run-as com.superdash cp /data/local/tmp/superdash-audio.wav files/superdash-audio.wav
    {{adb}} -s {{emulator}} shell am broadcast -a com.superdash.DEBUG_ASSIST_TEST --es name superdash-audio.wav --es provider "{{provider}}" -p com.superdash

send-wake-audio wake_file command_file provider="ha_assist" wakeword="hey_jarvis" silence_ms="250":
    {{adb}} -s {{emulator}} push "{{wake_file}}" /data/local/tmp/superdash-wake.wav
    {{adb}} -s {{emulator}} push "{{command_file}}" /data/local/tmp/superdash-command.wav
    {{adb}} -s {{emulator}} shell run-as com.superdash cp /data/local/tmp/superdash-wake.wav files/superdash-wake.wav
    {{adb}} -s {{emulator}} shell run-as com.superdash cp /data/local/tmp/superdash-command.wav files/superdash-command.wav
    {{adb}} -s {{emulator}} shell am broadcast -a com.superdash.DEBUG_WAKE_ASSIST_TEST --es wake_name superdash-wake.wav --es command_name superdash-command.wav --es provider "{{provider}}" --es word "{{wakeword}}" --ei silence_ms "{{silence_ms}}" -p com.superdash

set-voice wakeword="hey_jarvis" provider="moonshine":
    {{adb}} -s {{emulator}} shell am broadcast -a com.superdash.DEBUG_VOICE_SETTINGS --es wakeword "{{wakeword}}" --es provider "{{provider}}" -p com.superdash

set-local-intent enabled="true":
    {{adb}} -s {{emulator}} shell am broadcast -a com.superdash.DEBUG_VOICE_SETTINGS --ez local_intent_enabled "{{enabled}}" -p com.superdash

fetch-whisper:
    mkdir -p {{whisper_model_dir}} packages/app/src/main/cpp
    test -d {{whisper_src_dir}}/.git || git clone https://github.com/ggml-org/whisper.cpp {{whisper_src_dir}}
    git -C {{whisper_src_dir}} fetch --depth 1 origin {{whisper_source_commit}}
    git -C {{whisper_src_dir}} checkout {{whisper_source_commit}}
    curl -L --fail --output {{whisper_model_dir}}/{{whisper_model}} {{whisper_model_url}}
    test -s {{whisper_model_dir}}/{{whisper_model}}

bench-wake-audio provider="ha_assist" wakeword="hey_jarvis" command="turn_off_office_lights":
    #!/usr/bin/env bash
    set -euo pipefail
    command="{{command}}"
    if [[ "$command" == */* ]]; then
        command_file="$command"
    else
        command_file="{{voice_commands_dir}}/${command%.wav}.wav"
    fi
    test -s "$command_file"
    {{adb}} -s {{emulator}} logcat -c
    just send-wake-audio "{{voice_commands_dir}}/wakeword_{{wakeword}}.wav" "$command_file" {{provider}} {{wakeword}} 250
    {{adb}} -s {{emulator}} logcat -d -v time | rg "WakeAssistTest|MicroWakeWord|Coordinator|HaAssist|LocalTranscriptPipeline|LocalIntent|Whisper|state=Processing|state=ActionComplete"

generate-voice-command command_name text provider="gemini" voice="Kore":
    scripts/voice-fixtures/generate-command-wav.sh "{{command_name}}" "{{text}}" --provider "{{provider}}" --voice "{{voice}}" --out "{{voice_commands_dir}}" --force

bench-whisper-wake-audio wakeword="hey_jarvis":
    #!/usr/bin/env bash
    set -euo pipefail
    {{adb}} -s {{emulator}} logcat -c
    just send-wake-audio packages/app/src/androidTest/assets/voice/commands/wakeword_hey_jarvis.wav packages/app/src/androidTest/assets/voice/commands/turn_on_kitchen_lights.wav whisper_stt_ha {{wakeword}} 250
    sleep 35
    {{adb}} -s {{emulator}} logcat -d -v time | tee /tmp/superdash-whisper-bench.log
    rg "WhisperBatchStt: transcribing audio with Whisper" /tmp/superdash-whisper-bench.log
    rg "LocalSttHa: using local transcript for HA text" /tmp/superdash-whisper-bench.log
    if rg "LocalSttHa: falling back to HA audio|local STT failed" /tmp/superdash-whisper-bench.log; then
        exit 1
    fi
    rg "Coordinator: state=ActionComplete" /tmp/superdash-whisper-bench.log

export-voice-recordings output="build/voice-recordings":
    kotlin scripts/voice-fixtures/export-voice-recordings.main.kts --device {{emulator}} --output "{{output}}"

bench-local-voice providers="ha_assist,whisper,moonshine" stt_model="":
    kotlin scripts/voice-fixtures/benchmark-local-voice.main.kts --providers "{{providers}}" --device {{emulator}} --stt-model "{{stt_model}}"

bench-local-voice-recordings recordings="build/voice-recordings/recordings" providers="ha_assist,whisper,moonshine" stt_model="":
    kotlin scripts/voice-fixtures/benchmark-local-voice.main.kts --recordings "{{recordings}}" --providers "{{providers}}" --device {{emulator}} --stt-model "{{stt_model}}"

host-mic-on:
    {{adb}} -s {{emulator}} emu avd hostmicon

host-mic-off:
    {{adb}} -s {{emulator}} emu avd hostmicoff
