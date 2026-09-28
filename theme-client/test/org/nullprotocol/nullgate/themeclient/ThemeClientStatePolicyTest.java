package org.nullprotocol.nullgate.themeclient;

public final class ThemeClientStatePolicyTest {
    public static void main(String[] args) {
        check(ThemeClientStatePolicy.CLEAN.equals(
                ThemeClientStatePolicy.normalize(null, false, 0L, 100L)));
        check(ThemeClientStatePolicy.PENDING.equals(
                ThemeClientStatePolicy.normalize("PENDING", false, 0L, 100L)));
        check(ThemeClientStatePolicy.UNKNOWN.equals(
                ThemeClientStatePolicy.normalize("UNKNOWN", false, 0L, 100L)));
        check(ThemeClientStatePolicy.ACTIVE.equals(
                ThemeClientStatePolicy.normalize("ACTIVE", true, 200L, 100L)));
        check(ThemeClientStatePolicy.UNKNOWN.equals(
                ThemeClientStatePolicy.normalize("ACTIVE", true, 100L, 100L)));
        check(ThemeClientStatePolicy.UNKNOWN.equals(
                ThemeClientStatePolicy.normalize(null, true, 200L, 100L)));
        check(ThemeClientStatePolicy.isExpiredActive("ACTIVE", true, 100L, 100L));
        check(ThemeClientStatePolicy.isExpiredActive("ACTIVE", true, 100L, 101L));
        check(!ThemeClientStatePolicy.isExpiredActive("ACTIVE", true, 101L, 100L));
        check(!ThemeClientStatePolicy.isExpiredActive("ACTIVE", false, 100L, 101L));
        check(!ThemeClientStatePolicy.isExpiredActive("UNKNOWN", true, 100L, 101L));
        check(ThemeClientStatePolicy.canRequest("CLEAN"));
        check(!ThemeClientStatePolicy.canRequest("PENDING"));
        check(ThemeClientStatePolicy.needsReconcile("PENDING"));
        check(ThemeClientStatePolicy.needsReconcile("UNKNOWN"));
        System.out.println("NullGate theme-client lifecycle checks: 15 passed");
    }

    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
