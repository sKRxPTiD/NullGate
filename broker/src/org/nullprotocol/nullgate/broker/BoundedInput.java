package org.nullprotocol.nullgate.broker;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Reads a complete stream while rejecting, rather than truncating, oversized output. */
public final class BoundedInput {
    private BoundedInput() { }

    public static byte[] readAll(InputStream input, int limit) throws Exception {
        if (input == null || limit < 0) throw new IllegalArgumentException("invalid bound");
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 4096));
        byte[] buffer = new byte[1024];
        int total = 0;
        for (;;) {
            int count = input.read(buffer);
            if (count < 0) break;
            if (count == 0) continue;
            total += count;
            if (total > limit) throw new SecurityException("command output exceeds safety bound");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
