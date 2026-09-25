package org.nullprotocol.nullgate.protocol;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Shared constants and exact schemas for the app-to-controller protocol. */
public final class ExternalClientContract {
    public static final String ACTION_REQUEST_THEME =
            "org.nullprotocol.nullgate.action.REQUEST_THEME_LEASE";
    public static final String ACTION_REVOKE =
            "org.nullprotocol.nullgate.action.REVOKE_LEASE";
    public static final String ACTION_RECONCILE =
            "org.nullprotocol.nullgate.action.RECONCILE_THEME_LEASE";
    public static final String EXTRA_PROTOCOL_VERSION = "protocolVersion";
    public static final String EXTRA_SEED_ARGB = "seedArgb";
    public static final String EXTRA_THEME_STYLE = "themeStyle";
    public static final String EXTRA_DURATION_MILLIS = "durationMillis";
    public static final String EXTRA_LEASE_ID = "leaseId";
    public static final String EXTRA_DECISION = "decision";
    public static final String EXTRA_EXPIRES_ELAPSED = "expiresAtElapsedMillis";

    private static final Set<String> REQUEST_KEYS = set(
            EXTRA_PROTOCOL_VERSION, EXTRA_SEED_ARGB, EXTRA_THEME_STYLE,
            EXTRA_DURATION_MILLIS);
    private static final Set<String> REVOKE_KEYS = set(
            EXTRA_PROTOCOL_VERSION, EXTRA_LEASE_ID);
    private static final Set<String> RECONCILE_KEYS = set(EXTRA_PROTOCOL_VERSION);
    private static final Set<String> DECISION_KEYS = set(EXTRA_DECISION);
    private static final Set<String> GRANT_KEYS = set(
            EXTRA_DECISION, EXTRA_LEASE_ID, EXTRA_EXPIRES_ELAPSED);

    private ExternalClientContract() { }

    public static boolean hasExactRequestKeys(Set<String> actual) {
        return actual != null && actual.equals(REQUEST_KEYS);
    }
    public static boolean hasExactRevokeKeys(Set<String> actual) {
        return actual != null && actual.equals(REVOKE_KEYS);
    }
    public static boolean hasExactReconcileKeys(Set<String> actual) {
        return actual != null && actual.equals(RECONCILE_KEYS);
    }
    public static boolean hasExactDecisionKeys(Set<String> actual) {
        return actual != null && actual.equals(DECISION_KEYS);
    }
    public static boolean hasExactGrantKeys(Set<String> actual) {
        return actual != null && actual.equals(GRANT_KEYS);
    }

    private static Set<String> set(String... values) {
        return java.util.Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(values)));
    }
}
