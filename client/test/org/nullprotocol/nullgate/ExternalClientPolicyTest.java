package org.nullprotocol.nullgate;

public final class ExternalClientPolicyTest {
    private static final String PACKAGE = ExternalClientPolicy.COLORBLENDR_PACKAGE;
    private static final String SIGNER = ExternalClientPolicy.COLORBLENDR_SIGNER;
    private static final String TEST_PACKAGE = ExternalClientPolicy.TEST_CLIENT_PACKAGE;
    private static final String CONTROLLER_SIGNER =
            "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

    public static void main(String[] args) {
        allowsPinnedTypedRequest();
        denies(() -> request(2, true, 10258, PACKAGE, new String[]{PACKAGE},
                new String[]{SIGNER}, 0xff76543a, "TONAL_SPOT", 60_000));
        denies(() -> request(1, false, 10258, PACKAGE, new String[]{PACKAGE},
                new String[]{SIGNER}, 0xff76543a, "TONAL_SPOT", 60_000));
        denies(() -> request(1, true, 2000, PACKAGE, new String[]{PACKAGE},
                new String[]{SIGNER}, 0xff76543a, "TONAL_SPOT", 60_000));
        denies(() -> request(1, true, 10258, "evil.client", new String[]{"evil.client"},
                new String[]{SIGNER}, 0xff76543a, "TONAL_SPOT", 60_000));
        denies(() -> request(1, true, 10258, PACKAGE, new String[]{PACKAGE, "shared.uid"},
                new String[]{SIGNER}, 0xff76543a, "TONAL_SPOT", 60_000));
        denies(() -> request(1, true, 10258, PACKAGE, new String[]{PACKAGE},
                new String[]{repeat('0', 64)}, 0xff76543a, "TONAL_SPOT", 60_000));
        denies(() -> request(1, true, 10258, PACKAGE, new String[]{PACKAGE},
                new String[]{SIGNER, SIGNER}, 0xff76543a, "TONAL_SPOT", 60_000));
        denies(() -> request(1, true, 10258, PACKAGE, new String[]{PACKAGE},
                new String[]{SIGNER}, 0x0076543a, "TONAL_SPOT", 60_000));
        denies(() -> request(1, true, 10258, PACKAGE, new String[]{PACKAGE},
                new String[]{SIGNER}, 0xff76543a, "MADE_UP", 60_000));
        denies(() -> request(1, true, 10258, PACKAGE, new String[]{PACKAGE},
                new String[]{SIGNER}, 0xff76543a, "TONAL_SPOT", 0));
        denies(() -> request(1, true, 10258, PACKAGE, new String[]{PACKAGE},
                new String[]{SIGNER}, 0xff76543a, "TONAL_SPOT", 600_001));
        exactSchemasRejectMissingAndAdditionalFields();
        revokeIdentityUsesTheSamePin();
        allowsPairedFirstPartyTestClient();
        denies(() -> testRequest(null, CONTROLLER_SIGNER, 1));
        denies(() -> testRequest(repeat('0', 64), CONTROLLER_SIGNER, 1));
        denies(() -> testRequest(CONTROLLER_SIGNER, null, 1));
        denies(() -> testRequest(CONTROLLER_SIGNER, CONTROLLER_SIGNER, 2));
        denies(() -> ExternalClientPolicy.authorizeClient(1, true, 10258, PACKAGE, 43,
                new String[]{PACKAGE}, new String[]{SIGNER}));
        System.out.println("NullGate external-client policy tests: 22 passed");
    }

    private static void revokeIdentityUsesTheSamePin() {
        ExternalClientPolicy.authorizeClient(1, true, 10258, PACKAGE, 42,
                new String[]{PACKAGE}, new String[]{SIGNER});
    }

    private static void exactSchemasRejectMissingAndAdditionalFields() {
        java.util.Set<String> request = new java.util.HashSet<>(java.util.Arrays.asList(
                ClientRequestContract.EXTRA_PROTOCOL_VERSION,
                ClientRequestContract.EXTRA_SEED_ARGB,
                ClientRequestContract.EXTRA_THEME_STYLE,
                ClientRequestContract.EXTRA_DURATION_MILLIS));
        check(ClientRequestContract.hasExactRequestKeys(request));
        request.remove(ClientRequestContract.EXTRA_THEME_STYLE);
        check(!ClientRequestContract.hasExactRequestKeys(request));
        request.add(ClientRequestContract.EXTRA_THEME_STYLE); request.add("command");
        check(!ClientRequestContract.hasExactRequestKeys(request));
    }

    private static void allowsPinnedTypedRequest() {
        ExternalClientPolicy.ApprovedThemeRequest approved = request(1, true, 10258,
                PACKAGE, new String[]{PACKAGE}, new String[]{colonizedUpper(SIGNER)},
                0xff76543a, "TONAL_SPOT", 60_000);
        check(PACKAGE.equals(approved.clientPackage));
        check(approved.clientUid == 10258);
        check(approved.seedArgb == 0xff76543a);
        check(approved.durationMillis == 60_000);
    }

    private static void allowsPairedFirstPartyTestClient() {
        ExternalClientPolicy.ApprovedThemeRequest approved =
                testRequest(CONTROLLER_SIGNER, colonizedUpper(CONTROLLER_SIGNER), 1);
        check(TEST_PACKAGE.equals(approved.clientPackage));
    }

    private static ExternalClientPolicy.ApprovedThemeRequest testRequest(
            String clientSigner, String controllerSigner, long versionCode) {
        return ExternalClientPolicy.authorizeThemeRequest(1, true, 10259,
                TEST_PACKAGE, versionCode, new String[]{TEST_PACKAGE},
                clientSigner == null ? null : new String[]{clientSigner},
                controllerSigner, 0xff876a4b, "TONAL_SPOT", 60_000);
    }

    private static ExternalClientPolicy.ApprovedThemeRequest request(int version,
            boolean forResult, int uid, String claimed, String[] packages, String[] signers,
            int seed, String style, long duration) {
        return ExternalClientPolicy.authorizeThemeRequest(version, forResult, uid, claimed, 42,
                packages, signers, seed, style, duration);
    }

    private static void denies(Runnable request) {
        try { request.run(); throw new AssertionError("request should have been denied"); }
        catch (SecurityException expected) { }
    }

    private static String colonizedUpper(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i += 2) {
            if (i != 0) out.append(':');
            out.append(value.substring(i, i + 2).toUpperCase(java.util.Locale.ROOT));
        }
        return out.toString();
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count]; java.util.Arrays.fill(chars, value); return new String(chars);
    }

    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
