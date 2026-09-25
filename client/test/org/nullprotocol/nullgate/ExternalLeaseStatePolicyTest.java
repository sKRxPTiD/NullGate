package org.nullprotocol.nullgate;

public final class ExternalLeaseStatePolicyTest {
    public static void main(String[] args) {
        check(ExternalLeaseStatePolicy.owns("lease-A", "client", 10123,
                "lease-A", "client", 10123));
        check(!ExternalLeaseStatePolicy.owns("lease-B", "client", 10123,
                "lease-A", "client", 10123));
        check(!ExternalLeaseStatePolicy.owns("lease-A", "replacement", 10123,
                "lease-A", "client", 10123));
        check(!ExternalLeaseStatePolicy.owns("lease-A", "client", 10124,
                "lease-A", "client", 10123));
        check(ExternalLeaseStatePolicy.canReturnDelayedGrant(
                "lease-A", "lease-A", "ACTIVE", 200L, 100L));
        check(!ExternalLeaseStatePolicy.canReturnDelayedGrant(
                "lease-A", "lease-B", "ACTIVE", 200L, 100L));
        check(!ExternalLeaseStatePolicy.canReturnDelayedGrant(
                "lease-A", "lease-A", "UNKNOWN", 200L, 100L));
        check(!ExternalLeaseStatePolicy.canReturnDelayedGrant(
                "lease-A", "lease-A", "ACTIVE", 100L, 100L));
        System.out.println("NullGate external lease-state checks: 8 passed");
    }

    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
