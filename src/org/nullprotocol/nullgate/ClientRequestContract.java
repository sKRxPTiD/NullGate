package org.nullprotocol.nullgate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Public constants and exact schemas for the first NullGate client protocol. */
public final class ClientRequestContract {
    public static final String ACTION_REQUEST_THEME =
            "org.nullprotocol.nullgate.action.REQUEST_THEME_LEASE";
    public static final String ACTION_REVOKE =
            "org.nullprotocol.nullgate.action.REVOKE_LEASE";
    public static final String EXTRA_PROTOCOL_VERSION = "protocolVersion";
    public static final String EXTRA_SEED_ARGB = "seedArgb";
    public static final String EXTRA_THEME_STYLE = "themeStyle";
    public static final String EXTRA_DURATION_MILLIS = "durationMillis";
    public static final String EXTRA_LEASE_ID = "leaseId";
    public static final String EXTRA_DECISION = "decision";
    public static final String EXTRA_EXPIRES_ELAPSED = "expiresAtElapsedMillis";

    private static final Set<String> REQUEST_KEYS = new HashSet<>(Arrays.asList(
            EXTRA_PROTOCOL_VERSION, EXTRA_SEED_ARGB, EXTRA_THEME_STYLE,
            EXTRA_DURATION_MILLIS));
    private static final Set<String> REVOKE_KEYS = new HashSet<>(Arrays.asList(
            EXTRA_PROTOCOL_VERSION, EXTRA_LEASE_ID));

    private ClientRequestContract() { }

    public static boolean hasExactRequestKeys(Set<String> actual) {
        return actual != null && actual.equals(REQUEST_KEYS);
    }

    public static boolean hasExactRevokeKeys(Set<String> actual) {
        return actual != null && actual.equals(REVOKE_KEYS);
    }
}
