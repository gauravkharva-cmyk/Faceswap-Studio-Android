from pathlib import Path
r=Path("OpenPhotoStudio")
p=r/"app/src/main/java/com/openphotostudio/app/MainActivity.java"
s=p.read_text()
changes=[
("Create images from words or transform your photos","Free image generation · no subscription or credit card"),
("AI Horde · Community generation","AI Horde · FREE (no login or payment)"),
("AI Horde connects to public volunteer generation servers. No local computer needed. Availability depends on workers; anonymous access may be slow and anonymous requests are shared.","FREE COMMUNITY SERVER: No account, payment, paid API key, or personal computer required. Uses AI Horde volunteers. Anonymous requests have low priority and may be shared; generation depends on available workers."),
("AI Horde API key (optional; blank = anonymous)","Optional FREE AI Horde account key (leave blank for free guest)"),
("Leave blank for anonymous access","Leave blank · FREE guest mode"),
('        hordePanel.addView(keyField);\n        hordePanel.addView(label("Specific model (optional)"));', '''        hordePanel.addView(keyField);
        Button useFreeGuest = button("Use FREE guest server · no API key", true);
        hordePanel.addView(useFreeGuest);
        useFreeGuest.setOnClickListener(v -> {
            backendSpinner.setSelection(0);
            keyField.setText("");
            preferences.edit().remove("horde_key").putInt("backend", 0).apply();
            toast("Free AI Horde guest server selected. No payment or signup required.");
        });
        hordePanel.addView(label("Specific model (optional)"));'''),
("AI Horde workers and models may have restrictions.","Free AI Horde uses volunteers, queues and fair-use limits. No paid services are used in guest mode."),
('statusText.setText(backend == 0 ? "Submitting to AI Horde community workers…" : "Generating on your private server…");','statusText.setText(backend == 0 ? "Free guest/community generation · checking volunteer queue…" : "Generating on your private server…");'),
('req.put("shared", false);','req.put("shared", hordeKey.isEmpty()); // Anonymous requests are shared'),
]
for old,new in changes:
    if old not in s: raise ValueError("Missing patch anchor: "+old[:100])
    s=s.replace(old,new)
p.write_text(s)
p=r/"app/src/main/java/com/openphotostudio/app/HordeClient.java"
s=p.read_text().replace("OpenPhotoStudioAndroid:1.1:","OpenPhotoStudioAndroid:1.2:")
p.write_text(s)
p=r/"app/build.gradle"
s=p.read_text().replace("versionCode 1","versionCode 2").replace("versionName '1.0'","versionName '1.2'")
p.write_text(s)
print("Free guest mode patched")
