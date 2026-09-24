package org.nullprotocol.nullgate.broker;

import org.nullprotocol.nullgate.protocol.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Regression cases discovered in the pre-device audit, using deliberately controlled adapters. */
public final class SecurityRegressionTest {
    static final String PKG = "org.nullprotocol.nullgate";
    static final String CERT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final CallerIdentity CALLER = new CallerIdentity(10123, PKG, CERT);
    static final String ID = "lease_0000000001", NONCE = "nonce_0000000001";
    static class Clock implements BrokerEngine.Clock {
        volatile long now = 10000;
        public long elapsedRealtimeMillis() { return now; }
    }
    static class Adapter implements CapabilityAdapter {
        int starts, stops; boolean failStart, failStop;
        public boolean isReady(String target) { return true; }
        public void activate(LeaseEnvelope l) throws Exception {
            starts++; if (failStart) throw new IOException("partial activation");
        }
        public void deactivate(LeaseEnvelope l) throws Exception {
            stops++; if (failStop) throw new IOException("cleanup unavailable");
        }
    }
    static BrokerEngine engine(Clock c) {
        Map<String, Set<Capability>> allow = new HashMap<>();
        allow.put(PKG, EnumSet.of(Capability.NULLGATE_EPHEMERAL_MARKER));
        return new BrokerEngine(new BrokerPolicy(PKG, CERT, 600000, allow), c);
    }
    static LeaseEnvelope lease() { return lease(ID, NONCE, 10000, 20000); }
    static LeaseEnvelope lease(String id, String nonce, long issued, long expiry) {
        return new LeaseEnvelope(id, nonce, PKG, Capability.NULLGATE_EPHEMERAL_MARKER, issued, expiry);
    }
    static void check(boolean ok) { if (!ok) throw new AssertionError("security regression"); }
    static void code(Decision result, Decision.Code expected) { check(result.code == expected); }
    public static void main(String[] args) throws Exception {
        lateActivationIsRolledBack();
        cleanupFailureStaysTrackedAndBlocksGrants();
        partialActivationCleanupFailureIsVisible();
        revokeBeforeIssueCannotResurrect();
        shutdownIsTerminal();
        negativeTimestampCannotOverflow();
        noAdapterCannotGrant();
        revokeCannotRaceActivation();
        failedReplyRollsBack();
        failedAuditRollsBack();
        replyMustMatchOperationAndLease();
        identityRejectsAmbiguousAndUnsupportedCallers();
        expiryKeepsCleanupReceipt();
        System.out.println("NullGate security regressions: 13 passed");
    }
    static void lateActivationIsRolledBack() {
        Clock c = new Clock(); BrokerEngine e = engine(c);
        Adapter a = new Adapter() {
            public void activate(LeaseEnvelope l) { starts++; c.now = l.expiresAtElapsedMillis; }
        };
        code(e.request(a, CALLER, lease()), Decision.Code.EXPIRED);
        check(a.stops == 1 && e.activeCount() == 0);
    }
    static void expiryKeepsCleanupReceipt() {
        Clock c = new Clock(); BrokerEngine e = engine(c); Adapter a = new Adapter();
        check(e.request(a,CALLER,lease()).granted());
        c.now = 20000; check(e.activeCount() == 0);
        code(e.revoke(CALLER,ID),Decision.Code.REVOKED);
        code(e.revoke(CALLER,ID),Decision.Code.REVOKED);
        check(a.stops == 1);
    }
    static void cleanupFailureStaysTrackedAndBlocksGrants() {
        Clock c = new Clock(); BrokerEngine e = engine(c); Adapter a = new Adapter();
        check(e.request(a, CALLER, lease()).granted()); a.failStop = true;
        code(e.revoke(CALLER, ID), Decision.Code.CLEANUP_FAILED);
        check(e.activeCount() == 1);
        code(e.request(new Adapter(), CALLER,
                lease("lease_0000000002","nonce_0000000002",10000,20000)), Decision.Code.CLEANUP_FAILED);
        a.failStop = false; code(e.revoke(CALLER, ID), Decision.Code.REVOKED);
        check(e.activeCount() == 0);
        code(e.request(new Adapter(), CALLER,
                lease("lease_0000000003","nonce_0000000003",10000,20000)), Decision.Code.CLEANUP_FAILED);
    }
    static void partialActivationCleanupFailureIsVisible() {
        Clock c = new Clock(); BrokerEngine e = engine(c); Adapter a = new Adapter();
        a.failStart = a.failStop = true;
        code(e.request(a, CALLER, lease()), Decision.Code.CLEANUP_FAILED);
        check(e.activeCount() == 1); a.failStop = false;
        code(e.revoke(CALLER, ID), Decision.Code.REVOKED);
    }
    static void revokeBeforeIssueCannotResurrect() {
        BrokerEngine e = engine(new Clock()); Adapter a = new Adapter();
        code(e.revoke(CALLER, ID), Decision.Code.NOT_FOUND);
        code(e.request(a, CALLER, lease()), Decision.Code.LEASE_ID_REUSE);
        check(a.starts == 0);
    }
    static void shutdownIsTerminal() {
        BrokerEngine e = engine(new Clock()); Adapter a = new Adapter();
        check(e.request(a, CALLER, lease()).granted()); e.shutdown(); e.shutdown();
        check(a.stops == 1);
        code(e.request(a, CALLER, lease()), Decision.Code.BROKER_CLOSED);
    }
    static void negativeTimestampCannotOverflow() {
        BrokerEngine e = engine(new Clock());
        code(e.request(new Adapter(), CALLER, lease(ID,NONCE,Long.MIN_VALUE,Long.MAX_VALUE)),
                Decision.Code.INVALID_TIME_WINDOW);
    }
    static void noAdapterCannotGrant() {
        code(engine(new Clock()).request(null, CALLER, lease()), Decision.Code.ADAPTER_UNAVAILABLE);
    }
    static void revokeCannotRaceActivation() throws Exception {
        BrokerEngine e = engine(new Clock());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Adapter a = new Adapter() {
            public void activate(LeaseEnvelope l) throws Exception {
                entered.countDown();
                if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("test deadline");
                starts++;
            }
        };
        Thread issue = new Thread(() -> {
            try { check(e.request(a, CALLER, lease()).granted()); }
            catch (Throwable t) { failure.set(t); }
        });
        Thread revoke = new Thread(() -> {
            try { code(e.revoke(CALLER,ID), Decision.Code.REVOKED); }
            catch (Throwable t) { failure.set(t); }
        });
        issue.start(); check(entered.await(3,TimeUnit.SECONDS)); revoke.start(); release.countDown();
        issue.join(3000); revoke.join(3000);
        check(!issue.isAlive() && !revoke.isAlive() && failure.get() == null);
        check(a.starts == 1 && a.stops == 1 && e.activeCount() == 0);
    }
    static BrokerRequest request() {
        return BrokerRequest.issue(ID,NONCE,PKG,Capability.NULLGATE_EPHEMERAL_MARKER.name(),10000,20000);
    }
    static void exchange(BrokerEngine e, Adapter a, BrokerSession.AuditSink audit, OutputStream out)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request().writeTo(new DataOutputStream(bytes));
        TargetGate gate = new TargetGate() {
            public boolean isInstalled(String t) { return true; }
            public CapabilityAdapter adapterFor(String t, Capability cap) { return a; }
        };
        new BrokerSession(e,audit,gate).handle(new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray())),new DataOutputStream(out),CALLER);
    }
    static void failedReplyRollsBack() throws Exception {
        BrokerEngine e = engine(new Clock()); Adapter a = new Adapter();
        try {
            exchange(e,a,(caller,request,code)->{},new OutputStream() {
                public void write(int b) throws IOException { throw new IOException("disconnected"); }
            });
            throw new AssertionError("lost reply accepted");
        } catch (IOException expected) { }
        check(a.stops == 1 && e.activeCount() == 0);
    }
    static void failedAuditRollsBack() throws Exception {
        BrokerEngine e = engine(new Clock()); Adapter a = new Adapter();
        try {
            exchange(e,a,(caller,request,code)->{throw new IllegalStateException("audit failed");},
                    new ByteArrayOutputStream());
            throw new AssertionError("audit failure ignored");
        } catch (IOException expected) { }
        check(a.stops == 1 && e.activeCount() == 0);
    }
    static void replyMustMatchOperationAndLease() throws Exception {
        ResponseValidator.requireMatches(request(),new BrokerResponse("GRANTED",ID));
        for (BrokerResponse bad : new BrokerResponse[] {new BrokerResponse("GRANTED","lease_0000000002"),
                new BrokerResponse("REVOKED",ID),new BrokerResponse("BOGUS",ID)}) {
            try { ResponseValidator.requireMatches(request(),bad); throw new AssertionError(); }
            catch (IOException expected) { }
        }
        try {
            ResponseValidator.requireMatches(BrokerRequest.revoke(ID),new BrokerResponse("GRANTED",ID));
            throw new AssertionError();
        } catch (IOException expected) { }
    }
    static void identityRejectsAmbiguousAndUnsupportedCallers() throws Exception {
        for (int uid : new int[] {0,20000,99000,110123})
            denied(uid,new String[]{PKG},new String[]{CERT});
        denied(10123,new String[]{PKG,"org.other.app"},new String[]{CERT});
        denied(10123,new String[]{PKG},new String[]{CERT,CERT});
        try { new CallerIdentity(10123,PKG,"aa"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { }
    }
    static void denied(int uid,String[] names,String[] certs) throws Exception {
        VerifiedCallerResolver r = new VerifiedCallerResolver(PKG,CERT,new VerifiedCallerResolver.PackageEvidence(){
            public String[] packagesForUid(int id){return names;}
            public String[] currentSignerSha256(String name){return certs;}
        });
        try { r.resolve(uid); throw new AssertionError("identity accepted"); }
        catch (SecurityException expected) { }
    }
}
