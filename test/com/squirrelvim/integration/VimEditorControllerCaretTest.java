package com.squirrelvim.integration;

import java.awt.event.KeyEvent;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.Caret;

/** Regression test for mode-specific, editor-scoped caret presentation. */
public final class VimEditorControllerCaretTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = new JTextArea("select 1");
            Caret original = area.getCaret();
            VimEditorController controller = new VimEditorController(area, null, () -> {}, () -> {}, () -> {});

            controller.setEnabled(true);
            require(area.getCaret() instanceof VimCaret, "Vim mode installs its caret only on the SQL editor");
            require(!((VimCaret) area.getCaret()).isInsertMode(), "Normal mode uses the block caret");

            type(controller, area, 'i');
            require(((VimCaret) area.getCaret()).isInsertMode(), "Insert mode uses the thin caret");

            pressEscape(controller, area);
            require(!((VimCaret) area.getCaret()).isInsertMode(), "Escape restores the block caret");

            controller.dispose();
            require(area.getCaret() == original, "Disposal restores the host caret");
        });
    }

    private static void type(VimEditorController controller, JTextArea area, char character) {
        for (java.awt.event.KeyListener listener : area.getKeyListeners()) {
            listener.keyTyped(new KeyEvent(area, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, character));
        }
    }

    private static void pressEscape(VimEditorController controller, JTextArea area) {
        for (java.awt.event.KeyListener listener : area.getKeyListeners()) {
            listener.keyPressed(new KeyEvent(area, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
