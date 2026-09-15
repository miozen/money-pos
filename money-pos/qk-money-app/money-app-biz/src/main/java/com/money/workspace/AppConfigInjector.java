package com.money.workspace;

import com.money.platform.runtime.workspace.RuntimeWorkspaceConfiguration;

/** @deprecated Use {@link RuntimeWorkspaceConfiguration}. */
@Deprecated
public class AppConfigInjector {

    public static void inject() {
        RuntimeWorkspaceConfiguration.inject();
    }
}
