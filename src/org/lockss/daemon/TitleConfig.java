/*
 * $Id: TitleConfig.java,v 1.2 2004-01-04 06:14:31 tlipkis Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.daemon;

import java.io.*;
import java.util.*;
import org.lockss.util.*;
import org.lockss.plugin.*;

/**
 * An entry in the title database, specifying a title name, plugin and list
 * of {@link ConfigParamAssignment}s.
 */
public class TitleConfig {
  private String displayName;
  private String pluginName;
  private String pluginVersion = null;
  private List params = null;

  /**
   * Create a TitleConfig associating a title with a plugin.
   * @param displayName the title string
   * @param plugin the plugin that handles the title.
   */
  public TitleConfig(String displayName, Plugin plugin) {
    this(displayName, plugin.getPluginId());
  }

  /**
   * Create a TitleConfig associating a title with a plugin name.
   * @param displayName the title string
   * @param pluginName the name of the plugin that handles the title
   */
  public TitleConfig(String displayName, String pluginName) {
    this.pluginName = pluginName;
    this.displayName = displayName;
  }

  /**
   * Set the required plugin version
   * @param pluginVersion the plugin version
   */
  public void setPluginVersion(String pluginVersion) {
    this.pluginVersion = pluginVersion;
  }

  /**
   * Set the parameter value list
   * @param params List of {@link ConfigParamAssignment}s
   */
  public void setParams(List params) {
    this.params = params;
  }

  /**
   * @return the parameter assignments
   */
  public List getParams() {
    return params;
  }

  /**
   * Return the title string
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Return the plugin name
   */
  public String getPluginName() {
    return pluginName;
  }

  /**
   * Return the minimum plugin version
   */
  public String getPluginVersion() {
    return pluginVersion;
  }

  /** Temporary until clients fixed */
  public Configuration getConfig() {
    if (params == null) {
      return ConfigManager.EMPTY_CONFIGURATION;
    }
    Configuration config = ConfigManager.newConfiguration();
    for (Iterator iter = params.iterator(); iter.hasNext(); ) {
      ConfigParamAssignment cpa = (ConfigParamAssignment)iter.next();
      ConfigParamDescr cpd = cpa.getParamDescr();
      config.put(cpd.getKey(), cpa.getValue());
    }
    return config;
  }

  // AuConfig hasn't been fully converted to use TitleConfig.  These
  // methods provide the info it needs to do things mostly the old way

  public Collection getUnEditableKeys() {
    if (params == null) {
      return Collections.EMPTY_LIST;
    }
    List res = new ArrayList();
    for (Iterator iter = params.iterator(); iter.hasNext(); ) {
      ConfigParamAssignment cpa = (ConfigParamAssignment)iter.next();
      ConfigParamDescr cpd = cpa.getParamDescr();
      if (!cpa.isEditable()) {
	res.add(cpd.getKey());
      }
    }
    return res;
  }

  /** Generate Properties that will result in this TitleConfig when loaded
   * by BasePlugin */
  public Properties toProperties(String propAbbr) {
    String pre = "org.lockss.title." + propAbbr + ".";
    Properties p = new OrderedProperties();
    p.put(pre+"title", getDisplayName());
    p.put(pre+"plugin", getPluginName());
    if (params != null) {
      for (int ix = 0; ix < params.size(); ix++) {
	ConfigParamAssignment cpa = (ConfigParamAssignment)params.get(ix);
	String ppre = pre + "param." + (ix+1) + ".";
	p.put(ppre + "key", cpa.getParamDescr().getKey());
	p.put(ppre + "value", cpa.getValue());
      }
    }
    return p;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer(40);
    sb.append("[Title: ");
    sb.append(displayName);
    sb.append(", plugin:");
    sb.append(pluginName);
    sb.append(", params:");
    sb.append(params);
    sb.append("]");
    return sb.toString();
  }


}
