package com.money.workspace;

import com.money.platform.runtime.workspace.RuntimeWorkspace;

/**
 * @deprecated Use {@link RuntimeWorkspace}; retained for compatibility while
 *             remaining runtime callers are migrated.
 */
@Deprecated
public class WorkspaceEnv {

    public static String getAppHome() {
        return RuntimeWorkspace.getAppHome();
    }

    public static String getAppData() {
        return RuntimeWorkspace.getAppData();
    }

    public static void prepareDirectories() {
        RuntimeWorkspace.prepareDirectories();
    }
}
