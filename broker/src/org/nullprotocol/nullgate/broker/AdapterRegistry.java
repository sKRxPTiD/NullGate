package org.nullprotocol.nullgate.broker;

import java.util.HashMap;
import java.util.Map;

/** Explicit package-plus-capability registry; an allowlist entry alone never enables execution. */
public final class AdapterRegistry {
    private final Map<String, CapabilityAdapter> adapters = new HashMap<>();

    public void register(String targetPackage, Capability capability, CapabilityAdapter adapter) {
        if (targetPackage == null || capability == null || adapter == null) throw new NullPointerException();
        adapters.put(key(targetPackage, capability), adapter);
    }

    public boolean isReady(String targetPackage, Capability capability) {
        CapabilityAdapter adapter = adapterFor(targetPackage, capability);
        return adapter != null && adapter.isReady(targetPackage);
    }

    public CapabilityAdapter adapterFor(String targetPackage, Capability capability) {
        return adapters.get(key(targetPackage, capability));
    }

    private static String key(String targetPackage, Capability capability) {
        return targetPackage + "\u0000" + capability.name();
    }
}
