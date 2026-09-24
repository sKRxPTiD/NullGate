package org.nullprotocol.nullgate.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Small bounded response; codes are machine-readable and messages are display-only. */
public final class BrokerResponse {
    private static final int MAGIC = 0x4e475250; // NGRP
    private static final int VERSION = 1;
    public final String code;
    public final String leaseId;

    public BrokerResponse(String code, String leaseId) {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,63}")) throw new IllegalArgumentException("invalid code");
        if (leaseId == null || !leaseId.matches("[A-Za-z0-9_-]{16,128}")) throw new IllegalArgumentException("invalid lease id");
        this.code = code;
        this.leaseId = leaseId;
    }

    public void writeTo(DataOutputStream out) throws IOException {
        out.writeInt(MAGIC); out.writeByte(VERSION); writeText(out, code); writeText(out, leaseId); out.flush();
    }

    public static BrokerResponse readFrom(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("invalid response magic");
        if (in.readUnsignedByte() != VERSION) throw new IOException("unsupported response version");
        try { return new BrokerResponse(readText(in), readText(in)); }
        catch (IllegalArgumentException error) { throw new IOException("invalid response field", error); }
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeShort(bytes.length); out.write(bytes);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > 128) throw new IOException("response field too large");
        byte[] bytes = new byte[length]; in.readFully(bytes); return new String(bytes, StandardCharsets.UTF_8);
    }
}
