# SQuirreL Vim keybindings

| Command | Supported | Notes |
|---|---|---|
| `i I a A o O Esc` | Yes | Insert-mode transitions |
| `v V` | Yes | Character and line visual modes |
| `h j k l` | Yes | Basic cursor motion |
| `w b e` | Yes | Word motion; word characters are letters, digits, `_` |
| `0 ^ $ gg G { } Ctrl-d Ctrl-u` | Yes | Line, document, paragraph and half-page motions |
| counts | Yes | Examples: `3j`, `5w`, `3dd`, `2dw`, `10G` |
| `x dd D dw de d$` | Mostly | Composable delete supports motions; `d$` and `D` supported |
| `cc C cw` | Mostly | Composable change supports motions |
| `diw ciw` | Yes | Delete or change the inner word under the cursor; counts are supported |
| `yy Y p P` | Yes | One unnamed character/line register |
| `u Ctrl-r` | Delegated | Uses SQuirreL editor undo/redo actions when available |
| `r<char>` | Yes | Replace one character |
| `J` | Yes | Join lines |
| `/ ? n N` | Yes | Literal search, optional wrap |
| `<Leader>e` | Yes | Default leader `\`; calls SQuirreL execute-current-SQL |
| `<Leader>E` | Yes | Executes the selected SQL through SQuirreL |
| `<Leader>c` | Yes | Clears the current SQL editor through SQuirreL |
| `<Leader>f` | No | Formatting action lookup remains pending |
| text objects, mappings, macros, Ex commands | No | Explicitly out of MVP scope |

The plugin handles keys only on SQL editor text areas. Disable it at runtime
from **Global Preferences → SQuirreL Vim** to restore normal editor behavior.
