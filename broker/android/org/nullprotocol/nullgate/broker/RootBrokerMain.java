package org.nullprotocol.nullgate.broker;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import org.nullprotocol.nullgate.protocol.RootSessionProtocol;
import org.nullprotocol.nullgate.protocol.RootShellProtocol;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Root authority bootstrap is separate from the app's user-controlled ON/OFF switch. */
public final class RootBrokerMain {
    private static final String ROOT = "/data/local/tmp/nullgate-root";
    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 0 || args.length < 1 || args.length > 2)
            throw new SecurityException("root broker requires root and a controller signing digest");
        String digest = CallerIdentity.normalizeDigest(args[0]);
        String controllerPackage = args.length == 2 ? args[1] : "org.nullprotocol.nullgate";
        if (!"org.nullprotocol.nullgate".equals(controllerPackage)
                && !"org.nullprotocol.nullgate.rootcandidate".equals(controllerPackage))
            throw new SecurityException("unsupported controller package");
        RuntimeFiles.requirePrivateDirectory(ROOT);
        if (new File(RuntimeFiles.ROOT, "theme.snapshot").exists()
                || new File(RuntimeFiles.ROOT, "broker.pid").exists())
            throw new SecurityException("close the legacy theme session before starting the root broker");
        AndroidPackageEvidence evidence = new AndroidPackageEvidence();
        VerifiedCallerResolver controller = new VerifiedCallerResolver(controllerPackage, digest, evidence);
        AndroidRootSessionBackend backend = new AndroidRootSessionBackend(evidence, controllerPackage);
        RootSessionEngine engine = new RootSessionEngine(backend);
        AtomicBoolean stopped = new AtomicBoolean();
        File pidFile = new File(ROOT, "root-broker.pid");
        LocalServerSocket control = new LocalServerSocket(RootSessionProtocol.CONTROL_SOCKET);
        LocalServerSocket shells;
        try { shells = new LocalServerSocket(RootSessionProtocol.SHELL_SOCKET); }
        catch (Exception failure) { control.close(); throw failure; }
        try {
            RuntimeFiles.writeExclusive(pidFile.getPath(), (android.os.Process.myPid() + "\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (Exception failure) { control.close(); shells.close(); throw failure; }

        Runnable cleanup = () -> {
            stopped.set(true);
            try { control.close(); } catch (Exception ignored) { }
            try { shells.close(); } catch (Exception ignored) { }
            RootSessionProtocol.Response result = engine.shutdown();
            if (result.state == RootSessionProtocol.State.OFF) {
                if (!pidFile.delete() && pidFile.exists()) System.err.println("Root broker PID receipt remains");
            } else System.err.println("Root session cleanup remains unresolved");
        };
        Runtime.getRuntime().addShutdownHook(new Thread(cleanup, "nullgate-root-stop"));
        Thread acceptShells = new Thread(() -> {
            java.util.concurrent.ExecutorService exchanges = new java.util.concurrent.ThreadPoolExecutor(
                    0, 32, 30, java.util.concurrent.TimeUnit.SECONDS,
                    new java.util.concurrent.SynchronousQueue<>());
            try {
                while (!stopped.get()) {
                    LocalSocket socket = shells.accept();
                    try { exchanges.execute(() -> relay(socket, engine, backend)); }
                    catch (java.util.concurrent.RejectedExecutionException busy) { socket.close(); }
                }
            } catch (Exception ended) { if (!stopped.get()) System.err.println("Root shell listener stopped"); }
            finally { exchanges.shutdownNow(); }
        }, "nullgate-root-shells");
        acceptShells.setDaemon(true); acceptShells.start();
        System.out.println("NullGate root broker ready; root switch OFF; changes persist after OFF");
        try {
            while (!stopped.get()) {
                try (LocalSocket socket = control.accept()) {
                    socket.setSoTimeout(3000);
                    int peerUid = socket.getPeerCredentials().getUid();
                    RootSessionProtocol.Request request = RootSessionProtocol.Request.readFrom(
                            new DataInputStream(socket.getInputStream()));
                    if (peerUid == 0) {
                        // Rooted ADB already holds device-root authority. Permit host status and
                        // cleanup while keeping root admission exclusively behind the signed UI.
                        if (request.operation != RootSessionProtocol.Operation.STATUS
                                && request.operation != RootSessionProtocol.Operation.OFF
                                && request.operation != RootSessionProtocol.Operation.SHUTDOWN)
                            throw new SecurityException("root host cannot enable app access");
                    } else controller.resolve(peerUid);
                    RootSessionProtocol.Response response;
                    switch (request.operation) {
                        case ON: response = engine.on(request.target); break;
                        case OFF: response = engine.off(); break;
                        case SHUTDOWN: response = engine.shutdown(); break;
                        default: response = engine.status();
                    }
                    response.writeTo(new DataOutputStream(socket.getOutputStream()));
                    System.out.println("root switch=" + response.state + " target=" + response.target
                            + " decision=" + response.code);
                    if (request.operation == RootSessionProtocol.Operation.SHUTDOWN
                            && response.state == RootSessionProtocol.State.OFF) break;
                } catch (Exception rejected) { System.err.println("Root control exchange failed: " + rejected.getClass().getSimpleName()); }
            }
        } finally { cleanup.run(); System.exit(engine.status().state == RootSessionProtocol.State.OFF ? 0 : 1); }
    }

    private static void relay(LocalSocket socket, RootSessionEngine engine, AndroidRootSessionBackend backend) {
        AndroidRootSessionBackend.Shell shell = null;
        try {
            socket.setSoTimeout(3000);
            String target = engine.targetPackage();
            if (target == null) throw new SecurityException("root switch OFF");
            CallerIdentity caller = backend.resolvePeer(socket.getPeerCredentials().getUid(), target);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            RootShellProtocol.acceptHandshake(input);
            shell = (AndroidRootSessionBackend.Shell) engine.openShell(caller);
            shell.attach(socket);
            output.writeByte(1); output.flush(); socket.setSoTimeout(0);
            final AndroidRootSessionBackend.Shell owned = shell;
            Thread stdin = new Thread(() -> {
                try {
                    while (true) {
                        RootShellProtocol.Frame frame = RootShellProtocol.read(input);
                        if (frame.type == RootShellProtocol.EOF) { owned.process.getOutputStream().close(); break; }
                        if (frame.type != RootShellProtocol.STDIN) throw new java.io.IOException("invalid shell input");
                        owned.process.getOutputStream().write(frame.data); owned.process.getOutputStream().flush();
                    }
                } catch (Exception closed) {
                    try { engine.closeShell(owned); } catch (Exception failed) { System.err.println("Root shell cleanup unresolved"); }
                }
            }, "nullgate-root-stdin");
            stdin.setDaemon(true); stdin.start();
            Thread stdout = pump(shell.process.getInputStream(), output, RootShellProtocol.STDOUT, socket);
            Thread stderr = pump(shell.process.getErrorStream(), output, RootShellProtocol.STDERR, socket);
            int exit = shell.process.waitFor();
            stdout.join(1000); stderr.join(1000);
            byte[] code = java.nio.ByteBuffer.allocate(4).putInt(exit).array();
            RootShellProtocol.write(output, RootShellProtocol.EXIT, code, 4);
        } catch (Exception denied) {
            System.err.println("Root shell exchange failed: " + denied.getClass().getSimpleName() + ": " + denied.getMessage());
            if (shell == null) try { socket.getOutputStream().write(0); socket.getOutputStream().flush(); } catch (Exception ignored) { }
        } finally {
            if (shell != null) try { engine.closeShell(shell); } catch (Exception failed) { System.err.println("Root shell cleanup unresolved"); }
            try { socket.close(); } catch (Exception ignored) { }
        }
    }

    private static Thread pump(InputStream input, DataOutputStream output, int kind, LocalSocket socket) {
        Thread thread = new Thread(() -> {
            byte[] data = new byte[RootShellProtocol.MAX_FRAME];
            try {
                int count;
                while ((count = input.read(data)) >= 0)
                    if (count > 0) RootShellProtocol.write(output, kind, data, count);
            } catch (Exception disconnected) { try { socket.close(); } catch (Exception ignored) { } }
        }, "nullgate-root-output");
        thread.setDaemon(true); thread.start(); return thread;
    }
    private RootBrokerMain() { }
}
