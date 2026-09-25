package org.nullprotocol.nullgate.broker;

import java.util.HashMap;
import java.util.Map;

/** Explicit package-plus-capability registry; an allowlist entry alone never enables execution. */
public final class AdapterRegistry {
    private final Map<String, CapabilityAdapter> adapters = new HashMap<>();

    public void register(String targetPackage, Capability capability, CapabilityAdapter adapter) {
        if (targetPackage == null || capability == null || adapter == null) throw new NullPointerException();
        if (!("android".equals(targetPackage)
                || targetPackage.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
            throw new IllegalArgumentException("invalid adapter target package");
        String key = key(targetPackage, capability);
        if (adapters.containsKey(key))
            throw new IllegalStateException("adapter already registered");
        adapters.put(key, adapter);
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
