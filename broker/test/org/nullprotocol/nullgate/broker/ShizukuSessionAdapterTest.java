package org.nullprotocol.nullgate.broker;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class ShizukuSessionAdapterTest {
    private static final String TARGET = "com.drdisagree.colorblendr";

    private static final class Backend implements ShizukuSessionAdapter.Backend {
        boolean managerOk = true, targetOk = true, declares = true;
        boolean running, granted, failStart, failStop, failRevoke;
        int grants, starts, forceStops, stops, revokes;
        final Set<String> otherHolders = new HashSet<>();
        public boolean managerIdentityMatches() { return managerOk; }
        public boolean targetIdentityMatches(String target) { return targetOk; }
        public boolean targetDeclaresShizukuPermission(String target) { return declares; }
        public boolean serverRunning() { return running; }
        public boolean permissionGranted(String target) { return granted; }
        public Set<String> packagesHoldingShizukuPermission() {
            Set<String> result = new HashSet<>(otherHolders);
            if (granted) result.add(TARGET);
            return result;
        }
        public void grantPermission(String target) { grants++; granted = true; }
        public void startServer() throws Exception {
            starts++; if (failStart) throw new IOException("start failed"); running = true;
        }
        public void forceStopTarget(String target) { forceStops++; }
        public void stopServer() throws Exception {
            stops++; if (failStop) throw new IOException("stop failed"); running = false;
        }
        public void revokePermission(String target) throws Exception {
            revokes++; if (failRevoke) throw new IOException("revoke failed"); granted = false;
        }
    }

    public static void main(String[] args) throws Exception {
        cleanExclusiveSessionActivatesAndRevokes();
        refusesRunningServer();
        refusesExistingTargetGrant();
        refusesOtherAuthorizedPackages();
        refusesIdentityOrProtocolMismatch();
        failedStartRollsBack();
        failedActivationCleanupRemainsRetryable();
        cleanupFailureIsProvableAndRetryable();
        rejectsWrongCapabilityAndLease();
        System.out.println("NullGate Shizuku adapter tests: 9 passed");
    }

    private static void cleanExclusiveSessionActivatesAndRevokes() throws Exception {
        Backend backend = new Backend(); ShizukuSessionAdapter adapter = adapter(backend);
        LeaseEnvelope lease = lease("lease_0000000001");
        check(adapter.isReady(TARGET)); adapter.activate(lease);
        check(backend.running && backend.granted && backend.grants == 1 && backend.starts == 1);
        adapter.deactivate(lease);
        check(!backend.running && !backend.granted && backend.forceStops == 1
                && backend.stops == 1 && backend.revokes == 1);
    }

    private static void refusesRunningServer() {
        Backend backend = new Backend(); backend.running = true;
        check(!adapter(backend).isReady(TARGET));
    }

    private static void refusesExistingTargetGrant() {
        Backend backend = new Backend(); backend.granted = true;
        check(!adapter(backend).isReady(TARGET));
    }

    private static void refusesOtherAuthorizedPackages() {
        Backend backend = new Backend(); backend.otherHolders.add("org.example.other");
        check(!adapter(backend).isReady(TARGET));
    }

    private static void refusesIdentityOrProtocolMismatch() {
        Backend backend = new Backend(); backend.managerOk = false;
        check(!adapter(backend).isReady(TARGET));
        backend.managerOk = true; backend.targetOk = false;
        check(!adapter(backend).isReady(TARGET));
        backend.targetOk = true; backend.declares = false;
        check(!adapter(backend).isReady(TARGET));
    }

    private static void failedStartRollsBack() {
        Backend backend = new Backend(); backend.failStart = true;
        try { adapter(backend).activate(lease("lease_0000000001")); throw new AssertionError(); }
        catch (Exception expected) { }
        check(!backend.running && !backend.granted && backend.forceStops == 1
                && backend.stops == 1 && backend.revokes == 1);
    }

    private static void failedActivationCleanupRemainsRetryable() throws Exception {
        Backend backend = new Backend(); backend.failStart = true; backend.failRevoke = true;
        ShizukuSessionAdapter adapter = adapter(backend);
        LeaseEnvelope lease = lease("lease_0000000001");
        try { adapter.activate(lease); throw new AssertionError(); }
        catch (Exception expected) { }
        check(backend.granted);
        backend.failStart = false; backend.failRevoke = false;
        adapter.deactivate(lease);
        check(!backend.running && !backend.granted);
    }

    private static void cleanupFailureIsProvableAndRetryable() throws Exception {
        Backend backend = new Backend(); ShizukuSessionAdapter adapter = adapter(backend);
        LeaseEnvelope lease = lease("lease_0000000001"); adapter.activate(lease);
        backend.failStop = true;
        try { adapter.deactivate(lease); throw new AssertionError(); }
        catch (Exception expected) { }
        check(backend.running && !backend.granted);
        backend.failStop = false; adapter.deactivate(lease);
        check(!backend.running && !backend.granted);
    }

    private static void rejectsWrongCapabilityAndLease() throws Exception {
        Backend backend = new Backend(); ShizukuSessionAdapter adapter = adapter(backend);
        LeaseEnvelope wrong = new LeaseEnvelope("lease_0000000001", "nonce_0000000001", TARGET,
                Capability.COLORBLENDR_OVERLAY_APPLY, 10_000, 20_000);
        try { adapter.activate(wrong); throw new AssertionError(); }
        catch (SecurityException expected) { }
        LeaseEnvelope owned = lease("lease_0000000002"); adapter.activate(owned);
        try { adapter.deactivate(lease("lease_0000000003")); throw new AssertionError(); }
        catch (SecurityException expected) { }
        adapter.deactivate(owned);
    }

    private static ShizukuSessionAdapter adapter(Backend backend) {
        return new ShizukuSessionAdapter(TARGET, backend);
    }
    private static LeaseEnvelope lease(String id) {
        return new LeaseEnvelope(id, "nonce_0000000001", TARGET,
                Capability.SHIZUKU_SESSION_START, 10_000, 20_000);
    }
    private static void check(boolean value) {
        if (!value) throw new AssertionError("Shizuku adapter assertion failed");
    }
}
