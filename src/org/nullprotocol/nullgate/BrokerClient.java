package org.nullprotocol.nullgate;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import org.nullprotocol.nullgate.protocol.BrokerRequest;
import org.nullprotocol.nullgate.protocol.BrokerResponse;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Controller transport only. The root-side endpoint independently verifies peer UID and signing cert. */
public final class BrokerClient {
    public static final String SOCKET_NAME = "nullgate-broker-v1";

    public BrokerResponse issue(LeaseRequest lease) throws IOException {
        if ("SYSTEM_THEME_SEED_APPLY".equals(lease.capability)) {
            return exchange(BrokerRequest.issueSystemTheme(lease.id, lease.nonce,
                    lease.issuedAtElapsedMillis, lease.expiresAtElapsedMillis,
                    lease.seedArgb,
                    org.nullprotocol.nullgate.protocol.CapabilityPayload.ThemeStyle
                            .valueOf(lease.themeStyle)));
        }
        return exchange(BrokerRequest.issue(
                lease.id,
                lease.nonce,
                lease.targetPackage,
                lease.capability,
                lease.issuedAtElapsedMillis,
                lease.expiresAtElapsedMillis));
    }

    public BrokerResponse revoke(String leaseId) throws IOException {
        return exchange(BrokerRequest.revoke(leaseId));
    }

    private BrokerResponse exchange(BrokerRequest request) throws IOException {
        LocalSocket socket = new LocalSocket();
        try {
            socket.connect(new LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(2_000);
            if (socket.getPeerCredentials().getUid() != 0)
                throw new IOException("local endpoint is not root-owned");
            request.writeTo(new DataOutputStream(socket.getOutputStream()));
            BrokerResponse response = BrokerResponse.readFrom(new DataInputStream(socket.getInputStream()));
            org.nullprotocol.nullgate.protocol.ResponseValidator.requireMatches(request, response);
            return response;
        } finally {
            socket.close();
        }
    }
}
