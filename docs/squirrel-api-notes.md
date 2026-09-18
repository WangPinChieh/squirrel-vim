# SQuirreL 5.1 API spike

## Source examined

The API was inspected in the official `squirrel-sql-client/squirrel-sql-code`
repository, `sql12` tree, and compiled against the official
`squirrelsql-5.1.0-standard.zip` plain-ZIP distribution released 2026-03-26.
The project home specifies Java 17 as the minimum since SQuirreL 5.0.0.
The source repository did not publish a 5.1.0 Git tag; the distribution JAR is
therefore the authoritative binary API used for this spike.

## Verified integration contract

| Need | Verified public API |
| --- | --- |
| Plugin base/lifecycle | Extend `net.sourceforge.squirrel_sql.client.plugin.DefaultSessionPlugin`; use `load(IApplication)`, `initialize()`, `unload()`, `sessionStarted(ISession)`, and `sessionEnding(ISession)`. |
| Existing/new sessions | `sessionStarted` receives the existing session; return `PluginSessionCallback` to receive additional SQL tabs and detached SQL internal frames. |
| Main SQL editor | `session.getSessionPanel().getMainSQLPaneAPI()` returns `ISQLPanelAPI`. |
| Additional editor | `AdditionalSQLTab.getSQLPanelAPI()` and `SQLInternalFrame.getMainSQLPanelAPI()` return `ISQLPanelAPI`. |
| Text component | `ISQLPanelAPI.getSQLEntryPanel().getTextComponent()` returns `JTextArea`. In the bundled syntax implementation it is `SquirrelRSyntaxTextArea`, a `JTextArea` subclass. |
| SQL execution | `ISQLPanelAPI.executeCurrentSQL()`, `executeAllSQLs()`, and `executeSQL(String)` reuse SQuirreL execution logic. |
| Selection/editor operations | `ISQLPanelAPI` and `ISQLEntryPanel` provide selection, caret, text replacement, undo and document-facing operations. |
| SQL-panel listeners | `ISQLPanelAPI.addSQLPanelListener/removeSQLPanelListener` and SQL execution listener methods are public. |
| Preferences | A plugin may return `IGlobalPreferencesPanel[]` from `DefaultPlugin`; plugin files belong in `getPluginUserSettingsFolder()` / `getPluginAppSettingsFolder()`. |
| Menu/UI contributions | `ISQLPanelAPI` exposes SQL-entry popup menu methods. No general status-bar plugin extension was verified in this spike. |

## Lifecycle design selected for the spike

`SquirrelVimPlugin` is a `DefaultSessionPlugin`. It observes the main SQL
panel immediately in `sessionStarted`, and observes `SQLInternalFrame` and
`AdditionalSQLTab` editors through `PluginSessionCallback`. The observer is
strictly editor-scoped and installs a passive `KeyListener` only on the
verified SQL `JTextArea`; no global dispatcher or non-SQL field is touched.

`sessionEnding` removes listeners for that session and `unload` removes all
remaining listeners. The implementation currently makes no edits and consumes
no events, preserving standard SQuirreL behavior as required for the spike.

## Packaging/build findings

SQuirreL's source build is Ant-based (`sql12/build.xml`), outputs plugin jars
under `output/dist/plugins`, and ships core dependencies in `sql12/core/lib`.
This project therefore supplies an Ant build. `ant package` expects the
extracted official 5.1 distribution at
`squirrel-5.1.0-dist/squirrelsql-5.1.0-standard` by default, or an explicit
`-Dsquirrel.home=<installation>`.

## Reference implementations

`plugins/sqlreplace/.../SQLReplacePlugin.java` demonstrates the same session
callback pattern and listener cleanup. `RSyntaxSQLEntryPanel` verifies that
the normal SQL editor uses `SquirrelRSyntaxTextArea` while exposing it through
the stable `ISQLEntryPanel#getTextComponent()` return type.

## Deferred decisions

No public callback for individual additional-SQL-tab closure was identified
in this spike. Phase 1 must verify whether an `ISQLPanelListener` closure
event is sufficient before claiming per-tab listener cleanup. Status UI,
formatting action lookup, and preference persistence details also remain
Phase 1/4 work.
