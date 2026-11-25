package com.squirrelvim;

import com.squirrelvim.integration.VimEditorController;
import java.awt.event.KeyEvent;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.sourceforge.squirrel_sql.client.session.mainpanel.SquirrelDefaultUndoManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import net.sourceforge.squirrel_sql.client.plugin.IPlugin;

/** Verifies that the spike class loads and instantiates against SQuirreL 5.1. */
public final class PluginBinaryCompatibilityTest {
    public static void main(String[] args) throws Exception {
        require(IPlugin.class.isAssignableFrom(SquirrelVimPlugin.class),
                "plugin must implement the SQuirreL plugin contract");
        IPlugin plugin = new SquirrelVimPlugin();
        require("squirrelvim".equals(plugin.getInternalName()), "internal name is stable");
        require("0.1.0-api-spike".equals(plugin.getVersion()), "spike version is exposed");
        SwingUtilities.invokeAndWait(PluginBinaryCompatibilityTest::undoInSqlEditors);
        SwingUtilities.invokeAndWait(PluginBinaryCompatibilityTest::delimitedChangesInSqlEditors);
    }

    private static void delimitedChangesInSqlEditors() {
        String[][] pairs = { {"'", "'", "'"}, {"\"", "\"", "\""},
                {"(", ")", "()b"}, {"[", "]", "[]"}, {"{", "}", "{}B"} };
        for (String[] pair : pairs) {
            for (char key : pair[2].toCharArray()) {
                for (String contents : new String[] { "old value", "", "\nold\nvalue\n" }) {
                    if (pair[0].equals(pair[1]) && contents.contains("\n")) continue;
                    String original = "select " + pair[0] + contents + pair[1];
                    RSyntaxTextArea area = new RSyntaxTextArea();
                    area.setText(original); area.discardAllEdits(); area.setCaretPosition(7);
                    VimEditorController controller = new VimEditorController(area, null, () -> {}, () -> {}, () -> {});
                    controller.setEnabled(true);
                    type(area, 'c'); type(area, 'i'); type(area, key);
                    require(("select " + pair[0] + pair[1]).equals(area.getText()), "ci preserves delimiters: " + key);
                    require(controller.getMode() == com.squirrelvim.engine.VimMode.INSERT, "ci enters Insert: " + key);
                    require(area.getCaretPosition() == 8, "ci places caret between delimiters: " + key);
                    area.replaceSelection("new"); area.replaceSelection(" value");
                    press(area, KeyEvent.VK_ESCAPE, 0);
                    String changed = "select " + pair[0] + "new value" + pair[1];
                    require(changed.equals(area.getText()), "Insert replaces contents: " + key);
                    type(area, 'u'); require(original.equals(area.getText()), "one undo restores contents: " + key);
                    press(area, KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK);
                    require(changed.equals(area.getText()), "redo restores change: " + key);
                    controller.dispose();
                }
            }
        }
    }

    private static void undoInSqlEditors() {
        RSyntaxTextArea syntaxArea = new RSyntaxTextArea();
        syntaxArea.setText("select from");
        syntaxArea.discardAllEdits();
        syntaxArea.setCaretPosition(0);
        VimEditorController controller = new VimEditorController(syntaxArea, null, () -> {}, () -> {}, () -> {});
        controller.setEnabled(true);
        type(syntaxArea, 'u');
        require("select from".equals(syntaxArea.getText()), "empty history leaves text intact");

        type(syntaxArea, 'c');
        type(syntaxArea, 'w');
        syntaxArea.replaceSelection("foo");
        press(syntaxArea, KeyEvent.VK_ESCAPE, 0);
        require("foofrom".equals(syntaxArea.getText()), "change followed by Insert edits the SQL");
        type(syntaxArea, 'u');
        require("select from".equals(syntaxArea.getText()), "one undo restores both parts of the change");
        press(syntaxArea, KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK);
        require("foofrom".equals(syntaxArea.getText()), "redo restores the complete change");

        syntaxArea.setText("select 'old value'");
        syntaxArea.discardAllEdits();
        syntaxArea.setCaretPosition(11);
        type(syntaxArea, 'c');
        type(syntaxArea, 'i');
        type(syntaxArea, '\'');
        require("select ''".equals(syntaxArea.getText()), "ci' preserves the SQL quotes");
        require(controller.getMode() == com.squirrelvim.engine.VimMode.INSERT, "ci' enters Insert mode");
        syntaxArea.replaceSelection("new value");
        press(syntaxArea, KeyEvent.VK_ESCAPE, 0);
        require("select 'new value'".equals(syntaxArea.getText()), "Insert replaces quoted contents");
        type(syntaxArea, 'u');
        require("select 'old value'".equals(syntaxArea.getText()), "one undo restores the quoted change");
        press(syntaxArea, KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK);
        require("select 'new value'".equals(syntaxArea.getText()), "redo restores the quoted change");

        syntaxArea.setText("select\n1\nfrom");
        syntaxArea.discardAllEdits();
        syntaxArea.setCaretPosition(0);
        type(syntaxArea, '2');
        type(syntaxArea, 'J');
        require("select 1 from".equals(syntaxArea.getText()), "counted join edits two lines");
        type(syntaxArea, 'u');
        require("select\n1\nfrom".equals(syntaxArea.getText()), "one undo restores both joins");

        syntaxArea.setCaretPosition(0);
        type(syntaxArea, 'c');
        type(syntaxArea, 'w');
        controller.setEnabled(false);
        controller.setEnabled(true);
        press(syntaxArea, KeyEvent.VK_ESCAPE, 0);
        type(syntaxArea, 'u');
        require("select\n1\nfrom".equals(syntaxArea.getText()), "disabling Vim closes a pending edit");

        JTextArea plainArea = new JTextArea("select");
        SquirrelDefaultUndoManager manager = new SquirrelDefaultUndoManager();
        plainArea.getDocument().addUndoableEditListener(manager);
        VimEditorController plainController = new VimEditorController(plainArea, null, () -> {}, () -> {},
                () -> {}, () -> manager);
        plainController.setEnabled(true);
        type(plainArea, 'x');
        require("elect".equals(plainArea.getText()), "delete edits the plain SQL area");
        type(plainArea, 'u');
        require("select".equals(plainArea.getText()), "plain SQL area uses its SQuirreL undo manager");
    }

    private static void type(JTextArea area, char key) {
        KeyEvent event = new KeyEvent(area, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, key);
        area.getKeyListeners()[0].keyTyped(event);
        require(event.isConsumed(), "Vim handles typed command " + key);
    }

    private static void press(JTextArea area, int key, int modifiers) {
        KeyEvent event = new KeyEvent(area, KeyEvent.KEY_PRESSED, 0, modifiers, key, KeyEvent.CHAR_UNDEFINED);
        area.getKeyListeners()[0].keyPressed(event);
        require(event.isConsumed(), "Vim handles pressed key " + key);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
