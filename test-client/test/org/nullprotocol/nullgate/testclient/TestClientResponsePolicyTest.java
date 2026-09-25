package org.nullprotocol.nullgate.testclient;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class TestClientResponsePolicyTest {
    private static final String LEASE = "lease_0123456789abcdef";

    public static void main(String[] args) {
        Set<String> decision = keys(TestClientResponsePolicy.EXTRA_DECISION);
        Set<String> receipt = keys(TestClientResponsePolicy.EXTRA_DECISION,
                TestClientResponsePolicy.EXTRA_LEASE_ID,
                TestClientResponsePolicy.EXTRA_EXPIRES_ELAPSED);
        check("DENIED_BY_USER".equals(TestClientResponsePolicy.validateDecision(
                decision, "DENIED_BY_USER", true)));
        TestClientResponsePolicy.Receipt accepted =
                TestClientResponsePolicy.requireFreshGrant(
                        true, receipt, "GRANTED", LEASE, 70_000L, 10_000L);
        check(LEASE.equals(accepted.leaseId) && accepted.expiresElapsed == 70_000L);
        check(TestClientResponsePolicy.isConfirmedRevoke(
                true, decision, "REVOKED"));
        denies(() -> TestClientResponsePolicy.validateDecision(
                keys(TestClientResponsePolicy.EXTRA_DECISION, "command"), "GRANTED", true));
        denies(() -> TestClientResponsePolicy.validateDecision(decision, "", true));
        denies(() -> TestClientResponsePolicy.requireFreshGrant(
                false, receipt, "GRANTED", LEASE, 70_000L, 10_000L));
        denies(() -> TestClientResponsePolicy.requireFreshGrant(
                true, receipt, "GRANTED", "short", 70_000L, 10_000L));
        denies(() -> TestClientResponsePolicy.requireFreshGrant(
                true, receipt, "GRANTED", LEASE, 10_000L, 10_000L));
        denies(() -> TestClientResponsePolicy.requireFreshGrant(
                true, receipt, "GRANTED", LEASE, 70_001L, 10_000L));
        System.out.println("NullGate test-client response checks: 9 passed");
    }

    private static Set<String> keys(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static void denies(Runnable request) {
        try { request.run(); throw new AssertionError("response should have been denied"); }
        catch (SecurityException expected) { }
    }

    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
