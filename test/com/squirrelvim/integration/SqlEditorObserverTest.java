package com.squirrelvim.integration;

import javax.swing.JTextArea;

/** Dependency-free regression test for editor-scoped listener lifecycle. */
public final class SqlEditorObserverTest {
    public static void main(String[] args) {
        JTextArea first = new JTextArea();
        JTextArea second = new JTextArea();
        SqlEditorObserver observer = new SqlEditorObserver();

        observer.attach(first);
        observer.attach(first);
        require(observer.attachedEditorCount() == 1, "attachment must be idempotent");
        require(first.getKeyListeners().length == 1, "one listener must be installed");

        observer.attach(second);
        require(observer.attachedEditorCount() == 2, "each SQL editor is observed independently");
        observer.detach(first);
        require(first.getKeyListeners().length == 0, "detaching restores the original editor");
        require(observer.attachedEditorCount() == 1, "detach removes only its editor");

        observer.detachAll();
        require(second.getKeyListeners().length == 0, "unload removes every installed listener");
        require(observer.attachedEditorCount() == 0, "unload clears observer state");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
