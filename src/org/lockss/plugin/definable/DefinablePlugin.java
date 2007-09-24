/*
 * $Id: DefinablePlugin.java,v 1.30 2007-09-24 18:37:12 dshr Exp $
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.definable;

import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.config.Configuration;
import org.lockss.app.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.plugin.definable.DefinableArchivalUnit.ConfigurableCrawlWindow;

import java.util.*;
import java.io.FileNotFoundException;
import java.net.*;

/**
 * <p>DefinablePlugin: a plugin which uses the data stored in an
*  ExternalizableMap to configure it self.</p>
 * @author Claire Griffin
 * @version 1.0
 */

public class DefinablePlugin extends BasePlugin {
  // configuration map keys
  public static final String KEY_PLUGIN_NAME = "plugin_name";
  public static final String KEY_PLUGIN_VERSION = "plugin_version";
  public static final String KEY_REQUIRED_DAEMON_VERSION =
    "required_daemon_version";
  public static final String KEY_PUBLISHING_PLATFORM =
    "plugin_publishing_platform";
  public static final String KEY_PLUGIN_CONFIG_PROPS = "plugin_config_props";
  public static final String KEY_EXCEPTION_HANDLER =
      "plugin_cache_result_handler";
  public static final String KEY_EXCEPTION_LIST =
      "plugin_cache_result_list";
  public static final String KEY_PLUGIN_NOTES = "plugin_notes";
  public static final String KEY_CRAWL_TYPE =
      "plugin_crawl_type";
  public static final String KEY_FOLLOW_LINKS = "plugin_follow_link";
  /** Message to be displayed when user configures an AU with this plugin */
  public static final String KEY_PLUGIN_AU_CONFIG_USER_MSG =
    "plugin_au_config_user_msg";

  public static final String DEFAULT_PLUGIN_VERSION = "1";
  public static final String DEFAULT_REQUIRED_DAEMON_VERSION = "0.0.0";
  public static final String MAP_SUFFIX = ".xml";

  public static final String CRAWL_TYPE_HTML_LINKS = "HTML Links";
  public static final String CRAWL_TYPE_OAI = "OAI";
  public static final String[] CRAWL_TYPES = {
      CRAWL_TYPE_HTML_LINKS,
      CRAWL_TYPE_OAI,
  };
  public static final String DEFAULT_CRAWL_TYPE = CRAWL_TYPE_HTML_LINKS;
  
  protected String mapName = null;

  static Logger log = Logger.getLogger("DefinablePlugin");

  protected ExternalizableMap definitionMap = new ExternalizableMap();
  protected CacheResultHandler resultHandler = null;
  protected String loadedFrom;
  protected CrawlWindow crawlWindow;

  public void initPlugin(LockssDaemon daemon, String extMapName)
      throws FileNotFoundException {
    initPlugin(daemon, extMapName, this.getClass().getClassLoader());
  }

  // Used by tests
  void initPlugin(LockssDaemon daemon, ExternalizableMap defMap)
      throws FileNotFoundException {
    initPlugin(daemon, defMap, this.getClass().getClassLoader());
  }

  public void initPlugin(LockssDaemon daemon, String extMapName,
			 ClassLoader loader)
      throws FileNotFoundException {
    // convert the plugin class name to an xml file name
    String mapFile = extMapName.replace('.', '/') + MAP_SUFFIX;
    // load the configuration map from jar file
    ExternalizableMap defMap = new ExternalizableMap();
    defMap.loadMapFromResource(mapFile, loader);
    URL url = loader.getResource(mapFile);
    if (url != null) {
      loadedFrom = url.toString();
    }
    this.initPlugin(daemon, extMapName, defMap, loader);
  }

  // Used by tests
  void initPlugin(LockssDaemon daemon,
			 ExternalizableMap defMap,
			 ClassLoader loader)
      throws FileNotFoundException {
    initPlugin(daemon, "Internal", defMap, loader);
  }

  public void initPlugin(LockssDaemon daemon, String extMapName,
			 ExternalizableMap defMap,
			 ClassLoader loader)
      throws FileNotFoundException {
    mapName = extMapName;
    this.classLoader = loader;
    this.definitionMap = defMap;
    // then call the overridden initializaton.
    super.initPlugin(daemon);
    initMimeMap();
  }

  public String getLoadedFrom() {
    return loadedFrom;
  }

  public String getPluginName() {
    if (definitionMap.containsKey(KEY_PLUGIN_NAME)) {
      return definitionMap.getString(KEY_PLUGIN_NAME);
    } else {
      return getDefaultPluginName();
    }
  }

  protected String getDefaultPluginName() {
    return StringUtil.shortName(getPluginId());
  }
  
  public String getVersion() {
    return definitionMap.getString(KEY_PLUGIN_VERSION, DEFAULT_PLUGIN_VERSION);
  }

  public String getRequiredDaemonVersion() {
    return definitionMap.getString(KEY_REQUIRED_DAEMON_VERSION,
				   DEFAULT_REQUIRED_DAEMON_VERSION);
  }

  public String getPublishingPlatform() {
    return definitionMap.getString(KEY_PUBLISHING_PLATFORM, null);
  }

  public String getPluginNotes() {
    return definitionMap.getString(KEY_PLUGIN_NOTES, null);
  }

  public List getLocalAuConfigDescrs()
      throws PluginException.InvalidDefinition {
    List auConfigDescrs =
      (List) definitionMap.getCollection(KEY_PLUGIN_CONFIG_PROPS, null);
    if (auConfigDescrs == null) {
      throw
	new PluginException.InvalidDefinition(mapName +
					      " missing ConfigParamDescrs");
    }
    return auConfigDescrs;
  }

  protected ArchivalUnit createAu0(Configuration auConfig)
      throws ArchivalUnit.ConfigurationException {
    DefinableArchivalUnit au =
      new DefinableArchivalUnit(this, definitionMap);
    au.setConfiguration(auConfig);
    return au;
  }

  public ExternalizableMap getDefinitionMap() {
    return definitionMap;
  }

  CacheResultHandler getCacheResultHandler() {
    return resultHandler;
  }

  String stripSuffix(String str, String suffix) {
    return str.substring(0, str.length() - suffix.length());
  }

  protected void initMimeMap() throws PluginException.InvalidDefinition {
    for (Iterator iter = definitionMap.entrySet().iterator(); iter.hasNext();){
      Map.Entry ent = (Map.Entry)iter.next();
      String key = (String)ent.getKey();
      Object val = ent.getValue();
      if (key.endsWith(DefinableArchivalUnit.SUFFIX_LINK_EXTRACTOR_FACTORY)) {
	String mime =
	  stripSuffix(key, DefinableArchivalUnit.SUFFIX_LINK_EXTRACTOR_FACTORY);
	if (val instanceof String) {
	  String factName = (String)val;
	  log.debug(mime + " link extractor: " + factName);
	  MimeTypeInfo.Mutable mti = mimeMap.modifyMimeTypeInfo(mime);
	  LinkExtractorFactory fact =
	    (LinkExtractorFactory)newAuxClass(factName,
					      LinkExtractorFactory.class);
	  mti.setLinkExtractorFactory(fact);
	}
      } else if (key.endsWith(DefinableArchivalUnit.SUFFIX_FILTER_FACTORY)) {
	String mime = stripSuffix(key,
				  DefinableArchivalUnit.SUFFIX_FILTER_FACTORY);
	if (val instanceof String) {
	  String factName = (String)val;
	  log.debug(mime + " filter: " + factName);
	  MimeTypeInfo.Mutable mti = mimeMap.modifyMimeTypeInfo(mime);
	  FilterFactory fact =
	    (FilterFactory)newAuxClass(factName, FilterFactory.class);
	  mti.setFilterFactory(fact);
	}
      } else if (key.endsWith(DefinableArchivalUnit.SUFFIX_FETCH_RATE_LIMITER)) {
	String mime =
	  stripSuffix(key, DefinableArchivalUnit.SUFFIX_FETCH_RATE_LIMITER);
	if (val instanceof String) {
	  String rate = (String)val;
	  log.debug(mime + " fetch rate: " + rate);
	  MimeTypeInfo.Mutable mti = mimeMap.modifyMimeTypeInfo(mime);
	  RateLimiter limit = mti.getFetchRateLimiter();
	  if (limit != null) {
	    limit.setRate(rate);
	  } else {
	    mti.setFetchRateLimiter(new RateLimiter(rate));
	  }
	}
      }
    }
  }

  protected void initResultMap() throws PluginException.InvalidDefinition {
    resultMap = new HttpResultMap();
    // we support two form of result handlers... either a class which handles
    // installing the numbers as well as handling any exceptions
    String handler_class = null;
    handler_class = definitionMap.getString(KEY_EXCEPTION_HANDLER, null);
    if (handler_class != null) {
      try {
        resultHandler =
            (CacheResultHandler)newAuxClass(handler_class,
					    CacheResultHandler.class);
        resultHandler.init(resultMap);
      }
      catch (Exception ex) {
        throw new PluginException.InvalidDefinition(mapName
        + " has invalid Exception handler: " + handler_class, ex);
      }
      catch (LinkageError le) {
        throw new PluginException.InvalidDefinition(
            mapName + " has invalid Exception handler: " + handler_class,
	    le);

      }
    }
    else {// or a list of individual exception remappings
      Collection results;
      results = definitionMap.getCollection(KEY_EXCEPTION_LIST, null);
      if (results != null) {
        // add each entry
        for (Iterator it = results.iterator(); it.hasNext(); ) {
          String entry = (String) it.next();
	  String class_name = null;
          try {
            Vector s_vec = StringUtil.breakAt(entry, '=', 2, true, true);
            class_name = (String) s_vec.get(1);
            int code = Integer.parseInt(((String) s_vec.get(0)));
	    // Add the result class to the map.  Result classes are loaded
	    // from system classpath - there's no need for plugin-local
	    // exceptions
            Class result_class = null;
	    // 
            result_class = Class.forName(class_name);
            ( (HttpResultMap) resultMap).storeMapEntry(code, result_class);
          }
          catch (Exception ex) {
            throw new PluginException.InvalidDefinition(mapName
                                                 + " has invalid entry: "
                                                 + entry);
	  }
	  catch (LinkageError le) {
	    throw new PluginException.InvalidDefinition("Can't load " +
							class_name,
							le);
	  }
        }
      }
    }
  }

  /** Create a CrawlWindow if necessary and return it.  The CrawlWindow
   * must be thread-safe. */
  protected CrawlWindow makeCrawlWindow() {
    if (crawlWindow != null) {
      return crawlWindow;
    }
    CrawlWindow window =
      (CrawlWindow)definitionMap.getMapElement(DefinableArchivalUnit.KEY_AU_CRAWL_WINDOW_SER);
    if (window == null) {
      String window_class =
	definitionMap.getString(DefinableArchivalUnit.KEY_AU_CRAWL_WINDOW,
				null);
      if (window_class != null) {
	ConfigurableCrawlWindow ccw =
	  (ConfigurableCrawlWindow) newAuxClass(window_class,
						ConfigurableCrawlWindow.class);
	try {
	  window = ccw.makeCrawlWindow();
	} catch (PluginException e) {
	  throw new RuntimeException(e);
	}
      }
    }
    crawlWindow = window;
    return window;
  }

  protected UrlNormalizer getUrlNormalizer() {
    if (urlNorm == null) {
      String normalizerClass =
	definitionMap.getString(DefinableArchivalUnit.KEY_AU_URL_NORMALIZER,
				null);
      if (normalizerClass != null) {
	urlNorm =
	  (UrlNormalizer)newAuxClass(normalizerClass, UrlNormalizer.class);
      } else {
	urlNorm = NullUrlNormalizer.INSTANCE;
      }
    }
    return urlNorm;
  }

  protected ExploderHelper getExploderHelper() {
    if (exploderHelper == null) {
      String helperClass =
	definitionMap.getString(DefinableArchivalUnit.KEY_AU_EXPLODER_HELPER,
				null);
      if (helperClass != null) {
	exploderHelper =
	  (ExploderHelper)newAuxClass(helperClass, ExploderHelper.class);
      }
    }
    return exploderHelper;
  }

  protected FilterRule constructFilterRule(String contentType) {
    String mimeType = HeaderUtil.getMimeTypeFromContentType(contentType);

    Object filter_el =
      definitionMap.getMapElement(mimeType +
				  DefinableArchivalUnit.SUFFIX_FILTER_RULE);

    if (filter_el instanceof String) {
      log.debug("Loading filter "+filter_el);
      return (FilterRule) newAuxClass( (String) filter_el, FilterRule.class);
    }
    else if (filter_el instanceof List) {
      if ( ( (List) filter_el).size() > 0) {
        return new DefinableFilterRule( (List) filter_el);
      }
    }
    return super.constructFilterRule(mimeType);
  }

  public String getPluginId() {
    String className;
    if(mapName != null) {
      className = mapName;
    }
    else {
      //@TODO: eliminate this when we eliminate subclasses
      className = this.getClass().getName();
    }
    return className;
  }
}
