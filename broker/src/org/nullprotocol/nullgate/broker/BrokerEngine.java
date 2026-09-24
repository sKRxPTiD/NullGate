package org.nullprotocol.nullgate.broker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Serializes admission, activation and cleanup. Cleanup failure is never a successful revoke. */
public final class BrokerEngine {
    private static final int MAX_LEASES_PER_BROKER_LIFETIME = 256;
    public interface Clock { long elapsedRealtimeMillis(); }
    public interface LeaseLifecycle { void onLeaseRemoved(LeaseEnvelope lease, String reason); }
    private static final class Entry {
        final LeaseEnvelope lease;
        final CapabilityAdapter adapter;
        boolean cleanupPending;
        String reason;
        Entry(LeaseEnvelope lease, CapabilityAdapter adapter) {
            this.lease = lease; this.adapter = adapter;
        }
    }
    private final BrokerPolicy policy;
    private final Clock clock;
    private final LeaseLifecycle lifecycle;
    private final Map<String, Entry> active = new HashMap<>();
    private final Set<String> consumedNonces = new HashSet<>();
    private final Set<String> seenLeaseIds = new HashSet<>();
    private final Set<String> confirmedCleanupIds = new HashSet<>();
    private boolean closed;
    private boolean cleanupFault;

    public BrokerEngine(BrokerPolicy policy, Clock clock) {
        this(policy, clock, (lease, reason) -> { });
    }
    public BrokerEngine(BrokerPolicy policy, Clock clock, LeaseLifecycle lifecycle) {
        this.policy = policy; this.clock = clock; this.lifecycle = lifecycle;
    }

    public synchronized Decision request(CapabilityAdapter adapter, CallerIdentity caller, LeaseEnvelope lease) {
        Decision identityFailure = verifyCaller(caller, lease.leaseId);
        if (identityFailure != null) return identityFailure;
        expireNow();
        if (closed) return result(Decision.Code.BROKER_CLOSED, lease.leaseId);
        if (cleanupFault) return result(Decision.Code.CLEANUP_FAILED, lease.leaseId);
        long now = clock.elapsedRealtimeMillis();
        if (lease.issuedAtElapsedMillis < 0 || lease.issuedAtElapsedMillis > now
                || lease.expiresAtElapsedMillis <= now
                || lease.expiresAtElapsedMillis <= lease.issuedAtElapsedMillis) {
            return result(Decision.Code.INVALID_TIME_WINDOW, lease.leaseId);
        }
        if (lease.expiresAtElapsedMillis - lease.issuedAtElapsedMillis > policy.maximumLeaseMillis)
            return result(Decision.Code.DURATION_EXCEEDS_POLICY, lease.leaseId);
        if (!policy.allows(lease.targetPackage, lease.capability))
            return result(Decision.Code.TARGET_CAPABILITY_DENIED, lease.leaseId);
        if (consumedNonces.contains(lease.nonce)) return result(Decision.Code.NONCE_REPLAY, lease.leaseId);
        if (seenLeaseIds.contains(lease.leaseId)) return result(Decision.Code.LEASE_ID_REUSE, lease.leaseId);
        if (seenLeaseIds.size() >= MAX_LEASES_PER_BROKER_LIFETIME)
            return result(Decision.Code.CAPACITY_EXHAUSTED, lease.leaseId);
        if (adapter == null) return result(Decision.Code.ADAPTER_UNAVAILABLE, lease.leaseId);
        try {
            if (!adapter.isReady(lease.targetPackage))
                return result(Decision.Code.ADAPTER_UNAVAILABLE, lease.leaseId);
        } catch (RuntimeException unavailable) {
            return result(Decision.Code.ADAPTER_UNAVAILABLE, lease.leaseId);
        }
        consumedNonces.add(lease.nonce);
        seenLeaseIds.add(lease.leaseId);
        Entry entry = new Entry(lease, adapter);
        active.put(lease.leaseId, entry); // Track partial activation so rollback cannot be lost.
        try {
            adapter.activate(lease);
        } catch (Exception failed) {
            return result(cleanup(entry, "ACTIVATION_FAILED")
                    ? Decision.Code.ADAPTER_ACTIVATION_FAILED : Decision.Code.CLEANUP_FAILED, lease.leaseId);
        }
        // Activation may consume the entire lease window. Never send a late GRANTED.
        if (clock.elapsedRealtimeMillis() >= lease.expiresAtElapsedMillis) {
            return result(cleanup(entry, "EXPIRED") ? Decision.Code.EXPIRED
                    : Decision.Code.CLEANUP_FAILED, lease.leaseId);
        }
        return result(Decision.Code.GRANTED, lease.leaseId);
    }

    public synchronized Decision revoke(CallerIdentity caller, String leaseId) {
        Decision identityFailure = verifyCaller(caller, leaseId);
        if (identityFailure != null) return identityFailure;
        Entry entry = active.get(leaseId);
        if (entry != null)
            return result(cleanup(entry, "REVOKED") ? Decision.Code.REVOKED
                    : Decision.Code.CLEANUP_FAILED, leaseId);
        if (confirmedCleanupIds.contains(leaseId))
            return result(Decision.Code.REVOKED, leaseId);
        // A revoke that arrives before ISSUE must prevent the delayed ISSUE from resurrecting it.
        if (!seenLeaseIds.contains(leaseId)) {
            if (seenLeaseIds.size() >= MAX_LEASES_PER_BROKER_LIFETIME)
                return result(Decision.Code.CAPACITY_EXHAUSTED, leaseId);
            seenLeaseIds.add(leaseId);
        }
        return result(Decision.Code.NOT_FOUND, leaseId);
    }

    /** Includes unresolved cleanup: zero is never inferred simply because the deadline passed. */
    public synchronized boolean isActive(String leaseId) { expireNow(); return active.containsKey(leaseId); }
    public synchronized int activeCount() { expireNow(); return active.size(); }
    public synchronized void shutdown() {
        closed = true;
        for (Entry entry : active.values().toArray(new Entry[0])) cleanup(entry, "BROKER_SHUTDOWN");
    }

    private void expireNow() {
        long now = clock.elapsedRealtimeMillis();
        for (Entry entry : active.values().toArray(new Entry[0])) {
            if (entry.cleanupPending || entry.lease.expiresAtElapsedMillis <= now)
                cleanup(entry, entry.cleanupPending ? entry.reason : "EXPIRED");
        }
    }
    private boolean cleanup(Entry entry, String reason) {
        entry.cleanupPending = true; entry.reason = reason;
        try {
            entry.adapter.deactivate(entry.lease);
        } catch (Exception failed) {
            cleanupFault = true; // Sticky: restart and reconcile before admitting anything else.
            return false;
        }
        active.remove(entry.lease.leaseId);
        confirmedCleanupIds.add(entry.lease.leaseId);
        try { lifecycle.onLeaseRemoved(entry.lease, reason); }
        catch (RuntimeException auditFailed) { cleanupFault = true; }
        return true;
    }
    private static Decision result(Decision.Code code, String id) { return Decision.of(code, id); }
    private Decision verifyCaller(CallerIdentity caller, String id) {
        if (!policy.controllerPackage.equals(caller.packageName))
            return result(Decision.Code.CALLER_PACKAGE_MISMATCH, id);
        if (!policy.controllerCertificateSha256.equals(caller.certificateSha256))
            return result(Decision.Code.CALLER_CERTIFICATE_MISMATCH, id);
        return null;
    }
}
