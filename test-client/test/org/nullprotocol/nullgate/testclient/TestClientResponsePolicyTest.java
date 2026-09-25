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
        check(TestClientResponsePolicy.isConfirmedCleanDenial(
                true, decision, "DENIED_BY_USER"));
        check(!TestClientResponsePolicy.isConfirmedCleanDenial(
                true, decision, "CLEANUP_FAILED"));
        check(!TestClientResponsePolicy.isConfirmedCleanDenial(
                true, decision, "DENIED_STALE_CONTROLLER_RESULT"));
        check(TestClientResponsePolicy.isConfirmedCleanReconciliation(
                true, decision, "REVOKED_AFTER_UNCERTAIN_RESULT"));
        check(!TestClientResponsePolicy.isConfirmedCleanReconciliation(
                true, decision, "CLEANUP_FAILED"));
        check(TestClientResponsePolicy.evaluateLeaseResult(false, true, decision,
                "DENIED_BY_USER", null, -1L, 10_000L).outcome
                == TestClientResponsePolicy.Outcome.CLEAN);
        check(TestClientResponsePolicy.evaluateLeaseResult(false, true, decision,
                "CLEANUP_FAILED", null, -1L, 10_000L).outcome
                == TestClientResponsePolicy.Outcome.UNKNOWN);
        check(TestClientResponsePolicy.evaluateLeaseResult(false, true, decision,
                "FUTURE_UNKNOWN_CODE", null, -1L, 10_000L).outcome
                == TestClientResponsePolicy.Outcome.UNKNOWN);
        check(TestClientResponsePolicy.evaluateLeaseResult(true, false, decision,
                "DENIED_BY_USER", null, -1L, 10_000L).outcome
                == TestClientResponsePolicy.Outcome.UNKNOWN);
        check(TestClientResponsePolicy.evaluateReconcileResult(false, true, decision,
                "NOT_FOUND", null, -1L, 10_000L).outcome
                == TestClientResponsePolicy.Outcome.CLEAN);
        check(TestClientResponsePolicy.evaluateLeaseResult(true, false, receipt,
                "GRANTED", LEASE, 70_000L, 10_000L).outcome
                == TestClientResponsePolicy.Outcome.GRANT);
        check(TestClientResponsePolicy.evaluateReconcileResult(true, false, receipt,
                "GRANTED", LEASE, 70_000L, 10_000L).outcome
                == TestClientResponsePolicy.Outcome.UNKNOWN);
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
        System.out.println("NullGate test-client response checks: 22 passed");
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
