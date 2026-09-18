package com.squirrelvim.config;
import java.util.prefs.Preferences;
public final class VimPreferences {
 private static final Preferences S=Preferences.userNodeForPackage(VimPreferences.class);
 private boolean enabled=S.getBoolean("enabled",true), wrap=S.getBoolean("searchWrap",true), indicator=S.getBoolean("indicator",true);
 private String leader=S.get("leader","\\");
 public boolean enabled(){return enabled;} public void enabled(boolean v){enabled=v;S.putBoolean("enabled",v);} public boolean wrap(){return wrap;} public void wrap(boolean v){wrap=v;S.putBoolean("searchWrap",v);} public boolean indicator(){return indicator;} public void indicator(boolean v){indicator=v;S.putBoolean("indicator",v);} public String leader(){return leader;} public void leader(String v){leader=v!=null&&v.length()==1?v:"\\";S.put("leader",leader);}
}
