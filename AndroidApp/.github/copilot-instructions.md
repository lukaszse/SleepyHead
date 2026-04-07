# GitHub Copilot — Project Instructions for SleepyHead

## Response Language

- Always respond in the same language the user is writing in.

## Response Formatting

- **Write clearly.** Avoid walls of text — split responses into short sections with headings.
- Use **Markdown headings** (`##`, `###`) to organize sections.
- Format tables properly — with full names, no truncated text.
- Use bullet lists instead of long paragraphs.
- Always specify the language in code blocks (e.g., ` ```kotlin `).
- Use blank lines between sections for readability.
- Keep sentences short and concrete — don't ramble.

## Technical Style

- Project: **Android (Kotlin)**, architecture: **Hexagonal / Ports & Adapters**.
- Coroutines + Flow — not RxJava (RxJava only in the Polar SDK adapter layer).
- Tests: JUnit 5 + Turbine (for testing Flow).
- Class, method, and variable names in code — in English.
- KDoc comments — in English.
- Communication with the user — in Polish.

