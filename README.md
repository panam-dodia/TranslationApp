# Two-Way Translation App

A professional on-device translation app with speech recognition and text-to-speech capabilities, built with ONNX Runtime for production-ready performance.

## Features

- **Two-Way Translation**: Real-time bidirectional translation between multiple languages
- **Speech Recognition**: Speak in your language and get instant transcription
- **Text-to-Speech**: Hear the translated text in the target language
- **On-Device Processing**: Uses ONNX Runtime for fast, private, offline translation
- **On-Demand Model Downloads**: Downloads translation models only when needed
- **Smart Model Management**: Shares models efficiently (e.g., one English model for en→es and en→fr)
- **Professional UI**: Minimal, clean design with startup-quality aesthetics
- **Multiple Languages**: Supports 12 languages including English, Spanish, French, German, Chinese, Japanese, and more

## Architecture

### Technology Stack

- **ONNX Runtime**: Production-ready inference engine for on-device translation
- **OPUS-MT Models**: High-quality multilingual translation models from Helsinki-NLP
- **Jetpack Compose**: Modern UI toolkit with Material 3 design
- **Kotlin Coroutines**: Asynchronous operations and flow-based state management
- **Android Speech APIs**: Native speech recognition and text-to-speech

### Key Components

1. **ONNXTranslationService**: Manages ONNX Runtime sessions and model inference
2. **ModelDownloader**: Downloads translation models on-demand
3. **ONNXInferenceEngine**: Encoder-decoder inference with auto-regressive decoding
4. **MarianTokenizer**: Tokenization and detokenization for OPUS-MT models
5. **SpeechRecognitionService**: Handles speech-to-text conversion
6. **TextToSpeechService**: Converts translated text to speech
7. **TranslationViewModel**: Coordinates all services and manages app state
8. **TranslationScreen**: Professional minimal UI with two-person layout

## How It Works

### Model Management

The app uses **on-demand model downloading**:

1. **No Bundled Models**: Models are NOT included in the APK (keeps app size small)
2. **Download When Needed**: When user selects a language pair, app checks if model exists
3. **One-Time Download**: Models are cached locally and reused
4. **Smart Sharing**: Models are organized by language pair (en→es, es→en as separate models)

**Example:**
- User selects English ↔ Spanish
- App downloads `en-es` model (~200-300MB)
- App downloads `es-en` model (~200-300MB)
- Models stored in `app_files/models/en-es/` and `app_files/models/es-en/`
- Next time user selects this pair, models load instantly from disk

### Current Setup

Your `assets/` folder has the **en→es** model. On first run, it will be automatically migrated to `app_files/models/en-es/`.

## Setup Instructions

### 1. Prerequisites

- Android Studio Hedgehog or later
- Android SDK 26 (Android 8.0) or higher
- Models hosted on a server/CDN (for download functionality)

### 2. Converting OPUS-MT to ONNX (For Hosting)

Convert OPUS-MT models to ONNX format using Google Colab (avoids local environment issues):

```bash
# Install dependencies
pip install transformers torch onnx

# Convert a model (example: English to Spanish)
python -m transformers.onnx \
  --model=Helsinki-NLP/opus-mt-en-es \
  --feature=seq2seq-lm \
  onnx/en-es/

# This creates: encoder_model.onnx, decoder_model.onnx, etc.
# You'll need to merge or use them appropriately
```

### 3. Host Models for Download

**Option A: Use GitHub Releases (Free)**
1. Convert OPUS-MT models to ONNX
2. Create a GitHub release in your repo
3. Upload model files as release assets
4. Update `ModelDownloader.kt` with direct download URLs

**Option B: Use Cloud Storage (Better)**
1. Upload models to AWS S3, Google Cloud Storage, or Firebase Storage
2. Generate public URLs for each model
3. Update `ModelDownloader.kt` with the URLs

**Example URL structure:**
```
https://your-cdn.com/models/en-es/encoder_model.onnx
https://your-cdn.com/models/en-es/decoder_with_past_model.onnx
https://your-cdn.com/models/en-es/vocab.json
...
```

### 4. Configure Model URLs

Edit `ModelDownloader.kt` and update the `getModelBaseUrl` function:

```kotlin
private fun getModelBaseUrl(fromLang: Language, toLang: Language): String? {
    val modelKey = "${fromLang.code}-${toLang.code}"
    return "https://your-cdn.com/models/$modelKey"
}
```

**That's it!** The app will now download models on-demand.

## Usage

### Basic Flow

1. **Select Languages**: Tap language names to choose source and target languages
2. **Speak**: Tap the microphone button for your side
3. **Translate**: Speech is automatically transcribed and translated
4. **Listen**: The translation is spoken in the target language
5. **Swap**: Use the center swap button to reverse languages

### Two-Person Conversation

- **Person 1** (Top): Speaks in Language A
- **Person 2** (Bottom): Speaks in Language B
- Each person's speech is translated to the other's language
- Translations are displayed and spoken automatically

## Customization

### UI Colors

Edit `app/src/main/java/com/panam/translationapp/ui/theme/Color.kt`:

```kotlin
val AccentBlue = Color(0xFF2196F3) // Change accent color
```

### Supported Languages

Add more languages in `Language.kt`:

```kotlin
enum class Language(val displayName: String, val code: String) {
    // Add new languages here
    PORTUGUESE("Portuguese", "pt"),
}
```

### Model Configuration

For production deployment:

1. Optimize ONNX models with quantization for smaller size
2. Enable NNAPI execution provider for hardware acceleration
3. Implement model caching and lazy loading
4. Add model versioning and updates

## Performance Optimization

### NNAPI Acceleration

Enable in `ONNXTranslationService.kt`:

```kotlin
val sessionOptions = OrtSession.SessionOptions().apply {
    addNNAPI()
}
```

### Model Quantization

Reduce model size by 4x with minimal accuracy loss:

```bash
python -m onnxruntime.quantization.quantize \
  --model model.onnx \
  --output model_quant.onnx \
  --per_channel
```

## Production Checklist

- [ ] Convert all needed OPUS-MT models to ONNX
- [ ] Set up model hosting (CDN/server)
- [ ] Implement proper tokenization (SentencePiece)
- [ ] Add model download UI with progress
- [ ] Enable NNAPI for hardware acceleration
- [ ] Test on various Android devices
- [ ] Implement error handling and retry logic
- [ ] Add analytics for translation quality
- [ ] Optimize app size with ProGuard/R8
- [ ] Add offline mode indicator

## Troubleshooting

### Models Not Found

Ensure models are properly placed in `app/files/models/` or implement download functionality.

### Translation Quality Issues

1. Verify tokenizer matches OPUS-MT requirements
2. Check model is correctly converted to ONNX
3. Ensure proper input/output tensor handling

### Performance Issues

1. Enable NNAPI execution provider
2. Use quantized models
3. Implement model caching
4. Profile with Android Studio Profiler

## Resources

- [ONNX Runtime Android](https://onnxruntime.ai/docs/install/)
- [OPUS-MT Models](https://github.com/Helsinki-NLP/Opus-MT)
- [ONNX Model Conversion](https://huggingface.co/docs/transformers/serialization)
- [Android Speech APIs](https://developer.android.com/reference/android/speech/package-summary)

## License

MIT License - Feel free to use in your projects
