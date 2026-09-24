package org.nullprotocol.nullgate.broker;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class BrokerEngineTest {
    private static final String CONTROLLER = "org.nullprotocol.nullgate";
    private static final String CERT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String COLORBLENDR = "com.drdisagree.colorblendr";
    private static final String LEASE_1 = "lease_0000000001";
    private static final String LEASE_2 = "lease_0000000002";
    private static final String NONCE_1 = "nonce_0000000001";
    private static final String NONCE_2 = "nonce_0000000002";

    private static final class FakeClock implements BrokerEngine.Clock {
        long now = 10_000;
        public long elapsedRealtimeMillis() { return now; }
    }

    public static void main(String[] args) {
        acceptsAllowlistedLeaseThenRevokes();
        rejectsIdentityMismatch();
        rejectsCapabilityConfusion();
        expiresWithoutPersistence();
        rejectsReplayAndReuse();
        rejectsExcessDuration();
        boundsLifetimeHistory();
        System.out.println("NullGate broker tests: 7 passed");
    }

    private static BrokerPolicy policy() {
        Map<String, Set<Capability>> allow = new HashMap<>();
        allow.put(COLORBLENDR, EnumSet.of(Capability.COLORBLENDR_OVERLAY_APPLY));
        return new BrokerPolicy(CONTROLLER, CERT, 600_000, allow);
    }

    private static CallerIdentity caller() { return new CallerIdentity(10_123, CONTROLLER, CERT); }

    private static LeaseEnvelope lease(String id, String nonce, Capability capability, long expiry) {
        return new LeaseEnvelope(id, nonce, COLORBLENDR, capability, 10_000, expiry);
    }

    private static void acceptsAllowlistedLeaseThenRevokes() {
        FakeClock clock = new FakeClock(); BrokerEngine engine = new BrokerEngine(policy(), clock);
        check(engine.request(TestAdapters.READY, caller(), lease(LEASE_1, NONCE_1, Capability.COLORBLENDR_OVERLAY_APPLY, 20_000)).granted());
        check(engine.isActive(LEASE_1));
        check(engine.revoke(caller(), LEASE_1).code == Decision.Code.REVOKED);
        check(!engine.isActive(LEASE_1));
    }

    private static void rejectsIdentityMismatch() {
        FakeClock clock = new FakeClock(); BrokerEngine engine = new BrokerEngine(policy(), clock);
        CallerIdentity wrong = new CallerIdentity(10_123, "org.attacker.app", CERT);
        check(engine.request(TestAdapters.READY, wrong, lease(LEASE_1, NONCE_1, Capability.COLORBLENDR_OVERLAY_APPLY, 20_000)).code
                == Decision.Code.CALLER_PACKAGE_MISMATCH);
        wrong = new CallerIdentity(10_123, CONTROLLER, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        check(engine.request(TestAdapters.READY, wrong, lease(LEASE_1, NONCE_1, Capability.COLORBLENDR_OVERLAY_APPLY, 20_000)).code
                == Decision.Code.CALLER_CERTIFICATE_MISMATCH);
    }

    private static void rejectsCapabilityConfusion() {
        FakeClock clock = new FakeClock(); BrokerEngine engine = new BrokerEngine(policy(), clock);
        check(engine.request(TestAdapters.READY, caller(), lease(LEASE_1, NONCE_1, Capability.SHIZUKU_SESSION_START, 20_000)).code
                == Decision.Code.TARGET_CAPABILITY_DENIED);
    }

    private static void expiresWithoutPersistence() {
        FakeClock clock = new FakeClock(); BrokerEngine engine = new BrokerEngine(policy(), clock);
        check(engine.request(TestAdapters.READY, caller(), lease(LEASE_1, NONCE_1, Capability.COLORBLENDR_OVERLAY_APPLY, 20_000)).granted());
        clock.now = 20_000;
        check(!engine.isActive(LEASE_1));
        check(engine.activeCount() == 0);
    }

    private static void rejectsReplayAndReuse() {
        FakeClock clock = new FakeClock(); BrokerEngine engine = new BrokerEngine(policy(), clock);
        check(engine.request(TestAdapters.READY, caller(), lease(LEASE_1, NONCE_1, Capability.COLORBLENDR_OVERLAY_APPLY, 20_000)).granted());
        engine.revoke(caller(), LEASE_1);
        check(engine.request(TestAdapters.READY, caller(), lease(LEASE_2, NONCE_1, Capability.COLORBLENDR_OVERLAY_APPLY, 20_000)).code
                == Decision.Code.NONCE_REPLAY);
        check(engine.request(TestAdapters.READY, caller(), lease(LEASE_1, NONCE_2, Capability.COLORBLENDR_OVERLAY_APPLY, 20_000)).code
                == Decision.Code.LEASE_ID_REUSE);
    }

    private static void rejectsExcessDuration() {
        FakeClock clock = new FakeClock(); BrokerEngine engine = new BrokerEngine(policy(), clock);
        check(engine.request(TestAdapters.READY, caller(), lease(LEASE_1, NONCE_1, Capability.COLORBLENDR_OVERLAY_APPLY, 700_001)).code
                == Decision.Code.DURATION_EXCEEDS_POLICY);
    }

    private static void boundsLifetimeHistory() {
        FakeClock clock = new FakeClock(); BrokerEngine engine = new BrokerEngine(policy(), clock);
        for (int i = 0; i < 256; i++) {
            String id = String.format("lease_%012d", i);
            String nonce = String.format("nonce_%012d", i);
            check(engine.request(TestAdapters.READY, caller(), lease(id, nonce, Capability.COLORBLENDR_OVERLAY_APPLY, 20_000)).granted());
            check(engine.revoke(caller(), id).code == Decision.Code.REVOKED);
        }
        check(engine.request(TestAdapters.READY, caller(), lease("lease_999999999999", "nonce_999999999999",
                Capability.COLORBLENDR_OVERLAY_APPLY, 20_000)).code == Decision.Code.CAPACITY_EXHAUSTED);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("broker assertion failed");
    }
}
