package org.nullprotocol.nullgate.broker;

import org.nullprotocol.nullgate.protocol.RootSessionProtocol;
import org.nullprotocol.nullgate.protocol.RootShellProtocol;
import java.io.*;

public final class RootSessionProtocolTest {
    public static void main(String[] args) throws Exception {
        for (RootSessionProtocol.Operation op : RootSessionProtocol.Operation.values()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new RootSessionProtocol.Request(op,op == RootSessionProtocol.Operation.ON ? "example.target" : "")
                    .writeTo(new DataOutputStream(bytes));
            RootSessionProtocol.Request result = RootSessionProtocol.Request.readFrom(input(bytes.toByteArray()));
            check(result.operation == op);
        }
        for (RootSessionProtocol.State state : RootSessionProtocol.State.values()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new RootSessionProtocol.Response(RootSessionProtocol.Code.OK,state,state == RootSessionProtocol.State.OFF ? "" : "example.target",0)
                    .writeTo(new DataOutputStream(bytes));
            check(RootSessionProtocol.Response.readFrom(input(bytes.toByteArray())).state == state);
        }
        try { new RootSessionProtocol.Request(RootSessionProtocol.Operation.ON,"bad;id"); throw new AssertionError(); }
        catch (IllegalArgumentException okay) { }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        byte[] payload = "stdout and stderr stay separate".getBytes("UTF-8");
        RootShellProtocol.write(out,RootShellProtocol.STDOUT,payload,payload.length);
        RootShellProtocol.Frame frame = RootShellProtocol.read(input(bytes.toByteArray()));
        check(frame.type == RootShellProtocol.STDOUT && java.util.Arrays.equals(payload,frame.data));
        bytes.reset(); out.writeByte(RootShellProtocol.STDIN); out.writeInt(Integer.MAX_VALUE);
        try { RootShellProtocol.read(input(bytes.toByteArray())); throw new AssertionError(); } catch (IOException okay) { }
        bytes.reset(); out.writeByte(RootShellProtocol.EXIT); out.writeInt(0);
        try { RootShellProtocol.read(input(bytes.toByteArray())); throw new AssertionError(); } catch (IOException okay) { }
        bytes.reset(); RootShellProtocol.handshake(out); RootShellProtocol.acceptHandshake(input(bytes.toByteArray()));
        RootProcessIdentity identity = RootProcessIdentity.parse("123 (sh with (parens)) S 42 123 123 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 9876 0");
        check(identity.pid == 123 && identity.parent == 42 && identity.group == 123 && identity.session == 123 && identity.started == 9876);
        System.out.println("NullGate root protocol: control roundtrips, frame bounds and process identity checks passed");
    }
    private static DataInputStream input(byte[] data) { return new DataInputStream(new ByteArrayInputStream(data)); }
    private static void check(boolean okay) { if (!okay) throw new AssertionError("root protocol assertion failed"); }
}
