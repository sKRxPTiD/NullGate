package org.nullprotocol.nullgate.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Separate protocol for the user-controlled root switch. No theme rollback or lease timer. */
public final class RootSessionProtocol {
    public static final String CONTROL_SOCKET = "nullgate-root-control-v1";
    public static final String SHELL_SOCKET = "nullgate-root-shell-v1";
    private static final int MAGIC = 0x4e475253;
    private static final int VERSION = 1;
    public enum Operation { STATUS, ON, OFF, SHUTDOWN }
    public enum State { OFF, ON, FAULT }
    public enum Code { OK, BUSY, DENIED, STOP_FAILED }

    public static final class Request {
        public final Operation operation;
        public final String target;
        public Request(Operation operation, String target) {
            if (operation == null || target == null) throw new IllegalArgumentException();
            if (operation == Operation.ON) requirePackage(target);
            else if (!target.isEmpty()) throw new IllegalArgumentException("unexpected target");
            this.operation = operation; this.target = target;
        }
        public void writeTo(DataOutputStream out) throws IOException {
            out.writeInt(MAGIC); out.writeByte(VERSION); out.writeByte(operation.ordinal());
            out.writeUTF(target); out.flush();
        }
        public static Request readFrom(DataInputStream in) throws IOException {
            header(in); int op = in.readUnsignedByte();
            if (op >= Operation.values().length) throw new IOException("invalid root operation");
            try { return new Request(Operation.values()[op], readText(in)); }
            catch (IllegalArgumentException invalid) { throw new IOException("invalid root request", invalid); }
        }
    }

    public static final class Response {
        public final Code code;
        public final State state;
        public final String target;
        public final int shellCount;
        public Response(Code code, State state, String target, int shellCount) {
            if (code == null || state == null || target == null || shellCount < 0 || shellCount > 32)
                throw new IllegalArgumentException("invalid root status");
            if (!target.isEmpty()) requirePackage(target);
            if (state == State.OFF && (!target.isEmpty() || shellCount != 0))
                throw new IllegalArgumentException("OFF cannot retain a target or shell");
            if (state != State.OFF && target.isEmpty())
                throw new IllegalArgumentException("active or unresolved session requires a target");
            this.code = code; this.state = state; this.target = target; this.shellCount = shellCount;
        }
        public void writeTo(DataOutputStream out) throws IOException {
            out.writeInt(MAGIC); out.writeByte(VERSION); out.writeByte(code.ordinal());
            out.writeByte(state.ordinal()); out.writeUTF(target); out.writeByte(shellCount); out.flush();
        }
        public static Response readFrom(DataInputStream in) throws IOException {
            header(in); int code = in.readUnsignedByte(), state = in.readUnsignedByte();
            if (code >= Code.values().length || state >= State.values().length)
                throw new IOException("invalid root response");
            try { return new Response(Code.values()[code], State.values()[state], readText(in), in.readUnsignedByte()); }
            catch (IllegalArgumentException invalid) { throw new IOException("invalid root status", invalid); }
        }
    }

    private static void header(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION)
            throw new IOException("unsupported root protocol");
    }
    private static String readText(DataInputStream in) throws IOException {
        // Package strings are ASCII, so bound allocation before DataInput.readUTF.
        int length = in.readUnsignedShort();
        if (length > 255) throw new IOException("root field too long");
        byte[] value = new byte[length]; in.readFully(value);
        for (byte b : value) if (b < 0 || b == 0) throw new IOException("invalid root text");
        return new String(value, java.nio.charset.StandardCharsets.US_ASCII);
    }
    public static void requirePackage(String value) {
        if (value == null || value.length() > 255 || !value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))
            throw new IllegalArgumentException("invalid package");
    }
    private RootSessionProtocol() { }
}
