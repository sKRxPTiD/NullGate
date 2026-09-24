package org.nullprotocol.nullgate.broker;

public final class VerifiedCallerResolverTest {
    private static final String PACKAGE = "org.nullprotocol.nullgate";
    private static final String CERT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    public static void main(String[] args) throws Exception {
        resolvesExactUidPackageAndSigner();
        rejectsSystemUid();
        rejectsUidPackageMismatch();
        rejectsSignerMismatch();
        System.out.println("NullGate identity tests: 4 passed");
    }

    private static void resolvesExactUidPackageAndSigner() throws Exception {
        CallerIdentity caller = resolver(new String[] { PACKAGE }, new String[] { CERT }).resolve(10_123);
        check(caller.uid == 10_123 && PACKAGE.equals(caller.packageName));
    }

    private static void rejectsSystemUid() throws Exception {
        expectDenied(() -> resolver(new String[] { PACKAGE }, new String[] { CERT }).resolve(0));
    }

    private static void rejectsUidPackageMismatch() throws Exception {
        expectDenied(() -> resolver(new String[] { "org.attacker.app" }, new String[] { CERT }).resolve(10_123));
    }

    private static void rejectsSignerMismatch() throws Exception {
        String wrong = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        expectDenied(() -> resolver(new String[] { PACKAGE }, new String[] { wrong }).resolve(10_123));
    }

    private static VerifiedCallerResolver resolver(String[] packages, String[] signers) {
        return new VerifiedCallerResolver(PACKAGE, CERT, new VerifiedCallerResolver.PackageEvidence() {
            public String[] packagesForUid(int uid) { return packages; }
            public String[] currentSignerSha256(String packageName) { return signers; }
        });
    }

    private static void expectDenied(CheckedCall call) throws Exception {
        try { call.run(); throw new AssertionError("unverified caller accepted"); }
        catch (SecurityException expected) { }
    }

    private interface CheckedCall { void run() throws Exception; }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("identity assertion failed"); }
}
