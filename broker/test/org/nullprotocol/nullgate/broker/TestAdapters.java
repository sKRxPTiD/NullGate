package org.nullprotocol.nullgate.broker;

/** Deliberately fake implementations confined to host tests, never packaged with Android. */
final class TestAdapters {
    static final CapabilityAdapter READY = new CapabilityAdapter() {
        public boolean isReady(String target) { return true; }
        public void activate(LeaseEnvelope lease) { }
        public void deactivate(LeaseEnvelope lease) { }
    };
    static final TargetGate GATE = new TargetGate() {
        public boolean isInstalled(String target) { return true; }
        public CapabilityAdapter adapterFor(String target, Capability capability) { return READY; }
    };
}
