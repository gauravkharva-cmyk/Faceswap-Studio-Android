#!/usr/bin/env python3
"""Personalized, offline-only Android Stable Diffusion configuration.
Upstream: https://github.com/akleine/sdcpp-on-android (MIT).
"""
from pathlib import Path
import re

root=Path("sdapp")
build=root/"app/build.gradle.kts"
s=build.read_text()
assert 'applicationId = "local.kleine.sdcpp"' in s
s=s.replace('applicationId = "local.kleine.sdcpp"', 'applicationId = "com.openphoto.offlinereal"')
s=s.replace('versionCode = 1','versionCode = 2')
s=s.replace('versionName = "1.0"','versionName = "1.1-offline"')
build.write_text(s)

strings=root/"app/src/main/res/values/strings.xml"
s=strings.read_text()
old='<string name="app_name">SD.cpp</string>'
assert old in s
s=s.replace(old,'<string name="app_name">Offline Real Photo Studio</string>')
s=s.replace('HQ photo of snowy mountains, blue sky',
            'Realistic editorial portrait of an adult woman, natural skin texture, 85mm photography, high detail, studio lighting')
s=s.replace('<string name="default_negative">blurry</string>',
            '<string name="default_negative">blurry, cartoon, low quality, distorted, extra fingers, artifacts</string>')
s=s.replace('<string name="field_modelfile">model:</string>',
            '<string name="field_modelfile">Local AI model (.safetensors / .ckpt):</string>')
s=s.replace('<string name="field_widthXheight">WxH:</string>',
            '<string name="field_widthXheight">Image width × height (start at 512 on mid-range phones):</string>')
strings.write_text(s)

manifest=root/"app/src/main/AndroidManifest.xml"
m=manifest.read_text()
assert "android.permission.INTERNET" not in m
manifest.write_text(m)

notice=root/"app/src/main/assets/THIRD_PARTY_NOTICE.txt"
notice.parent.mkdir(parents=True,exist_ok=True)
notice.write_text("""Offline Real Photo Studio uses akleine/sdcpp-on-android
https://github.com/akleine/sdcpp-on-android
MIT License, Copyright (c) 2025 akleine.
The complete upstream MIT license is also bundled in this directory.

The native executable is based on stable-diffusion.cpp and ggml.
No server, login, API key or internet connection is required after
you obtain a compatible model .safetensors/.ckpt file separately.
Your prompts and generated images remain on your Android device.

Use responsibly: adults only in sexual imagery; no nonconsensual,
exploitative or otherwise unlawful images.
""")
(root/"app/src/main/assets/LICENSE_upstream.txt").write_text((root/"LICENSE").read_text())
print("Patched offline stable diffusion app; model not bundled; no INTERNET permission")
