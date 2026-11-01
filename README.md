# Private AI

A privacy-first, completely on-device Android AI voice companion.

## How This Application Stays Offline

### Zero Network Dependencies

Private AI is designed to operate with **zero network connectivity**. Here's how:

#### 1. No Network Permissions
The AndroidManifest.xml does **not** include:
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.ACCESS_WIFI_STATE`

Without these permissions, the Android OS **prevents** the application from making any network calls. This is enforced at the OS level.

#### 2. No Network Libraries
The project dependencies contain **no networking libraries**:
- No OkHttp, Retrofit, or Volley
- No Firebase SDKs
- No analytics or telemetry libraries
- No crash reporting (Crashlytics, Sentry, etc.)
- No advertising SDKs
- No remote configuration

The only network-capable library is `androidx.security:security-crypto`, which is used purely for local encryption.

#### 3. Network Security Config
The `network_security_config.xml` explicitly:
- Blocks all cleartext (HTTP) traffic
- Blocks all domain connections
- Only trusts system certificates (defense-in-depth)

#### 4. Backup Exclusion
The `data_extraction_rules.xml` explicitly excludes:
- All conversation data
- All memory data
- All model files
- All security keys

From both cloud backup and device transfer.

Backup is killed with 4-layer defense-in-depth:
1. `allowBackup="false"` - OS-level block
2. `fullBackupContent="false"` - Legacy block (API < 31)
3. `dataExtractionRules` - Excludes ALL domains (API 31+)
4. `BackupDenier` agent - Programmatic zero-byte backup

#### 5. On-Device Inference
All AI inference happens locally using:
- **LiteRT-LM**: Google's on-device LLM runtime
- **Gemma 4 E2B**: 2.58GB model running entirely on-device
- **GPU acceleration**: OpenCL on Tensor G4 (Pixel 9a)
- **CPU fallback**: Always available

#### 6. On-Device Speech (Phase 2)
Speech processing will use:
- **STT**: sherpa-onnx (Parakeet model, ~232MB RAM)
- **TTS**: sherpa-onnx (Kokoro model, ~640MB RAM)
- **VAD**: Silero VAD v5 (<10MB RAM)

All speech models run locally. No audio data leaves the device.

### Data Flow

```
User Input (Text/Voice)
        ↓
[ON-DEVICE] Speech-to-Text (Phase 2)
        ↓
[ON-DEVICE] LLM Inference (LiteRT-LM + Gemma 4)
        ↓
[ON-DEVICE] Text-to-Speech (Phase 2)
        ↓
User Output (Text/Speech)
```

**At no point does data leave the device.**

### Model Provisioning

Models are **not** downloaded from the Internet. The developer must:

1. Download the model manually:
   ```bash
   litert-lm run \
     --from-huggingface-repo=litert-community/gemma-4-E2B-it-litert-lm \
     gemma-4-E2B-it.litertlm \
     --prompt="test"
   ```

2. Push to device via ADB:
   ```bash
   adb push gemma-4-E2B-it.litertlm /data/data/com.privateai/files/models/
   ```

3. The app loads the model from local storage.

### Verification

To verify the application is truly offline:

1. Enable airplane mode
2. Open the application
3. Type a message
4. The AI responds locally

To verify no network calls are made:
1. Use Android Studio's Network Profiler
2. Monitor traffic with a firewall app
3. Check that no network interfaces are accessed

### Security Model

See [SECURITY.md](SECURITY.md) for the complete threat model.

Key security properties:
- Hardware-backed encryption via Android Keystore
- AES-256-GCM for data at rest
- User-controlled data deletion
- No analytics or telemetry
- 4-layer backup killing (no data leaves via backup)

## Architecture

```
com.privateai/
├── llm/                    # LLM inference abstraction
│   ├── LLMEngine.kt       # Interface
│   ├── LiteRTLMEngine.kt  # LiteRT-LM implementation
│   └── LLMEngineFactory.kt
├── model/                  # Model file management
│   └── ModelManager.kt
├── security/               # Encryption & key management
│   └── SecurityManager.kt
├── memory/                 # Long-term memory
│   └── MemoryManager.kt
├── conversation/           # Conversation history
│   └── ConversationManager.kt
├── di/                     # Dependency injection
│   └── AppContainer.kt
└── ui/                     # Compose UI
    ├── MainActivity.kt
    ├── ChatScreen.kt
    ├── ChatViewModel.kt
    ├── components/
    └── theme/
```

## Requirements

- **Target Device**: Google Pixel 9a (8GB RAM, Tensor G4)
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)
- **Architecture**: arm64-v8a
- **Runtime**: LiteRT-LM (Kotlin API)

## Setup

### Prerequisites

1. Android Studio Hedgehog or later
2. JDK 11+
3. Android SDK 35
4. NDK r28b+ (for native libraries)
5. A physical device or emulator with 8GB+ RAM

### Building

```bash
# Clone the repository
git clone <repository-url>
cd PrivateAI

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

### Adding the Model

1. Download the model:
   ```bash
   # Install litert-lm CLI
   uv tool install litert-lm

   # Download model (requires HuggingFace account)
   litert-lm run \
     --from-huggingface-repo=litert-community/gemma-4-E2B-it-litert-lm \
     gemma-4-E2B-it.litertlm \
     --prompt="test"
   ```

2. Push to device:
   ```bash
   adb push gemma-4-E2B-it.litertlm /data/data/com.privateai/files/models/
   ```

3. Launch the app.

## Performance

### Expected on Pixel 9a (Tensor G4, 8GB RAM)

| Metric | GPU Backend | CPU Backend |
|--------|-------------|-------------|
| Model Load | ~3-5s | ~5-8s |
| Prefill | ~3800 tk/s | ~557 tk/s |
| Decode | ~52 tk/s | ~47 tk/s |
| TTFT | ~0.3s | ~1.8s |
| Peak RAM | ~1.1 GB | ~1.7 GB |

### RAM Budget (8GB device)

| Component | RAM |
|-----------|-----|
| OS + System | ~2.5 GB |
| Gemma 4 E2B | ~1.1 GB |
| App + UI | ~200 MB |
| Available for other apps | ~4.2 GB |

## Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumentation Tests
```bash
./gradlew connectedAndroidTest
```

## Roadmap

### Phase 1 (Current)
- [x] Text input
- [x] Local LLM inference (LiteRT-LM + Gemma 4 E2B)
- [x] Text response display
- [x] Streaming responses
- [x] Conversation history
- [x] Encrypted storage
- [x] Security architecture

### Phase 2 (Next)
- [ ] Local speech-to-text (sherpa-onnx)
- [ ] Voice activity detection (Silero VAD)
- [ ] Local text-to-speech (sherpa-onnx)
- [ ] Interruption/barge-in
- [ ] Voice conversation mode

### Phase 3 (Future)
- [ ] Long-term memory with semantic search
- [ ] Personality configuration
- [ ] Multiple interaction modes
- [ ] Widget for quick access
- [ ] Wear OS companion

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Acknowledgments

- [Google AI Edge](https://ai.google.dev/edge) for LiteRT-LM
- [Google Gemma](https://ai.google.dev/gemma) for the on-device LLM
- [Jetpack Compose](https://developer.android.com/jetpack/compose) for the UI framework
- [Android Keystore](https://developer.android.com/training/articles/keystore) for hardware-backed encryption

## Disclaimer

This is an alpha-stage application. The security implementation is incomplete and should not be relied upon for sensitive data without a formal security audit.
