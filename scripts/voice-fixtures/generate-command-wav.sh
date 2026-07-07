#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  scripts/voice-fixtures/generate-command-wav.sh <command-name> <text> [options]

Options:
  --out <dir>        Output directory.
  --provider <name>  TTS provider. Currently only gemini is supported.
  --model <model>    Gemini TTS model.
  --voice <voice>    Gemini prebuilt voice.
  --force            Overwrite an existing WAV.

Environment:
  GEMINI_API_KEY, or ~/.gemini-key / ~/gemini-key
EOF
}

if [[ $# -lt 2 ]]; then
    usage
    exit 2
fi

command_name="$1"
text="$2"
shift 2

output_dir="packages/app/src/androidTest/assets/voice/commands"
provider="gemini"
model="gemini-2.5-flash-preview-tts"
voice="Kore"
force="false"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --out)
            output_dir="$2"
            shift 2
            ;;
        --provider)
            provider="$2"
            shift 2
            ;;
        --model)
            model="$2"
            shift 2
            ;;
        --voice)
            voice="$2"
            shift 2
            ;;
        --force)
            force="true"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ "$provider" != "gemini" ]]; then
    echo "unsupported provider: $provider" >&2
    exit 2
fi

if [[ ! "$command_name" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "command-name must contain only letters, numbers, dot, underscore, and dash" >&2
    exit 2
fi

if [[ "$command_name" == *.wav ]]; then
    output_name="$command_name"
else
    output_name="${command_name}.wav"
fi

mkdir -p "$output_dir"
output_file="${output_dir}/${output_name}"
if [[ -e "$output_file" && "$force" != "true" ]]; then
    echo "exists $output_file; pass --force to overwrite" >&2
    exit 1
fi

api_key="${GEMINI_API_KEY:-}"
if [[ -z "$api_key" ]]; then
    if [[ -f "${HOME}/.gemini-key" ]]; then
        api_key="$(<"${HOME}/.gemini-key")"
    elif [[ -f "${HOME}/gemini-key" ]]; then
        api_key="$(<"${HOME}/gemini-key")"
    else
        echo "Set GEMINI_API_KEY or create ~/.gemini-key" >&2
        exit 1
    fi
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

request_file="${tmp_dir}/request.json"
response_file="${tmp_dir}/response.json"
pcm_file="${tmp_dir}/audio.pcm"

jq -n \
    --arg text "$text" \
    --arg voice "$voice" \
    '{
      contents: [
        {
          parts: [
            {
              text: ("Speak clearly as a nearby smart-home user. Natural pace, no extra words.\n\nSay exactly this phrase and nothing else:\n\"" + $text + "\"")
            }
          ]
        }
      ],
      generationConfig: {
        responseModalities: ["AUDIO"],
        speechConfig: {
          voiceConfig: {
            prebuiltVoiceConfig: {
              voiceName: $voice
            }
          }
        }
      }
    }' > "$request_file"

curl -sS --fail \
    -X POST \
    -H "x-goog-api-key: ${api_key}" \
    -H "Content-Type: application/json" \
    "https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent" \
    -d @"$request_file" \
    -o "$response_file"

if base64 --decode </dev/null >/dev/null 2>&1; then
    jq -r '.. | objects | select(has("data")) | .data' "$response_file" | head -n 1 | base64 --decode > "$pcm_file"
else
    jq -r '.. | objects | select(has("data")) | .data' "$response_file" | head -n 1 | base64 -D > "$pcm_file"
fi

test -s "$pcm_file"
ffmpeg -y -hide_banner -loglevel error \
    -f s16le \
    -ar 24000 \
    -ac 1 \
    -i "$pcm_file" \
    -ac 1 \
    -ar 16000 \
    -sample_fmt s16 \
    "$output_file"
test -s "$output_file"
echo "generated $output_file"
