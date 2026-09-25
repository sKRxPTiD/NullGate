package org.nullprotocol.nullgate.testclient;

/** Pure fail-closed lifecycle rules for the external test client's durable state. */
public final class TestClientStatePolicy {
    public static final String CLEAN = "CLEAN";
    public static final String PENDING = "PENDING";
    public static final String ACTIVE = "ACTIVE";
    public static final String UNKNOWN = "UNKNOWN";

    private TestClientStatePolicy() { }

    public static String normalize(String storedPhase, boolean hasLeaseId,
            long expiresElapsed, long nowElapsed) {
        if (ACTIVE.equals(storedPhase))
            return hasLeaseId && expiresElapsed > nowElapsed ? ACTIVE : UNKNOWN;
        if (PENDING.equals(storedPhase) || UNKNOWN.equals(storedPhase))
            return storedPhase;
        if (storedPhase == null && !hasLeaseId) return CLEAN;
        return UNKNOWN;
    }

    public static boolean canRequest(String phase) { return CLEAN.equals(phase); }
    public static boolean needsReconcile(String phase) {
        return PENDING.equals(phase) || UNKNOWN.equals(phase);
    }
}
