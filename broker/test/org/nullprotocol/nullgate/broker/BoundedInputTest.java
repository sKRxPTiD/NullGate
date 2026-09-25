package org.nullprotocol.nullgate.broker;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

public final class BoundedInputTest {
    public static void main(String[] args) throws Exception {
        byte[] exact = new byte[16_386];
        Arrays.fill(exact, (byte) 'x');
        check(BoundedInput.readAll(new ByteArrayInputStream(exact), exact.length).length == exact.length);
        denies(() -> BoundedInput.readAll(new ByteArrayInputStream(new byte[16_387]), 16_386));
        check(BoundedInput.readAll(new ByteArrayInputStream(new byte[0]), 0).length == 0);
        System.out.println("NullGate bounded-input checks: 3 passed");
    }

    private interface Checked { void run() throws Exception; }
    private static void denies(Checked action) throws Exception {
        try { action.run(); throw new AssertionError("oversized stream should be rejected"); }
        catch (SecurityException expected) { }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
