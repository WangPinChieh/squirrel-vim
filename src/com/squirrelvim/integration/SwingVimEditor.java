package com.squirrelvim.integration;

import com.squirrelvim.engine.VimEditor;
import java.util.function.Supplier;
import javax.swing.JTextArea;
import javax.swing.undo.UndoManager;
import org.fife.ui.rtextarea.RTextArea;

/** Swing implementation of the engine contract. All callers must use the EDT. */
public final class SwingVimEditor implements VimEditor {
    private final JTextArea area;
    private final Supplier<? extends UndoManager> undoManager;
    private RTextArea compoundEditor;

    public SwingVimEditor(JTextArea area, Supplier<? extends UndoManager> undoManager) {
        this.area = area;
        this.undoManager = undoManager;
    }
    @Override public String getText() { return area.getText(); }
    @Override public void replace(int start, int end, String value) { area.select(start, end); area.replaceSelection(value); }
    @Override public int getCaretOffset() { return area.getCaretPosition(); }
    @Override public void setCaretOffset(int offset) { area.setCaretPosition(offset); }
    @Override public void select(int start, int end) { area.select(start, end); }
    @Override public int getSelectionStart() { return area.getSelectionStart(); }
    @Override public int getSelectionEnd() { return area.getSelectionEnd(); }
    @Override public void beginCompoundEdit() {
        if (compoundEditor == null && area instanceof RTextArea syntaxArea) {
            syntaxArea.beginAtomicEdit();
            compoundEditor = syntaxArea;
        }
    }

    @Override public void endCompoundEdit() {
        if (compoundEditor != null) {
            RTextArea syntaxArea = compoundEditor;
            compoundEditor = null;
            syntaxArea.endAtomicEdit();
        }
    }

    @Override public void undo() {
        if (area instanceof RTextArea syntaxArea) {
            syntaxArea.undoLastAction();
        } else {
            UndoManager manager = undoManager.get();
            if (manager != null && manager.canUndo()) manager.undo();
        }
    }

    @Override public void redo() {
        if (area instanceof RTextArea syntaxArea) {
            syntaxArea.redoLastAction();
        } else {
            UndoManager manager = undoManager.get();
            if (manager != null && manager.canRedo()) manager.redo();
        }
    }
}
