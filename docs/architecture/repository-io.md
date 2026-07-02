# Repository I/O

- File-backed repositories expose suspend APIs.
- File mutations run on `Dispatchers.IO`.
- Mutations that touch the same directory are serialized with one `Mutex`.
- Writes use temp files and atomic rename.
- Retention and clear operations remove orphan temp, JSON, and data files.
- ViewModels do not call blocking file APIs directly.

## Voice Recordings

- `VoiceCommandRecordingRepository` owns recording file layout.
- `VoiceCommandRecordingService` owns run-scoped save timing.
- Recording saves rethrow `CancellationException`.
- Clear operations use a generation token so active saves cannot recreate cleared files.
