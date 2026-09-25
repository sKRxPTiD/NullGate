package org.nullprotocol.nullgate.testclient;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.nullprotocol.nullgate.protocol.ExternalClientContract;
import org.nullprotocol.nullgate.protocol.ExternalResultPolicy;

public final class TestClientResponsePolicyTest {
    private static final String LEASE = "lease_0123456789abcdef";

    public static void main(String[] args) {
        Set<String> decision = keys(ExternalClientContract.EXTRA_DECISION);
        Set<String> receipt = keys(ExternalClientContract.EXTRA_DECISION,
                ExternalClientContract.EXTRA_LEASE_ID,
                ExternalClientContract.EXTRA_EXPIRES_ELAPSED);
        check("DENIED_BY_USER".equals(ExternalResultPolicy.validateDecision(
                decision, "DENIED_BY_USER", true)));
        ExternalResultPolicy.Receipt accepted =
                ExternalResultPolicy.requireFreshGrant(
                        true, receipt, "GRANTED", LEASE, 70_000L, 10_000L, 60_000L);
        check(LEASE.equals(accepted.leaseId) && accepted.expiresElapsed == 70_000L);
        check(ExternalResultPolicy.isConfirmedRevoke(
                true, decision, "REVOKED"));
        check(ExternalResultPolicy.isConfirmedCleanDenial(
                true, decision, "DENIED_BY_USER"));
        check(!ExternalResultPolicy.isConfirmedCleanDenial(
                true, decision, "CLEANUP_FAILED"));
        check(!ExternalResultPolicy.isConfirmedCleanDenial(
                true, decision, "DENIED_STALE_CONTROLLER_RESULT"));
        check(ExternalResultPolicy.isConfirmedCleanReconciliation(
                true, decision, "REVOKED_AFTER_UNCERTAIN_RESULT"));
        check(!ExternalResultPolicy.isConfirmedCleanReconciliation(
                true, decision, "CLEANUP_FAILED"));
        check(ExternalResultPolicy.evaluateLeaseResult(false, true, decision,
                "DENIED_BY_USER", null, -1L, 10_000L, 60_000L).outcome
                == ExternalResultPolicy.Outcome.CLEAN);
        check(ExternalResultPolicy.evaluateLeaseResult(false, true, decision,
                "CLEANUP_FAILED", null, -1L, 10_000L, 60_000L).outcome
                == ExternalResultPolicy.Outcome.UNKNOWN);
        check(ExternalResultPolicy.evaluateLeaseResult(false, true, decision,
                "FUTURE_UNKNOWN_CODE", null, -1L, 10_000L, 60_000L).outcome
                == ExternalResultPolicy.Outcome.UNKNOWN);
        check(ExternalResultPolicy.evaluateLeaseResult(true, false, decision,
                "DENIED_BY_USER", null, -1L, 10_000L, 60_000L).outcome
                == ExternalResultPolicy.Outcome.UNKNOWN);
        check(ExternalResultPolicy.evaluateReconcileResult(true, decision,
                "NOT_FOUND").outcome == ExternalResultPolicy.Outcome.CLEAN);
        check(ExternalResultPolicy.evaluateLeaseResult(true, false, receipt,
                "GRANTED", LEASE, 70_000L, 10_000L, 60_000L).outcome
                == ExternalResultPolicy.Outcome.GRANT);
        check(ExternalResultPolicy.evaluateReconcileResult(false, receipt,
                "GRANTED").outcome == ExternalResultPolicy.Outcome.UNKNOWN);
        denies(() -> ExternalResultPolicy.validateDecision(
                keys(ExternalClientContract.EXTRA_DECISION, "command"), "GRANTED", true));
        denies(() -> ExternalResultPolicy.validateDecision(decision, "", true));
        denies(() -> ExternalResultPolicy.requireFreshGrant(
                false, receipt, "GRANTED", LEASE, 70_000L, 10_000L, 60_000L));
        denies(() -> ExternalResultPolicy.requireFreshGrant(
                true, receipt, "GRANTED", "short", 70_000L, 10_000L, 60_000L));
        denies(() -> ExternalResultPolicy.requireFreshGrant(
                true, receipt, "GRANTED", LEASE, 10_000L, 10_000L, 60_000L));
        denies(() -> ExternalResultPolicy.requireFreshGrant(
                true, receipt, "GRANTED", LEASE, 70_001L, 10_000L, 60_000L));
        denies(() -> ExternalResultPolicy.requireFreshGrant(
                true, receipt, "GRANTED", LEASE, 70_000L, 10_000L, 0L));
        check(ExternalResultPolicy.evaluateLeaseResult(true, false, receipt,
                "GRANTED", LEASE, 70_000L, 10_000L, 30_000L).outcome
                == ExternalResultPolicy.Outcome.UNKNOWN);
        System.out.println("NullGate test-client response checks: 24 passed");
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
