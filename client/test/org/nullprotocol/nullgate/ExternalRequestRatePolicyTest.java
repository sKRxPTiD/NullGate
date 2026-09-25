package org.nullprotocol.nullgate;

public final class ExternalRequestRatePolicyTest {
    public static void main(String[] args) {
        ExternalRequestRatePolicy.Decision first = evaluate(10_000, -1, -1);
        check(first.allowed && first.windowStartElapsed == 10_000 && first.requestCount == 1);
        ExternalRequestRatePolicy.Decision second = evaluate(20_000, 10_000, 1);
        check(second.allowed && second.windowStartElapsed == 10_000 && second.requestCount == 2);
        ExternalRequestRatePolicy.Decision limited = evaluate(20_000, 10_000, 6);
        check(!limited.allowed && limited.windowStartElapsed == 10_000
                && limited.requestCount == 6);
        ExternalRequestRatePolicy.Decision boundary = evaluate(70_000, 10_000, 6);
        check(boundary.allowed && boundary.windowStartElapsed == 70_000
                && boundary.requestCount == 1);
        ExternalRequestRatePolicy.Decision reboot = evaluate(5_000, 10_000, 6);
        check(reboot.allowed && reboot.windowStartElapsed == 5_000
                && reboot.requestCount == 1);
        check(evaluate(20_000, -1, 4).requestCount == 1);
        check(evaluate(20_000, 10_000, -1).requestCount == 1);
        denies(() -> ExternalRequestRatePolicy.evaluate(10_000, 10_000, 0, 0, 6));
        System.out.println("NullGate external request-rate checks: 8 passed");
    }

    private static ExternalRequestRatePolicy.Decision evaluate(
            long now, long start, int count) {
        return ExternalRequestRatePolicy.evaluate(now, start, count, 60_000L, 6);
    }

    private static void denies(Runnable action) {
        try { action.run(); throw new AssertionError("invalid rate policy accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("request-rate assertion failed");
    }
}
