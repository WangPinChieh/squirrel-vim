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
