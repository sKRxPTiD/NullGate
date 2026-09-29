package org.nullprotocol.nullgate;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import org.nullprotocol.nullgate.protocol.RootSessionProtocol;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Controller transport for the root switch. A missing reply leaves status unknown. */
public final class RootBrokerClient {
    public RootSessionProtocol.Response exchange(RootSessionProtocol.Operation op, String target) throws IOException {
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress(RootSessionProtocol.CONTROL_SOCKET,
                    LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(op == RootSessionProtocol.Operation.STATUS ? 3000 : 15000);
            if (socket.getPeerCredentials().getUid() != 0) throw new IOException("broker is not root-owned");
            new RootSessionProtocol.Request(op, target).writeTo(new DataOutputStream(socket.getOutputStream()));
            RootSessionProtocol.Response response = RootSessionProtocol.Response.readFrom(
                    new DataInputStream(socket.getInputStream()));
            if (response.code == RootSessionProtocol.Code.OK) {
                if (op == RootSessionProtocol.Operation.ON
                        && (response.state != RootSessionProtocol.State.ON || !target.equals(response.target)))
                    throw new IOException("broker did not confirm the selected target");
                if ((op == RootSessionProtocol.Operation.OFF || op == RootSessionProtocol.Operation.SHUTDOWN)
                        && response.state != RootSessionProtocol.State.OFF)
                    throw new IOException("broker did not confirm OFF");
            }
            return response;
        }
    }
}
