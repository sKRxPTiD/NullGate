package org.nullprotocol.nullgate;

import java.util.UUID;

/** A controller-side request only; a rooted broker must independently verify it. */
public final class LeaseRequest {
    public final String id;
    public final String nonce;
    public final String targetPackage;
    public final String capability;
    public final long issuedAtElapsedMillis;
    public final long expiresAtElapsedMillis;
    public final int seedArgb;
    public final String themeStyle;

    LeaseRequest(String id, String nonce, String targetPackage, String capability,
            long issuedAtElapsedMillis, long expiresAtElapsedMillis) {
        this(id, nonce, targetPackage, capability, issuedAtElapsedMillis,
                expiresAtElapsedMillis, 0, "");
    }

    LeaseRequest(String id, String nonce, String targetPackage, String capability,
            long issuedAtElapsedMillis, long expiresAtElapsedMillis, int seedArgb,
            String themeStyle) {
        this.id = id;
        this.nonce = nonce;
        this.targetPackage = targetPackage;
        this.capability = capability;
        this.issuedAtElapsedMillis = issuedAtElapsedMillis;
        this.expiresAtElapsedMillis = expiresAtElapsedMillis;
        this.seedArgb = seedArgb;
        this.themeStyle = themeStyle;
    }

    public static LeaseRequest forTarget(String targetPackage, String capability, long elapsedNow,
            long durationMillis) {
        if (durationMillis <= 0 || durationMillis > 10 * 60 * 1000L) {
            throw new IllegalArgumentException("duration must be between one millisecond and ten minutes");
        }
        return new LeaseRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                targetPackage,
                capability,
                elapsedNow,
                elapsedNow + durationMillis);
    }

    public static LeaseRequest forSystemTheme(int seedArgb, String style, long elapsedNow,
            long durationMillis) {
        if (durationMillis <= 0 || durationMillis > 10 * 60 * 1000L)
            throw new IllegalArgumentException("invalid duration");
        org.nullprotocol.nullgate.protocol.CapabilityPayload.ThemeStyle.valueOf(style);
        if ((seedArgb >>> 24) != 0xff) throw new IllegalArgumentException("seed must be opaque");
        return new LeaseRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "android", "SYSTEM_THEME_SEED_APPLY", elapsedNow, elapsedNow + durationMillis,
                seedArgb, style);
    }

    public String display(String state) {
        long seconds = (expiresAtElapsedMillis - issuedAtElapsedMillis) / 1000L;
        return "Lease request: " + id + "\nTarget: " + targetPackage
                + "\nCapability: " + capability + "\nDuration: " + seconds + " seconds\n\n"
                + "Status: " + state;
    }
}
