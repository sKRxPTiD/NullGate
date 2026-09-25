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
        // An outstanding approval cannot submit after reconciliation advances generation.
        check(!ExternalLeaseStatePolicy.canReserveSubmission(false, 7L, 8L));
        check(ExternalLeaseStatePolicy.canReserveSubmission(false, 8L, 8L));
        check(!ExternalLeaseStatePolicy.canReserveSubmission(true, 8L, 8L));
        check(ExternalLeaseStatePolicy.canSettle(
                "lease-A", "lease-A", "SUBMITTING", 7L, 7L));
        check(!ExternalLeaseStatePolicy.canSettle(
                "lease-A", "lease-A", "RECONCILING", 7L, 8L));
        check(!ExternalLeaseStatePolicy.canSettle(
                "lease-A", "lease-B", "SUBMITTING", 7L, 7L));
        check(ExternalLeaseStatePolicy.canDeliverGrant(
                "lease-A", "lease-A", "ACTIVE", 7L, 7L, 200L, 200L, 100L));
        check(!ExternalLeaseStatePolicy.canDeliverGrant(
                "lease-A", "lease-A", "REVOKING", 7L, 8L, 200L, 200L, 100L));
        check(!ExternalLeaseStatePolicy.canDeliverGrant(
                "lease-A", "lease-A", "ACTIVE", 7L, 7L, 200L, 200L, 200L));
        check(!ExternalLeaseStatePolicy.canDeliverGrant(
                "lease-A", "lease-B", "ACTIVE", 7L, 7L, 200L, 200L, 100L));
        System.out.println("NullGate external lease-state checks: 14 passed");
    }

    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
