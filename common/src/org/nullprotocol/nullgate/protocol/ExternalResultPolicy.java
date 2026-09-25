package org.nullprotocol.nullgate.protocol;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Shared fail-closed validation for controller Activity results. */
public final class ExternalResultPolicy {
    public enum Outcome { GRANT, CLEAN, UNKNOWN }

    public static final class Receipt {
        public final String leaseId;
        public final long expiresElapsed;

        private Receipt(String leaseId, long expiresElapsed) {
            this.leaseId = leaseId;
            this.expiresElapsed = expiresElapsed;
        }
    }

    public static final class Evaluation {
        public final Outcome outcome;
        public final Receipt receipt;

        private Evaluation(Outcome outcome, Receipt receipt) {
            this.outcome = outcome;
            this.receipt = receipt;
        }
    }

    private static final Set<String> CONFIRMED_CLEAN_DENIALS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "DENIED_BY_USER", "DENIED_CALLER_CHANGED",
                    "DENIED_RECOVERY_RECORD_FAILED", "DENIED_INVALID_CLIENT_REQUEST",
                    "INVALID_TIME_WINDOW", "DURATION_EXCEEDS_POLICY",
                    "TARGET_CAPABILITY_DENIED", "TARGET_NOT_INSTALLED",
                    "ADAPTER_UNAVAILABLE", "ADAPTER_ACTIVATION_FAILED", "NONCE_REPLAY",
                    "LEASE_ID_REUSE", "EXPIRED", "CALLER_PACKAGE_MISMATCH",
                    "CALLER_CERTIFICATE_MISMATCH", "CAPACITY_EXHAUSTED",
                    "BROKER_CLOSED")));

    private ExternalResultPolicy() { }

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
            String decision, String leaseId, long expiresElapsed, long nowElapsed,
            long maximumLeaseMillis) {
        validateDecision(keys, decision, true);
        if (!resultOk || !"GRANTED".equals(decision))
            throw new SecurityException("result is not a grant");
        if (!ExternalClientContract.hasExactGrantKeys(keys))
            throw new SecurityException("grant receipt fields required");
        if (leaseId == null || !leaseId.matches("[A-Za-z0-9_-]{16,128}"))
            throw new SecurityException("invalid lease receipt");
        if (maximumLeaseMillis <= 0 || expiresElapsed <= nowElapsed
                || expiresElapsed - nowElapsed > maximumLeaseMillis)
            throw new SecurityException("invalid lease expiration");
        return new Receipt(leaseId, expiresElapsed);
    }

    public static boolean isConfirmedRevoke(boolean resultOk, Set<String> keys,
            String decision) {
        validateDecision(keys, decision, false);
        return resultOk && "REVOKED".equals(decision);
    }

    public static boolean isConfirmedCleanDenial(boolean resultCanceled,
            Set<String> keys, String decision) {
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

    public static Evaluation evaluateLeaseResult(boolean resultOk,
            boolean resultCanceled, Set<String> keys, String decision, String leaseId,
            long expiresElapsed, long nowElapsed, long maximumLeaseMillis) {
        try {
            if (resultOk && "GRANTED".equals(decision))
                return new Evaluation(Outcome.GRANT, requireFreshGrant(true, keys,
                        decision, leaseId, expiresElapsed, nowElapsed,
                        maximumLeaseMillis));
            if (isConfirmedCleanDenial(resultCanceled, keys, decision))
                return new Evaluation(Outcome.CLEAN, null);
        } catch (SecurityException invalid) { }
        return new Evaluation(Outcome.UNKNOWN, null);
    }

    public static Evaluation evaluateReconcileResult(boolean resultCanceled,
            Set<String> keys, String decision) {
        try {
            if (isConfirmedCleanReconciliation(resultCanceled, keys, decision))
                return new Evaluation(Outcome.CLEAN, null);
        } catch (SecurityException invalid) { }
        return new Evaluation(Outcome.UNKNOWN, null);
    }
}
