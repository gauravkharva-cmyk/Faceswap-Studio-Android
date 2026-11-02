# GK Qwen Image 2.1 PRIVATE Studio

Android client for a **self-owned** Qwen-Image-2.1 backend. Unlike the earlier public-demo wrapper this native APK has no Hugging Face Space or Stuffer.ai connection.

- Requires your own HTTPS backend and strong API key.
- Select up to 10 reference images using Android's system picker.
- Prompts and images are uploaded **only after explicit confirmation** to the HTTPS origin you entered.
- No external tracking/analytics/third-party libraries.
- AES-256-GCM API-key encryption using Android Keystore, no Android backup, no plaintext HTTP, and prevents screenshots.
- Result preview stays in application memory; Save uses Android's system document chooser; Clear Session clears images and prompt.
- Does not include the Qwen image model weights and does not promise unlimited GPU compute.
- Backend code and test suite are delivered separately as an archive.

Be aware that selecting a file through another Android app, running on a cloud GPU host, TLS reverse proxies, crash dumps, swap, backups, system memory and provider infrastructure can expose data beyond what this app can guarantee.

**Developed By : Gaurav Kharva**
