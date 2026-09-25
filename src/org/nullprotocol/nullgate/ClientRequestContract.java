package org.nullprotocol.nullgate;

import java.util.Set;
import org.nullprotocol.nullgate.protocol.ExternalClientContract;

/** Public constants and exact schemas for the first NullGate client protocol. */
public final class ClientRequestContract {
    public static final String ACTION_REQUEST_THEME =
            ExternalClientContract.ACTION_REQUEST_THEME;
    public static final String ACTION_REVOKE =
            ExternalClientContract.ACTION_REVOKE;
    public static final String EXTRA_PROTOCOL_VERSION = ExternalClientContract.EXTRA_PROTOCOL_VERSION;
    public static final String EXTRA_SEED_ARGB = ExternalClientContract.EXTRA_SEED_ARGB;
    public static final String EXTRA_THEME_STYLE = ExternalClientContract.EXTRA_THEME_STYLE;
    public static final String EXTRA_DURATION_MILLIS = ExternalClientContract.EXTRA_DURATION_MILLIS;
    public static final String EXTRA_LEASE_ID = ExternalClientContract.EXTRA_LEASE_ID;
    public static final String EXTRA_DECISION = ExternalClientContract.EXTRA_DECISION;
    public static final String EXTRA_EXPIRES_ELAPSED = ExternalClientContract.EXTRA_EXPIRES_ELAPSED;

    private ClientRequestContract() { }

    public static boolean hasExactRequestKeys(Set<String> actual) {
        return ExternalClientContract.hasExactRequestKeys(actual);
    }

    public static boolean hasExactRevokeKeys(Set<String> actual) {
        return ExternalClientContract.hasExactRevokeKeys(actual);
    }
}
