# EPUB Novel Translator

An Android application designed for offline EPUB novel translation powered by local LLM models using Google LiteRT (`.litertlm`).

##  Features

- **On-Device LLM Translation**: Translate EPUB books completely offline using local `.litertlm` models.
- **Model Manager**: Import, manage, select, and delete custom LiteRT LLM model files directly within the app.
- **EPUB Parser & Progress Tracker**: Translates novels chapter-by-chapter and paragraph-by-paragraph with visual progress tracking.
- **Modern Jetpack Compose UI**: Built with Jetpack Compose, Material 3, and Kotlin.
- **Background Support**: Background translation progress with Android notification support.

##  Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose & Material 3
- **Local AI Engine**: LiteRT (`.litertlm` session model execution)
- **Database / Architecture**: Room, ViewModel, Coroutines & StateFlow

##  Getting Started

### Prerequisites

- **Android Studio** (Ladybug / Jellyfish or newer recommended)
- **Android Device** with sufficient RAM to run local `.litertlm` models
- A compatible `.litertlm` LiteRT model file

### How to Use

1. **Import a Model**: Open the app, navigate to **Model Settings**, and import your local `.litertlm` model file.
2. **Open an EPUB**: Select an EPUB book file from your device storage.
3. **Translate**: Choose target languages/settings and start the translation process.
