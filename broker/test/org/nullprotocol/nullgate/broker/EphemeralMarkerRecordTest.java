package org.nullprotocol.nullgate.broker;

import java.nio.charset.StandardCharsets;

public final class EphemeralMarkerRecordTest {
    private static final String ID = "lease_0000000001";
    private static final String NONCE = "nonce_0000000001";

    public static void main(String[] args) {
        serializesBoundedMarker();
        rejectsAnotherTarget();
        rejectsAnotherCapability();
        System.out.println("NullGate marker-record tests: 3 passed");
    }

    private static void serializesBoundedMarker() {
        LeaseEnvelope lease = lease("org.nullprotocol.nullgate", Capability.NULLGATE_EPHEMERAL_MARKER);
        check((ID + ".lease").equals(EphemeralMarkerRecord.fileName(lease)));
        String content = new String(EphemeralMarkerRecord.content(lease), StandardCharsets.UTF_8);
        check(content.equals("lease=" + ID + "\nexpiresElapsed=20000\n"));
        check(content.length() < 128);
    }

    private static void rejectsAnotherTarget() {
        expectDenied(() -> EphemeralMarkerRecord.fileName(
                lease("com.drdisagree.colorblendr", Capability.NULLGATE_EPHEMERAL_MARKER)));
    }

    private static void rejectsAnotherCapability() {
        expectDenied(() -> EphemeralMarkerRecord.content(
                lease("org.nullprotocol.nullgate", Capability.COLORBLENDR_OVERLAY_APPLY)));
    }

    private static LeaseEnvelope lease(String target, Capability capability) {
        return new LeaseEnvelope(ID, NONCE, target, capability, 10_000, 20_000);
    }

    private static void expectDenied(Runnable call) {
        try { call.run(); throw new AssertionError("invalid marker lease accepted"); }
        catch (SecurityException expected) { }
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("marker assertion failed");
    }
}
