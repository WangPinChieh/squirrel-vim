package com.squirrelvim.engine;

/** Small editor contract; deliberately independent of SQuirreL and Swing. */
public interface VimEditor {
    String getText();
    void replace(int start, int end, String value);
    int getCaretOffset();
    void setCaretOffset(int offset);
    void select(int start, int end);
    int getSelectionStart();
    int getSelectionEnd();
    void undo();
    void redo();
}
