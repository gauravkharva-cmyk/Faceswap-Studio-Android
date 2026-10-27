# AI Web Access for Stuffer (unofficial)
Independent Android WebView wrapper for https://stuffer.ai/. Not affiliated with or endorsed by the website.

Open app to access the online service. The app needs internet access and does not run AI models itself.
Buttons: Back, Refresh, Browser. Android file upload and standard HTTPS downloads supported.
If a download uses a blob URL, use the Browser button.
Built through GitHub Actions; download AI-Web-Access-APK from the Actions run's Artifacts section.
Only use this 18+ website in compliance with its policies at https://stuffer.ai/terms
Never create nonconsensual sexual deepfakes of identifiable real people or sexualized content involving minors.

## Build locally
Open this standalone branch as an Android Studio project, with Android SDK 35 and JDK 17. Run 'assembleDebug'.
The APK is debug-signed for personal use, not store distribution.


## Version 1.1.0
Adds Save Image in the toolbar. It prefers the largest visible direct HTTPS image; otherwise saves a screenshot of the visible website to Android Gallery > Pictures > AI Web Access. Long press an image to choose original download or visible screenshot. Image downloads go to Downloads. Canvas/blob images use screenshot fallback. No storage permission needed on Android 10+. If the older app has a different debug signing certificate, uninstall v1.0 before installing v1.1 (this clears the app's website session).


## Version 1.2.0 - upload improvements
Android upload from the site's reference/editing inputs now uses ACTION_OPEN_DOCUMENT and returns one or more content:// URIs to the website's WebChromeClient.FileChooserParams callback. Works without blanket media permissions on Android 10+; supports photos, downloads and multi-file uploads where requested.

Tap "Upload Photo" for a convenience picker that identifies editing/reference image inputs on the website and opens the selected input. If none is found, select an image-capable model (Qwen Edit, ControlNet++, WAN I2V) and tap its image-input control. An "Open in Chrome" fallback is available. This wrapper never automatically uses the website's PUBLIC upload page for reference images.

Limitations: website-specific WebView incompatibilities may persist; if you cannot upload in the wrapper, use the Browser button to upload in Chrome. For v1.x-to-v1.2 updates, uninstalling the older build may be necessary if debug signing key changed (website login may be cleared).


## Version 1.3.0
- Renamed **Upload Photo** to **Reference Photo** to avoid confusion with the website's public uploader.
- Reference Photo explains the correct workflow (Qwen Edit > Primary Image Required or Controlnet++ reference input) and can highlight the page's reference input when present. This control never opens or submits the public upload gallery.
- Android's system document/photo picker handles upload callback for the site's original reference file fields.
- This is a general purpose website wrapper; site rendering quirks or restrictions may still affect reference image uploads.
