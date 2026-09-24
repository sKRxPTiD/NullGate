package org.nullprotocol.nullgate.broker;

/** Fail-closed result returned by the policy and engine. */
public final class Decision {
    public enum Code {
        GRANTED,
        REVOKED,
        NOT_FOUND,
        CALLER_PACKAGE_MISMATCH,
        CALLER_CERTIFICATE_MISMATCH,
        INVALID_TIME_WINDOW,
        DURATION_EXCEEDS_POLICY,
        TARGET_CAPABILITY_DENIED,
        TARGET_NOT_INSTALLED,
        ADAPTER_UNAVAILABLE,
        ADAPTER_ACTIVATION_FAILED,
        CLEANUP_FAILED,
        BROKER_CLOSED,
        NONCE_REPLAY,
        LEASE_ID_REUSE,
        CAPACITY_EXHAUSTED,
        EXPIRED
    }

    public final Code code;
    public final String leaseId;

    private Decision(Code code, String leaseId) {
        this.code = code;
        this.leaseId = leaseId;
    }

    public static Decision of(Code code, String leaseId) { return new Decision(code, leaseId); }
    public boolean granted() { return code == Code.GRANTED; }
}
