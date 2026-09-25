package org.nullprotocol.nullgate.testclient;

import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;
import org.nullprotocol.nullgate.protocol.ExternalClientContract;

/** Pure fail-closed validation for controller Activity results. */
public final class TestClientResponsePolicy {
    private static final Set<String> CONFIRMED_CLEAN_DENIALS = new HashSet<>(Arrays.asList(
            "DENIED_BY_USER", "DENIED_CALLER_CHANGED", "DENIED_RECOVERY_RECORD_FAILED",
            "DENIED_INVALID_CLIENT_REQUEST", "INVALID_TIME_WINDOW",
            "DURATION_EXCEEDS_POLICY", "TARGET_CAPABILITY_DENIED", "TARGET_NOT_INSTALLED",
            "ADAPTER_UNAVAILABLE", "ADAPTER_ACTIVATION_FAILED", "NONCE_REPLAY",
            "LEASE_ID_REUSE", "EXPIRED", "CALLER_PACKAGE_MISMATCH",
            "CALLER_CERTIFICATE_MISMATCH", "CAPACITY_EXHAUSTED", "BROKER_CLOSED"));
    public static final String EXTRA_DECISION = ExternalClientContract.EXTRA_DECISION;
    public static final String EXTRA_LEASE_ID = ExternalClientContract.EXTRA_LEASE_ID;
    public static final String EXTRA_EXPIRES_ELAPSED =
            ExternalClientContract.EXTRA_EXPIRES_ELAPSED;

    public static final class Receipt {
        public final String leaseId;
        public final long expiresElapsed;

        private Receipt(String leaseId, long expiresElapsed) {
            this.leaseId = leaseId;
            this.expiresElapsed = expiresElapsed;
        }
    }

    private TestClientResponsePolicy() { }

    public static String validateDecision(Set<String> keys, String decision,
            boolean leaseResponse) {
        if (keys == null) throw new SecurityException("missing result extras");
        boolean decisionOnly = ExternalClientContract.hasExactDecisionKeys(keys);
        boolean grantedReceipt = ExternalClientContract.hasExactGrantKeys(keys);
        if (leaseResponse ? !decisionOnly && !grantedReceipt : !decisionOnly)
            throw new SecurityException("result schema mismatch");
        if (decision == null || decision.isEmpty())
            throw new SecurityException("missing decision");
        return decision;
    }

    public static Receipt requireFreshGrant(boolean resultOk, Set<String> keys,
            String decision, String leaseId, long expiresElapsed, long nowElapsed) {
        validateDecision(keys, decision, true);
        if (!resultOk || !"GRANTED".equals(decision))
            throw new SecurityException("result is not a grant");
        if (!ExternalClientContract.hasExactGrantKeys(keys))
            throw new SecurityException("grant receipt fields required");
        if (leaseId == null || !leaseId.matches("[A-Za-z0-9_-]{16,128}"))
            throw new SecurityException("invalid lease receipt");
        if (expiresElapsed <= nowElapsed || expiresElapsed - nowElapsed > 60_000L)
            throw new SecurityException("invalid lease expiration");
        return new Receipt(leaseId, expiresElapsed);
    }

    public static boolean isConfirmedRevoke(boolean resultOk, Set<String> keys,
            String decision) {
        validateDecision(keys, decision, false);
        return resultOk && "REVOKED".equals(decision);
    }

    public static boolean isConfirmedCleanDenial(boolean resultCanceled, Set<String> keys,
            String decision) {
        validateDecision(keys, decision, true);
        return resultCanceled && ExternalClientContract.hasExactDecisionKeys(keys)
                && CONFIRMED_CLEAN_DENIALS.contains(decision);
    }

    public static boolean isConfirmedCleanReconciliation(boolean resultCanceled,
            Set<String> keys, String decision) {
        validateDecision(keys, decision, true);
        return resultCanceled && ExternalClientContract.hasExactDecisionKeys(keys)
                && ("NOT_FOUND".equals(decision)
                    || "REVOKED_AFTER_UNCERTAIN_RESULT".equals(decision));
    }
}
