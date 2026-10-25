#!/usr/bin/env python3
"""Provide precise on-device diagnosis for missing or unlaunchable ARM64 SD engine."""
from pathlib import Path
p=Path("sdapp/app/src/main/java/local/kleine/sdcpp/SDActivity.java")
s=p.read_text()
old='''        model_spinner.setAdapter(model_adapter);
        addModelSetupControls(model_spinner, hasModel);

        Spinner sampler_spinner'''
new='''        model_spinner.setAdapter(model_adapter);
        addModelSetupControls(model_spinner, hasModel);
        addNativeEngineDiagnostics(model_spinner);

        Spinner sampler_spinner'''
assert old in s, "Model importer not applied"
s=s.replace(old,new)
anchor='    private List<String> fileList, samplerList, schedulerList;'
assert anchor in s
code=r'''
    private android.widget.TextView nativeEngineReport;

    private void addNativeEngineDiagnostics(Spinner modelSpinner) {
        android.view.ViewGroup parent = (android.view.ViewGroup) modelSpinner.getParent();
        Button test = new Button(this);
        test.setText("DIAGNOSE LIBSD.SO — CHECK ENGINE");
        parent.addView(test);
        nativeEngineReport = new android.widget.TextView(this);
        nativeEngineReport.setText("If you see 'library not found', tap DIAGNOSE LIBSD.SO " +
                "and send a screenshot of this report.");
        nativeEngineReport.setPadding(12, 10, 12, 12);
        parent.addView(nativeEngineReport);
        Button share = new Button(this);
        share.setText("SHARE ENGINE DIAGNOSTIC REPORT");
        parent.addView(share);
        share.setOnClickListener(v -> {
            String report = nativeEngineReport.getText().toString();
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, report);
            try { startActivity(Intent.createChooser(i, "Share AI engine diagnosis")); }
            catch (Exception e) { Toast.makeText(this, report, Toast.LENGTH_LONG).show(); }
        });
        test.setOnClickListener(v -> {
            test.setEnabled(false);
            nativeEngineReport.setText("Checking engine extraction and launch...");
            new Thread(() -> {
                String report = checkEngine();
                runOnUiThread(() -> {
                    test.setEnabled(true);
                    if (nativeEngineReport != null) nativeEngineReport.setText(report);
                });
            }, "sd-engine-diagnostic").start();
        });
    }

    private String checkEngine() {
        StringBuilder report = new StringBuilder();
        report.append("Offline Real Photo Studio Engine Check\n");
        report.append("Version: 1.3-diagnostic\n");
        report.append("Device: ").append(Build.MANUFACTURER).append(" ")
                .append(Build.MODEL).append("\n");
        report.append("Android SDK: ").append(Build.VERSION.SDK_INT).append("\n");
        report.append("ABIs: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append("\n");
        File libDir = new File(getApplicationInfo().nativeLibraryDir);
        File expected = new File(libDir, "libsd.so");
        report.append("nativeLibraryDir: ").append(libDir).append("\n");
        report.append("Native directory exists: ").append(libDir.isDirectory()).append("\n");
        report.append("Installed libsd.so exists: ").append(expected.isFile()).append("\n");
        report.append("libsd.so bytes: ").append(expected.isFile() ? expected.length() : 0).append("\n");
        report.append("Library readable: ").append(expected.canRead()).append("\n");
        report.append("Library executable: ").append(expected.canExecute()).append("\n");
        try {
            java.util.zip.ZipFile apk = new java.util.zip.ZipFile(getApplicationInfo().sourceDir);
            try {
                for (String abi : Build.SUPPORTED_ABIS) {
                    java.util.zip.ZipEntry entry = apk.getEntry("lib/" + abi + "/libsd.so");
                    report.append("APK lib/").append(abi).append(": ");
                    report.append(entry == null ? "ABSENT" : "PRESENT (" + entry.getSize() + " bytes)");
                    report.append("\n");
                }
            } finally { apk.close(); }
        } catch (Exception e) {
            report.append("APK inspection error: ").append(e).append("\n");
        }
        if (expected.isFile()) {
            try {
                Process proc = new ProcessBuilder(expected.getAbsolutePath(), "--help")
                        .redirectErrorStream(true).start();
                final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                Thread reader = new Thread(() -> {
                    try (java.io.InputStream stream = proc.getInputStream()) {
                        byte[] buffer = new byte[1024];
                        int count, total = 0;
                        while ((count = stream.read(buffer)) > 0 && total < 10000) {
                            int n = Math.min(count, 10000-total);
                            bytes.write(buffer,0,n); total += n;
                        }
                    } catch (Exception ignored) {}
                }, "sd-help-output");
                reader.start();
                boolean completed = proc.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
                if (!completed) proc.destroyForcibly();
                reader.join(2500);
                report.append("Native --help finished: ").append(completed).append("\n");
                if (completed) report.append("Exit code: ").append(proc.exitValue()).append("\n");
                String output = bytes.toString("UTF-8");
                report.append("Native output: ").append(output.substring(0,Math.min(output.length(),600))).append("\n");
            } catch (Exception e) {
                report.append("Native launch error: ").append(e.toString()).append("\n");
            }
        } else {
            report.append("FINDING: Executable is inside APK but not present under nativeLibraryDir.\n");
            report.append("Possible causes: installer did not extract libs, APK/ABI mismatch, or stale installation.\n");
        }
        report.append("No server connection is used for this test.\n");
        return report.toString();
    }
'''
s=s.replace(anchor,anchor+code)
p.write_text(s)
print("Engine checker added to installed Android app")
