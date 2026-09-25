package org.nullprotocol.nullgate;

/** Pure elapsed-time request-window policy; persistence remains in the controller. */
public final class ExternalRequestRatePolicy {
    public static final class Decision {
        public final boolean allowed;
        public final long windowStartElapsed;
        public final int requestCount;

        private Decision(boolean allowed, long windowStartElapsed, int requestCount) {
            this.allowed = allowed;
            this.windowStartElapsed = windowStartElapsed;
            this.requestCount = requestCount;
        }
    }

    private ExternalRequestRatePolicy() { }

    public static Decision evaluate(long nowElapsed, long savedWindowStartElapsed,
            int savedRequestCount, long windowMillis, int maximumRequests) {
        if (nowElapsed < 0 || windowMillis <= 0 || maximumRequests <= 0)
            throw new IllegalArgumentException("invalid request-rate policy");
        boolean reset = savedWindowStartElapsed < 0 || savedRequestCount < 0
                || nowElapsed < savedWindowStartElapsed
                || nowElapsed - savedWindowStartElapsed >= windowMillis;
        if (reset) return new Decision(true, nowElapsed, 1);
        if (savedRequestCount >= maximumRequests)
            return new Decision(false, savedWindowStartElapsed, savedRequestCount);
        return new Decision(true, savedWindowStartElapsed, savedRequestCount + 1);
    }
}
