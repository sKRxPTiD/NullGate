package org.nullprotocol.nullgate.broker;

import java.util.Objects;

/** Identity facts supplied by a trusted platform verifier, never by request data. */
public final class CallerIdentity {
    public final int uid;
    public final String packageName;
    public final String certificateSha256;

    public CallerIdentity(int uid, String packageName, String certificateSha256) {
        this.uid = uid;
        this.packageName = Objects.requireNonNull(packageName);
        this.certificateSha256 = normalizeDigest(certificateSha256);
    }

    static String normalizeDigest(String value) {
        String digest = Objects.requireNonNull(value);
        if (!digest.matches("[0-9a-fA-F]{64}|(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}"))
            throw new IllegalArgumentException("expected SHA-256 certificate digest");
        return digest.replace(":", "").toLowerCase(java.util.Locale.ROOT);
    }
}
