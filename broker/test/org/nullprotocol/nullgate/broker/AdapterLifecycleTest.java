package org.nullprotocol.nullgate.broker;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AdapterLifecycleTest {
    private static final String CONTROLLER = "org.nullprotocol.nullgate";
    private static final String CERT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TARGET = "com.drdisagree.colorblendr";
    private static final CallerIdentity CALLER = new CallerIdentity(10_123, CONTROLLER, CERT);

    public static void main(String[] args) {
        deactivatesOnRevoke();
        deactivatesOnExpiry();
        deactivatesOnShutdown();
        System.out.println("NullGate adapter-lifecycle tests: 3 passed");
    }

    private static void deactivatesOnRevoke() {
        FakeClock clock = new FakeClock(); Counter counter = new Counter();
        BrokerEngine engine = engine(clock, counter);
        LeaseEnvelope lease = lease("lease_0000000001", "nonce_0000000001", 20_000);
        check(engine.request(TestAdapters.READY, CALLER, lease).granted());
        check(engine.revoke(CALLER, lease.leaseId).code == Decision.Code.REVOKED);
        check(counter.removed == 1 && "REVOKED".equals(counter.reason));
    }

    private static void deactivatesOnExpiry() {
        FakeClock clock = new FakeClock(); Counter counter = new Counter();
        BrokerEngine engine = engine(clock, counter);
        check(engine.request(TestAdapters.READY, CALLER, lease("lease_0000000001", "nonce_0000000001", 20_000)).granted());
        clock.now = 20_000; check(engine.activeCount() == 0);
        check(counter.removed == 1 && "EXPIRED".equals(counter.reason));
    }

    private static void deactivatesOnShutdown() {
        FakeClock clock = new FakeClock(); Counter counter = new Counter();
        BrokerEngine engine = engine(clock, counter);
        check(engine.request(TestAdapters.READY, CALLER, lease("lease_0000000001", "nonce_0000000001", 20_000)).granted());
        engine.shutdown();
        check(counter.removed == 1 && "BROKER_SHUTDOWN".equals(counter.reason));
    }

    private static BrokerEngine engine(FakeClock clock, Counter counter) {
        Map<String, Set<Capability>> allow = new HashMap<>();
        allow.put(TARGET, EnumSet.of(Capability.COLORBLENDR_OVERLAY_APPLY));
        return new BrokerEngine(new BrokerPolicy(CONTROLLER, CERT, 600_000, allow), clock,
                (lease, reason) -> { counter.removed++; counter.reason = reason; });
    }

    private static LeaseEnvelope lease(String id, String nonce, long expiry) {
        return new LeaseEnvelope(id, nonce, TARGET, Capability.COLORBLENDR_OVERLAY_APPLY,
                10_000, expiry);
    }

    private static final class FakeClock implements BrokerEngine.Clock {
        long now = 10_000; public long elapsedRealtimeMillis() { return now; }
    }
    private static final class Counter { int removed; String reason; }
    private static void check(boolean value) { if (!value) throw new AssertionError("lifecycle assertion failed"); }
}
