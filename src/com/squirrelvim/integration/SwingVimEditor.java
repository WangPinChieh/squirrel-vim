package com.squirrelvim.integration;

import com.squirrelvim.engine.VimEditor;
import javax.swing.JTextArea;

/** Swing implementation of the engine contract. All callers must use the EDT. */
public final class SwingVimEditor implements VimEditor {
    private final JTextArea area;
    public SwingVimEditor(JTextArea area) { this.area = area; }
    @Override public String getText() { return area.getText(); }
    @Override public void replace(int start, int end, String value) { area.select(start, end); area.replaceSelection(value); }
    @Override public int getCaretOffset() { return area.getCaretPosition(); }
    @Override public void setCaretOffset(int offset) { area.setCaretPosition(offset); }
    @Override public void select(int start, int end) { area.select(start, end); }
    @Override public int getSelectionStart() { return area.getSelectionStart(); }
    @Override public int getSelectionEnd() { return area.getSelectionEnd(); }
    @Override public void undo() { area.getActionMap().get("Undo").actionPerformed(null); }
    @Override public void redo() { area.getActionMap().get("Redo").actionPerformed(null); }
}
