package org.nullprotocol.nullgate.broker;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class BoundedProcessRunnerTest {
    public static void main(String[] args) throws Exception {
        BoundedProcessRunner.Result valid = BoundedProcessRunner.run(
                Arrays.asList("/bin/sh", "-c", "printf 12345678"), 1_000L, 8);
        check(valid.exitCode == 0);
        check("12345678".equals(new String(valid.output, StandardCharsets.UTF_8)));
        denies(SecurityException.class, () -> BoundedProcessRunner.run(
                Arrays.asList("/bin/sh", "-c", "printf 123456789"), 1_000L, 8));
        denies(IllegalStateException.class, () -> BoundedProcessRunner.run(
                Arrays.asList("/bin/sh", "-c", "sleep 2"), 50L, 8));
        System.out.println("NullGate bounded-process checks: 4 passed");
    }

    private interface Checked { void run() throws Exception; }
    private static void denies(Class<? extends Throwable> expected, Checked action)
            throws Exception {
        try { action.run(); throw new AssertionError("operation should fail"); }
        catch (java.util.concurrent.ExecutionException wrapped) {
            if (!expected.isInstance(wrapped.getCause())) throw wrapped;
        } catch (Throwable direct) {
            if (!expected.isInstance(direct)) throw direct;
        }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
