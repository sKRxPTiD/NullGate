package org.nullprotocol.nullgate.rootclient;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import org.nullprotocol.nullgate.protocol.RootSessionProtocol;
import org.nullprotocol.nullgate.protocol.RootShellProtocol;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/** Unprivileged app_process trampoline used as libsu's configured shell command. */
public final class RootShellMain {
    public static void main(String[] args) {
        int exit = 126;
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress(RootSessionProtocol.SHELL_SOCKET,
                    LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(3000);
            if (socket.getPeerCredentials().getUid() != 0)
                throw new SecurityException("root endpoint is not root-owned");
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            RootShellProtocol.handshake(out);
            if (in.readUnsignedByte() != 1) throw new SecurityException("NullGate root switch is OFF");
            socket.setSoTimeout(0);
            Thread input = new Thread(() -> {
                byte[] bytes = new byte[RootShellProtocol.MAX_FRAME];
                try {
                    int count;
                    while ((count = System.in.read(bytes)) >= 0)
                        if (count > 0) RootShellProtocol.write(out, RootShellProtocol.STDIN, bytes, count);
                    RootShellProtocol.write(out, RootShellProtocol.EOF, bytes, 0);
                } catch (Exception failed) { try { socket.close(); } catch (Exception ignored) { } }
            }, "nullgate-shell-input");
            input.setDaemon(true); input.start();
            while (true) {
                RootShellProtocol.Frame frame = RootShellProtocol.read(in);
                if (frame.type == RootShellProtocol.STDOUT) { System.out.write(frame.data); System.out.flush(); }
                else if (frame.type == RootShellProtocol.STDERR) { System.err.write(frame.data); System.err.flush(); }
                else if (frame.type == RootShellProtocol.EXIT) {
                    exit = java.nio.ByteBuffer.wrap(frame.data).getInt(); break;
                } else throw new java.io.IOException("unexpected server frame");
            }
        } catch (Exception unavailable) { System.err.println("NullGate root unavailable: " + unavailable.getMessage()); }
        System.exit(exit);
    }
    private RootShellMain() { }
}
