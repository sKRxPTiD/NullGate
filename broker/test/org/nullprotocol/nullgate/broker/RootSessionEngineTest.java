package org.nullprotocol.nullgate.broker;

import org.nullprotocol.nullgate.protocol.RootSessionProtocol;

public final class RootSessionEngineTest {
    private static final CallerIdentity TARGET = new CallerIdentity(11001,"example.target", repeat('a'));
    private static final class Shell implements RootSessionEngine.ShellHandle {
        boolean stopped, fail;
        public void stop() throws Exception { if (fail) throw new Exception("stop failed"); stopped = true; }
    }
    private static class Backend implements RootSessionEngine.Backend {
        int starts, stops;
        boolean unavailable, stopFails, startFails;
        String appEffect = "chosen theme";
        Shell last;
        public CallerIdentity targetIdentity(String name) throws Exception {
            if (unavailable) throw new Exception();
            return new CallerIdentity(11001,name,repeat('a'));
        }
        public RootSessionEngine.ShellHandle startShell() throws Exception {
            if (startFails) throw new Exception();
            starts++; return last = new Shell();
        }
        public void forceStopTarget(String name) throws Exception { stops++; if (stopFails) throw new Exception(); }
    }
    public static void main(String[] args) throws Exception {
        Backend b = new Backend(); RootSessionEngine e = new RootSessionEngine(b);
        denied(e,TARGET); check(b.starts == 0);
        check(e.on(TARGET.packageName).state == RootSessionProtocol.State.ON);
        check(e.on(TARGET.packageName).code == RootSessionProtocol.Code.OK && b.starts == 0);
        check(e.on("example.other").code == RootSessionProtocol.Code.BUSY);
        denied(e,new CallerIdentity(11002,TARGET.packageName,repeat('a')));
        denied(e,new CallerIdentity(11001,"example.other",repeat('a')));
        denied(e,new CallerIdentity(11001,TARGET.packageName,repeat('b')));
        RootSessionEngine.ShellHandle shell = e.openShell(TARGET);
        check(e.status().shellCount == 1);
        check(e.off().state == RootSessionProtocol.State.OFF && b.last.stopped && b.stops == 2);
        check("chosen theme".equals(b.appEffect)); denied(e,TARGET);
        check(e.off().state == RootSessionProtocol.State.OFF && b.stops == 2);
        check(e.on(TARGET.packageName).state == RootSessionProtocol.State.ON);
        b.startFails = true;
        try { e.openShell(TARGET); throw new AssertionError(); } catch (Exception expected) { }
        check(e.status().shellCount == 0); b.startFails = false;
        shell = e.openShell(TARGET); b.last.fail = true;
        check(e.off().code == RootSessionProtocol.Code.STOP_FAILED && e.status().shellCount == 1);
        denied(e,TARGET); check(e.on(TARGET.packageName).code == RootSessionProtocol.Code.DENIED);
        b.last.fail = false; check(e.off().state == RootSessionProtocol.State.OFF);
        e.on(TARGET.packageName); b.stopFails = true;
        check(e.off().state == RootSessionProtocol.State.FAULT);
        b.stopFails = false; check(e.off().state == RootSessionProtocol.State.OFF);
        e.on(TARGET.packageName); shell = e.openShell(TARGET); e.closeShell(shell);
        check(b.last.stopped && e.status().shellCount == 0);
        e.shutdown(); check(e.on(TARGET.packageName).code == RootSessionProtocol.Code.DENIED);
        b = new Backend(); b.unavailable = true; e = new RootSessionEngine(b);
        check(e.on(TARGET.packageName).code == RootSessionProtocol.Code.DENIED && e.status().state == RootSessionProtocol.State.OFF);
        raceWithOff();
        System.out.println("NullGate root session: identity, OFF, retention, retry, shutdown and admission-race checks passed");
    }
    private static void raceWithOff() throws Exception {
        java.util.concurrent.CountDownLatch starting = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finishStart = new java.util.concurrent.CountDownLatch(1);
        Backend b = new Backend() {
            @Override public RootSessionEngine.ShellHandle startShell() throws Exception {
                starting.countDown(); finishStart.await(); return super.startShell();
            }
        };
        RootSessionEngine e = new RootSessionEngine(b); e.on(TARGET.packageName);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread open = new Thread(() -> { try { e.openShell(TARGET); } catch (Throwable error) { failure.set(error); } });
        open.start(); check(starting.await(2,java.util.concurrent.TimeUnit.SECONDS));
        Thread off = new Thread(e::off); off.start(); finishStart.countDown();
        open.join(2000); off.join(2000);
        check(!open.isAlive() && !off.isAlive() && failure.get() == null && b.last.stopped && e.status().state == RootSessionProtocol.State.OFF);
    }
    private static void denied(RootSessionEngine e,CallerIdentity caller) throws Exception {
        try { e.openShell(caller); throw new AssertionError("unexpected root admission"); }
        catch (SecurityException expected) { }
    }
    private static String repeat(char c) { char[] text = new char[64]; java.util.Arrays.fill(text,c); return new String(text); }
    private static void check(boolean okay) { if (!okay) throw new AssertionError("root session assertion failed"); }
}
