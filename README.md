# Translation App

A modern, minimalist real-time translation app for Android built with Jetpack Compose and powered by Google's Gemini AI.

## Features

- Real-time voice translation between 12 languages
- Clean, startup-inspired UI design with minimal colors
- Bidirectional conversation support
- Text-to-speech output
- Fast, cloud-based translation using Gemini 2.5 Flash
- Understanding of idioms, phrases, and natural language

## Supported Languages

- English, Spanish, French, German, Italian, Portuguese
- Chinese, Japanese, Korean
- Arabic, Russian, Hindi

## Architecture

### Technology Stack

- **Gemini API**: Google's Gemini 2.5 Flash model for translation
- **Jetpack Compose**: Modern UI toolkit with Material Design 3
- **Kotlin Coroutines**: Asynchronous operations and flow-based state management
- **Retrofit**: HTTP client for API calls
- **Android Speech APIs**: Native speech recognition and text-to-speech

### Key Components

1. **GeminiTranslationService**: Manages API calls to Gemini
2. **SpeechRecognitionService**: Handles speech-to-text conversion
3. **TextToSpeechService**: Converts translated text to speech
4. **TranslationViewModel**: Coordinates all services and manages app state
5. **TranslationScreen**: Minimal, modern UI with two-person layout

## Setup

### Prerequisites

- Android Studio Hedgehog or newer
- Android SDK with API level 26+
- Gemini API Key from [Google AI Studio](https://makersuite.google.com/app/apikey)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/TranslationApp.git
   cd TranslationApp
   ```

2. Copy the local properties template:
   ```bash
   cp local.properties.template local.properties
   ```

3. Edit `local.properties` and add your Gemini API key:
   ```properties
   GEMINI_API_KEY=your_actual_gemini_api_key_here
   ```

4. Open the project in Android Studio

5. Build and run on your device or emulator

## Usage

### Basic Flow

1. **Select Languages**: Choose your language and their language
2. **Speak**: Tap the microphone button for your side
3. **Translate**: Speech is automatically transcribed and translated
4. **Listen**: The translation is spoken in the target language
5. **Swap**: Use the swap button to reverse languages

### Two-Person Conversation

- **Person 1** (Top): Speaks in Language A
- **Person 2** (Bottom): Speaks in Language B
- Each person's speech is translated to the other's language
- Translations are displayed and spoken automatically

## Design Philosophy

The UI follows modern startup design principles:
- **Minimal color palette**: Only 5 colors (indigo accent, slate black, gray, white, light accent)
- **Generous whitespace**: Clean, uncluttered layouts
- **Simple typography**: Clear, readable text hierarchy
- **Intuitive interactions**: Obvious actions without unnecessary complexity

## Customization

### UI Colors

Edit `app/src/main/java/com/panam/translationapp/ui/theme/Color.kt`:

```kotlin
val Accent = Color(0xFF6366F1)  // Change accent color
```

### Add Languages

Edit `app/src/main/java/com/panam/translationapp/translation/Language.kt`:

```kotlin
enum class Language(val displayName: String, val code: String) {
    // Add new languages here
    VIETNAMESE("Vietnamese", "vi"),
}
```

## API

This app uses the Gemini 2.5 Flash model for translation:
- Fast response times
- High translation accuracy
- Understanding of idioms and colloquialisms
- Natural conversation flow
- Support for 100+ languages (currently using 12)

## Security

- API keys are stored in `local.properties` (gitignored)
- Never commit `local.properties` to version control
- Use `local.properties.template` for setup instructions

## License

This project is open source and available under the [MIT License](LICENSE).
