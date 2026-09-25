package org.nullprotocol.nullgate;

import org.nullprotocol.nullgate.protocol.CapabilityPayload;

/** Fail-closed admission for app-to-controller requests; contains no Android trust inference. */
public final class ExternalClientPolicy {
    public static final int PROTOCOL_VERSION = 1;
    public static final long MAX_DURATION_MILLIS = 10L * 60L * 1000L;
    public static final String COLORBLENDR_PACKAGE = "com.drdisagree.colorblendr";
    public static final String COLORBLENDR_SIGNER =
            "4af4ffa12ce90815a1775c4604ea16c19f5bed2a6e09ae7c3c92815982af052e";
    public static final long COLORBLENDR_VERSION_CODE = 42L;
    public static final String TEST_CLIENT_PACKAGE =
            "org.nullprotocol.nullgate.testclient";
    public static final long TEST_CLIENT_VERSION_CODE = 1L;

    public static final class ApprovedThemeRequest {
        public final String clientPackage;
        public final int clientUid;
        public final int seedArgb;
        public final CapabilityPayload.ThemeStyle style;
        public final long durationMillis;

        private ApprovedThemeRequest(String clientPackage, int clientUid, int seedArgb,
                CapabilityPayload.ThemeStyle style, long durationMillis) {
            this.clientPackage = clientPackage;
            this.clientUid = clientUid;
            this.seedArgb = seedArgb;
            this.style = style;
            this.durationMillis = durationMillis;
        }
    }

    private ExternalClientPolicy() { }

    public static void authorizeClient(int protocolVersion, boolean launchedForResult,
            int callingUid, String claimedPackage, long clientVersionCode,
            String[] packagesForUid,
            String[] currentSignerDigests) {
        authorizeClient(protocolVersion, launchedForResult, callingUid, claimedPackage,
                clientVersionCode, packagesForUid, currentSignerDigests, null);
    }

    public static void authorizeClient(int protocolVersion, boolean launchedForResult,
            int callingUid, String claimedPackage, long clientVersionCode,
            String[] packagesForUid, String[] currentSignerDigests,
            String controllerSignerDigest) {
        if (protocolVersion != PROTOCOL_VERSION)
            throw new SecurityException("unsupported client protocol");
        if (!launchedForResult) throw new SecurityException("verified result caller required");
        if (callingUid < 10_000 || callingUid > 19_999)
            throw new SecurityException("owner-user ordinary app UID required");
        ExternalClientRegistry.Registration registration =
                ExternalClientRegistry.require(claimedPackage);
        String expectedSigner = registration.expectedSigner(controllerSignerDigest);
        if (expectedSigner.isEmpty())
            throw new SecurityException("client signer policy is unavailable");
        if (clientVersionCode != registration.versionCode)
            throw new SecurityException("client version is not reviewed");
        if (packagesForUid == null || packagesForUid.length != 1
                || !claimedPackage.equals(packagesForUid[0]))
            throw new SecurityException("caller UID does not uniquely own the client package");
        if (currentSignerDigests == null || currentSignerDigests.length != 1
                || !expectedSigner.equals(normalizeDigest(currentSignerDigests[0])))
            throw new SecurityException("client signer mismatch");
    }

    public static ApprovedThemeRequest authorizeThemeRequest(int protocolVersion,
            boolean launchedForResult, int callingUid, String claimedPackage,
            long clientVersionCode,
            String[] packagesForUid, String[] currentSignerDigests, int seedArgb,
            String styleName, long durationMillis) {
        return authorizeThemeRequest(protocolVersion, launchedForResult, callingUid,
                claimedPackage, clientVersionCode, packagesForUid, currentSignerDigests,
                null, seedArgb, styleName, durationMillis);
    }

    public static ApprovedThemeRequest authorizeThemeRequest(int protocolVersion,
            boolean launchedForResult, int callingUid, String claimedPackage,
            long clientVersionCode,
            String[] packagesForUid, String[] currentSignerDigests,
            String controllerSignerDigest, int seedArgb,
            String styleName, long durationMillis) {
        authorizeClient(protocolVersion, launchedForResult, callingUid, claimedPackage,
                clientVersionCode, packagesForUid, currentSignerDigests,
                controllerSignerDigest);
        ExternalClientRegistry.requireCapability(
                claimedPackage, "SYSTEM_THEME_SEED_APPLY");
        if ((seedArgb >>> 24) != 0xff) throw new SecurityException("seed must be opaque ARGB");
        if (durationMillis <= 0 || durationMillis > MAX_DURATION_MILLIS)
            throw new SecurityException("duration exceeds client policy");
        final CapabilityPayload.ThemeStyle style;
        try { style = CapabilityPayload.ThemeStyle.valueOf(styleName); }
        catch (RuntimeException invalid) { throw new SecurityException("unsupported theme style"); }
        return new ApprovedThemeRequest(
                claimedPackage, callingUid, seedArgb, style, durationMillis);
    }

    private static String normalizeDigest(String value) {
        return ExternalClientRegistry.normalizeDigest(value);
    }
}
