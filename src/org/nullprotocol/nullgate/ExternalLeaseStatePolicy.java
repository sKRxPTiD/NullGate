package org.nullprotocol.nullgate;

/** Pure identity guards for controller record transitions and delayed replies. */
public final class ExternalLeaseStatePolicy {
    private ExternalLeaseStatePolicy() { }

    public static boolean owns(String recordedLeaseId, String recordedPackage,
            int recordedUid, String expectedLeaseId, String expectedPackage,
            int expectedUid) {
        return expectedLeaseId != null && expectedPackage != null
                && expectedLeaseId.equals(recordedLeaseId)
                && expectedPackage.equals(recordedPackage)
                && expectedUid == recordedUid;
    }

    public static boolean canReturnDelayedGrant(String capturedLeaseId,
            String currentLeaseId, String phase, long expiresElapsed, long nowElapsed) {
        return capturedLeaseId != null && capturedLeaseId.equals(currentLeaseId)
                && "ACTIVE".equals(phase) && expiresElapsed > nowElapsed;
    }
}
