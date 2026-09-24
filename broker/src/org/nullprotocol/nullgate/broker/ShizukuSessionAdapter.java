package org.nullprotocol.nullgate.broker;

import java.util.Set;

/**
 * Exclusive, temporary Shizuku compatibility session.
 *
 * <p>This adapter intentionally refuses to adopt a running Shizuku server or an existing
 * authorization. That keeps NullGate from claiming ownership of state it did not create and
 * makes expiry/revocation measurable.</p>
 */
public final class ShizukuSessionAdapter implements CapabilityAdapter {
    public interface Backend {
        boolean managerIdentityMatches() throws Exception;
        boolean targetIdentityMatches(String targetPackage) throws Exception;
        boolean targetDeclaresShizukuPermission(String targetPackage) throws Exception;
        boolean serverRunning() throws Exception;
        boolean permissionGranted(String targetPackage) throws Exception;
        Set<String> packagesHoldingShizukuPermission() throws Exception;
        void grantPermission(String targetPackage) throws Exception;
        void startServer() throws Exception;
        void forceStopTarget(String targetPackage) throws Exception;
        void stopServer() throws Exception;
        void revokePermission(String targetPackage) throws Exception;
    }

    private final String targetPackage;
    private final Backend backend;
    private String ownedLeaseId;

    public ShizukuSessionAdapter(String targetPackage, Backend backend) {
        if (targetPackage == null || backend == null) throw new NullPointerException();
        this.targetPackage = targetPackage;
        this.backend = backend;
    }

    @Override public synchronized boolean isReady(String requestedTarget) {
        if (!targetPackage.equals(requestedTarget) || ownedLeaseId != null) return false;
        try {
            return backend.managerIdentityMatches()
                    && backend.targetIdentityMatches(targetPackage)
                    && backend.targetDeclaresShizukuPermission(targetPackage)
                    && !backend.serverRunning()
                    && !backend.permissionGranted(targetPackage)
                    && backend.packagesHoldingShizukuPermission().isEmpty();
        } catch (Exception unavailable) {
            return false;
        }
    }

    @Override public synchronized void activate(LeaseEnvelope lease) throws Exception {
        requireLease(lease);
        if (ownedLeaseId != null) throw new SecurityException("adapter already owns a session");
        if (!isReady(targetPackage))
            throw new SecurityException("Shizuku preflight is not clean and exclusive");

        // Claim ownership before the first mutation so a failed rollback remains retryable.
        ownedLeaseId = lease.leaseId;
        boolean permissionAttempted = false;
        boolean serverAttempted = false;
        try {
            permissionAttempted = true;
            backend.grantPermission(targetPackage);
            if (!backend.permissionGranted(targetPackage))
                throw new SecurityException("target authorization was not granted");

            serverAttempted = true;
            backend.startServer();
            if (!backend.serverRunning())
                throw new SecurityException("Shizuku server did not become ready");
            Set<String> holders = backend.packagesHoldingShizukuPermission();
            if (holders.size() != 1 || !holders.contains(targetPackage))
                throw new SecurityException("Shizuku authorization is not target-exclusive");
        } catch (Exception failure) {
            Exception cleanupFailure = rollback(permissionAttempted, serverAttempted);
            if (cleanupFailure == null) ownedLeaseId = null;
            else failure.addSuppressed(cleanupFailure);
            throw failure;
        }
    }

    @Override public synchronized void deactivate(LeaseEnvelope lease) throws Exception {
        requireLease(lease);
        if (ownedLeaseId == null) return;
        if (!ownedLeaseId.equals(lease.leaseId))
            throw new SecurityException("lease does not own this Shizuku session");

        Exception failure = rollback(true, true);
        if (failure != null) throw failure;
        ownedLeaseId = null;
    }

    private Exception rollback(boolean permissionAttempted, boolean serverAttempted) {
        Exception first = null;
        try { backend.forceStopTarget(targetPackage); }
        catch (Exception error) { first = error; }
        if (serverAttempted) {
            try { backend.stopServer(); }
            catch (Exception error) { if (first == null) first = error; else first.addSuppressed(error); }
        }
        if (permissionAttempted) {
            try { backend.revokePermission(targetPackage); }
            catch (Exception error) { if (first == null) first = error; else first.addSuppressed(error); }
        }
        try {
            if (backend.serverRunning() || backend.permissionGranted(targetPackage)) {
                Exception error = new SecurityException("Shizuku cleanup could not be proven");
                if (first == null) first = error; else first.addSuppressed(error);
            }
        } catch (Exception error) {
            if (first == null) first = error; else first.addSuppressed(error);
        }
        return first;
    }

    private void requireLease(LeaseEnvelope lease) {
        if (!targetPackage.equals(lease.targetPackage)
                || lease.capability != Capability.SHIZUKU_SESSION_START)
            throw new SecurityException("adapter received the wrong target or capability");
    }
}
