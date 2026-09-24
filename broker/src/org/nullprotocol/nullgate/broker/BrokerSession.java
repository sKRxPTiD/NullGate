package org.nullprotocol.nullgate.broker;

import org.nullprotocol.nullgate.protocol.BrokerRequest;
import org.nullprotocol.nullgate.protocol.BrokerResponse;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** One verified local-socket exchange. Caller identity must come from peer credentials, never the frame. */
public final class BrokerSession {
    public interface AuditSink {
        void record(CallerIdentity caller, BrokerRequest request, String decisionCode);
    }

    private final BrokerEngine engine;
    private final AuditSink audit;
    private final TargetGate targetGate;

    public BrokerSession(BrokerEngine engine, AuditSink audit, TargetGate targetGate) {
        this.engine = engine;
        this.audit = audit;
        this.targetGate = targetGate;
    }

    public void handle(DataInputStream input, DataOutputStream output, CallerIdentity verifiedCaller)
            throws IOException {
        BrokerRequest request = BrokerRequest.readFrom(input);
        Decision decision;
        if (request.operation == BrokerRequest.Operation.REVOKE) {
            decision = engine.revoke(verifiedCaller, request.leaseId);
        } else {
            Capability capability;
            try {
                capability = Capability.valueOf(request.capability);
            } catch (IllegalArgumentException error) {
                String code = Decision.Code.TARGET_CAPABILITY_DENIED.name();
                audit.record(verifiedCaller, request, code);
                new BrokerResponse(code, request.leaseId).writeTo(output);
                return;
            }
            boolean installed;
            try {
                installed = targetGate.isInstalled(request.targetPackage);
            } catch (Exception unavailable) {
                installed = false;
            }
            if (!installed) {
                String code = Decision.Code.TARGET_NOT_INSTALLED.name();
                audit.record(verifiedCaller, request, code);
                new BrokerResponse(code, request.leaseId).writeTo(output);
                return;
            }
            CapabilityAdapter adapter = targetGate.adapterFor(request.targetPackage, capability);
            LeaseEnvelope lease = new LeaseEnvelope(
                    request.leaseId,
                    request.nonce,
                    request.targetPackage,
                    capability,
                    request.issuedAtElapsedMillis,
                    request.expiresAtElapsedMillis,
                    request.payload);
            decision = engine.request(adapter, verifiedCaller, lease);
        }
        try {
            audit.record(verifiedCaller, request, decision.code.name());
            new BrokerResponse(decision.code.name(), decision.leaseId).writeTo(output);
            output.flush();
        } catch (RuntimeException | IOException exchangeFailed) {
            // A lost grant reply must not deliberately leave a lease running.
            // Client still treats this as unknown: rollback can itself fail.
            if (decision.granted()) engine.revoke(verifiedCaller, request.leaseId);
            throw new IOException("exchange failed; cleanup attempted", exchangeFailed);
        }
    }
}
