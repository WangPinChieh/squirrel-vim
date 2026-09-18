package com.squirrelvim;

import com.squirrelvim.integration.VimEditorController;
import com.squirrelvim.config.VimPreferences;
import com.squirrelvim.ui.VimPreferencesPanel;
import java.util.IdentityHashMap;
import java.util.Map;
import net.sourceforge.squirrel_sql.client.gui.session.ObjectTreeInternalFrame;
import net.sourceforge.squirrel_sql.client.gui.session.SQLInternalFrame;
import net.sourceforge.squirrel_sql.client.plugin.DefaultSessionPlugin;
import net.sourceforge.squirrel_sql.client.plugin.PluginException;
import net.sourceforge.squirrel_sql.client.plugin.PluginSessionCallback;
import net.sourceforge.squirrel_sql.client.session.ISQLPanelAPI;
import net.sourceforge.squirrel_sql.client.session.ISession;
import net.sourceforge.squirrel_sql.client.session.mainpanel.objecttree.ObjectTreePanel;
import net.sourceforge.squirrel_sql.client.session.mainpanel.sqltab.AdditionalSQLTab;
import net.sourceforge.squirrel_sql.client.preferences.IGlobalPreferencesPanel;

/** SQuirreL session plugin for editor-scoped Vim behavior. */
public final class SquirrelVimPlugin extends DefaultSessionPlugin {
    private final Map<ISQLPanelAPI, VimEditorController> editors = new IdentityHashMap<>();
    private final VimPreferences preferences = new VimPreferences();
    private boolean enabled = preferences.enabled();

    @Override public String getInternalName() { return "squirrelvim"; }
    @Override public String getDescriptiveName() { return "SQuirreL Vim"; }
    @Override public String getAuthor() { return "SQuirreL Vim contributors"; }
    @Override public String getVersion() { return "0.1.0-api-spike"; }
    @Override public boolean allowsSessionStartedInBackground() { return true; }

    @Override
    public void initialize() throws PluginException {
        super.initialize();
    }

    @Override
    public PluginSessionCallback sessionStarted(ISession session) {
        observe(session.getSessionPanel().getMainSQLPaneAPI());
        return new PluginSessionCallback() {
            @Override public void sqlInternalFrameOpened(SQLInternalFrame frame, ISession ignored) {
                observe(frame.getMainSQLPanelAPI());
            }
            @Override public void additionalSQLTabOpened(AdditionalSQLTab tab) {
                observe(tab.getSQLPanelAPI());
            }
            @Override public void objectTreeInternalFrameOpened(ObjectTreeInternalFrame frame, ISession ignored) { }
            @Override public void objectTreeInSQLTabOpened(ObjectTreePanel panel) { }
        };
    }

    @Override
    public void sessionEnding(ISession session) {
        editors.entrySet().removeIf(entry -> {
            if (entry.getKey().getSession().getIdentifier().equals(session.getIdentifier())) {
                entry.getValue().dispose();
                return true;
            }
            return false;
        });
    }

    @Override
    public void unload() {
        editors.values().forEach(VimEditorController::dispose);
        editors.clear();
    }

    private void observe(ISQLPanelAPI panel) {
        if (editors.containsKey(panel)) {
            return;
        }
        VimEditorController controller = new VimEditorController(panel.getSQLEntryPanel().getTextComponent(), panel.getSQLEntryPanel().getTextAreaEmbeddedInScrollPane(), panel::executeCurrentSQL, () -> panel.executeSQL(panel.getSelectedSQLScript()), () -> panel.setEntireSQLScript("", false));
        controller.setEnabled(enabled);
        controller.applyPreferences(preferences);
        editors.put(panel, controller);
    }

    /** Used by the preferences panel and tests; takes effect without restart. */
    public void setVimEnabled(boolean enabled) { this.enabled = enabled; editors.values().forEach(controller -> controller.setEnabled(enabled)); }
    public boolean isVimEnabled() { return enabled; }
    @Override public IGlobalPreferencesPanel[] getGlobalPreferencePanels() { return new IGlobalPreferencesPanel[] {new VimPreferencesPanel(this, preferences)}; }
    public void applyPreferences() { setVimEnabled(preferences.enabled()); editors.values().forEach(c -> c.applyPreferences(preferences)); }
}
