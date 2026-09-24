package org.nullprotocol.nullgate.broker;

import org.nullprotocol.nullgate.protocol.BrokerRequest;
import org.nullprotocol.nullgate.protocol.BrokerResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TargetGateTest {
    private static final String CONTROLLER = "org.nullprotocol.nullgate";
    private static final String CERT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TARGET = "com.drdisagree.colorblendr";
    private static final String LEASE = "lease_0000000001";
    private static final String NONCE = "nonce_0000000001";

    public static void main(String[] args) throws Exception {
        rejectsUninstalledTarget();
        rejectsMissingAdapterEvenWhenInstalled();
        acceptsOnlyInstalledTargetWithReadyAdapter();
        System.out.println("NullGate target-gate tests: 3 passed");
    }

    private static void rejectsUninstalledTarget() throws Exception {
        BrokerResponse response = exchange(new TargetGate() {
            public boolean isInstalled(String target) { return false; }
            public CapabilityAdapter adapterFor(String target, Capability cap) { return readyAdapter(); }
        }, new AdapterRegistry());
        check("TARGET_NOT_INSTALLED".equals(response.code));
    }

    private static void rejectsMissingAdapterEvenWhenInstalled() throws Exception {
        BrokerResponse response = exchange(new TargetGate() {
            public boolean isInstalled(String target) { return true; }
            public CapabilityAdapter adapterFor(String target, Capability cap) { return null; }
        }, new AdapterRegistry());
        check("ADAPTER_UNAVAILABLE".equals(response.code));
    }

    private static void acceptsOnlyInstalledTargetWithReadyAdapter() throws Exception {
        AdapterRegistry adapters = new AdapterRegistry();
        adapters.register(TARGET, Capability.COLORBLENDR_OVERLAY_APPLY, readyAdapter());
        BrokerResponse response = exchange(new TargetGate() {
            public boolean isInstalled(String target) { return true; }
            public CapabilityAdapter adapterFor(String target, Capability cap) { return adapters.adapterFor(target, cap); }
        }, adapters);
        check("GRANTED".equals(response.code));
    }

    private static BrokerResponse exchange(TargetGate gate, AdapterRegistry unused) throws Exception {
        Map<String, Set<Capability>> allow = new HashMap<>();
        allow.put(TARGET, EnumSet.of(Capability.COLORBLENDR_OVERLAY_APPLY));
        BrokerEngine engine = new BrokerEngine(new BrokerPolicy(CONTROLLER, CERT, 600_000, allow), () -> 10_000);
        BrokerRequest request = BrokerRequest.issue(LEASE, NONCE, TARGET,
                Capability.COLORBLENDR_OVERLAY_APPLY.name(), 10_000, 20_000);
        ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
        request.writeTo(new DataOutputStream(requestBytes));
        ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
        new BrokerSession(engine, (caller, frame, code) -> { }, gate).handle(
                new DataInputStream(new ByteArrayInputStream(requestBytes.toByteArray())),
                new DataOutputStream(responseBytes), new CallerIdentity(10_123, CONTROLLER, CERT));
        return BrokerResponse.readFrom(new DataInputStream(new ByteArrayInputStream(responseBytes.toByteArray())));
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("target gate assertion failed");
    }

    private static CapabilityAdapter readyAdapter() {
        return new CapabilityAdapter() {
            public boolean isReady(String target) { return true; }
            public void activate(LeaseEnvelope lease) { }
            public void deactivate(LeaseEnvelope lease) { }
        };
    }
}
