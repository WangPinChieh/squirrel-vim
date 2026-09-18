# SQuirreL Vim

An in-progress Vim-style editing plugin for SQuirreL SQL Client 5.1.x.

## Current milestone

The plugin discovers the main, additional, and detached SQL editors using
public SQuirreL APIs and provides a deliberately focused modal Vim engine.
It is an MVP, not a full Vim implementation.

See [the command matrix](docs/keybindings.md) for the precise supported keys.

## Requirements

- SQuirreL SQL Client 5.1.x
- Java 17
- Apache Ant 1.9.3 or newer

## Build and test

Download/extract a matching SQuirreL 5.1.0 distribution locally (it is ignored
by Git), then run `ant test` for the observer lifecycle and binary-plugin
compatibility tests. Point the build at that distribution:

```
ant -Dsquirrel.home=/path/to/squirrel/output/dist package
```

The plugin jar is written to `dist/squirrel-vim.jar`. Copy it to SQuirreL's
`plugins` directory, restart SQuirreL, then open a SQL editor. Use **Global
Preferences → SQuirreL Vim** to enable/disable the feature and set the leader
key. To uninstall, exit SQuirreL and remove `plugins/squirrel-vim.jar`.
