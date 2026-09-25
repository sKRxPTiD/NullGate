package org.nullprotocol.nullgate.broker;

import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Runs a fixed argv command while concurrently draining and bounding combined output. */
public final class BoundedProcessRunner {
    public static final class Result {
        public final int exitCode;
        public final byte[] output;
        Result(int exitCode, byte[] output) { this.exitCode = exitCode; this.output = output; }
    }

    private BoundedProcessRunner() { }

    public static Result run(List<String> command, long timeoutMillis, int outputLimit)
            throws Exception {
        if (command == null || command.isEmpty() || timeoutMillis <= 0)
            throw new IllegalArgumentException("invalid command policy");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        FutureTask<byte[]> reader = new FutureTask<>(
                () -> BoundedInput.readAll(process.getInputStream(), outputLimit));
        Thread drain = new Thread(reader, "NullGate-command-output");
        drain.setDaemon(true);
        drain.start();
        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                if (!process.waitFor(1, TimeUnit.SECONDS))
                    throw new IllegalStateException("timed-out command could not be terminated");
                throw new IllegalStateException("fixed command timed out");
            }
            return new Result(process.exitValue(), reader.get(1, TimeUnit.SECONDS));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
            reader.cancel(true);
            try { process.getInputStream().close(); } catch (Exception ignored) { }
            try { process.getOutputStream().close(); } catch (Exception ignored) { }
            try { process.getErrorStream().close(); } catch (Exception ignored) { }
        }
    }
}
