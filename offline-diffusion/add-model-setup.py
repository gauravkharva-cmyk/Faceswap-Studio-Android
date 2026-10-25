#!/usr/bin/env python3
"""Add a one-time model download/import onboarding flow to the offline Android APK."""
from pathlib import Path

root=Path("sdapp")
p=root/"app/src/main/java/local/kleine/sdcpp/SDActivity.java"
src=p.read_text()

anchor='    private List<String> fileList, samplerList, schedulerList;'
assert anchor in src
src=src.replace(anchor,anchor+r'''
    private android.widget.TextView modelSetupStatus;
    private final ActivityResultLauncher<Intent> importModelLauncher =
            registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != RESULT_OK ||
                            result.getData() == null || result.getData().getData() == null) return;
                    importCheckpoint(result.getData().getData());
                });

    private void importCheckpoint(Uri uri) {
        String displayName = "imported-model.safetensors";
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                displayName = cursor.getString(0);
            }
        } catch (Exception ignored) {}
        displayName = displayName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (displayName.length() > 120) displayName = displayName.substring(displayName.length() - 120);
        String lower = displayName.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.endsWith(".safetensors") || lower.endsWith(".ckpt") ||
                lower.endsWith(".gguf"))) {
            Toast.makeText(this, "Choose a .safetensors, .ckpt, or .gguf model file",
                    Toast.LENGTH_LONG).show();
            return;
        }
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        File models = new File(base, "Models");
        if (!models.exists() && !models.mkdirs()) {
            Toast.makeText(this, "Cannot create local model folder", Toast.LENGTH_LONG).show();
            return;
        }
        final File outputModel = new File(models, displayName);
        if (modelSetupStatus != null)
            modelSetupStatus.setText("Importing model to private app storage. Keep the app open.");
        new Thread(() -> {
            long copied = 0L, lastReport = 0L;
            try (java.io.InputStream input = getContentResolver().openInputStream(uri);
                 java.io.FileOutputStream output = new java.io.FileOutputStream(outputModel)) {
                if (input == null) throw new java.io.IOException("File is not readable");
                byte[] buffer = new byte[1024 * 1024];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    output.write(buffer, 0, n);
                    copied += n;
                    if (copied - lastReport >= 100L * 1024L * 1024L) {
                        lastReport = copied;
                        long mibs = copied / (1024L * 1024L);
                        runOnUiThread(() -> {
                            if (modelSetupStatus != null) modelSetupStatus.setText(
                                    "Importing " + mibs + " MB — keep the app open...");
                        });
                    }
                }
                output.getFD().sync();
                if (copied < 500_000_000L)
                    throw new java.io.IOException("Selected file is too small (" + copied +
                            " bytes). Download the complete model checkpoint (~2.13 GB).");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Model imported! Ready for offline generation.",
                            Toast.LENGTH_LONG).show();
                    recreate();
                });
            } catch (Exception e) {
                outputModel.delete();
                String message = e.getMessage();
                runOnUiThread(() -> {
                    if (modelSetupStatus != null)
                        modelSetupStatus.setText("Import failed: " + message);
                    Toast.makeText(this, "Model import failed: " + message,
                            Toast.LENGTH_LONG).show();
                });
            }
        }, "model-import").start();
    }

    private void addModelSetupControls(Spinner modelSpinner, boolean hasModel) {
        android.view.ViewGroup group = (android.view.ViewGroup) modelSpinner.getParent();
        Button download = new Button(this);
        download.setText("1. GET FREE PHOTO AI MODEL (2.13 GB)");
        group.addView(download);
        download.setOnClickListener(v -> {
            String link = "https://huggingface.co/Comfy-Org/stable-diffusion-1.5/blob/main/" +
                    "v1-5-pruned-emaonly-fp16.safetensors";
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            } catch (Exception e) {
                Toast.makeText(this, "Open huggingface.co/Comfy-Org/stable-diffusion-1.5",
                        Toast.LENGTH_LONG).show();
            }
        });
        Button importFile = new Button(this);
        importFile.setText("2. IMPORT DOWNLOADED .SAFETENSORS MODEL");
        group.addView(importFile);
        importFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            importModelLauncher.launch(intent);
        });
        modelSetupStatus = new android.widget.TextView(this);
        modelSetupStatus.setPadding(16, 12, 16, 18);
        modelSetupStatus.setText(hasModel
                ? "Model loaded. Generate locally at 512 x 512 first. Larger images may run out of memory."
                : "Missing AI model. Download the free 2.13 GB checkpoint in your browser, " +
                  "then import it here. Only the one-time download needs internet. " +
                  "After importing, all image generation is offline.");
        group.addView(modelSetupStatus);
    }
''')

old='''        fileList = listExternalFiles(sdWorkPath);
        if (fileList.isEmpty()) {
            submitButton.setEnabled(false);
            fileList.add("At first copy SD model file to this device.");
        }
        Collections.sort(fileList);'''
new='''        fileList = listExternalFiles(sdWorkPath);
        File privateBase = getExternalFilesDir(null);
        if (privateBase == null) privateBase = getFilesDir();
        File privateModels = new File(privateBase, "Models");
        if (privateModels.isDirectory()) listFilesRecursively(privateModels, fileList);
        boolean hasModel = !fileList.isEmpty();
        if (!hasModel) {
            submitButton.setEnabled(false);
            fileList.add("No model installed: use buttons below.");
        }
        Collections.sort(fileList);'''
assert old in src
src=src.replace(old,new)

old='''        model_spinner.setAdapter(model_adapter);

        Spinner sampler_spinner'''
new='''        model_spinner.setAdapter(model_adapter);
        addModelSetupControls(model_spinner, hasModel);

        Spinner sampler_spinner'''
assert old in src
src=src.replace(old,new)

# Existing native CPU extraction fix is applied in patch_app.py.
p.write_text(src)
print("Model importer and one-time official model download UI installed.")
