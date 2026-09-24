package org.nullprotocol.nullgate.broker;

import org.nullprotocol.nullgate.protocol.BrokerRequest;
import org.nullprotocol.nullgate.protocol.BrokerResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class BrokerProtocolTest {
    private static final String CONTROLLER = "org.nullprotocol.nullgate";
    private static final String CERT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TARGET = "com.drdisagree.colorblendr";
    private static final String LEASE = "lease_0000000001";
    private static final String NONCE = "nonce_0000000001";

    public static void main(String[] args) throws Exception {
        issueRoundTripAndSessionGrant();
        revokeRequiresVerifiedIdentity();
        rejectsOversizedFrame();
        auditsEveryParsedDecision();
        activationFailureRevokesLease();
        typedThemePayloadRoundTrips();
        typedPayloadCannotRetargetCapability();
        System.out.println("NullGate protocol tests: 7 passed");
    }

    private static void issueRoundTripAndSessionGrant() throws Exception {
        BrokerRequest request = BrokerRequest.issue(LEASE, NONCE, TARGET,
                Capability.COLORBLENDR_OVERLAY_APPLY.name(), 10_000, 20_000);
        BrokerResponse response = exchange(request, caller(CONTROLLER, CERT), engine());
        check("GRANTED".equals(response.code));
        check(LEASE.equals(response.leaseId));
    }

    private static void revokeRequiresVerifiedIdentity() throws Exception {
        BrokerEngine engine = engine();
        check("GRANTED".equals(exchange(BrokerRequest.issue(LEASE, NONCE, TARGET,
                Capability.COLORBLENDR_OVERLAY_APPLY.name(), 10_000, 20_000), caller(CONTROLLER, CERT), engine).code));
        BrokerResponse denied = exchange(BrokerRequest.revoke(LEASE), caller("org.attacker.app", CERT), engine);
        check("CALLER_PACKAGE_MISMATCH".equals(denied.code));
        check(engine.isActive(LEASE));
        check("REVOKED".equals(exchange(BrokerRequest.revoke(LEASE), caller(CONTROLLER, CERT), engine).code));
    }

    private static void rejectsOversizedFrame() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x4e474154); out.writeByte(2); out.writeByte(1); out.writeShort(257);
        try {
            BrokerRequest.readFrom(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            throw new AssertionError("oversized field accepted");
        } catch (IOException expected) { }
    }

    private static void typedThemePayloadRoundTrips() throws Exception {
        BrokerRequest request = BrokerRequest.issueSystemTheme(LEASE, NONCE, 10_000, 20_000,
                0xff76543a, org.nullprotocol.nullgate.protocol.CapabilityPayload.ThemeStyle.TONAL_SPOT);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request.writeTo(new DataOutputStream(bytes));
        BrokerRequest decoded = BrokerRequest.readFrom(new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray())));
        check("android".equals(decoded.targetPackage));
        check("SYSTEM_THEME_SEED_APPLY".equals(decoded.capability));
        check(decoded.payload.seedArgb == 0xff76543a);
        check(decoded.payload.themeStyle
                == org.nullprotocol.nullgate.protocol.CapabilityPayload.ThemeStyle.TONAL_SPOT);
    }

    private static void typedPayloadCannotRetargetCapability() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x4e474154); out.writeByte(2); out.writeByte(0);
        writeRaw(out, LEASE); writeRaw(out, NONCE); writeRaw(out, TARGET);
        writeRaw(out, Capability.COLORBLENDR_OVERLAY_APPLY.name());
        out.writeLong(10_000); out.writeLong(20_000); out.writeByte(1); out.writeInt(0xff76543a); out.writeByte(0);
        try { BrokerRequest.readFrom(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            throw new AssertionError("typed payload retargeted"); } catch (IOException expected) { }
    }

    private static void writeRaw(DataOutputStream out, String value) throws Exception {
        byte[] data=value.getBytes(java.nio.charset.StandardCharsets.UTF_8); out.writeShort(data.length); out.write(data);
    }

    private static void auditsEveryParsedDecision() throws Exception {
        ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
        BrokerRequest request = BrokerRequest.issue(LEASE, NONCE, TARGET,
                Capability.COLORBLENDR_OVERLAY_APPLY.name(), 10_000, 20_000);
        request.writeTo(new DataOutputStream(requestBytes));
        ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
        final int[] records = { 0 };
        new BrokerSession(engine(), (caller, auditedRequest, code) -> {
            check(caller.uid == 10_123);
            check(LEASE.equals(auditedRequest.leaseId));
            check("GRANTED".equals(code));
            records[0]++;
        }, TestAdapters.GATE).handle(new DataInputStream(new ByteArrayInputStream(requestBytes.toByteArray())),
                new DataOutputStream(responseBytes), caller(CONTROLLER, CERT));
        check(records[0] == 1);
    }

    private static void activationFailureRevokesLease() throws Exception {
        BrokerEngine engine = engine();
        BrokerRequest request = BrokerRequest.issue(LEASE, NONCE, TARGET,
                Capability.COLORBLENDR_OVERLAY_APPLY.name(), 10_000, 20_000);
        ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
        request.writeTo(new DataOutputStream(requestBytes));
        ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
        TargetGate gate = new TargetGate() {
            public boolean isInstalled(String target) { return true; }
            public CapabilityAdapter adapterFor(String target, Capability capability) {
                return new CapabilityAdapter() {
                    public boolean isReady(String packageName) { return true; }
                    public void activate(LeaseEnvelope lease) throws Exception {
                        throw new IOException("simulated partial activation failure");
                    }
                    public void deactivate(LeaseEnvelope lease) { }
                };
            }
        };
        new BrokerSession(engine, (caller, frame, code) -> { }, gate).handle(
                new DataInputStream(new ByteArrayInputStream(requestBytes.toByteArray())),
                new DataOutputStream(responseBytes), caller(CONTROLLER, CERT));
        BrokerResponse response = BrokerResponse.readFrom(
                new DataInputStream(new ByteArrayInputStream(responseBytes.toByteArray())));
        check("ADAPTER_ACTIVATION_FAILED".equals(response.code));
        check(!engine.isActive(LEASE));
    }

    private static BrokerResponse exchange(BrokerRequest request, CallerIdentity caller, BrokerEngine engine)
            throws Exception {
        ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
        request.writeTo(new DataOutputStream(requestBytes));
        ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
        new BrokerSession(engine, (identity, frame, code) -> { }, TestAdapters.GATE).handle(
                new DataInputStream(new ByteArrayInputStream(requestBytes.toByteArray())),
                new DataOutputStream(responseBytes), caller);
        return BrokerResponse.readFrom(new DataInputStream(new ByteArrayInputStream(responseBytes.toByteArray())));
    }

    private static BrokerEngine engine() {
        Map<String, Set<Capability>> allow = new HashMap<>();
        allow.put(TARGET, EnumSet.of(Capability.COLORBLENDR_OVERLAY_APPLY));
        BrokerPolicy policy = new BrokerPolicy(CONTROLLER, CERT, 600_000, allow);
        return new BrokerEngine(policy, () -> 10_000);
    }

    private static CallerIdentity caller(String packageName, String cert) {
        return new CallerIdentity(10_123, packageName, cert);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("protocol assertion failed");
    }
}
