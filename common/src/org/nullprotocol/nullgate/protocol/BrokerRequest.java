package org.nullprotocol.nullgate.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Strict binary IPC request. It deliberately has no generic command or path field. */
public final class BrokerRequest {
    private static final int MAGIC = 0x4e474154; // NGAT
    private static final int VERSION = 2;
    private static final int MAX_TEXT_BYTES = 256;

    public enum Operation { ISSUE, REVOKE }

    public final Operation operation;
    public final String leaseId;
    public final String nonce;
    public final String targetPackage;
    public final String capability;
    public final long issuedAtElapsedMillis;
    public final long expiresAtElapsedMillis;
    public final CapabilityPayload payload;

    private BrokerRequest(Operation operation, String leaseId, String nonce, String targetPackage,
            String capability, long issuedAtElapsedMillis, long expiresAtElapsedMillis,
            CapabilityPayload payload) {
        this.operation = operation;
        this.leaseId = leaseId;
        this.nonce = nonce;
        this.targetPackage = targetPackage;
        this.capability = capability;
        this.issuedAtElapsedMillis = issuedAtElapsedMillis;
        this.expiresAtElapsedMillis = expiresAtElapsedMillis;
        this.payload = payload;
    }

    public static BrokerRequest issue(String leaseId, String nonce, String targetPackage,
            String capability, long issuedAtElapsedMillis, long expiresAtElapsedMillis) {
        requireToken(leaseId); requireToken(nonce); requirePackage(targetPackage); requireCapability(capability);
        return new BrokerRequest(Operation.ISSUE, leaseId, nonce, targetPackage, capability,
                issuedAtElapsedMillis, expiresAtElapsedMillis, CapabilityPayload.none());
    }

    public static BrokerRequest issueSystemTheme(String leaseId, String nonce,
            long issuedAtElapsedMillis, long expiresAtElapsedMillis, int seedArgb,
            CapabilityPayload.ThemeStyle style) {
        requireToken(leaseId); requireToken(nonce);
        return new BrokerRequest(Operation.ISSUE, leaseId, nonce, "android",
                "SYSTEM_THEME_SEED_APPLY", issuedAtElapsedMillis, expiresAtElapsedMillis,
                CapabilityPayload.systemThemeSeed(seedArgb, style));
    }

    public static BrokerRequest revoke(String leaseId) {
        requireToken(leaseId);
        return new BrokerRequest(Operation.REVOKE, leaseId, "", "", "", 0, 0,
                CapabilityPayload.none());
    }

    public void writeTo(DataOutputStream out) throws IOException {
        out.writeInt(MAGIC);
        out.writeByte(VERSION);
        out.writeByte(operation.ordinal());
        writeText(out, leaseId);
        if (operation == Operation.ISSUE) {
            writeText(out, nonce);
            writeText(out, targetPackage);
            writeText(out, capability);
            out.writeLong(issuedAtElapsedMillis);
            out.writeLong(expiresAtElapsedMillis);
            out.writeByte(payload.kind.ordinal());
            if (payload.kind == CapabilityPayload.Kind.SYSTEM_THEME_SEED) {
                out.writeInt(payload.seedArgb);
                out.writeByte(payload.themeStyle.ordinal());
            }
        }
        out.flush();
    }

    public static BrokerRequest readFrom(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("invalid protocol magic");
        if (in.readUnsignedByte() != VERSION) throw new IOException("unsupported protocol version");
        int operation = in.readUnsignedByte();
        if (operation < 0 || operation >= Operation.values().length) throw new IOException("invalid operation");
        String leaseId = readText(in);
        try {
            if (operation == Operation.REVOKE.ordinal()) return revoke(leaseId);
            String nonce = readText(in), target = readText(in), capability = readText(in);
            long issued = in.readLong(), expires = in.readLong();
            int kind = in.readUnsignedByte();
            if (kind == CapabilityPayload.Kind.NONE.ordinal())
                return issue(leaseId, nonce, target, capability, issued, expires);
            if (kind == CapabilityPayload.Kind.SYSTEM_THEME_SEED.ordinal()) {
                int seed = in.readInt(); int style = in.readUnsignedByte();
                if (style >= CapabilityPayload.ThemeStyle.values().length)
                    throw new IllegalArgumentException("invalid theme style");
                BrokerRequest request = issueSystemTheme(leaseId, nonce, issued, expires, seed,
                        CapabilityPayload.ThemeStyle.values()[style]);
                if (!request.targetPackage.equals(target) || !request.capability.equals(capability))
                    throw new IllegalArgumentException("payload does not match capability");
                return request;
            }
            throw new IllegalArgumentException("invalid payload kind");
        } catch (IllegalArgumentException error) {
            throw new IOException("invalid request field", error);
        }
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) throw new IOException("field too large");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > MAX_TEXT_BYTES) throw new IOException("field too large");
        byte[] bytes = new byte[length];
        try { in.readFully(bytes); } catch (EOFException error) { throw new IOException("truncated field", error); }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void requireToken(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{16,128}")) throw new IllegalArgumentException("invalid token");
    }

    private static void requirePackage(String value) {
        if (value == null || !("android".equals(value)
                || value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
            throw new IllegalArgumentException("invalid package");
    }

    private static void requireCapability(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{2,63}")) throw new IllegalArgumentException("invalid capability");
    }
}
