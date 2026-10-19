# Virtual Try-On AI — Android

**Developed By : Gaurav Kharva**

A standalone Android outfit-changing app, independent of the FaceSwap Studio code. It uses FASHN Virtual Try-On v1.6 for realistic clothing replacement, with the **conservative** clothing moderation setting always on. This is a virtual *try-on*, **not** a nudification tool.

## Features
- Select a photo of a person and a separate photo of a garment from Android's document picker.
- Choose tops, bottoms, one-pieces or automatic garment category.
- Preview and save the AI-styled image locally, with a permanent `AI STYLED • VIRTUAL TRY-ON` label.
- Explicit consent confirmation and network-upload disclosure before each generation.
- No advertising, no third-party analytics, no login needed in the app, no backup, and no persistent storage of the API key.
- FASHN receives image content **only after** the user taps Generate and confirms consent.
- Uses base64 input/output to avoid exposing photo URLs. Generation requires an internet connection and the user's FASHN API key/credits.

## Requirements

- Android 8.0 or newer.
- FASHN developer API key from https://app.fashn.ai (the provider may charge for usage).
- A user-supplied photo of a clothed person plus a garment image.

### Privacy and limitations

Photos are selected from local storage; before an AI request they are JPEG-compressed and sent via HTTPS to **FASHN**, which processes them with its AI infrastructure. They are **not processed offline**. Inputs are transmitted as base64 within an HTTPS request. The app requests `return_base64:true`, so output is not hosted through a public output image URL. FASHN retains request metadata according to its own policy. The API key is read from the current screen only, never hardcoded into the APK or saved in application preferences. Exports go to a location the user chooses; another app (e.g. Google Photos) may back them up separately.

See https://docs.fashn.ai/api-overview/data-retention-privacy .

## Build

Run `gradle --no-daemon assembleDebug` in this folder (Gradle 8.10.2 / Android Gradle Plugin 8.7.3 / Java 17 / Android SDK 35), or use the included GitHub Actions workflow.

## API

`POST https://api.fashn.ai/v1/run` with `model_name: tryon-v1.6`, then poll `GET /v1/status/{id}`. The provider requires its own Bearer API key. For the user to run real AI try-on, they must enter a valid key in the app; the APK does not include one. Output generation may consume API credits.
