package org.nullprotocol.nullgate.broker;

import java.util.Objects;
import org.nullprotocol.nullgate.protocol.CapabilityPayload;

/** Immutable request. It contains no shell string, path, intent URI, or free-form action. */
public final class LeaseEnvelope {
    public final String leaseId;
    public final String nonce;
    public final String targetPackage;
    public final Capability capability;
    public final long issuedAtElapsedMillis;
    public final long expiresAtElapsedMillis;
    public final CapabilityPayload payload;

    public LeaseEnvelope(
            String leaseId,
            String nonce,
            String targetPackage,
            Capability capability,
            long issuedAtElapsedMillis,
            long expiresAtElapsedMillis) {
        this(leaseId, nonce, targetPackage, capability, issuedAtElapsedMillis,
                expiresAtElapsedMillis, CapabilityPayload.none());
    }

    public LeaseEnvelope(String leaseId, String nonce, String targetPackage,
            Capability capability, long issuedAtElapsedMillis, long expiresAtElapsedMillis,
            CapabilityPayload payload) {
        this.leaseId = requireToken("leaseId", leaseId);
        this.nonce = requireToken("nonce", nonce);
        this.targetPackage = requirePackage(targetPackage);
        this.capability = Objects.requireNonNull(capability);
        this.issuedAtElapsedMillis = issuedAtElapsedMillis;
        this.expiresAtElapsedMillis = expiresAtElapsedMillis;
        this.payload = Objects.requireNonNull(payload);
    }

    private static String requireToken(String name, String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new IllegalArgumentException(name + " must be a 16-128 character opaque token");
        }
        return value;
    }

    private static String requirePackage(String value) {
        if (value == null || !("android".equals(value)
                || value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) {
            throw new IllegalArgumentException("invalid target package");
        }
        return value;
    }
}
