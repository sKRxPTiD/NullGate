package org.nullprotocol.nullgate;

public final class ExternalActivityOperationTest {
    public static void main(String[] args) {
        ExternalActivityOperation operation = new ExternalActivityOperation(
                ExternalActivityOperation.Kind.REQUEST, "caller/request", 7L);
        check(operation.matches(ExternalActivityOperation.Kind.REQUEST, "caller/request"));
        check(!operation.matches(ExternalActivityOperation.Kind.REVOKE, "caller/request"));
        check(operation.begin());
        check(!operation.begin());

        int[] first = { 0 };
        int[] recreated = { 0 };
        ExternalActivityOperation.Listener oldUi = () -> first[0]++;
        ExternalActivityOperation.Listener newUi = () -> recreated[0]++;
        operation.attach(oldUi);
        operation.detach(oldUi);
        operation.attach(newUi);
        ExternalActivityOperation.Event granted = ExternalActivityOperation.Event.issue(
                "GRANTED", "lease-A", 200L, true);
        operation.publish(granted);
        check(first[0] == 0 && recreated[0] == 1);
        check(operation.pendingEvent() == granted);
        check(operation.claim(granted));
        check(!operation.claim(granted));
        check(operation.pendingEvent() == null);

        operation.continueWithCleanup(granted);
        ExternalActivityOperation.Event revoked =
                ExternalActivityOperation.Event.cleanup("REVOKED", "lease-A");
        operation.publish(revoked);
        check(recreated[0] == 2 && operation.claim(revoked));

        ExternalActivityOperation restarted = new ExternalActivityOperation(
                ExternalActivityOperation.Kind.REQUEST, "caller/request", 8L);
        check(!restarted.isStarted() && restarted.pendingEvent() == null);
        check(ExternalActivityOperation.Event.issueTransportFailure("lease-B").kind
                == ExternalActivityOperation.EventKind.ISSUE_TRANSPORT_FAILURE);
        check(ExternalActivityOperation.Event.cleanupTransportFailure("lease-B").kind
                == ExternalActivityOperation.EventKind.CLEANUP_TRANSPORT_FAILURE);

        ExternalActivityOperation detached = new ExternalActivityOperation(
                ExternalActivityOperation.Kind.RECONCILE, "caller/reconcile", -1L);
        check(detached.begin());
        ExternalActivityOperation.Event result =
                ExternalActivityOperation.Event.cleanup("NOT_FOUND", "lease-C");
        detached.publish(result);
        int[] late = { 0 };
        detached.attach(() -> late[0]++);
        check(late[0] == 1);
        check(detached.pendingEvent() == result && detached.claim(result));
        System.out.println("NullGate external Activity-operation checks: 16 passed");
    }

    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
