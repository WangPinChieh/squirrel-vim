package com.squirrelvim.integration;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;

/** A thin insertion caret and a Vim-like block caret for command modes. */
final class VimCaret extends DefaultCaret {
    private boolean insertMode;

    VimCaret(boolean insertMode) {
        this.insertMode = insertMode;
    }

    void setInsertMode(boolean insertMode) {
        if (this.insertMode == insertMode) return;
        this.insertMode = insertMode;
        repaint();
    }

    boolean isInsertMode() { return insertMode; }

    @Override
    public void paint(Graphics graphics) {
        if (insertMode) {
            super.paint(graphics);
            return;
        }
        JTextComponent component = getComponent();
        if (component == null || !isVisible()) return;
        try {
            Rectangle bounds = modelBounds(component, getDot());
            if (bounds == null) return;
            int width = blockWidth(component, getDot());
            graphics.setColor(component.getCaretColor());
            graphics.fillRect(bounds.x, bounds.y, width, bounds.height);
        } catch (Exception ignored) {
            // A document change can briefly make the caret position unpaintable.
        }
    }

    @Override
    protected synchronized void damage(Rectangle rectangle) {
        if (rectangle == null) return;
        JTextComponent component = getComponent();
        int width = component == null || insertMode ? 1 : blockWidth(component, getDot());
        x = rectangle.x;
        y = rectangle.y;
        height = rectangle.height;
        this.width = Math.max(1, width);
        repaint();
    }

    private static Rectangle modelBounds(JTextComponent component, int offset) throws Exception {
        Shape shape = component.modelToView2D(offset);
        return shape == null ? null : shape.getBounds();
    }

    private static int blockWidth(JTextComponent component, int offset) {
        try {
            if (offset < component.getDocument().getLength()) {
                String character = component.getDocument().getText(offset, 1);
                return Math.max(1, component.getFontMetrics(component.getFont()).stringWidth(character));
            }
        } catch (Exception ignored) {
            // Use the space width at end-of-document or during document mutation.
        }
        return Math.max(1, component.getFontMetrics(component.getFont()).charWidth(' '));
    }
}
