package com.money.workspace;

import com.money.platform.runtime.workspace.RuntimeWorkspace;

/**
 * MoneyPOS 系统级守护者门面 (V3.0 工业级底座)
 */
public class AppWorkspace {

    public static void init() {
        RuntimeWorkspace.initialize();
    }
}
