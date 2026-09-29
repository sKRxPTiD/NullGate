package org.nullprotocol.nullgate.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Stream framing preserves libsu's separate stdout and stderr without a native su binary. */
public final class RootShellProtocol {
    public static final int MAGIC = 0x4e475348;
    public static final int VERSION = 1;
    public static final int STDIN = 1, STDOUT = 2, STDERR = 3, EOF = 4, EXIT = 5;
    public static final int MAX_FRAME = 8192;
    public static final class Frame {
        public final int type;
        public final byte[] data;
        Frame(int type, byte[] data) { this.type = type; this.data = data; }
    }
    public static void handshake(DataOutputStream out) throws IOException {
        out.writeInt(MAGIC); out.writeByte(VERSION); out.flush();
    }
    public static void acceptHandshake(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION)
            throw new IOException("unsupported root shell protocol");
    }
    public static Frame read(DataInputStream in) throws IOException {
        int type = in.readUnsignedByte(), size = in.readInt();
        validate(type, size);
        byte[] data = new byte[size]; in.readFully(data); return new Frame(type, data);
    }
    public static void write(DataOutputStream out, int type, byte[] data, int size) throws IOException {
        validate(type, size);
        if (data == null || size > data.length) throw new IOException("invalid frame buffer");
        synchronized (out) { out.writeByte(type); out.writeInt(size); out.write(data, 0, size); out.flush(); }
    }
    private static void validate(int type, int size) throws IOException {
        if (type < STDIN || type > EXIT || size < 0 || size > MAX_FRAME
                || (type == EOF && size != 0) || (type == EXIT && size != 4))
            throw new IOException("invalid root shell frame");
    }
    private RootShellProtocol() { }
}
