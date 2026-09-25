package org.nullprotocol.nullgate.broker;

import org.nullprotocol.nullgate.protocol.CapabilityPayload;

public final class SystemThemeSeedAdapterTest {
    private static final class Backend implements SystemThemeSeedAdapter.Backend {
        String value = "original"; boolean available = true, failSnapshot, failApply, failRestore;
        String recorded; boolean hasRecord;
        int restoreCalls, clearCalls;
        int seed; CapabilityPayload.ThemeStyle style;
        public boolean available() { return available; }
        public String snapshot() throws Exception {
            if (failSnapshot) throw new Exception("snapshot"); return value;
        }
        public void recordSnapshot(String snapshot) { recorded=snapshot; hasRecord=true; }
        public void apply(int seed, CapabilityPayload.ThemeStyle style) throws Exception {
            if (failApply) throw new Exception("apply"); this.seed=seed; this.style=style; value="changed";
        }
        public boolean matches(int seed, CapabilityPayload.ThemeStyle style) {
            return value.equals("changed") && this.seed == seed && this.style == style;
        }
        public void restore(String snapshot) throws Exception {
            restoreCalls++; if (failRestore) throw new Exception("restore"); value=snapshot;
        }
        public boolean matchesSnapshot(String snapshot) { return java.util.Objects.equals(value,snapshot); }
        public void clearSnapshotRecord() { clearCalls++; hasRecord=false; recorded=null; }
    }
    public static void main(String[] args) throws Exception {
        appliesAndRestores(); expiresThroughBroker(); snapshotFailureDoesNotWrite(); failedApplyRollsBack();
        cleanupFailureStaysOwned(); rejectsUntypedLease();
        System.out.println("NullGate system-theme adapter tests: 6 passed");
    }
    static void appliesAndRestores() throws Exception {
        Backend b=new Backend(); SystemThemeSeedAdapter a=new SystemThemeSeedAdapter(b); LeaseEnvelope l=lease();
        a.activate(l); check("changed".equals(b.value) && b.hasRecord); a.deactivate(l);
        check("original".equals(b.value) && !b.hasRecord);
    }
    static void expiresThroughBroker() {
        Backend b=new Backend(); final long[] now={10_000};
        java.util.Map<String,java.util.Set<Capability>> allow=new java.util.HashMap<>();
        allow.put("android",java.util.EnumSet.of(Capability.SYSTEM_THEME_SEED_APPLY));
        BrokerEngine e=new BrokerEngine(new BrokerPolicy("org.nullprotocol.nullgate",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",600_000,allow),()->now[0]);
        CallerIdentity c=new CallerIdentity(10123,"org.nullprotocol.nullgate",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        check(e.request(new SystemThemeSeedAdapter(b),c,lease()).granted()); now[0]=20_000;
        check(e.activeCount()==0 && "original".equals(b.value));
    }
    static void failedApplyRollsBack() {
        Backend b=new Backend(); b.failApply=true;
        try { new SystemThemeSeedAdapter(b).activate(lease()); throw new AssertionError(); }
        catch(Exception expected){} check("original".equals(b.value));
    }
    static void snapshotFailureDoesNotWrite() {
        Backend b=new Backend(); b.failSnapshot=true;
        SystemThemeSeedAdapter a=new SystemThemeSeedAdapter(b);
        try { a.activate(lease()); throw new AssertionError(); }
        catch(Exception expected){}
        check("original".equals(b.value) && b.restoreCalls==0 && b.clearCalls==0
                && !b.hasRecord && a.isReady("android"));
    }
    static void cleanupFailureStaysOwned() throws Exception {
        Backend b=new Backend(); SystemThemeSeedAdapter a=new SystemThemeSeedAdapter(b); LeaseEnvelope l=lease();
        a.activate(l); b.failRestore=true;
        try { a.deactivate(l); throw new AssertionError(); } catch(Exception expected){}
        check(!a.isReady("android")); b.failRestore=false; a.deactivate(l); check("original".equals(b.value));
    }
    static void rejectsUntypedLease() {
        LeaseEnvelope l=new LeaseEnvelope("lease_0000000001","nonce_0000000001","android",
                Capability.SYSTEM_THEME_SEED_APPLY,10_000,20_000);
        try { new SystemThemeSeedAdapter(new Backend()).activate(l); throw new AssertionError(); }
        catch(Exception expected){}
    }
    static LeaseEnvelope lease(){return new LeaseEnvelope("lease_0000000001","nonce_0000000001","android",
            Capability.SYSTEM_THEME_SEED_APPLY,10_000,20_000,
            CapabilityPayload.systemThemeSeed(0xff76543a,CapabilityPayload.ThemeStyle.TONAL_SPOT));}
    static void check(boolean v){if(!v)throw new AssertionError("system theme adapter assertion failed");}
}
