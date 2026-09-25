package org.nullprotocol.nullgate.broker;

import org.nullprotocol.nullgate.protocol.CapabilityPayload;

/** Reversible system-theme seed lease; owns and restores the exact prior setting value. */
public final class SystemThemeSeedAdapter implements CapabilityAdapter {
    public interface Backend {
        boolean available() throws Exception;
        String snapshot() throws Exception;
        void recordSnapshot(String snapshot) throws Exception;
        void apply(int seedArgb, CapabilityPayload.ThemeStyle style) throws Exception;
        boolean matches(int seedArgb, CapabilityPayload.ThemeStyle style) throws Exception;
        void restore(String snapshot) throws Exception;
        boolean matchesSnapshot(String snapshot) throws Exception;
        void clearSnapshotRecord() throws Exception;
    }

    private final Backend backend;
    private String ownedLeaseId;
    private String prior;
    private boolean baselineCaptured;
    private boolean snapshotWriteAttempted;
    private boolean snapshotRecordOwned;
    private boolean mutationMayHaveOccurred;

    public SystemThemeSeedAdapter(Backend backend) { this.backend = backend; }

    @Override public synchronized boolean isReady(String targetPackage) {
        if (!"android".equals(targetPackage) || ownedLeaseId != null) return false;
        try { return backend.available(); }
        catch (Exception unavailable) { return false; }
    }

    @Override public synchronized void activate(LeaseEnvelope lease) throws Exception {
        requireLease(lease);
        if (!isReady(lease.targetPackage)) throw new SecurityException("system theme backend unavailable");
        ownedLeaseId = lease.leaseId;
        try {
            prior = backend.snapshot();
            baselineCaptured = true;
            snapshotWriteAttempted = true;
            backend.recordSnapshot(prior);
            snapshotRecordOwned = true;
            mutationMayHaveOccurred = true;
            backend.apply(lease.payload.seedArgb, lease.payload.themeStyle);
            if (!backend.matches(lease.payload.seedArgb, lease.payload.themeStyle))
                throw new SecurityException("system theme change could not be verified");
        } catch (Exception failure) {
            if (!baselineCaptured) {
                resetOwnership();
                throw failure;
            }
            try {
                cleanupOwnedState();
            } catch (Exception cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    @Override public synchronized void deactivate(LeaseEnvelope lease) throws Exception {
        requireLease(lease);
        if (ownedLeaseId == null) return;
        if (!ownedLeaseId.equals(lease.leaseId)) throw new SecurityException("lease does not own theme state");
        cleanupOwnedState();
    }

    private void cleanupOwnedState() throws Exception {
        if (!baselineCaptured)
            throw new IllegalStateException("theme baseline was never captured");
        if (snapshotWriteAttempted && !snapshotRecordOwned)
            throw new SecurityException("theme snapshot receipt ownership is unconfirmed");
        if (mutationMayHaveOccurred) {
            backend.restore(prior);
            if (!backend.matchesSnapshot(prior))
                throw new SecurityException("system theme restoration could not be verified");
        }
        if (snapshotRecordOwned) backend.clearSnapshotRecord();
        resetOwnership();
    }

    private void resetOwnership() {
        ownedLeaseId = null;
        prior = null;
        baselineCaptured = false;
        snapshotWriteAttempted = false;
        snapshotRecordOwned = false;
        mutationMayHaveOccurred = false;
    }

    private static void requireLease(LeaseEnvelope lease) {
        if (!"android".equals(lease.targetPackage)
                || lease.capability != Capability.SYSTEM_THEME_SEED_APPLY
                || lease.payload.kind != CapabilityPayload.Kind.SYSTEM_THEME_SEED)
            throw new SecurityException("adapter received incompatible typed parameters");
    }
}
