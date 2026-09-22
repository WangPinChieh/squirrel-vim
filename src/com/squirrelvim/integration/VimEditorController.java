package com.squirrelvim.integration;

import com.squirrelvim.engine.VimEngine;
import com.squirrelvim.engine.VimMode;
import com.squirrelvim.config.VimPreferences;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

/** Installs Vim handling only on one recognized SQL editor. */
public final class VimEditorController {
    private final JTextArea area;
    private final VimEngine engine;
    private final KeyAdapter listener;
    private final JScrollPane scrollPane;
    private final Component originalCorner;
    private final JLabel indicator = new JLabel();
    private final Runnable executeCurrent;
    private final Runnable executeSelection;
    private final Runnable clearEditor;
    private boolean enabled;
    private boolean showIndicator = true;
    private String leader = "\\";
    private boolean leaderPending;

    public VimEditorController(JTextArea area, JScrollPane scrollPane, Runnable executeCurrent, Runnable executeSelection, Runnable clearEditor) {
        this.area = area;
        this.scrollPane = scrollPane;
        this.executeCurrent = executeCurrent;
        this.executeSelection = executeSelection;
        this.clearEditor = clearEditor;
        this.originalCorner = scrollPane == null ? null : scrollPane.getCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER);
        this.engine = new VimEngine(new SwingVimEditor(area));
        this.listener = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { pressed(e); }
            @Override public void keyTyped(KeyEvent e) { typed(e); }
        };
    }
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) { area.addKeyListener(listener); if (showIndicator) installIndicator(); updateIndicator(); }
        else { area.removeKeyListener(listener); removeIndicator(); }
    }
    public boolean isEnabled() { return enabled; }
    public VimMode getMode() { return engine.getMode(); }
    public void dispose() { setEnabled(false); }
    public void applyPreferences(VimPreferences preferences) { engine.setSearchWrap(preferences.wrap()); showIndicator=preferences.indicator(); leader=preferences.leader(); if(enabled){removeIndicator();if(showIndicator)installIndicator();} }

    private void pressed(KeyEvent e) {
        if (!enabled) return;
        String key = null;
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) key = "<Esc>";
        else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_R) key = "<C-r>";
        else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D) key = "<C-d>";
        else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_U) key = "<C-u>";
        else if (e.getKeyCode() == KeyEvent.VK_ENTER && (engine.getMode() == VimMode.SEARCH_FORWARD || engine.getMode() == VimMode.SEARCH_BACKWARD)) key = "<Enter>";
        if (key != null) { engine.key(key); e.consume(); updateIndicator(); }
    }
    private void typed(KeyEvent e) {
        if (!enabled || e.isConsumed() || e.isControlDown() || e.isAltDown()) return;
        if (engine.getMode() != VimMode.INSERT) {
            String key = String.valueOf(e.getKeyChar());
            if (leaderPending) { leaderPending=false; if ("e".equals(key)) executeCurrent.run(); else if ("E".equals(key)) executeSelection.run(); else if ("c".equals(key)) clearEditor.run(); e.consume(); return; }
            if (key.equals(leader)) { leaderPending=true; e.consume(); return; }
            engine.key(key); e.consume(); updateIndicator();
        }
    }
    private void installIndicator() { if (scrollPane != null) scrollPane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, indicator); }
    private void removeIndicator() { if (scrollPane != null) scrollPane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, originalCorner); }
    private void updateIndicator() { indicator.setText(" -- " + engine.getMode().name().replace('_', '-') + " -- "); }
}
