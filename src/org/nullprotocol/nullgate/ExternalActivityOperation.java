package org.nullprotocol.nullgate;

/**
 * Process-local handoff for one result-bound Activity operation.
 *
 * Android retains this object only across configuration recreation. A process
 * restart has no object to recover and must use the durable fail-closed path.
 */
public final class ExternalActivityOperation {
    public enum Kind { REQUEST, REVOKE, RECONCILE }
    public enum EventKind {
        ISSUE_RESULT, CLEANUP_RESULT, ISSUE_TRANSPORT_FAILURE, CLEANUP_TRANSPORT_FAILURE
    }

    public interface Listener { void onOperationChanged(); }

    public static final class Event {
        public final EventKind kind;
        public final String decision;
        public final String leaseId;
        public final long expiresElapsed;
        public final boolean settled;
        private boolean claimed;

        private Event(EventKind kind, String decision, String leaseId,
                long expiresElapsed, boolean settled) {
            this.kind = kind;
            this.decision = decision;
            this.leaseId = leaseId;
            this.expiresElapsed = expiresElapsed;
            this.settled = settled;
        }

        public static Event issue(String decision, String leaseId,
                long expiresElapsed, boolean settled) {
            return new Event(EventKind.ISSUE_RESULT, decision, leaseId,
                    expiresElapsed, settled);
        }

        public static Event cleanup(String decision, String leaseId) {
            return new Event(EventKind.CLEANUP_RESULT, decision, leaseId, -1L, true);
        }

        public static Event issueTransportFailure(String leaseId) {
            return new Event(EventKind.ISSUE_TRANSPORT_FAILURE,
                    null, leaseId, -1L, false);
        }

        public static Event cleanupTransportFailure(String leaseId) {
            return new Event(EventKind.CLEANUP_TRANSPORT_FAILURE,
                    null, leaseId, -1L, false);
        }
    }

    public final Kind kind;
    public final String fingerprint;
    public final long approvalGeneration;
    private boolean started;
    private Event event;
    private Listener listener;
    private boolean recoveryRequired;

    public ExternalActivityOperation(Kind kind, String fingerprint,
            long approvalGeneration) {
        if (kind == null || fingerprint == null || fingerprint.isEmpty())
            throw new IllegalArgumentException("operation identity required");
        this.kind = kind;
        this.fingerprint = fingerprint;
        this.approvalGeneration = approvalGeneration;
    }

    public synchronized boolean matches(Kind expectedKind, String expectedFingerprint) {
        return kind == expectedKind && fingerprint.equals(expectedFingerprint);
    }

    /** Returns false if a recreated UI attempts to dispatch the operation twice. */
    public synchronized boolean begin() {
        if (started) return false;
        started = true;
        return true;
    }

    public synchronized boolean isStarted() { return started; }

    public synchronized void requireRecovery() { recoveryRequired = true; }

    public synchronized boolean isRecoveryRequired() { return recoveryRequired; }

    public void attach(Listener next) {
        boolean notify;
        synchronized (this) {
            listener = next;
            notify = event != null && !event.claimed;
        }
        if (notify && next != null) next.onOperationChanged();
    }

    public synchronized void detach(Listener current) {
        if (listener == current) listener = null;
    }

    public void publish(Event next) {
        Listener notify;
        synchronized (this) {
            if (next == null || event != null)
                throw new IllegalStateException("operation already has a result");
            event = next;
            notify = listener;
        }
        if (notify != null) notify.onOperationChanged();
    }

    public synchronized Event pendingEvent() {
        return event != null && !event.claimed ? event : null;
    }

    /** Claiming happens on Android's main thread, where Activity callbacks serialize. */
    public synchronized boolean claim(Event expected) {
        if (event != expected || expected == null || expected.claimed) return false;
        expected.claimed = true;
        return true;
    }

    /** Allows a claimed issue/failure result to continue as fail-closed cleanup. */
    public synchronized void continueWithCleanup(Event expected) {
        if (event != expected || expected == null || !expected.claimed)
            throw new IllegalStateException("only a claimed result can continue");
        event = null;
    }
}
