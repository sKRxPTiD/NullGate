package org.nullprotocol.nullgate.broker;

import java.util.Objects;

/** Converts trusted Android package-manager evidence into a broker caller identity. */
public final class VerifiedCallerResolver {
    public interface PackageEvidence {
        String[] packagesForUid(int uid) throws Exception;
        String[] currentSignerSha256(String packageName) throws Exception;
    }

    private final String expectedPackage;
    private final String expectedSignerSha256;
    private final PackageEvidence evidence;

    public VerifiedCallerResolver(String expectedPackage, String expectedSignerSha256,
            PackageEvidence evidence) {
        this.expectedPackage = Objects.requireNonNull(expectedPackage);
        this.expectedSignerSha256 = CallerIdentity.normalizeDigest(expectedSignerSha256);
        this.evidence = Objects.requireNonNull(evidence);
    }

    public CallerIdentity resolve(int peerUid) throws Exception {
        if (peerUid < 10_000 || peerUid > 19_999)
            throw new SecurityException("only owner-user ordinary application UIDs are supported");
        boolean ownsPackage = false;
        String[] packages = evidence.packagesForUid(peerUid);
        if (packages != null && packages.length == 1) {
            for (String packageName : packages) {
                if (expectedPackage.equals(packageName)) ownsPackage = true;
            }
        }
        if (!ownsPackage) throw new SecurityException("peer UID does not own controller package");

        boolean signerMatches = false;
        String[] signers = evidence.currentSignerSha256(expectedPackage);
        if (signers != null && signers.length == 1) {
            for (String signer : signers) {
                if (expectedSignerSha256.equals(CallerIdentity.normalizeDigest(signer))) signerMatches = true;
            }
        }
        if (!signerMatches) throw new SecurityException("controller signing certificate mismatch");
        return new CallerIdentity(peerUid, expectedPackage, expectedSignerSha256);
    }
}
