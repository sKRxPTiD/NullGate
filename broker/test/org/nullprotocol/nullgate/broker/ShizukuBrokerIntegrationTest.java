package org.nullprotocol.nullgate.broker;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ShizukuBrokerIntegrationTest {
    private static final String CONTROLLER = "org.nullprotocol.nullgate";
    private static final String TARGET = "com.drdisagree.colorblendr";
    private static final String CERT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final CallerIdentity CALLER = new CallerIdentity(10_123, CONTROLLER, CERT);

    private static final class Clock implements BrokerEngine.Clock {
        long now = 10_000; public long elapsedRealtimeMillis() { return now; }
    }
    private static final class Backend implements ShizukuSessionAdapter.Backend {
        boolean running, granted, failStop;
        public boolean managerIdentityMatches() { return true; }
        public boolean targetIdentityMatches(String target) { return TARGET.equals(target); }
        public boolean targetDeclaresShizukuPermission(String target) { return TARGET.equals(target); }
        public boolean serverRunning() { return running; }
        public boolean permissionGranted(String target) { return granted; }
        public Set<String> packagesHoldingShizukuPermission() {
            return granted ? new HashSet<>(Collections.singleton(TARGET)) : Collections.emptySet();
        }
        public void grantPermission(String target) { granted = true; }
        public void startServer() { running = true; }
        public void forceStopTarget(String target) { }
        public void stopServer() throws Exception {
            if (failStop) throw new IOException("stop failed"); running = false;
        }
        public void revokePermission(String target) { granted = false; }
    }

    public static void main(String[] args) {
        revokeRemovesCompatibilitySession();
        expiryRemovesCompatibilitySession();
        failedCleanupRemainsTrackedAndBlocksAdmission();
        System.out.println("NullGate Shizuku broker-integration tests: 3 passed");
    }

    private static void revokeRemovesCompatibilitySession() {
        Clock clock = new Clock(); Backend backend = new Backend(); BrokerEngine engine = engine(clock);
        LeaseEnvelope lease = lease("lease_0000000001", "nonce_0000000001");
        check(engine.request(new ShizukuSessionAdapter(TARGET, backend), CALLER, lease).granted());
        check(engine.revoke(CALLER, lease.leaseId).code == Decision.Code.REVOKED);
        check(!backend.running && !backend.granted && engine.activeCount() == 0);
    }

    private static void expiryRemovesCompatibilitySession() {
        Clock clock = new Clock(); Backend backend = new Backend(); BrokerEngine engine = engine(clock);
        LeaseEnvelope lease = lease("lease_0000000001", "nonce_0000000001");
        check(engine.request(new ShizukuSessionAdapter(TARGET, backend), CALLER, lease).granted());
        clock.now = 20_000;
        check(engine.activeCount() == 0 && !backend.running && !backend.granted);
    }

    private static void failedCleanupRemainsTrackedAndBlocksAdmission() {
        Clock clock = new Clock(); Backend backend = new Backend(); BrokerEngine engine = engine(clock);
        LeaseEnvelope first = lease("lease_0000000001", "nonce_0000000001");
        check(engine.request(new ShizukuSessionAdapter(TARGET, backend), CALLER, first).granted());
        backend.failStop = true;
        check(engine.revoke(CALLER, first.leaseId).code == Decision.Code.CLEANUP_FAILED);
        check(engine.activeCount() == 1 && backend.running);
        LeaseEnvelope second = lease("lease_0000000002", "nonce_0000000002");
        check(engine.request(new ShizukuSessionAdapter(TARGET, new Backend()), CALLER, second).code
                == Decision.Code.CLEANUP_FAILED);
        backend.failStop = false;
        check(engine.revoke(CALLER, first.leaseId).code == Decision.Code.REVOKED);
        check(!backend.running && !backend.granted && engine.activeCount() == 0);
    }

    private static BrokerEngine engine(Clock clock) {
        Map<String, Set<Capability>> allow = new HashMap<>();
        allow.put(TARGET, EnumSet.of(Capability.SHIZUKU_SESSION_START));
        return new BrokerEngine(new BrokerPolicy(CONTROLLER, CERT, 600_000, allow), clock);
    }
    private static LeaseEnvelope lease(String id, String nonce) {
        return new LeaseEnvelope(id, nonce, TARGET, Capability.SHIZUKU_SESSION_START,
                10_000, 20_000);
    }
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Shizuku broker integration assertion failed");
    }
}
