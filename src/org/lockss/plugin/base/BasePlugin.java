/*
 * $Id: BasePlugin.java,v 1.26 2004-09-27 22:39:12 smorabito Exp $
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

package org.lockss.plugin.base;

import java.util.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.app.*;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.plugin.*;

/**
 * Abstract base class for Plugins.  Plugins are encouraged to extend this
 * class to get some common Plugin functionality.
 */
public abstract class BasePlugin
    implements Plugin {
  static Logger log = Logger.getLogger("BasePlugin");

  static final String PARAM_TITLE_DB = ConfigManager.PARAM_TITLE_DB;

  // Below org.lockss.title.xxx.
  static final String TITLE_PARAM_TITLE = "title";
  static final String TITLE_PARAM_JOURNAL = "journalTitle";
  static final String TITLE_PARAM_PLUGIN = "plugin";
  static final String TITLE_PARAM_PLUGIN_VERSION = "pluginVersion";
  static final String TITLE_PARAM_PARAM = "param";
  // Below org.lockss.title.xxx.param.n.
  static final String TITLE_PARAM_PARAM_KEY = "key";
  static final String TITLE_PARAM_PARAM_VALUE = "value";
  static final String TITLE_PARAM_PARAM_EDITABLE = "editable";

  protected LockssDaemon theDaemon;
  protected PluginManager pluginMgr;
  protected Collection aus = new ArrayList();
  protected Map titleConfigMap;
  // XXX need to generalize this
  protected CacheResultMap resultMap = new HttpResultMap();

  /**
   * Must invoke this constructor in plugin subclass.
   */
  protected BasePlugin() {
  }

  public void initPlugin(LockssDaemon daemon) {
    theDaemon = daemon;
    pluginMgr = theDaemon.getPluginManager();

    Configuration.registerConfigurationCallback(new Configuration.Callback() {
	public void configurationChanged(Configuration newConfig,
					 Configuration prevConfig,
					 Configuration.Differences changedKeys) {
	  setConfig(newConfig, prevConfig, changedKeys);
	}});
    installCacheExceptionHandler();
  }

  public void stopPlugin() {
  }

  /**
   * Default implementation collects keys from titleConfigMap.
   * @return a List
   */
  public List getSupportedTitles() {
    if (titleConfigMap == null) {
      return Collections.EMPTY_LIST;
    }
    return new ArrayList(titleConfigMap.keySet());
  }

  /**
   * Default implementation looks in titleConfigMap.
   */
  public TitleConfig getTitleConfig(String title) {
    if (titleConfigMap == null) {
      return null;
    }
    return (TitleConfig)titleConfigMap.get(title);
  }

  /** Set up our titleConfigMap from the title definitions in the
   * Configuration.  Each title config looks like:<pre>
   * org.lockss.title.uid.title=Sample Title
   * org.lockss.title.uid.plugin=org.lockss.plugin.sample.SamplePlugin
   * org.lockss.title.uid.param.1.key=base_url
   * org.lockss.title.uid.param.1.value=http\://sample.org/
   * org.lockss.title.uid.param.2.key=year
   * org.lockss.title.uid.param.2.value=2003
   * org.lockss.title.uid.param.2.editable=true</pre> where
   * <code>uid</code> is an identifier that is unique for each title.
   * Parameters for which <code>editable</code> is true (<i>eg</i>,
   * <code>year</code>) may be edited by the user to select a related AU.
   * <br>See TitleParams (and test/scripts/title-params) for an easy way to
   * create these property files.
   */
  protected void setConfig(Configuration newConfig,
			   Configuration prevConfig,
			   Configuration.Differences changedKeys) {
    if (changedKeys.contains(PARAM_TITLE_DB)) {
      setTitleConfigFromConfig(newConfig.getConfigTree(PARAM_TITLE_DB));
    }
  }

  private void setTitleConfigFromConfig(Configuration allTitles) {
    String myName = getPluginId();
    Map titleMap = new HashMap();
    for (Iterator iter = allTitles.nodeIterator(); iter.hasNext(); ) {
      String titleKey = (String)iter.next();
      Configuration titleConfig = allTitles.getConfigTree(titleKey);
      String pluginName = titleConfig.get(TITLE_PARAM_PLUGIN);
      if (myName.equals(pluginName)) {
	if (log.isDebug2()) {
	  log.debug2("my titleKey: " + titleKey);
	  log.debug2("my titleConfig: " + titleConfig);
	}
	String title = titleConfig.get(TITLE_PARAM_TITLE);
	TitleConfig tc = initOneTitle(titleConfig);
	titleMap.put(title, tc);
      } else {
	if (log.isDebug3()) {
	  log.debug3("titleKey: " + titleKey);
	  log.debug3("titleConfig: " + titleConfig);
	}
      }
    }
    //TODO: decide on how to support plug-ins which do not use the title registry
    if (!titleMap.isEmpty()) {
      setTitleConfigMap(titleMap);
    }
  }

  TitleConfig initOneTitle(Configuration titleConfig) {
    String pluginName = titleConfig.get(TITLE_PARAM_PLUGIN);
    String title = titleConfig.get(TITLE_PARAM_TITLE);
    TitleConfig tc = new TitleConfig(title, this);
    tc.setPluginVersion(titleConfig.get(TITLE_PARAM_PLUGIN_VERSION));
    tc.setJournalTitle(titleConfig.get(TITLE_PARAM_JOURNAL));
    List params = new ArrayList();
    Configuration allParams = titleConfig.getConfigTree(TITLE_PARAM_PARAM);
    for (Iterator iter = allParams.nodeIterator(); iter.hasNext(); ) {
      Configuration oneParam = allParams.getConfigTree((String)iter.next());
      String key = oneParam.get(TITLE_PARAM_PARAM_KEY);
      String val = oneParam.get(TITLE_PARAM_PARAM_VALUE);
      ConfigParamDescr descr = findParamDescr(key);
      if (descr != null) {
	ConfigParamAssignment cpa = new ConfigParamAssignment(descr, val);
	if (oneParam.containsKey(TITLE_PARAM_PARAM_EDITABLE)) {
	  cpa.setEditable(oneParam.getBoolean(TITLE_PARAM_PARAM_EDITABLE,
					      cpa.isEditable()));
	}
	params.add(cpa);
      } else {
	log.warning("Unknown parameter key: " + key + " in title: " + title);
	log.debug("   title config: " + titleConfig);
      }
    }
    tc.setParams(params);
    return tc;

  }


  protected void setTitleConfigMap(Map titleConfigMap) {
    this.titleConfigMap = titleConfigMap;
    pluginMgr.resetTitles();
  }

  /**
   * Find the ConfigParamDescr that this plugin uses for the specified key.
   * @return the element of {@link #getAuConfigDescrs()} whose key
   * matches <code>key</code>, or null if none.
   */
  protected ConfigParamDescr findParamDescr(String key) {
    List descrs = getAuConfigDescrs();
    for (Iterator iter = descrs.iterator(); iter.hasNext(); ) {
      ConfigParamDescr descr = (ConfigParamDescr)iter.next();
      if (descr.getKey().equals(key)) {
	return descr;
      }
    }
    return null;
  }


  // for now use the plugin's class name
  // tk - this will have to change to account for versioning
  public String getPluginId() {
    return this.getClass().getName();
  }

  public Collection getAllAus() {
    if (log.isDebug2()) log.debug2("getAllAus: aus: " + aus);
    return aus;
  }

  public ArchivalUnit configureAu(Configuration config, ArchivalUnit au) throws
      ArchivalUnit.ConfigurationException {
    if(config == null) {
      throw new  ArchivalUnit.ConfigurationException("Null Configuration");
    }
    if (au != null) {
      au.setConfiguration(config);
    }
    else {
      au = createAu(config);
      aus.add(au);
    }
    return au;
  }

  /**
   * Return the LockssDaemon instance
   * @return the LockssDaemon instance
   */
  public LockssDaemon getDaemon() {
    return theDaemon;
  }

  public CachedUrlSet makeCachedUrlSet(ArchivalUnit owner,
                                       CachedUrlSetSpec cuss) {
    return new BaseCachedUrlSet(owner, cuss);
  }

  public CachedUrl makeCachedUrl(CachedUrlSet owner, String url) {
    return new BaseCachedUrl(owner, url);
  }

  public UrlCacher makeUrlCacher(CachedUrlSet owner, String url) {
    return new BaseUrlCacher(owner, url);
  }

  /**
   * return the CacheResultMap to use with this plugin
   * @return CacheResultMap
   */
  public CacheResultMap getCacheResultMap() {
    return resultMap;
  }

  protected void installCacheExceptionHandler() {
    // default is to do nothing - override if you need one
  }
}
