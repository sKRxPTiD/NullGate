package org.nullprotocol.nullgate.broker;

public final class AdapterRegistryTest {
    private static final String TARGET = "org.nullprotocol.client";

    public static void main(String[] args) {
        AdapterRegistry registry = new AdapterRegistry();
        CapabilityAdapter adapter = readyAdapter();
        registry.register(TARGET, Capability.SYSTEM_THEME_SEED_APPLY, adapter);
        check(registry.adapterFor(TARGET, Capability.SYSTEM_THEME_SEED_APPLY) == adapter);
        check(registry.adapterFor(TARGET, Capability.SHIZUKU_SESSION_START) == null);
        deniesDuplicate(() -> registry.register(
                TARGET, Capability.SYSTEM_THEME_SEED_APPLY, readyAdapter()));
        deniesInvalid(() -> new AdapterRegistry().register(
                "not a package", Capability.SYSTEM_THEME_SEED_APPLY, readyAdapter()));
        deniesInvalid(() -> new AdapterRegistry().register(
                "", Capability.SYSTEM_THEME_SEED_APPLY, readyAdapter()));
        System.out.println("NullGate adapter-registry checks: 5 passed");
    }

    private static CapabilityAdapter readyAdapter() {
        return new CapabilityAdapter() {
            public boolean isReady(String target) { return true; }
            public void activate(LeaseEnvelope lease) { }
            public void deactivate(LeaseEnvelope lease) { }
        };
    }

    private static void deniesDuplicate(Runnable action) {
        try { action.run(); throw new AssertionError("duplicate adapter should fail"); }
        catch (IllegalStateException expected) { }
    }

    private static void deniesInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("invalid adapter target should fail"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("adapter registry assertion failed");
    }
}
