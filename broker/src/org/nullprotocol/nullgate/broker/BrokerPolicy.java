package org.nullprotocol.nullgate.broker;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Static allowlist and controller identity boundary for one broker launch. */
public final class BrokerPolicy {
    public final String controllerPackage;
    public final String controllerCertificateSha256;
    public final long maximumLeaseMillis;
    public final int maximumActiveLeases;
    private final Map<String, Set<Capability>> allowed;

    public BrokerPolicy(
            String controllerPackage,
            String controllerCertificateSha256,
            long maximumLeaseMillis,
            Map<String, Set<Capability>> allowed) {
        this(controllerPackage, controllerCertificateSha256, maximumLeaseMillis, 1, allowed);
    }

    public BrokerPolicy(
            String controllerPackage,
            String controllerCertificateSha256,
            long maximumLeaseMillis,
            int maximumActiveLeases,
            Map<String, Set<Capability>> allowed) {
        if (maximumLeaseMillis <= 0) throw new IllegalArgumentException("maximumLeaseMillis must be positive");
        if (maximumActiveLeases <= 0)
            throw new IllegalArgumentException("maximumActiveLeases must be positive");
        this.controllerPackage = controllerPackage;
        this.controllerCertificateSha256 = CallerIdentity.normalizeDigest(controllerCertificateSha256);
        this.maximumLeaseMillis = maximumLeaseMillis;
        this.maximumActiveLeases = maximumActiveLeases;
        Map<String, Set<Capability>> copy = new HashMap<>();
        for (Map.Entry<String, Set<Capability>> entry : allowed.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(EnumSet.copyOf(entry.getValue())));
        }
        this.allowed = Collections.unmodifiableMap(copy);
    }

    public boolean allows(String targetPackage, Capability capability) {
        Set<Capability> capabilities = allowed.get(targetPackage);
        return capabilities != null && capabilities.contains(capability);
    }
}
