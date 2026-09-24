package org.nullprotocol.nullgate.broker;

/** A named, audited action implementation. Adapters never receive shell text. */
public interface CapabilityAdapter {
    boolean isReady(String targetPackage);
    void activate(LeaseEnvelope lease) throws Exception;
    void deactivate(LeaseEnvelope lease) throws Exception;
}
