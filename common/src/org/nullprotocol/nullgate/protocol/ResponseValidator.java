package org.nullprotocol.nullgate.protocol;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Reject spoofed/misrouted or operation-inconsistent replies, never infer success. */
public final class ResponseValidator {
    private static final Set<String> COMMON = new HashSet<>(Arrays.asList(
            "CALLER_PACKAGE_MISMATCH", "CALLER_CERTIFICATE_MISMATCH",
            "CAPACITY_EXHAUSTED", "CLEANUP_FAILED", "BROKER_CLOSED"));
    private static final Set<String> ISSUE = new HashSet<>(Arrays.asList(
            "GRANTED", "INVALID_TIME_WINDOW", "DURATION_EXCEEDS_POLICY",
            "TARGET_CAPABILITY_DENIED", "TARGET_NOT_INSTALLED", "ADAPTER_UNAVAILABLE",
            "ADAPTER_ACTIVATION_FAILED", "NONCE_REPLAY", "LEASE_ID_REUSE", "EXPIRED"));
    private static final Set<String> REVOKE = new HashSet<>(Arrays.asList("REVOKED", "NOT_FOUND"));
    public static void requireMatches(BrokerRequest request, BrokerResponse response) throws IOException {
        if (!request.leaseId.equals(response.leaseId))
            throw new IOException("reply lease mismatch");
        Set<String> allowed = request.operation == BrokerRequest.Operation.ISSUE ? ISSUE : REVOKE;
        if (!allowed.contains(response.code) && !COMMON.contains(response.code))
            throw new IOException("unexpected reply code");
    }
}
