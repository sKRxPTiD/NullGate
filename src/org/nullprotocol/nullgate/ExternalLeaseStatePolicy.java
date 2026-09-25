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

    public static boolean canSettle(String capturedLeaseId, String currentLeaseId,
            String phase, long capturedGeneration, long currentGeneration) {
        return capturedLeaseId != null && capturedLeaseId.equals(currentLeaseId)
                && "SUBMITTING".equals(phase)
                && capturedGeneration == currentGeneration;
    }

    public static boolean canDeliverGrant(String capturedLeaseId, String currentLeaseId,
            String phase, long capturedGeneration, long currentGeneration,
            long capturedExpiry, long currentExpiry, long nowElapsed) {
        return capturedLeaseId != null && capturedLeaseId.equals(currentLeaseId)
                && "ACTIVE".equals(phase)
                && capturedGeneration == currentGeneration
                && capturedExpiry == currentExpiry && currentExpiry > nowElapsed;
    }
}
