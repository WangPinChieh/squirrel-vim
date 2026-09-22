# SQuirreL Vim — Agent Handoff

## Objective

Build an installable SQuirreL SQL Client 5.1.x plugin that adds practical,
editor-scoped Vim modal editing. The authoritative requirements are in
`squirrel-vim-implementation-plan.md`.

Do not claim the project complete until the full plan's Definition of Done is
verified, including a live SQuirreL installation test.

## Verified API baseline

The workspace contains both the upstream source checkout and the official
SQuirreL 5.1.0 plain-ZIP distribution used to verify compilation:

- `squirrel-sql-code/` — official `squirrel-sql-client/squirrel-sql-code`
  source checkout.
- `squirrel-5.1.0-dist/squirrelsql-5.1.0-standard/` — official 5.1.0 binary
  distribution. Treat its `squirrel-sql.jar` and `lib/` as the binary API
  authority.

Confirmed public APIs:

- Base plugin: `DefaultSessionPlugin`
- Session lifecycle: `sessionStarted(ISession)`, `sessionEnding(ISession)`,
  `PluginSessionCallback`
- Main SQL panel: `session.getSessionPanel().getMainSQLPaneAPI()`
- Additional SQL tabs: `AdditionalSQLTab.getSQLPanelAPI()`
- Detached SQL windows: `SQLInternalFrame.getMainSQLPanelAPI()`
- Editor component: `ISQLPanelAPI.getSQLEntryPanel().getTextComponent()`
  returns `JTextArea` (normally `SquirrelRSyntaxTextArea`)
- SQL execution: `ISQLPanelAPI.executeCurrentSQL()`, `executeSQL(String)`,
  `getSelectedSQLScript()`, `setEntireSQLScript(String, boolean)`

See `docs/squirrel-api-notes.md` for source references and decisions.

## Current implementation

- `src/com/squirrelvim/SquirrelVimPlugin.java`
  - Uses public session callbacks to discover main, additional, and detached
    SQL editors.
  - Attaches `VimEditorController` only to recognized SQL editor text areas.
  - Cleans up controller listeners on session end and plugin unload.
  - Contributes a SQuirreL Global Preferences panel.
- `src/com/squirrelvim/engine/`
  - Editor-independent state machine with Normal, Insert, Visual, Visual
    Line, and forward/backward search modes.
  - Supports basic motions, counts, delete/change/yank/paste, `x`, `r`, `J`,
    search, visual operators, and an unnamed register.
- `src/com/squirrelvim/integration/`
  - `SwingVimEditor` adapts `JTextArea`.
  - `VimEditorController` installs an editor-scoped key listener and a small
    scroll-pane mode indicator.
  - Leader mappings currently: `\e` execute current SQL, `\E` execute the
    selected SQL, `\c` clear the SQL editor; all call SQuirreL APIs.
- `src/com/squirrelvim/config/` and `ui/`
  - Preferences panel supports enabled state, search wrap, indicator, and
    leader key. It currently uses `java.util.prefs`; replace this with
    SQuirreL plugin-user-settings-file persistence before release.

## Current tests and verification

- `test/com/squirrelvim/engine/VimEngineTest.java` — dependency-free engine
  tests for modes, motions/counts, operators/registers, visual/search, empty
  text, Unicode, and `d$`.
- `test/com/squirrelvim/integration/SqlEditorObserverTest.java` — listener
  attach/detach lifecycle.
- `test/com/squirrelvim/PluginBinaryCompatibilityTest.java` — loads the
  plugin against SQuirreL 5.1.0 classes.
- Current artifact: `dist/squirrel-vim.jar`.

The local default `java` is Java 8. Use JDK 17 explicitly:

```text
C:\Progra~1\Java\jdk-17\bin\javac.exe
C:\Progra~1\Java\jdk-17\bin\java.exe
```

`build.xml` is Ant-based, but Ant was not installed in this environment.
Manual JDK-17 compilation and test commands were used. Do not report `ant
test` as verified until Ant is installed and that target passes.

## Packaging dependencies

Build the plugin with JDK 17 and the official SQuirreL 5.1.0 plain-ZIP
distribution. The required compile-time API is:

- `squirrel-5.1.0-dist/squirrelsql-5.1.0-standard/squirrel-sql.jar`

The SQuirreL installation's `lib/*.jar` files are supplied by the host at
runtime. Do **not** shade, copy, or otherwise bundle `squirrel-sql.jar` or
any `lib/*.jar` dependency into `dist/squirrel-vim.jar`; it must contain only
the plugin's own `com/squirrelvim/**` classes (and any future plugin-owned
resources). Compile against the installed `squirrel-sql.jar`, then run the
binary-compatibility test with that JAR on the classpath before handing off a
release artifact.

If the plain-ZIP distribution is absent, obtain the official
`squirrelsql-5.1.0-standard.zip` and extract it under
`squirrel-5.1.0-dist/` before packaging. The archive is a build dependency,
not a release artifact.

## Important remaining work

1. Replace Java registry preferences with normal SQuirreL plugin settings.
2. Add proper SQuirreL undo/redo and compound-edit integration; the current
   Swing action lookup is not sufficient proof of sensible undo grouping.
3. Investigate SQuirreL's formatting action and add `\f` only through a
   verified public action/API.
4. Test/handle individual additional-tab closure, not just session end and
   plugin unload.
5. Add integration tests for multiple sessions/editors, runtime disable and
   re-enable, SQL actions, and non-SQL text-field isolation.
6. Run the manual acceptance script in a clean SQuirreL 5.1.0 installation.
7. Ensure all features listed as supported in `docs/keybindings.md` are
   covered by regression tests; correct implementation or documentation if
   they diverge.
8. Complete README release instructions and clean install/uninstall checks.

## Safety and design rules

- Keep the Vim engine independent of SQuirreL and Swing.
- Use public SQuirreL APIs only; do not invent APIs or introduce reflection
  without documenting an unavoidable reason.
- Keep UI/document mutation on Swing's EDT.
- Never install a global keyboard hook; key handling must remain SQL-editor
  scoped.
- Every listener, UI component, and altered binding must be removable.
- Preserve ordinary SQuirreL typing whenever Vim mode is disabled or Insert
  mode is active.
