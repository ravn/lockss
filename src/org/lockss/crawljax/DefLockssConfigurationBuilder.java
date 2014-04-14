/*
 * $Id: DefLockssConfigurationBuilder.java,v 1.1 2014-04-14 23:08:24 clairegriffin Exp $
 */

/*

Copyright (c) 2000-2014 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.crawljax;

import com.crawljax.browser.EmbeddedBrowser.BrowserType;
import com.crawljax.core.configuration.BrowserConfiguration;
import com.crawljax.core.configuration.CrawljaxConfiguration;
import com.crawljax.core.configuration.CrawljaxConfiguration.CrawljaxConfigurationBuilder;
import com.crawljax.core.configuration.InputSpecification;
import com.crawljax.core.configuration.ProxyConfiguration;
import com.crawljax.core.plugin.Plugin;
import com.crawljax.plugins.crawloverview.CrawlOverview;
import com.crawljax.plugins.proxy.WebScarabProxyPlugin;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.concurrent.TimeUnit;


public class DefLockssConfigurationBuilder implements
                                           LockssConfigurationBuilder {
  /**
   * The maximum number of DOM states.  (Crawljax defaults to 0 = unlimited)
   */
  static final String MAX_STATES_PARAM = "maxStates";
  static final int MAX_STATES_DEFAULT = 0;

  /**
   * The maximum crawl depth. (Crawljax defaults to 2.  We default to 1)
   */
  static final String DEPTH_PARAM = "depth";
  static final int DEPTH_DEFAULT = 1;

  /**
   * The browser to use for our crawl. (We default to PHANTOMJS which is
   * faceless)
   */
  static final String BROWSER_PARAM = "browser";
  static final BrowserType BROWSER_DEFAULT = BrowserType.PHANTOMJS;

  /**
   * The number of browsers to run in parallel.  We only need one for a 1 level
   * crawl.
   */
  static final String PARALLEL_PARAM = "parallel";
  static final int PARALLEL_DEFAULT = 1;

  /**
   * Should we crawl hidden elements.  This has the up side of catching hidden
   * elements that are hit by javascript but it may also hit tags that are
   * simply
   * left in the doc and never used. We default to true.
   */
  static final String CRAWL_HIDDEN_PARAM = "crawlHidden";
  static final boolean CRAWL_HIDDEN_DEFAULT = true;

  /**
   * Click elements only once.  Crawljax defaults to true.
   */
  static final String CLICK_ONCE_PARAM = "clickOnce";
  static final boolean CLICK_ONCE_DEFAULT = true;

  /**
   * How long we should wait for a single crawl to run in minutes.  We default
   * to 5.
   */
  static final String TIMEOUT_PARAM = "timeout";
  static final int TIMEOUT_DEFAULT = 5;

  /**
   * The time to wait after a page is loaded before we begin doing any
   * clicking.
   * This needs to be long enough for scripts etc to load (500 ms is the
   * crawljax
   * default)
   */
  static final String WAIT_AFTER_RELOAD_PARAM = "waitAfterReload";
  static final long WAIT_AFTER_RELOAD_DEFAULT = 500;

  /**
   * The time to wait after an event has fired before we do any more clicks.
   * This needs to be long enough for scripts etc to load (500 ms is the
   * crawljax
   * default)
   */
  static final String WAIT_AFTER_EVENT_PARAM = "waitAfterEvent";
  static final long WAIT_AFTER_EVENT_DEFAULT = 500;

  /**
   * Should we crawl frames. We default to true.
   */
  static final String CRAWL_FRAMES_PARAM = "crawlFrames";
  static final boolean CRAWL_FRAMES_DEFAULT = true;

  /**
   * Should we insert random data into any form elements.  We default to false.
   */
  static final String INSERT_RANDOM_DATA_PARAM = "insertRandomData";
  static final boolean INSERT_RANDOM_DATA_DEFAULT = false;

  /**
   * A comma separated list of html tags to click. By default we click A, BUTTON
   * and
   * DIV which contain the attribute 'click'
   */
  static final String CLICK_PARAM = "click";
  static final String CLICK_DEFAULT = "A, BUTTON";

  /**
   * A comma separated list of html tags to not click.  If an item is in both
   * the
   * click and dontClick list the dont click will take precedence.
   */
  static final String DONT_CLICK_PARAM = "dontClick";

  /**
   * A comma separated list of html tags whose child elements we don't crawl.
   */
  static final String DONT_CLICK_CHILDREN_PARAM = "dontClickChildren";

  /**
   * The comma separated list of plugin class names.  A plugin MUST be installed
   * for a crawljax crawl to output data.  By default we use the
   * LockssOutputPlugin which requires the scarab proxy to collect
   * the outgoing request urls.  If the scarab proxy is not installed.  The
   * default is simply to store the DOM
   * snapshots to the output directory.
   */
  static final String PLUGINS_PARAM = "plugins";
  /**
   * Should we use the ScarabProxy to collect urls.
   */
  static final String USE_SCARAB_PROXY_PARAM = "useScarabProxy";
  static final boolean USE_SCARAB_PROXY_DEFAULT = true;


  /* ----------------------------------------------------------------------- */
  /*                        Scarab Proxy Support                             */
  /* ----------------------------------------------------------------------- */
  /**
   * The address of the ScarabProxy. Default to localhost.
   */
  static final String SCARAB_PROXY_ADDR_PARAM = "scarabProxyHost";
  static final String SCARAB_PROXY_ADDR_DEFAULT = "127.0.0.1";
  /**
   * The port for the ScarabProxy.  Default to 8084.
   */
  static final String SCARAB_PROXY_PORT_PARAM = "scarabProxyPort";
  static final int SCARAB_PROXY_PORT_DEFAULT = 8084;
  /**
   * Should we use the LAP PROXY to collect store WARC files.
   */

  /* ----------------------------------------------------------------------- */
  /*           LAP w/ WARC WRITER Proxy Support  */
  /* ----------------------------------------------------------------------- */
  static final String USE_LAP_PROXY_PARAM = "useLapProxy";
  static final boolean USE_LAP_PROXY_DEFAULT = false;
  /**
   * The address of the LAP PROXY. Default to localhost.
   */
  static final String LAP_PROXY_HOST_PARAM = "lapProxyHost";
  static final String LAP_PROXY_HOST_DEFAULT = "127.0.0.1";
  /**
   * The port for the LAP PROXY WEB PORT.  Default to 4338.
   */
  static final String LAP_PROXY_WEB_PORT_PARAM = "lapProxyWebPort";
  static final int LAP_PROXY_WEB_PORT_DEFAULT = 4338;
  /**
   * The port for the LAP PROXY WRITER PORT.  Default to 4365.
   */
  static final String LAP_PROXY_WRITER_PORT_PARAM = "lapProxyWriterPort";
  static final int LAP_PROXY_WRITER_PORT_DEFAULT = 4365;
  /**
   * The name InputSpecification classname to use for filling in forms.
   */
  private static final String INPUT_SPECS_PARAM = "inputSpecs";
  /**
   * The url from which to start the crawl
   */
  protected String m_urlValue;
  /**
   * The directory to store all output files
   */
  protected String m_outDir;
  /**
   * The configuration file.
   */
  protected String m_configFile;
  /**
   * The PropertiesConfiguration created from our configuration file.
   */
  protected PropertiesConfiguration m_config;
  /**
   * The Crawljax ScarabProxy RequestBufferProxy Addon.  This is the actual
   * collector of 'Request/Responses'.
   */
  LockssWebScarabProxyAddon m_webScarabProxyAddon;

  /**
   * Configure a CrawljaxConfiguration for a given url from a configuration
   * file with all output stored in a directory.
   *
   * @param urlValue the url we use for the CrawljaxConfiguration
   * @param outDir the output directory to use for content
   * @param configFile the properties file to use for the
   * CrawljaxConfiguration
   *
   * @return the CrawljaxConfiguration
   */
  @Override
  public CrawljaxConfiguration configure(final String urlValue,
                                         final String outDir,
                                         final String configFile) {
    m_urlValue = urlValue;
    m_outDir = outDir;
    m_configFile = configFile;

  /* get a new builder for the stating url*/
    CrawljaxConfigurationBuilder builder =
        CrawljaxConfiguration.builderFor(m_urlValue);

    /* setup the output directory, creating it if necessary and store it in the
      configuration for later use.
    */
    builder.setOutputDirectory(new File(m_outDir));

    try {
      m_config = new PropertiesConfiguration(configFile);

      configureBuilder(builder);
    } catch (ConfigurationException e) {
      System.out.println("Error configuring crawl: " + e.getMessage());
    }
    return builder.build();
  }

  /* ----------------------------------------------------------------------- */
  /*                        GETTERS/SETTERS                                  */
  /* ----------------------------------------------------------------------- */
  public PropertiesConfiguration getConfig() {
    return m_config;
  }

  protected void setConfig(final PropertiesConfiguration config) {
    m_config = config;
  }

  public String getUrlValue() {
    return m_urlValue;
  }

  protected void setUrlValue(final String urlValue) {
    m_urlValue = urlValue;
  }

  public String getOutDir() {
    return m_outDir;
  }

  protected void setOutDir(final String outDir) {
    m_outDir = outDir;
  }

  public String getConfigFile() {
    return m_configFile;
  }

  protected void setConfigFile(final String configFile) {
    m_configFile = configFile;
  }

  /**
   * @param builder the CrawljaxConfigurationBuilder we add to
   *
   * @throws ConfigurationException if we could not create at least one output
   * plugin.
   */
  protected void configureBuilder(CrawljaxConfigurationBuilder builder)
      throws ConfigurationException {

    // setup the browser config. default = 1 instance of phantomjs
    BrowserType browser = BROWSER_DEFAULT;
    if (m_config.containsKey(BROWSER_PARAM)) {
      browser = getSpecifiedBrowser(m_config.getString(BROWSER_PARAM));
    }
    int num_parallel = m_config.getInt(PARALLEL_PARAM, PARALLEL_DEFAULT);
    builder.setBrowserConfig(new BrowserConfiguration(browser, num_parallel));

    // setup crawl depth. default = 1.
    int depth = m_config.getInt(DEPTH_PARAM, DEPTH_DEFAULT);
    builder.setMaximumDepth(depth);

    // set up max states.  defaults to 0 i.e. no limit.
    int max_states = m_config.getInt(MAX_STATES_PARAM, MAX_STATES_DEFAULT);
    builder.setMaximumStates(max_states);
    configureCrawlRules(builder);


    // configure any form input specs
    if (m_config.containsKey(INPUT_SPECS_PARAM)) {
      configureInputSpecs(builder);
    }

    // configure the proxy plugin - this is what will listen and record our
    // incoming and outgoing traffic.  We use this in the default plugin
    // to write out the request/response pairs.
    if (m_config.getBoolean(USE_SCARAB_PROXY_PARAM, USE_SCARAB_PROXY_DEFAULT)) {
      installScarabProxyPlugin(builder);
    } else if (m_config.getBoolean(USE_LAP_PROXY_PARAM, USE_LAP_PROXY_DEFAULT)) {
      installLapProxyPlugin(builder);
    }
    // configure any plugins - by default crawljax won't output anything
    // without a plugin. So we need at least one for anything useful to happen.
    // The order of execution in the event of two plugins with overlap is the
    // order n which they are added.
    boolean has_one_plugin = false;
    if (m_config.containsKey(PLUGINS_PARAM)) {
      has_one_plugin = configurePlugins(builder);
    }
    if (!has_one_plugin && m_webScarabProxyAddon != null) {
      builder.addPlugin(new LockssOutputPlugin(m_webScarabProxyAddon));
    } else {
      builder.addPlugin(new CrawlOverview());
    }
  }

  /**
   * CrawlRule specific configurations.  By default this will set
   * set crawlHiddenAnchors true or as defined in properties
   * set clickOnce true or as defined in properties
   * set crawlFrames true or as defined in properties
   * set insertRandomDataInInputForms false or as defined in properties
   * set setMaximumRunTime 5m or as defined in properties
   * set waitAfterEvent 500ms or as defined in properties
   * set waitAfterReload 500ms or as defined in properties
   * then call configureCrawlClicks to install items to click or not click.
   * Override configureCrawlClicks to change click crawlrules from simple
   * tag lists.
   *
   * @param builder the CrawljaxConfigurationBuilder we will insert into
   */
  protected void configureCrawlRules(final CrawljaxConfigurationBuilder builder) {
    // setup crawl of hidden anchors.  default to true.
    boolean crawl_hidden = m_config.getBoolean(CRAWL_HIDDEN_PARAM,
                                               CRAWL_HIDDEN_DEFAULT);
    builder.crawlRules().crawlHiddenAnchors(crawl_hidden);

    // setup click each element once.  default to true.
    boolean click_once = m_config.getBoolean(CLICK_ONCE_PARAM,
                                             CLICK_ONCE_DEFAULT);
    builder.crawlRules().clickOnce(click_once);


    // setup whether we crawl frames.  default to true.
    boolean crawl_frames = m_config.getBoolean(CRAWL_FRAMES_PARAM,
                                               CRAWL_FRAMES_DEFAULT);
    builder.crawlRules().crawlFrames(crawl_frames);

    // setup whether we insert random data into forms. default to false.
    boolean random_data = m_config.getBoolean(INSERT_RANDOM_DATA_PARAM,
                                              INSERT_RANDOM_DATA_DEFAULT);
    builder.crawlRules().insertRandomDataInInputForms(random_data);


    builder.crawlRules().clickOnce(click_once);
    // set up the basic crawl times these need reasonable defaults
    int time_out = m_config.getInt(TIMEOUT_PARAM, TIMEOUT_DEFAULT);
    builder.setMaximumRunTime(time_out, TimeUnit.MINUTES);

    // time to wait after event fires. default to 500ms
    long wait_after_event = m_config.getLong(WAIT_AFTER_EVENT_PARAM,
                                             WAIT_AFTER_EVENT_DEFAULT);
    builder.crawlRules()
        .waitAfterEvent(wait_after_event, TimeUnit.MILLISECONDS);

    // time to wait after url fetched before clicks start.  default to 500ms
    long wait_after_reload = m_config.getLong(WAIT_AFTER_RELOAD_PARAM,
                                              WAIT_AFTER_RELOAD_DEFAULT);
    builder.crawlRules().waitAfterReloadUrl(wait_after_reload,
                                            TimeUnit.MILLISECONDS);
    configureCrawlClicks(builder);
  }

  /**
   * Configure the click, dontClick and dontClickChildren portions of
   * crawlrules.  This should be overridden if the desired behaviour is not the
   * default behaviour and requires more complicated behaviour than simple tag
   * lists.
   *
   * @param builder the CrawljaxConfigurationBuilder we add to
   */
  @SuppressWarnings("MethodWithMultipleLoops")
  protected void configureCrawlClicks(CrawljaxConfigurationBuilder builder) {
    // configure which elements we will click.
    // click these elements - you can override these by adding them to the don't
    // click list default elements is A or BUTTON
    String click = m_config.getString(CLICK_PARAM, CLICK_DEFAULT);
    // This is just a comma separated list of html tags
    builder.crawlRules().click(click);

    // optional. configure which elements we don't click. Another comma
    // separated list of html tags. If tags appear in both lists exclude
    // takes precedence.
    if (m_config.containsKey(DONT_CLICK_PARAM)) {
      String[] tag_names = m_config.getStringArray(DONT_CLICK_PARAM);
      for (String tag_name : tag_names) {
        builder.crawlRules().dontClick(tag_name);
      }
    }

    // optional. configure any elements which should be ignored along with
    // their children
    if (m_config.containsKey(DONT_CLICK_CHILDREN_PARAM)) {
      String[] tag_names =
          m_config.getStringArray(DONT_CLICK_CHILDREN_PARAM);
      for (String tag_name : tag_names) {
        builder.crawlRules().dontClickChildrenOf(tag_name);
      }
    }
  }

  /**
   * Install InputSpecifications for any form elements.
   *
   * @param builder the CrawljaxConfigurationBuilder we add to
   */
  protected void configureInputSpecs(CrawljaxConfigurationBuilder builder) {
    String form_inputs = m_config.getString(INPUT_SPECS_PARAM);
    InputSpecification spec;
    try {
      spec = instantiate(form_inputs, InputSpecification.class);
      builder.crawlRules().setInputSpec(spec);
    } catch (IllegalStateException e) {
      System.out.println("Failed to install plugin '" + form_inputs +
                             "' -" + e.getMessage());
    }
  }


  /**
   * install any plugins defined in the config file.  If at least one isn't
   * specified we install the default one.
   *
   * @param builder the CrawljaxConfigurationBuilder we add to
   */
  protected boolean configurePlugins(CrawljaxConfigurationBuilder builder) {
    boolean has_one_plugin = false;
    String[] plugin_names = m_config.getStringArray(PLUGINS_PARAM);
    for (String plugin_name : plugin_names) {
      try {
        Plugin plugin = instantiate(plugin_name, Plugin.class);
        builder.addPlugin(plugin);
        has_one_plugin = true;
      } catch (IllegalStateException e) {
        System.out.println("Failed to install plugin '" + plugin_name +
                               "' -" + e.getMessage());
      }
    }
    return has_one_plugin;
  }

  /**
   * Install the WebScarab ProxyPlugin. This will cause the installed browser
   * toproxy through WebScarab which does request caching.
   *
   * @param builder the CrawljaxConfigurationBuilder we add to
   */
  protected void installScarabProxyPlugin(CrawljaxConfigurationBuilder builder) {
    WebScarabProxyPlugin proxyPlugin = new WebScarabProxyPlugin();
    // we create a new request buffer - we pass in true to collect 'everything'
    // when we're done with the crawl we flush the internal buffer.  The files
    // remain in the output directory until they are.
    m_webScarabProxyAddon = new LockssWebScarabProxyAddon(m_outDir);
    proxyPlugin.addPlugin(m_webScarabProxyAddon);
    String proxyAddr = m_config.getString(SCARAB_PROXY_ADDR_DEFAULT,
                                          SCARAB_PROXY_ADDR_DEFAULT);
    int proxyPort = m_config.getInt(SCARAB_PROXY_PORT_PARAM,
                                    SCARAB_PROXY_PORT_DEFAULT);

    // Configure the proxy to use the port 8084 (you can change this of course)
    builder.addPlugin(proxyPlugin);
    builder.setProxyConfig(ProxyConfiguration.manualProxyOn(proxyAddr,
                                                            proxyPort));
  }

  void installLapProxyPlugin(final CrawljaxConfigurationBuilder builder) {
    String proxyAddr = m_config.getString(LAP_PROXY_HOST_PARAM,
                                          LAP_PROXY_HOST_DEFAULT);
    int proxyPort = m_config.getInt(LAP_PROXY_WEB_PORT_PARAM,
                                    LAP_PROXY_WEB_PORT_DEFAULT);

    // We only need this if we want to tie the proxy into the LAP proxy
    // life-cycle.
    //builder.addPlugin(proxyPlugin);
    builder.setProxyConfig(ProxyConfiguration.manualProxyOn(proxyAddr,
                                                            proxyPort));

  }

  /**
   * build out the list of Crawljax supported browser types for display in help
   * msg.
   *
   * @return a String of comma separated browser type names.
   */
  protected String availableBrowsers() {

    return StringUtils.join(BrowserType.values(), ",");
  }

  /**
   * Convert the String name of a browser to the BrowserType which matches that
   * name.
   *
   * @param browser_name the name of the browser
   *
   * @return a BrowserType of that name
   *
   * @throws java.lang.IllegalArgumentException if name is not found in out
   * types.
   */
  protected BrowserType getSpecifiedBrowser(String browser_name) {
    for (BrowserType b : BrowserType.values()) {
      if (b.name().equalsIgnoreCase(browser_name)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unrecognized browser: '" +
                                           browser_name +
                                           "'. Available browsers are: "
                                           + availableBrowsers());
  }

  /**
   * instantiate a class handle any exceptions
   *
   * @param className the name of the class to instantiate
   * @param type the type or Class to instantiate
   *
   * @return an instance of the class cast as a object of type T
   *
   * @throws java.lang.IllegalStateException if we unable to make the class
   */
  public <T> T instantiate(final String className, final Class<T> type)
      throws IllegalStateException {
    try {
      return type.cast(Class.forName(className).newInstance());
    } catch (final InstantiationException e) {
      throw new IllegalStateException(e);
    } catch (final IllegalAccessException e) {
      throw new IllegalStateException(e);
    } catch (final ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

}
