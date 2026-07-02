# Wake-Word Models

superdash currently ships one wake-word model:

| ID | Source | License |
| --- | --- | --- |
| `hey_jarvis` | ESPHome `micro-wake-word-models` | Apache-2.0 |

## Runtime

- Supported models live in `WakeWordModel`.
- Assets live under `packages/app/src/main/assets/models/wakeword/`.
- `hey_jarvis.json` records the source, threshold, and window size.
- `MicroWakeWordRunner` reads model paths and decision metadata from the registry.

## Licensing

- ESPHome `micro-wake-word-models` are Apache-2.0.
- Keep the downloaded model license next to the model assets.
- Do not bundle openWakeWord pretrained models unless licensing changes.
  Its package is Apache-2.0, but the pretrained model set is non-commercial.

## Adding Models

- Add the `.tflite` asset.
- Add a JSON manifest next to it.
- Add one `WakeWordModel` registry entry.
- Validate with positive and negative audio fixtures before shipping.
