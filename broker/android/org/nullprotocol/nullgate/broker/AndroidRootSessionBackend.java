package org.nullprotocol.nullgate.broker;

import android.net.LocalSocket;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Starts ordinary root shells in distinct sessions. OFF terminates their whole process groups. */
public final class AndroidRootSessionBackend implements RootSessionEngine.Backend {
    private final AndroidPackageEvidence evidence;
    private final String controllerPackage;
    public AndroidRootSessionBackend(AndroidPackageEvidence evidence, String controllerPackage) {
        this.evidence = evidence; this.controllerPackage = controllerPackage;
    }

    @Override public CallerIdentity targetIdentity(String name) throws Exception {
        if (controllerPackage.equals(name)) throw new SecurityException("controller is not a target");
        int uid = evidence.context().getPackageManager().getApplicationInfo(name, 0).uid;
        String[] signers = evidence.currentSignerSha256(name);
        if (signers.length != 1) throw new SecurityException("target has multiple signers");
        return new VerifiedCallerResolver(name, signers[0], evidence).resolve(uid);
    }

    public CallerIdentity resolvePeer(int uid, String target) throws Exception {
        CallerIdentity expected = targetIdentity(target);
        if (uid != expected.uid) throw new SecurityException("unselected app");
        return expected;
    }

    @Override public RootSessionEngine.ShellHandle startShell() throws Exception {
        int brokerPid = android.os.Process.myPid();
        long brokerStarted = identity(brokerPid).started;
        String watchdog = "(while :; do read -r record < /proc/" + brokerPid + "/stat || break; "
                + "record=${record##*) }; set -- $record; [ \"${20}\" = \"" + brokerStarted
                + "\" ] || break; sleep 1; done; kill -KILL -$$) </dev/null >/dev/null 2>&1 & ";
        Process process = new ProcessBuilder("/system/bin/setsid", "/system/bin/sh", "-c",
                "printf 'NULLGATE_PG:%s\\n' \"$$\"; read -r permit; "
                + "[ \"$permit\" = NULLGATE_START ] || exit 126; " + watchdog + "exec /system/bin/sh").start();
        FutureTask<Integer> pidReader = new FutureTask<>(() -> {
            StringBuilder line = new StringBuilder(); int value;
            while ((value = process.getInputStream().read()) != '\n') {
                if (value < 0 || line.length() >= 64) throw new SecurityException("missing root group receipt");
                line.append((char) value);
            }
            if (!line.toString().matches("NULLGATE_PG:[1-9][0-9]{1,8}"))
                throw new SecurityException("invalid root group receipt");
            return Integer.parseInt(line.substring("NULLGATE_PG:".length()));
        });
        Thread reader = new Thread(pidReader, "nullgate-group-receipt"); reader.setDaemon(true); reader.start();
        try {
            int pid = pidReader.get(2, TimeUnit.SECONDS);
            RootProcessIdentity identity = identity(pid);
            if (identity.parent != android.os.Process.myPid() || identity.group != pid
                    || identity.session != pid || Os.lstat("/proc/" + pid).st_uid != 0)
                throw new SecurityException("root process group ownership was not verified");
            process.getOutputStream().write("NULLGATE_START\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            process.getOutputStream().flush();
            return new Shell(process, identity);
        } catch (Exception failure) {
            process.destroyForcibly();
            try { process.getInputStream().close(); } catch (Exception ignored) { }
            throw failure;
        } finally { pidReader.cancel(true); }
    }

    @Override public void forceStopTarget(String name) throws Exception {
        org.nullprotocol.nullgate.protocol.RootSessionProtocol.requirePackage(name);
        BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                Arrays.asList("/system/bin/am", "force-stop", "--user", "0", name), 5000, 16384);
        if (result.exitCode != 0) throw new IllegalStateException("target did not stop");
    }

    public static final class Shell implements RootSessionEngine.ShellHandle {
        final Process process;
        final RootProcessIdentity receipt;
        private LocalSocket socket;
        private boolean stopped;
        Shell(Process process, RootProcessIdentity receipt) { this.process = process; this.receipt = receipt; }
        synchronized void attach(LocalSocket connection) throws Exception {
            if (stopped) { connection.close(); throw new SecurityException("session was switched OFF"); }
            socket = connection;
        }
        @Override public synchronized void stop() throws Exception {
            if (stopped) return;
            if (socket != null) try { socket.close(); } catch (Exception ignored) { }
            // Even if sh exited, its background RootService can still be in this session.
            if (groupExists(receipt)) Os.kill(-receipt.group, OsConstants.SIGKILL);
            long deadline = SystemClock.elapsedRealtime() + 3000;
            while (groupExists(receipt) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25);
            if (groupExists(receipt)) throw new IllegalStateException("root group did not stop");
            process.destroyForcibly();
            try { process.getOutputStream().close(); } catch (Exception ignored) { }
            try { process.getInputStream().close(); } catch (Exception ignored) { }
            try { process.getErrorStream().close(); } catch (Exception ignored) { }
            stopped = true;
        }
    }

    private static RootProcessIdentity identity(int pid) throws Exception {
        try (FileInputStream input = new FileInputStream("/proc/" + pid + "/stat")) {
            return RootProcessIdentity.parse(new String(BoundedInput.readAll(input, 8192),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static boolean groupExists(RootProcessIdentity receipt) throws Exception {
        File leader = new File("/proc/" + receipt.pid);
        if (leader.exists()) {
            RootProcessIdentity now;
            try { now = identity(receipt.pid); }
            catch (Exception gone) { if (leader.exists()) throw gone; now = null; }
            if (now != null && (now.started != receipt.started || now.session != receipt.session))
                throw new SecurityException("root group leader identity changed; refusing signal");
        }
        File[] entries = new File("/proc").listFiles();
        if (entries == null) throw new IllegalStateException("process inventory unavailable");
        boolean found = false;
        for (File entry : entries) {
            if (!entry.getName().matches("[1-9][0-9]*")) continue;
            try {
                RootProcessIdentity process = identity(Integer.parseInt(entry.getName()));
                if (process.group != receipt.group) continue;
                if (process.session != receipt.session || process.started < receipt.started
                        || Os.lstat(entry.getPath()).st_uid != 0)
                    throw new SecurityException("process group has unexpected ownership");
                // Zombies cannot execute; the parent/init is responsible for reaping them.
                try (FileInputStream input = new FileInputStream(new File(entry, "stat"))) {
                    String stat = new String(BoundedInput.readAll(input, 8192), java.nio.charset.StandardCharsets.UTF_8);
                    if (!stat.substring(stat.lastIndexOf(')') + 1).trim().startsWith("Z ")) found = true;
                }
            } catch (Exception disappeared) { if (entry.exists()) throw disappeared; }
        }
        return found;
    }
}
