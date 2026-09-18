# SQuirreL SQL Client Vim Plugin --- Implementation Plan

## 1. Goal

Build an installable SQuirreL SQL Client plugin that adds practical
Vim-style modal editing to the SQL editor.

The first release should focus on reliable everyday Vim behavior rather
than attempting 100% Vim compatibility. The design should keep the Vim
engine sufficiently isolated so more commands, motions, registers,
mappings, and SQuirreL-specific actions can be added later.

## 2. Target and Assumptions

-   Target SQuirreL SQL Client 5.1.x first.
-   SQuirreL is a Java/Swing application.
-   Integrate with the SQL editor component used by SQuirreL rather than
    implementing a separate editor.
-   Do not replace or fork SQuirreL's SQL editor.
-   Package the result as a normal SQuirreL plugin that can be installed
    in the application's plugin directory.
-   Preserve normal SQuirreL SQL editor behavior while Vim mode is
    disabled.
-   Prefer SQuirreL's public/stable plugin APIs. Avoid reflection or
    private implementation APIs unless there is no viable alternative.
-   Before implementation, verify these assumptions against the exact
    SQuirreL 5.1.x source/API being compiled against.

## 3. Product Scope

### MVP

Implement:

-   Normal mode
-   Insert mode
-   Visual character mode
-   Visual line mode
-   Mode indicator
-   Basic Vim cursor motions
-   Basic operators
-   Undo/redo
-   Yank/delete/paste
-   Search
-   Counts
-   A leader key
-   SQL execution mappings
-   Plugin preferences
-   Enable/disable Vim behavior without restarting SQuirreL

### Initial Vim commands

#### Modes

-   `i`, `I`
-   `a`, `A`
-   `o`, `O`
-   `Esc`
-   `v`
-   `V`

#### Motions

-   `h`, `j`, `k`, `l`
-   `w`, `b`, `e`
-   `0`
-   `^`
-   `$`
-   `gg`
-   `G`
-   `{`, `}`
-   `Ctrl-d`
-   `Ctrl-u`

#### Operators/actions

-   `x`
-   `dd`
-   `D`
-   `dw`
-   `de`
-   `d$`
-   `cc`
-   `C`
-   `cw`
-   `yy`
-   `Y`
-   `p`
-   `P`
-   `u`
-   `Ctrl-r`
-   `r<char>`
-   `J`

Where practical, implement operators compositionally
(`operator + motion`) rather than hard-coding every combination.

#### Counts

Support useful forms such as:

-   `3j`
-   `5w`
-   `3dd`
-   `2dw`
-   `10G`

#### Search

-   `/pattern`
-   `?pattern`
-   `n`
-   `N`

Use the editor/document search facilities where possible.

#### SQL-specific mappings

Provide configurable mappings with sensible defaults, for example:

-   `<Leader>e` --- execute current SQL/current statement
-   `<Leader>E` --- execute selected SQL
-   `<Leader>c` --- clear SQL editor, only if this can be done safely
-   `<Leader>f` --- invoke SQuirreL SQL formatting action if available

Do not duplicate SQL execution logic. Invoke existing SQuirreL
actions/APIs.

Default leader: `\` unless there is a strong integration reason to
choose another key.

## 4. Explicitly Out of Scope for MVP

Do not block the initial release on:

-   Full Vimscript
-   Plugins written for Vim/Neovim
-   Macros
-   Marks
-   Multiple named registers
-   Text objects beyond what is easy to implement cleanly
-   Ex commands beyond a minimal command framework
-   Vim regex compatibility
-   Split windows
-   Vim buffers
-   Vim file management semantics
-   Vim command-line completion
-   Neovim RPC
-   Exact byte-for-byte Vim behavior

These can become later milestones.

## 5. Architecture

Keep SQuirreL integration separate from the Vim editing engine.

Suggested structure:

``` text
squirrel-vim/
├── src/
│   └── .../
│       └── vim/
│           ├── VimPlugin.java
│           ├── integration/
│           │   ├── SquirrelEditorAdapter.java
│           │   ├── SquirrelActionAdapter.java
│           │   └── EditorLifecycleListener.java
│           ├── engine/
│           │   ├── VimEngine.java
│           │   ├── VimMode.java
│           │   ├── KeySequence.java
│           │   ├── CommandParser.java
│           │   ├── Motion.java
│           │   ├── Operator.java
│           │   ├── Register.java
│           │   └── SearchState.java
│           ├── ui/
│           │   ├── ModeIndicator.java
│           │   └── VimPreferencesPanel.java
│           └── config/
│               └── VimPreferences.java
├── test/
├── plugin.xml
├── build.xml
└── README.md
```

Adapt names/layout to SQuirreL's actual plugin conventions.

### VimEngine

Own all modal editing state:

-   current mode
-   pending operator
-   pending count
-   pending multi-key command
-   leader state
-   unnamed register
-   last search
-   last command where required

It should not depend directly on SQuirreL classes.

### Editor Adapter

Define a small abstraction around editor operations, for example:

``` java
interface VimEditor {
    int getCaretOffset();
    void setCaretOffset(int offset);

    String getText();
    String getText(int start, int end);

    void replace(int start, int end, String text);
    void delete(int start, int end);

    int getSelectionStart();
    int getSelectionEnd();
    void select(int start, int end);

    int getLineOfOffset(int offset);
    int getLineStartOffset(int line);
    int getLineEndOffset(int line);
    int getLineCount();

    void undo();
    void redo();
}
```

Implement this abstraction using SQuirreL's actual SQL editor component.

This separation is important for unit testing Vim behavior without
starting SQuirreL.

### Key Handling

Determine the safest Swing integration mechanism during the API spike.

Potential approaches include:

-   editor `InputMap`/`ActionMap`
-   a focused editor key listener
-   a narrowly scoped keyboard dispatcher

Prefer the least invasive approach.

Requirements:

-   Intercept Vim commands in Normal/Visual mode.
-   Preserve normal text entry in Insert mode.
-   `Esc` must reliably leave Insert/Visual mode.
-   Do not globally consume shortcuts intended for other SQuirreL UI
    components.
-   Do not break copy/paste or OS shortcuts unnecessarily.
-   Correctly attach/detach handlers as SQL editor tabs/sessions are
    created and destroyed.

## 6. Command Parsing Model

Avoid implementing commands as a large flat `switch` statement.

Model a command as approximately:

``` text
[count] [operator] [count] motion
```

Examples:

``` text
3w
d2w
2dw
3dd
```

Maintain parser state for incomplete commands:

``` text
d
g
2d
<Leader>
r
```

Reset incomplete state when:

-   the command completes
-   `Esc` is pressed
-   an invalid sequence occurs
-   focus/editor context changes when appropriate

Operator-motion composition should calculate a text range and then apply
the operator.

This enables commands such as `dw`, `d$`, `yw`, `c2w` without
implementing each as an unrelated command.

## 7. Mode Semantics

### Normal

-   Caret represents the Vim cursor.
-   Printable keys are interpreted as commands.
-   Prevent accidental text insertion.

### Insert

-   Delegate ordinary typing/editing to the existing editor.
-   Intercept only Vim-specific escape behavior and any deliberately
    supported Insert-mode mappings.

### Visual

Track the visual anchor separately from the current caret.

Support:

-   character selection
-   line selection
-   movement using Normal-mode motions
-   `d`
-   `c`
-   `y`

Return to Normal mode after an operator completes.

## 8. Undo/Redo

This needs special attention because Vim commands can perform several
Swing document mutations.

A command such as `cw` should ideally appear as one undoable operation.

Investigate SQuirreL/editor undo infrastructure and use compound edits
if supported.

Acceptance criterion:

``` text
cwfoo<Esc>
u
```

should restore the pre-change text in an intuitive single undo
operation.

## 9. Registers and Clipboard

MVP requires an unnamed Vim register.

Store:

-   text
-   whether it is character-wise or line-wise

Commands that delete/change/yank should update it appropriately.

`p` and `P` must respect line-wise versus character-wise contents.

System clipboard integration can be optional/configurable for MVP.

## 10. Search

Create a small search state containing:

-   pattern
-   direction
-   last match

Implement:

-   `/`
-   `?`
-   `n`
-   `N`

Use a lightweight command/search UI appropriate for SQuirreL. Do not
create a blocking dialog for every search if an inline/non-modal
implementation is feasible.

Search should wrap around the document unless disabled by preference.

## 11. SQuirreL Integration

### API Spike --- Do This First

Before writing the Vim engine integration, inspect SQuirreL 5.1.x
source/API and document:

1.  Required plugin base class/interfaces.
2.  Plugin lifecycle methods.
3.  How to detect opened/closed sessions.
4.  How to detect SQL editor creation/destruction.
5.  How to obtain the active SQL editor text component.
6.  Exact editor component class.
7.  How existing SQL execution actions are exposed.
8.  How plugin preferences are normally stored.
9.  How plugins contribute menus/toolbars/status UI.
10. Required plugin metadata and packaging layout.
11. SQuirreL's Java version and build requirements.
12. Existing plugin examples that should be used as reference
    implementations.

Record findings in:

``` text
docs/squirrel-api-notes.md
```

Do not guess API names.

### Editor Lifecycle

Vim behavior must work for:

-   existing SQL editor when plugin initializes
-   newly opened sessions
-   additional SQL tabs/windows if supported
-   editor/tab closure
-   session closure

Avoid memory leaks by removing listeners when an editor is destroyed.

### SQL Actions

Create an integration adapter instead of calling SQuirreL APIs
throughout the Vim engine:

``` java
interface SqlActions {
    void executeCurrentStatement();
    void executeSelection();
    void formatSql();
}
```

Only expose operations that can be reliably mapped to SQuirreL.

## 12. UI

### Mode Indicator

Show the current mode without obscuring SQL content.

Possible labels:

``` text
-- NORMAL --
-- INSERT --
-- VISUAL --
-- V-LINE --
```

Prefer integrating with an existing status area if SQuirreL exposes an
appropriate extension point.

Otherwise use a small unobtrusive component associated with the editor.

### Preferences

Provide settings for at least:

-   Enable Vim mode
-   Leader key
-   Search wrap
-   System clipboard integration
-   SQL execution mappings
-   Show mode indicator

Persist settings using the normal SQuirreL plugin preference mechanism.

## 13. Safety and Compatibility

The plugin must not interfere with non-SQL text fields.

Examples that should continue behaving normally:

-   connection dialogs
-   object-tree filtering
-   preferences
-   search fields
-   table data editors
-   other plugin text fields

Only activate Vim handling for recognized SQL editor components.

On plugin disable/unload:

-   remove key handlers
-   remove listeners
-   restore overridden key bindings
-   remove UI elements
-   leave editor content untouched

## 14. Testing Strategy

### Unit Tests

Most Vim behavior should be testable against an in-memory/fake
`VimEditor`.

Test at minimum:

#### Modes

-   Normal → Insert
-   Insert → Normal
-   Normal → Visual
-   Visual → Normal

#### Motions

-   beginning/end of lines
-   empty lines
-   first/last line
-   words separated by punctuation
-   whitespace
-   multiline SQL
-   Unicode text

#### Operators

-   `dd`
-   `dw`
-   `d$`
-   `cw`
-   `yy`
-   `p`
-   `P`
-   Visual delete/yank/change

#### Counts

-   `3j`
-   `4w`
-   `2dd`
-   `d2w`
-   `2d3w` if supported by the parser

#### Parser

-   incomplete commands
-   invalid sequences
-   reset on `Esc`
-   `gg`
-   leader sequences

#### Search

-   forward
-   backward
-   repeat
-   reverse repeat
-   wrap
-   no match

### Integration Tests

Where practical, verify:

-   plugin loads successfully
-   SQL editor is discovered
-   handlers attach once
-   handlers detach correctly
-   typing works in Insert mode
-   Normal mode does not insert characters
-   SQL execution mappings invoke the correct SQuirreL action
-   opening multiple SQL editors does not share inappropriate per-editor
    state
-   disabling Vim mode restores normal editor behavior

## 15. Manual Acceptance Test

Use a SQL document similar to:

``` sql
SELECT
    customer_id,
    customer_name,
    created_at
FROM customers
WHERE status = 'ACTIVE'
ORDER BY created_at DESC;
```

Verify interactively:

``` text
i
Esc
hjkl
wbe
0^$
gg
G
3j
dw
dd
yy
p
u
Ctrl-r
v
V
/pattern
n
N
<Leader>e
```

Also verify normal SQuirreL keyboard shortcuts still work where they do
not conflict with Vim Normal-mode mappings.

## 16. Build and Packaging

Use the build system/conventions required by the targeted SQuirreL
version.

Deliver an installable artifact and document exactly:

``` text
build
test
package
install
uninstall
```

Do not require developers to manually copy individual `.class` files.

Expected output should be approximately:

``` text
dist/
└── squirrel-vim.jar
```

If SQuirreL requires additional metadata/resources or dependency JARs,
package them according to its documented plugin layout.

## 17. Documentation

Create `README.md` containing:

-   What the plugin does
-   Supported SQuirreL versions
-   Java requirements
-   Installation
-   Uninstallation
-   Enabling/disabling Vim mode
-   Supported commands
-   Default SQL mappings
-   Configuration
-   Known limitations
-   Development/build instructions
-   Troubleshooting

Create:

``` text
docs/keybindings.md
```

with a command compatibility table:

``` text
| Command | Supported | Notes |
|---------|-----------|-------|
| h/j/k/l | Yes | Basic movement |
| w/b/e | Yes | Word motions |
| dd | Yes | Line delete |
| ciw | No | Planned |
```

## 18. Implementation Tasks

### Phase 0 --- Repository/API Research

-   [ ] Obtain/check out the target SQuirreL 5.1.x source.
-   [ ] Identify official plugin examples.
-   [ ] Verify Java/build requirements.
-   [ ] Verify SQL editor implementation.
-   [ ] Verify editor lifecycle APIs.
-   [ ] Verify SQL execution APIs/actions.
-   [ ] Verify preferences API.
-   [ ] Verify plugin packaging.
-   [ ] Write `docs/squirrel-api-notes.md`.
-   [ ] Create the smallest possible plugin and prove it loads.

**Exit criterion:** a minimal plugin loads in SQuirreL and can obtain
the active SQL editor without reflection.

### Phase 1 --- Plugin Skeleton

-   [ ] Create plugin metadata.
-   [ ] Create build.
-   [ ] Implement plugin lifecycle.
-   [ ] Implement preferences storage.
-   [ ] Detect SQL editors.
-   [ ] Attach/detach editor adapters.
-   [ ] Add enable/disable setting.
-   [ ] Add mode indicator.

**Exit criterion:** every SQL editor can display NORMAL/INSERT state and
Vim handling can be enabled/disabled safely.

### Phase 2 --- Vim Engine

-   [ ] Implement `VimMode`.
-   [ ] Implement parser state.
-   [ ] Implement counts.
-   [ ] Implement motions.
-   [ ] Implement Normal/Insert transitions.
-   [ ] Implement Visual mode.
-   [ ] Implement operator-motion range calculation.
-   [ ] Implement delete/change/yank.
-   [ ] Implement unnamed register.
-   [ ] Implement paste.
-   [ ] Implement undo/redo integration.
-   [ ] Add unit tests.

**Exit criterion:** core editing commands pass unit tests without
requiring SQuirreL.

### Phase 3 --- Search and Extended Motions

-   [ ] `/`
-   [ ] `?`
-   [ ] `n`
-   [ ] `N`
-   [ ] `{` / `}`
-   [ ] `gg` / `G`
-   [ ] `Ctrl-d` / `Ctrl-u`
-   [ ] Search wrap preference.
-   [ ] Tests.

### Phase 4 --- SQL Integration

-   [ ] Implement `SqlActions`.
-   [ ] Map execute-current-statement.
-   [ ] Map execute-selection.
-   [ ] Investigate/map SQL formatting.
-   [ ] Implement leader parser.
-   [ ] Make mappings configurable.
-   [ ] Verify SQL actions reuse SQuirreL behavior.

**Exit criterion:** Vim mappings execute SQL exactly through SQuirreL's
existing execution mechanisms.

### Phase 5 --- Hardening

-   [ ] Multiple sessions.
-   [ ] Multiple SQL editor tabs.
-   [ ] Editor recreation.
-   [ ] Plugin disable/re-enable.
-   [ ] Listener cleanup.
-   [ ] Focus handling.
-   [ ] Keyboard shortcut conflicts.
-   [ ] Windows/Linux/macOS modifier behavior where feasible.
-   [ ] Large SQL document performance.
-   [ ] Unicode.
-   [ ] Empty document/line edge cases.
-   [ ] Undo grouping.
-   [ ] Regression tests.

### Phase 6 --- Release

-   [ ] Produce installable JAR/package.
-   [ ] Complete README.
-   [ ] Complete keybinding matrix.
-   [ ] Document known deviations from Vim.
-   [ ] Test clean installation.
-   [ ] Test clean uninstallation.
-   [ ] Tag/version initial release.

## 19. Suggested Post-MVP Backlog

After the core plugin is stable:

-   [ ] `f`, `F`, `t`, `T`
-   [ ] `;`, `,`
-   [ ] `%`
-   [ ] `H`, `M`, `L`
-   [ ] `*`, `#`
-   [ ] `.` repeat
-   [ ] `~`
-   [ ] indentation operators
-   [ ] `>>`, `<<`
-   [ ] text objects: `iw`, `aw`, `i"`, `a"`, `i(`, `a(`
-   [ ] named registers
-   [ ] system clipboard registers
-   [ ] marks
-   [ ] macros
-   [ ] configurable mappings
-   [ ] minimal `:` command line
-   [ ] SQL-aware motions/text objects
-   [ ] jump between SQL statements
-   [ ] select current SQL statement
-   [ ] comment/uncomment SQL mappings

## 20. Design Rules for Codex

1.  Verify SQuirreL APIs from the target source before coding against
    them.
2.  Do not invent SQuirreL API names.
3.  Prefer public plugin APIs over reflection/private classes.
4.  Keep the Vim engine independent from SQuirreL.
5.  Write tests alongside command implementation.
6.  Prefer composable operators and motions over command-specific hacks.
7.  Keep all Swing UI/document mutations on the EDT.
8.  Never install a global keyboard hook when an editor-scoped solution
    is sufficient.
9.  Do not silently override important SQuirreL shortcuts without
    documenting the conflict.
10. Ensure every listener/key binding installed by the plugin can be
    removed.
11. Keep MVP deliberately smaller than full Vim.
12. Make behavior deterministic and document intentional differences
    from Vim.

## 21. Definition of Done

The first release is complete when:

-   The plugin installs into a clean supported SQuirreL installation.
-   SQuirreL starts without plugin errors.
-   Vim behavior activates only in SQL editors.
-   Normal, Insert, Visual, and Visual Line modes work.
-   Core motions/operators/counts work reliably.
-   Yank/delete/paste work.
-   Undo/redo behave sensibly.
-   Search and repeat search work.
-   SQL execution can be triggered with Vim mappings.
-   Multiple SQL editors/sessions are handled safely.
-   Vim mode can be disabled without restarting.
-   Plugin listeners/key bindings are cleaned up correctly.
-   Automated tests cover the Vim engine's core behavior.
-   Installation, supported commands, limitations, and development
    instructions are documented.

## 22. First Instruction to Codex

Start with **Phase 0 only**.

Inspect the exact SQuirreL 5.1.x source/API and produce
`docs/squirrel-api-notes.md`. Then create a minimal plugin that loads
and proves it can obtain and observe the SQL editor.

Do not begin implementing Vim commands until that integration spike
works. This prevents building the Vim engine around guessed SQuirreL
APIs.
