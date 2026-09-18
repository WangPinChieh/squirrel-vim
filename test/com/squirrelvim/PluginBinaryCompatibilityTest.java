package com.squirrelvim;

import net.sourceforge.squirrel_sql.client.plugin.IPlugin;

/** Verifies that the spike class loads and instantiates against SQuirreL 5.1. */
public final class PluginBinaryCompatibilityTest {
    public static void main(String[] args) {
        require(IPlugin.class.isAssignableFrom(SquirrelVimPlugin.class),
                "plugin must implement the SQuirreL plugin contract");
        IPlugin plugin = new SquirrelVimPlugin();
        require("squirrelvim".equals(plugin.getInternalName()), "internal name is stable");
        require("0.1.0-api-spike".equals(plugin.getVersion()), "spike version is exposed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
