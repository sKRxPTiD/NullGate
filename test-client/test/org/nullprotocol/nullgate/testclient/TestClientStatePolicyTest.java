package org.nullprotocol.nullgate.testclient;

public final class TestClientStatePolicyTest {
    public static void main(String[] args) {
        check(TestClientStatePolicy.CLEAN.equals(
                TestClientStatePolicy.normalize(null, false, 0L, 100L)));
        check(TestClientStatePolicy.PENDING.equals(
                TestClientStatePolicy.normalize("PENDING", false, 0L, 100L)));
        check(TestClientStatePolicy.UNKNOWN.equals(
                TestClientStatePolicy.normalize("UNKNOWN", false, 0L, 100L)));
        check(TestClientStatePolicy.ACTIVE.equals(
                TestClientStatePolicy.normalize("ACTIVE", true, 200L, 100L)));
        check(TestClientStatePolicy.UNKNOWN.equals(
                TestClientStatePolicy.normalize("ACTIVE", true, 100L, 100L)));
        check(TestClientStatePolicy.UNKNOWN.equals(
                TestClientStatePolicy.normalize(null, true, 200L, 100L)));
        check(TestClientStatePolicy.canRequest("CLEAN"));
        check(!TestClientStatePolicy.canRequest("PENDING"));
        check(TestClientStatePolicy.needsReconcile("PENDING"));
        check(TestClientStatePolicy.needsReconcile("UNKNOWN"));
        System.out.println("NullGate test-client lifecycle checks: 10 passed");
    }

    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
