package org.nullprotocol.nullgate;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Closed registry of reviewed external clients and their narrow capabilities. */
final class ExternalClientRegistry {
    enum Capability { SYSTEM_THEME_SEED_APPLY }

    static final class Registration {
        final String packageName;
        final long versionCode;
        private final String fixedSigner;
        private final boolean signerPairedToController;
        private final Set<Capability> capabilities;

        private Registration(String packageName, long versionCode, String fixedSigner,
                boolean signerPairedToController, Set<Capability> capabilities) {
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.fixedSigner = fixedSigner;
            this.signerPairedToController = signerPairedToController;
            this.capabilities = Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        }

        String expectedSigner(String controllerSignerDigest) {
            return signerPairedToController
                    ? normalizeDigest(controllerSignerDigest) : fixedSigner;
        }

        boolean allows(Capability capability) {
            return capabilities.contains(capability);
        }
    }

    private static final Map<String, Registration> CLIENTS;
    static {
        Map<String, Registration> clients = new HashMap<>();
        register(clients, fixed(
                ExternalClientPolicy.COLORBLENDR_PACKAGE,
                ExternalClientPolicy.COLORBLENDR_VERSION_CODE,
                ExternalClientPolicy.COLORBLENDR_SIGNER,
                Capability.SYSTEM_THEME_SEED_APPLY));
        register(clients, paired(
                ExternalClientPolicy.TEST_CLIENT_PACKAGE,
                ExternalClientPolicy.TEST_CLIENT_VERSION_CODE,
                Capability.SYSTEM_THEME_SEED_APPLY));
        CLIENTS = Collections.unmodifiableMap(clients);
    }

    private ExternalClientRegistry() { }

    static Registration require(String packageName) {
        Registration registration = CLIENTS.get(packageName);
        if (registration == null)
            throw new SecurityException("client package is not allowlisted");
        return registration;
    }

    static void requireCapability(String packageName, String capabilityName) {
        final Capability capability;
        try { capability = Capability.valueOf(capabilityName); }
        catch (RuntimeException invalid) {
            throw new SecurityException("client capability is not recognized");
        }
        if (!require(packageName).allows(capability))
            throw new SecurityException("client capability is not allowlisted");
    }

    static String normalizeDigest(String value) {
        if (value == null) return "";
        String normalized = value.replace(":", "").toLowerCase(Locale.ROOT);
        return normalized.matches("[0-9a-f]{64}") ? normalized : "";
    }

    private static Registration fixed(String packageName, long versionCode,
            String signer, Capability... capabilities) {
        String normalized = normalizeDigest(signer);
        if (normalized.isEmpty()) throw new IllegalArgumentException("invalid fixed signer");
        return new Registration(packageName, versionCode, normalized, false,
                capabilitySet(capabilities));
    }

    private static Registration paired(String packageName, long versionCode,
            Capability... capabilities) {
        return new Registration(packageName, versionCode, "", true,
                capabilitySet(capabilities));
    }

    private static Set<Capability> capabilitySet(Capability... capabilities) {
        if (capabilities == null || capabilities.length == 0)
            throw new IllegalArgumentException("client requires a capability");
        EnumSet<Capability> result = EnumSet.noneOf(Capability.class);
        Collections.addAll(result, capabilities);
        return result;
    }

    private static void register(Map<String, Registration> clients,
            Registration registration) {
        if (registration.packageName == null
                || !registration.packageName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
                || registration.versionCode <= 0
                || clients.put(registration.packageName, registration) != null)
            throw new IllegalArgumentException("invalid or duplicate client registration");
    }
}
