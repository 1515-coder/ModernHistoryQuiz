# ModernHistoryQuiz

A local Android quiz application for *Outline of Modern Chinese History*.

## Version 2.2.0 changes

- Removed the C++/NDK/JNI quiz engine.
- Loads the built-in 280-question bank from `app/src/main/assets/builtin_questions.json`.
- Stores user-created questions in `files/question_banks/user_questions.json`.
- Supports pasted-text parsing and direct JSON import/export.
- Stores favorites in `files/state/favorites.json`.
- Stores study statistics and wrong questions in `files/state/progress.json`.
- Stores the last question and filter state in `files/state/session_state.json`.
- Saves the current position 500 ms after navigation and immediately in `onPause()`.
- Uses an expandable left drawer for bank, mode, category, type, and tools.

## Removed native files

The following native implementation is no longer needed and has been deleted:

```text
app/src/main/cpp/
app/src/main/java/com/lzq/shigangquiz/NativeQuiz.java
```

The `externalNativeBuild`, `ndk`, and CMake settings were also removed from `app/build.gradle.kts`.

## Text import format

```text
1. Question text ( )
A. Option A
B. Option B
C. Option C
D. Option D
【答案】B
【解析】Explanation text
```

Multiple answers such as `【答案】ACD` are automatically treated as a multiple-choice question. An optional category line is supported:

```text
【分类】抗日战争
```

## JSON format

The application accepts either a top-level `questions` object or a direct array. Each question uses this shape:

```json
{
  "question": "Question text",
  "type": "single",
  "category": "User Questions",
  "options": [
    {"label": "A", "text": "Option A"},
    {"label": "B", "text": "Option B"}
  ],
  "answer": ["B"],
  "explanation": "Explanation"
}
```

## Release build

A signed release APK can be generated in Android Studio through:

```text
Build
→ Generate Signed App Bundle or APK
→ APK
```

The generated release APK is typically located at:

```text
app/release/app-release.apk
```

Release APK files and signing keys are not committed to the source repository. Published APK files are distributed separately through GitHub Releases.

## Development disclosure

This project was developed with generative AI assistance. AI tools were used for parts of the code generation, refactoring, debugging, and documentation. The project maintainer reviewed the implementation, made the final design decisions, tested the application, and is responsible for the released builds.
