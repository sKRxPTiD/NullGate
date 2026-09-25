package org.nullprotocol.nullgate.broker;

import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Ephemeral root-side broker entry point. It grants policy state, not a generic shell. */
public final class NullGateBrokerMain {
    private static final String CONTROLLER = "org.nullprotocol.nullgate";
    private static final String COLORBLENDR = "com.drdisagree.colorblendr";
    private static final String OFFICIAL_SHIZUKU_SIGNER =
            "268b5590e868fb08bae7e0ac413564cd1ff88f5ccff74af9dbd0dc918e30db30";
    private static final String COLORBLENDR_SIGNER =
            "4af4ffa12ce90815a1775c4604ea16c19f5bed2a6e09ae7c3c92815982af052e";
    private static final String SOCKET = "nullgate-broker-v1";
    private static final long MAX_LEASE_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_ACTIVE_LEASES = 1;
    private static final long MAX_BROKER_LIFETIME_MILLIS = 15 * 60 * 1000L;
    private static final File PID_FILE = new File("/data/local/tmp/nullgate/broker.pid");

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("expected controller cert and optional typed mode");
        }
        String pinnedCert = CallerIdentity.normalizeDigest(args[0]);
        boolean themeMode = args.length == 2 && "SYSTEM_THEME_V1".equals(args[1]);
        boolean colorBlendrMode = args.length == 2
                && "COLORBLENDR_SHIZUKU_V1".equals(args[1]);
        if (args.length == 2 && !themeMode && !colorBlendrMode)
            throw new SecurityException("unknown broker mode");
        if (android.os.Process.myUid() != 0) throw new SecurityException("broker requires root");
        RuntimeFiles.requirePrivateDirectory(RuntimeFiles.ROOT);
        if (new File(RuntimeFiles.ROOT, "theme.snapshot").exists())
            throw new SecurityException("stale theme snapshot requires host recovery");
        File leaseDirectory = new File(RuntimeFiles.ROOT, "leases");
        if (leaseDirectory.exists()) {
            RuntimeFiles.requirePrivateDirectory(leaseDirectory.getAbsolutePath());
            String[] stale = leaseDirectory.list();
            if (stale == null || stale.length != 0)
                throw new SecurityException("stale lease artifacts require host reconciliation");
        }
        Map<String, Set<Capability>> allow = new HashMap<>();
        allow.put(CONTROLLER, EnumSet.of(Capability.NULLGATE_EPHEMERAL_MARKER));
        AndroidPackageEvidence evidence = new AndroidPackageEvidence();
        AdapterRegistry adapters = new AdapterRegistry();
        adapters.register(CONTROLLER, Capability.NULLGATE_EPHEMERAL_MARKER,
                new EphemeralMarkerAdapter());
        if (themeMode) {
            allow.put("android", EnumSet.of(Capability.SYSTEM_THEME_SEED_APPLY));
            adapters.register("android", Capability.SYSTEM_THEME_SEED_APPLY,
                    new SystemThemeSeedAdapter(new AndroidSystemThemeBackend(evidence.context())));
        }
        if (colorBlendrMode) {
            allow.put(COLORBLENDR, EnumSet.of(Capability.SHIZUKU_SESSION_START));
            adapters.register(COLORBLENDR, Capability.SHIZUKU_SESSION_START,
                    new ShizukuSessionAdapter(COLORBLENDR,
                            new AndroidShizukuSessionBackend(evidence.context(),
                                    OFFICIAL_SHIZUKU_SIGNER, COLORBLENDR,
                                    COLORBLENDR_SIGNER)));
        }
        BrokerPolicy policy = new BrokerPolicy(
                CONTROLLER, pinnedCert, MAX_LEASE_MILLIS, MAX_ACTIVE_LEASES, allow);
        BrokerEngine engine = new BrokerEngine(policy, SystemClock::elapsedRealtime, (lease, reason) -> {
            System.out.println("audit lease=" + lease.leaseId + " cleanup=" + reason);
        });
        BrokerSession session = new BrokerSession(engine, (caller, request, code) -> {
                System.out.println("audit uid=" + caller.uid
                        + " operation=" + request.operation.name()
                        + " lease=" + request.leaseId
                        + " target=" + (request.targetPackage.isEmpty() ? "-" : request.targetPackage)
                        + " capability=" + (request.capability.isEmpty() ? "-" : request.capability)
                        + " decision=" + code);
                if (System.out.checkError()) throw new IllegalStateException("audit output failed");
        }, new TargetGate() {
            public boolean isInstalled(String targetPackage) {
                return evidence.isInstalled(targetPackage);
            }
            public CapabilityAdapter adapterFor(String targetPackage, Capability capability) {
                return adapters.adapterFor(targetPackage, capability);
            }
        });
        VerifiedCallerResolver resolver = new VerifiedCallerResolver(
                CONTROLLER, pinnedCert, evidence);

        final LocalServerSocket server = new LocalServerSocket(SOCKET);
        final java.util.concurrent.atomic.AtomicReference<LocalSocket> current =
                new java.util.concurrent.atomic.AtomicReference<>();
        final long brokerDeadline = SystemClock.elapsedRealtime() + MAX_BROKER_LIFETIME_MILLIS;
        final java.util.concurrent.atomic.AtomicLong exchangeDeadline =
                new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);
        final java.util.concurrent.atomic.AtomicBoolean stopped =
                new java.util.concurrent.atomic.AtomicBoolean();
        final Object cleanupLock = new Object();
        Runnable cleanup = () -> {
            synchronized (cleanupLock) {
            if (!stopped.compareAndSet(false, true)) return;
            try { server.close(); } catch (Exception ignored) { }
            LocalSocket accepted = current.getAndSet(null);
            if (accepted != null) try { accepted.close(); } catch (Exception ignored) { }
            engine.shutdown();
            int unresolved = engine.activeCount();
            if (unresolved == 0) {
                if (!PID_FILE.delete() && PID_FILE.exists())
                    System.err.println("NullGate could not remove PID file");
            }
            System.out.println("NullGate stopped; unresolved cleanup records=" + unresolved);
            }
        };
        writePid();
        Runtime.getRuntime().addShutdownHook(new Thread(cleanup, "nullgate-shutdown"));
        Thread expiry = new Thread(() -> {
            while (!stopped.get()) {
                SystemClock.sleep(250);
                long now = SystemClock.elapsedRealtime();
                LocalSocket accepted = current.get();
                if (accepted != null && now >= exchangeDeadline.get()) {
                    try { accepted.close(); } catch (Exception ignored) { }
                }
                engine.activeCount();
                if (now >= brokerDeadline) {
                    cleanup.run();
                    System.exit(0);
                }
            }
        }, "nullgate-watchdog");
        expiry.setDaemon(true);
        expiry.start();
        String modeName = themeMode ? "system-theme-v1"
                : colorBlendrMode ? "colorblendr-shizuku-v1" : "marker-test";
        System.out.println("NullGate broker ready; mode=" + modeName
                + "; elapsed deadline 15 minutes; no generic shell");

        try {
            while (true) {
                LocalSocket socket = server.accept();
                exchangeDeadline.set(SystemClock.elapsedRealtime() + 2_000);
                current.set(socket);
                if (stopped.get() || SystemClock.elapsedRealtime() >= brokerDeadline) {
                    socket.close(); break;
                }
                try {
                    socket.setSoTimeout(2_000);
                    Credentials credentials = socket.getPeerCredentials();
                    CallerIdentity caller = resolver.resolve(credentials.getUid());
                    session.handle(
                            new DataInputStream(socket.getInputStream()),
                            new DataOutputStream(socket.getOutputStream()),
                            caller);
                } catch (SecurityException denied) {
                    System.err.println("NullGate denied an unverified local peer");
                } catch (Exception failed) {
                    System.err.println("NullGate rejected a malformed or failed exchange");
                } finally {
                    current.compareAndSet(socket, null);
                    try { socket.close(); } catch (Exception ignored) { }
                }
            }
        } finally {
            cleanup.run();
            System.exit(0); // app_process may have non-daemon Android runtime threads.
        }
    }

    private static void writePid() throws Exception {
        RuntimeFiles.requirePrivateDirectory(RuntimeFiles.ROOT);
        RuntimeFiles.writeExclusive(PID_FILE.getAbsolutePath(),
                (android.os.Process.myPid() + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
