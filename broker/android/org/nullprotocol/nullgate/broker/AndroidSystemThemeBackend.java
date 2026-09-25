package org.nullprotocol.nullgate.broker;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import org.nullprotocol.nullgate.protocol.CapabilityPayload;

/** Fixed settings-tool implementation; no general shell API or caller-supplied JSON. */
public final class AndroidSystemThemeBackend implements SystemThemeSeedAdapter.Backend {
    private static final String SETTING = "theme_customization_overlay_packages";
    private static final String SOURCE = "android.theme.customization.color_source";
    private static final String STYLE = "android.theme.customization.theme_style";
    private static final String PALETTE = "android.theme.customization.system_palette";
    private static final String TIMESTAMP = "_applied_timestamp";
    private static final String SNAPSHOT = RuntimeFiles.ROOT + "/theme.snapshot";

    public AndroidSystemThemeBackend(Context context) {
        if (context == null) throw new IllegalArgumentException("context required");
    }

    @Override public boolean available() {
        return android.os.Process.myUid() == 0;
    }

    @Override public String snapshot() throws Exception {
        ProcessResult result = runSettings("get", "secure", SETTING);
        if (result.exitCode != 0)
            throw new IllegalStateException("fixed settings read failed: " + result.output);
        String value = stripLineEndings(result.output);
        return "null".equals(value) ? null : value;
    }

    @Override public void recordSnapshot(String snapshot) throws Exception {
        byte[] value = snapshot == null ? new byte[0]
                : snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (value.length > 16_384) throw new SecurityException("theme snapshot exceeds safety bound");
        byte[] prefix = (snapshot == null ? "NULL\n" : "VALUE\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] record = new byte[prefix.length + value.length];
        System.arraycopy(prefix, 0, record, 0, prefix.length);
        System.arraycopy(value, 0, record, prefix.length, value.length);
        RuntimeFiles.writeExclusive(SNAPSHOT, record);
    }

    @Override public void apply(int seedArgb, CapabilityPayload.ThemeStyle style) throws Exception {
        JSONObject value = parse(snapshot());
        value.put(SOURCE, "preset");
        value.put(STYLE, style.name());
        value.put(PALETTE, String.format(java.util.Locale.ROOT, "%06x", seedArgb & 0xffffff));
        value.put(TIMESTAMP, System.currentTimeMillis());
        writeSetting(value.toString());
    }

    @Override public boolean matches(int seedArgb, CapabilityPayload.ThemeStyle style) throws Exception {
        if (!matchesOnce(seedArgb, style)) return false;
        SystemClock.sleep(500);
        return matchesOnce(seedArgb, style);
    }

    private boolean matchesOnce(int seedArgb, CapabilityPayload.ThemeStyle style) throws Exception {
        JSONObject value = parse(snapshot());
        return "preset".equals(value.optString(SOURCE))
                && style.name().equals(value.optString(STYLE))
                && String.format(java.util.Locale.ROOT, "%06x", seedArgb & 0xffffff)
                    .equalsIgnoreCase(value.optString(PALETTE));
    }

    @Override public void restore(String snapshot) throws Exception {
        writeSetting(snapshot);
    }

    @Override public boolean matchesSnapshot(String expected) throws Exception {
        if (!java.util.Objects.equals(expected, snapshot())) return false;
        SystemClock.sleep(500);
        return java.util.Objects.equals(expected, snapshot());
    }

    @Override public void clearSnapshotRecord() throws Exception {
        android.system.StructStat stat;
        try {
            stat = android.system.Os.lstat(SNAPSHOT);
        } catch (android.system.ErrnoException absent) {
            if (absent.errno == android.system.OsConstants.ENOENT) return;
            throw absent;
        }
        if (!android.system.OsConstants.S_ISREG(stat.st_mode) || stat.st_uid != 0
                || (stat.st_mode & 0777) != 0600)
            throw new SecurityException("unsafe theme snapshot receipt");
        android.system.Os.remove(SNAPSHOT);
    }

    private static JSONObject parse(String value) throws Exception {
        if (value == null || "null".equals(value) || value.isEmpty()) return new JSONObject();
        if (value.length() > 16_384) throw new SecurityException("theme setting exceeds safety bound");
        return new JSONObject(value);
    }

    private void writeSetting(String value) throws Exception {
        ProcessResult result = value == null
                ? runSettings("delete", "secure", SETTING)
                : runSettings("put", "secure", SETTING, value);
        if (result.exitCode != 0)
            throw new IllegalStateException("fixed settings operation failed: " + result.output);
    }

    private static ProcessResult runSettings(String... arguments) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("/system/bin/cmd");
        command.add("settings");
        java.util.Collections.addAll(command, arguments);
        BoundedProcessRunner.Result result = BoundedProcessRunner.run(command, 5_000L, 16_386);
        byte[] output = result.output;
        String decoded = new String(output, java.nio.charset.StandardCharsets.UTF_8);
        String stripped = stripLineEndings(decoded);
        if (stripped.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16_384)
            throw new SecurityException("theme setting exceeds safety bound");
        return new ProcessResult(result.exitCode,
                decoded);
    }

    private static String stripLineEndings(String value) {
        int end = value.length();
        while (end > 0 && (value.charAt(end - 1) == '\n' || value.charAt(end - 1) == '\r')) end--;
        return value.substring(0, end);
    }

    private static final class ProcessResult {
        final int exitCode;
        final String output;
        ProcessResult(int exitCode, String output) { this.exitCode = exitCode; this.output = output; }
    }
}
