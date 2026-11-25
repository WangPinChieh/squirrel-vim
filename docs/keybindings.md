# SQuirreL Vim keybindings

| Command | Supported | Notes |
|---|---|---|
| `i I a A o O Esc` | Yes | Insert-mode transitions |
| `v V` | Yes | Character and line visual modes |
| `h j k l` | Yes | Basic cursor motion |
| `f<char>` | Yes | In Normal mode, find the next occurrence of a character on the current line. A count selects a later occurrence. |
| `w b e` | Yes | Word motion; word characters are letters, digits, `_` |
| `0 ^ $ gg G { } Ctrl-d Ctrl-u` | Yes | Line, document, paragraph and half-page motions |
| counts | Yes | Examples: `3j`, `5w`, `3dd`, `2dw`, `10G` |
| `x dd D dw de d$` | Mostly | Composable delete supports motions; `d$` and `D` supported |
| `cc C cw` | Mostly | Composable change supports motions |
| `viw` | Yes | Select the word under the cursor. On whitespace or punctuation, select the contiguous group of that type on the current line. |
| `diw ciw` | Yes | Delete or change the inner word under the cursor; counts are supported |
| `ci' di' yi'` | Yes | Change, delete, or yank inside single quotes on the current line. Keep the quotes. Use the enclosing pair or the next pair on the line. SQL doubled quotes and backslash escapes are supported; counts are not supported. |
| `ci" di" yi"` | Yes | Same as single quotes, for double quotes. Doubled quotes and backslash escapes are supported; counts are not supported. |
| `ci( di( yi(`, `ci[ di[ yi[`, `ci{ di{ yi{` | Yes | Normal-mode operations inside matching brackets. Keep both brackets. Pairs can span lines. Select the innermost enclosing pair, or the next pair that starts on the current line. Ignore brackets inside single/double quotes and SQL `--` or `/* ... */` comments. Change/delete removes all contents, including line breaks. |
| `i) i] i} ib iB` after `c`, `d`, or `y` | Yes | Closing brackets are aliases for opening brackets. `ib` means `i(`; `iB` means `i{`. Counts select enclosing levels, for example `2ci(` or `c2i(`. No edit occurs if that enclosing level is absent. |
| `yy Y p P` | Yes | One unnamed character/line register |
| `u Ctrl-r` | Yes | In Normal mode, `u` undoes the last edit and `Ctrl-r` redoes it. Both use the SQL editor's native undo history. |
| `r<char>` | Yes | Replace one character |
| `J` | Yes | Join lines |
| `/ ? n N` | Yes | Literal search, optional wrap |
| `<Leader>e` | Yes | Default leader `\`; calls SQuirreL execute-current-SQL |
| `<Leader>E` | Yes | Executes the selected SQL through SQuirreL |
| `<Leader>c` | Yes | Clears the current SQL editor through SQuirreL |
| `<Leader>f` | No | Formatting action lookup remains pending |
| other text objects, mappings, macros, Ex commands | No | Explicitly out of MVP scope |

The plugin handles keys only on SQL editor text areas. Disable it at runtime
from **Global Preferences → SQuirreL Vim** to restore normal editor behavior.
