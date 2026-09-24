package org.nullprotocol.nullgate.broker;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Android implementation using only fixed Shizuku lifecycle and permission operations. */
public final class AndroidShizukuSessionBackend implements ShizukuSessionAdapter.Backend {
    static final String MANAGER = "moe.shizuku.privileged.api";
    static final String PERMISSION = "moe.shizuku.manager.permission.API_V23";
    private static final String SERVER_NAME = "shizuku_server";
    private static final long COMMAND_TIMEOUT_MILLIS = 5_000;
    private final PackageManager packages;
    private final String managerSigner;
    private final String targetPackage;
    private final String targetSigner;

    public AndroidShizukuSessionBackend(Context context, String managerSigner,
            String targetPackage, String targetSigner) {
        this.packages = context.getPackageManager();
        this.managerSigner = CallerIdentity.normalizeDigest(managerSigner);
        this.targetPackage = targetPackage;
        this.targetSigner = CallerIdentity.normalizeDigest(targetSigner);
    }

    @Override public boolean managerIdentityMatches() throws Exception {
        return signerMatches(MANAGER, managerSigner) && managerInfo().applicationInfo != null;
    }

    @Override public boolean targetIdentityMatches(String requestedTarget) throws Exception {
        return targetPackage.equals(requestedTarget) && signerMatches(targetPackage, targetSigner);
    }

    @Override public boolean targetDeclaresShizukuPermission(String requestedTarget)
            throws Exception {
        if (!targetPackage.equals(requestedTarget)) return false;
        PackageInfo info = packages.getPackageInfo(targetPackage, PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions == null) return false;
        for (String permission : info.requestedPermissions)
            if (PERMISSION.equals(permission)) return true;
        return false;
    }

    @Override public boolean serverRunning() throws Exception {
        return !serverPids().isEmpty();
    }

    @Override public boolean permissionGranted(String requestedTarget) {
        requireTarget(requestedTarget);
        return packages.checkPermission(PERMISSION, targetPackage)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override public Set<String> packagesHoldingShizukuPermission() {
        Set<String> result = new HashSet<>();
        for (PackageInfo info : packages.getPackagesHoldingPermissions(
                new String[]{PERMISSION}, PackageManager.GET_PERMISSIONS)) {
            if (info != null && info.packageName != null
                    && packages.checkPermission(PERMISSION, info.packageName)
                    == PackageManager.PERMISSION_GRANTED)
                result.add(info.packageName);
        }
        return result;
    }

    @Override public void grantPermission(String requestedTarget) throws Exception {
        requireTarget(requestedTarget);
        runFixed("/system/bin/pm", "grant", targetPackage, PERMISSION);
    }

    @Override public void startServer() throws Exception {
        PackageInfo manager = managerInfo();
        File libraryDirectory = new File(manager.applicationInfo.nativeLibraryDir).getCanonicalFile();
        File starter = new File(libraryDirectory, "libshizuku.so").getCanonicalFile();
        if (!starter.getParentFile().equals(libraryDirectory))
            throw new SecurityException("Shizuku starter escaped its package library directory");
        StructStat stat = Os.lstat(starter.getAbsolutePath());
        if (!OsConstants.S_ISREG(stat.st_mode) || (stat.st_mode & 0111) == 0)
            throw new SecurityException("Shizuku starter is not a regular executable");
        runFixed(starter.getAbsolutePath(), "--apk=" + manager.applicationInfo.sourceDir);
        waitForServer(true);
    }

    @Override public void forceStopTarget(String requestedTarget) throws Exception {
        requireTarget(requestedTarget);
        runFixed("/system/bin/am", "force-stop", "--user", "0", targetPackage);
    }

    @Override public void stopServer() throws Exception {
        for (Integer pid : serverPids()) Os.kill(pid, OsConstants.SIGKILL);
        waitForServer(false);
    }

    @Override public void revokePermission(String requestedTarget) throws Exception {
        requireTarget(requestedTarget);
        runFixed("/system/bin/pm", "revoke", targetPackage, PERMISSION);
    }

    private PackageInfo managerInfo() throws Exception {
        return packages.getPackageInfo(MANAGER,
                PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_PERMISSIONS);
    }

    private boolean signerMatches(String packageName, String expected) throws Exception {
        PackageInfo info = packages.getPackageInfo(packageName,
                PackageManager.GET_SIGNING_CERTIFICATES);
        if (info.signingInfo == null) return false;
        Signature[] signers = info.signingInfo.getApkContentsSigners();
        if (signers == null || signers.length != 1) return false;
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] digest = sha256.digest(signers[0].toByteArray());
        StringBuilder text = new StringBuilder(64);
        for (byte value : digest) text.append(String.format("%02x", value & 0xff));
        return expected.equals(text.toString());
    }

    private Set<Integer> serverPids() throws Exception {
        Set<Integer> result = new HashSet<>();
        File[] entries = new File("/proc").listFiles();
        if (entries == null) throw new IllegalStateException("cannot enumerate processes");
        for (File entry : entries) {
            if (!entry.getName().matches("[0-9]+")) continue;
            try {
                StructStat stat = Os.lstat(entry.getAbsolutePath());
                if (stat.st_uid != 0) continue;
                byte[] name = readBounded(new File(entry, "cmdline"), 256);
                int end = 0;
                while (end < name.length && name[end] != 0) end++;
                String processName = new String(name, 0, end,
                        java.nio.charset.StandardCharsets.UTF_8);
                if (SERVER_NAME.equals(processName)) result.add(Integer.parseInt(entry.getName()));
            } catch (Exception processExited) {
                // Processes can disappear while /proc is enumerated.
            }
        }
        return result;
    }

    private void waitForServer(boolean expectedRunning) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + COMMAND_TIMEOUT_MILLIS;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (serverRunning() == expectedRunning) return;
            SystemClock.sleep(50);
        }
        throw new IllegalStateException(expectedRunning
                ? "Shizuku server start was not observed" : "Shizuku server stop was not observed");
    }

    private static void runFixed(String... argv) throws Exception {
        Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
        if (!process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("fixed Android operation timed out");
        }
        byte[] output = readBounded(process.getInputStream(), 16_384);
        if (process.exitValue() != 0)
            throw new IllegalStateException("fixed Android operation failed: "
                    + new String(output, java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] readBounded(File file, int limit) throws Exception {
        try (InputStream input = new FileInputStream(file)) { return readBounded(input, limit); }
    }

    private static byte[] readBounded(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (output.size() < limit) {
            int count = input.read(buffer, 0, Math.min(buffer.length, limit - output.size()));
            if (count < 0) break;
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void requireTarget(String requestedTarget) {
        if (!targetPackage.equals(requestedTarget))
            throw new SecurityException("unexpected Shizuku target package");
    }
}
