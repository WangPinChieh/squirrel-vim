package com.squirrelvim.ui;
import com.squirrelvim.SquirrelVimPlugin; import com.squirrelvim.config.VimPreferences; import java.awt.*; import javax.swing.*; import net.sourceforge.squirrel_sql.client.IApplication; import net.sourceforge.squirrel_sql.client.preferences.IGlobalPreferencesPanel;
public final class VimPreferencesPanel implements IGlobalPreferencesPanel {
 private final SquirrelVimPlugin plugin; private final VimPreferences prefs; private final JCheckBox on,wrap,indicator; private final JTextField leader;
 public VimPreferencesPanel(SquirrelVimPlugin p,VimPreferences v){plugin=p;prefs=v;on=new JCheckBox("Enable Vim mode",v.enabled());wrap=new JCheckBox("Wrap search",v.wrap());indicator=new JCheckBox("Show mode indicator",v.indicator());leader=new JTextField(v.leader(),2);}
 public void initialize(IApplication a){} public void uninitialize(IApplication a){} public void applyChanges(){prefs.enabled(on.isSelected());prefs.wrap(wrap.isSelected());prefs.indicator(indicator.isSelected());prefs.leader(leader.getText());plugin.applyPreferences();} public String getTitle(){return "SQuirreL Vim";} public String getHint(){return "Vim settings for SQL editors";} public Component getPanelComponent(){JPanel p=new JPanel(new GridLayout(0,1));p.add(on);p.add(wrap);p.add(indicator);p.add(new JLabel("Leader key"));p.add(leader);return p;}
}
