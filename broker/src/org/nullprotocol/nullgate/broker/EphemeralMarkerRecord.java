package org.nullprotocol.nullgate.broker;

import java.nio.charset.StandardCharsets;

/** Pure validation and serialization for the harmless root-side lifecycle marker. */
public final class EphemeralMarkerRecord {
    private static final String CONTROLLER = "org.nullprotocol.nullgate";

    private EphemeralMarkerRecord() { }

    public static String fileName(LeaseEnvelope lease) {
        requireSelfTestLease(lease);
        return lease.leaseId + ".lease";
    }

    public static byte[] content(LeaseEnvelope lease) {
        requireSelfTestLease(lease);
        return ("lease=" + lease.leaseId + "\nexpiresElapsed="
                + lease.expiresAtElapsedMillis + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static void requireSelfTestLease(LeaseEnvelope lease) {
        if (!CONTROLLER.equals(lease.targetPackage)
                || lease.capability != Capability.NULLGATE_EPHEMERAL_MARKER) {
            throw new SecurityException("adapter received a non-self-test lease");
        }
    }
}
