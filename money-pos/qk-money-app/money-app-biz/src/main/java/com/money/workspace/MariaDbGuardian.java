package com.money.workspace;

import com.money.platform.runtime.database.EmbeddedMariaDbGuardian;

/**
 * Compatibility facade for callers not yet migrated to the runtime package.
 *
 * @deprecated Use {@link EmbeddedMariaDbGuardian} from runtime code.
 */
@Deprecated
public final class MariaDbGuardian {

    public static final int DB_PORT = EmbeddedMariaDbGuardian.DB_PORT;
    public static final String DB_NAME = EmbeddedMariaDbGuardian.DB_NAME;

    private MariaDbGuardian() {
    }

    public static String getDbPassword() {
        return EmbeddedMariaDbGuardian.getDbPassword();
    }

    public static void start() {
        EmbeddedMariaDbGuardian.start();
    }
}
