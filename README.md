# FaceSwap Studio (Android)

**Functional scope:** consent-based AI-assisted **photo face swap** for Android. The app uses on-device Google ML Kit face detection to detect faces, aligns a source face onto a target photograph using eye/nose landmarks, softly blends the target face region, adds an `AI EDITED • FACE SWAP` watermark, and exports a PNG. It is **not** a generative deepfake-video model, and the blending will not perfectly match lighting, expressions, or pose.

- Photos stay on the device, without upload or an API key.
- Supports Android 8.0+ (API 26).
- Uses the largest face in each photograph; best with one forward-looking face per image.
- Request consent from both photo subjects. Do not use for impersonation, deceptive content, or nonconsensual intimate imagery.
- Attribution in the app: Developed by Gaurav Kharva.

## Generate the APK using GitHub Actions (no Android Studio required)

1. Create a **new GitHub repository** (for example `FaceSwap-Studio-Android`). Do not upload these files into another project's repository.
2. Extract `FaceSwap-Studio-Android.zip`; upload the **contents of the extracted folder** to the repository root, including the hidden `.github/workflows/build-apk.yml` file. A Git client preserves hidden folders better than a phone file picker.
3. Commit to the **main** (or **master**) branch. On GitHub open **Actions → Build FaceSwap APK → Run workflow** if a run did not start on push.
4. After the workflow succeeds, open the run → **Artifacts → FaceSwapStudio-APK**. Download and unzip it to get `app-debug.apk`.
5. Install the APK on your Android phone; Android may ask to allow installation from your file manager/browser.

Alternatively, open this project in Android Studio with JDK 17 and run the `assembleDebug` Gradle task.

## Usage

1. Choose a clear source face photo.
2. Choose a target portrait photo.
3. Confirm you have permission from the people pictured.
4. Tap **Swap Face**. Once preview is ready, tap **Save PNG** to save the watermarked image.

## Processing details

Face detection and contours are based on Google's ML Kit on-device detector (`com.google.mlkit:face-detection:16.1.7`). The app performs a local 3-point affine warp (eyes plus nose), contour masking, edge feathering, and overlay composition in Java/Android graphics. Quality is constrained by the source images. This is a real algorithmic face swap, *not* neural diffusion or face reenactment.

### Privacy and responsible use

No Internet permission is declared; there is no server component. Consent is required by the app. Exported images carry a visible `AI EDITED • FACE SWAP` label. This may not meet every platform's disclosure requirements: users should label synthetic/manipulated media appropriately when sharing.
