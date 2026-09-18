package com.squirrelvim.integration;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.JTextArea;

/**
 * Editor-scoped observation used by the API spike. It deliberately does not
 * consume events: normal SQuirreL editing remains unchanged until Phase 1.
 */
public final class SqlEditorObserver {
    private final Map<JTextArea, KeyAdapter> listeners = new IdentityHashMap<>();

    public void attach(JTextArea editor) {
        if (listeners.containsKey(editor)) {
            return;
        }
        KeyAdapter listener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                // Deliberately passive. This confirms editor-scoped key events
                // reach the plugin without modifying SQuirreL behavior.
            }
        };
        editor.addKeyListener(listener);
        listeners.put(editor, listener);
    }

    public void detach(JTextArea editor) {
        KeyAdapter listener = listeners.remove(editor);
        if (listener != null) {
            editor.removeKeyListener(listener);
        }
    }

    public void detachAll() {
        for (Map.Entry<JTextArea, KeyAdapter> entry : listeners.entrySet()) {
            entry.getKey().removeKeyListener(entry.getValue());
        }
        listeners.clear();
    }

    public int attachedEditorCount() {
        return listeners.size();
    }
}
