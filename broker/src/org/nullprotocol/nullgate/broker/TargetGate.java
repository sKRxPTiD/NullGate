package org.nullprotocol.nullgate.broker;

/** Runtime checks performed immediately before a lease can be issued. */
public interface TargetGate {
    boolean isInstalled(String targetPackage) throws Exception;
    CapabilityAdapter adapterFor(String targetPackage, Capability capability);
}
