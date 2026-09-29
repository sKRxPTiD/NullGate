package org.nullprotocol.nullgate.broker;

import org.nullprotocol.nullgate.protocol.RootSessionProtocol;
import java.util.ArrayList;
import java.util.List;

/** One selected app receives real root shells until OFF. Applied app changes are never undone. */
public final class RootSessionEngine {
    public interface ShellHandle { void stop() throws Exception; }
    public interface Backend {
        CallerIdentity targetIdentity(String targetPackage) throws Exception;
        ShellHandle startShell() throws Exception;
        void forceStopTarget(String targetPackage) throws Exception;
    }
    private final Backend backend;
    private final List<ShellHandle> shells = new ArrayList<>();
    private CallerIdentity target;
    private boolean fault, closed;
    public RootSessionEngine(Backend backend) { this.backend = backend; }

    public synchronized RootSessionProtocol.Response on(String packageName) {
        RootSessionProtocol.requirePackage(packageName);
        if (closed || fault) return response(RootSessionProtocol.Code.DENIED);
        if (target != null)
            return response(target.packageName.equals(packageName)
                    ? RootSessionProtocol.Code.OK : RootSessionProtocol.Code.BUSY);
        try {
            CallerIdentity resolved = backend.targetIdentity(packageName);
            if (resolved.uid < 10000 || resolved.uid > 19999 || !packageName.equals(resolved.packageName))
                return response(RootSessionProtocol.Code.DENIED);
            // Restart the chosen app when it is next opened, clearing any cached OFF connection.
            backend.forceStopTarget(packageName);
            target = resolved;
            return response(RootSessionProtocol.Code.OK);
        } catch (Exception unavailable) { return response(RootSessionProtocol.Code.DENIED); }
    }

    public synchronized ShellHandle openShell(CallerIdentity caller) throws Exception {
        if (closed || fault || target == null || caller == null || caller.uid != target.uid
                || !target.packageName.equals(caller.packageName)
                || !target.certificateSha256.equals(caller.certificateSha256))
            throw new SecurityException("root switch is OFF for this peer");
        if (shells.size() >= 32) throw new SecurityException("too many simultaneous root shells");
        // Start under the same lock as OFF, so a shell cannot appear after cleanup.
        ShellHandle handle = backend.startShell();
        shells.add(handle); return handle;
    }

    public synchronized void closeShell(ShellHandle handle) throws Exception {
        if (!shells.contains(handle)) return;
        try { handle.stop(); shells.remove(handle); }
        catch (Exception failed) { fault = true; throw failed; }
    }

    public synchronized RootSessionProtocol.Response off() {
        if (target == null) return response(RootSessionProtocol.Code.OK);
        // Block admission before closing streams or signalling any process.
        fault = true;
        boolean failed = false;
        try { backend.forceStopTarget(target.packageName); }
        catch (Exception failure) { failed = true; }
        for (ShellHandle shell : new ArrayList<>(shells)) {
            try { shell.stop(); shells.remove(shell); }
            catch (Exception failure) { failed = true; }
        }
        if (!failed) { target = null; fault = false; }
        return response(failed ? RootSessionProtocol.Code.STOP_FAILED : RootSessionProtocol.Code.OK);
    }
    public synchronized RootSessionProtocol.Response shutdown() { closed = true; return off(); }
    public synchronized RootSessionProtocol.Response status() { return response(RootSessionProtocol.Code.OK); }
    public synchronized String targetPackage() { return target == null ? null : target.packageName; }
    private RootSessionProtocol.Response response(RootSessionProtocol.Code code) {
        return new RootSessionProtocol.Response(code, target == null ? RootSessionProtocol.State.OFF
                : fault ? RootSessionProtocol.State.FAULT : RootSessionProtocol.State.ON,
                target == null ? "" : target.packageName, shells.size());
    }
}
