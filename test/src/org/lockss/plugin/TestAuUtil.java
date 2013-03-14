/*
 * $Id: TestAuUtil.java,v 1.21 2013-03-14 06:38:49 tlipkis Exp $
 */

/*

Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin;

import java.io.*;
import java.util.*;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.crawler.*;
import org.lockss.state.*;
import org.lockss.test.*;
import org.lockss.plugin.base.*;
import org.lockss.util.*;
import org.lockss.plugin.ArchivalUnit.ConfigurationException;
import org.lockss.plugin.exploded.*;
import org.lockss.plugin.definable.*;

/**
 * This is the test class for org.lockss.plugin.AuUtil
 */
public class TestAuUtil extends LockssTestCase {

  static final ConfigParamDescr PD_VOL = ConfigParamDescr.VOLUME_NUMBER;
  static final ConfigParamDescr PD_YEAR = ConfigParamDescr.YEAR;
  static final ConfigParamDescr PD_OPT = new ConfigParamDescr("OPT_KEY");
  static {
    PD_OPT.setDefinitional(false);
  }

  static final String AUPARAM_VOL = PD_VOL.getKey();
  static final String AUPARAM_YEAR = PD_YEAR.getKey();
  static final String AUPARAM_OPT = PD_OPT.getKey();

  LocalMockPlugin mbp;

  public void setUp() throws Exception {
    super.setUp();
    mbp = new LocalMockPlugin();
    mbp.initPlugin(getMockLockssDaemon());
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  TitleConfig makeTitleConfig(ConfigParamDescr descr, String val) {
    TitleConfig tc = new TitleConfig("foo", new MockPlugin());
    tc.setParams(ListUtil.list(new ConfigParamAssignment(descr, val)));
    return tc;
  }

  public void testGetDaemon()  {
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit(mbp);
    assertSame(getMockLockssDaemon(), AuUtil.getDaemon(mau));
  }

  public void testThreadName() throws IOException {
    MockArchivalUnit mau = new MockArchivalUnit();
    mau.setName("Artichokes Volume Six");
    assertEquals("Crawl: Artichokes Volume Six",
		 AuUtil.getThreadNameFor("Crawl", mau));
    mau.setName("Fran\u00E7ais Volume Six");
    assertEquals("Crawl: Francais Volume Six",
		 AuUtil.getThreadNameFor("Crawl", mau));
  }

  public void testGetPollVersion() throws IOException {
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit(mbp);
    assertEquals(null, AuUtil.getPollVersion(mau));
    mbp.setVersion("12");
    assertEquals("12", AuUtil.getPollVersion(mau));
    mbp.setFeatureVersionMap(MapUtil.map(Plugin.Feature.Poll, "3"));
    assertEquals("3", AuUtil.getPollVersion(mau));
  }

  public void testGetAuState() throws IOException {
    setUpDiskPaths();
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit(mbp);
    getMockLockssDaemon().getNodeManager(mau).startService();
    AuState aus = AuUtil.getAuState(mau);
    assertEquals(AuState.CLOCKSS_SUB_UNKNOWN,
		 aus.getClockssSubscriptionStatus());
  }

  public void testIsCurrentFeatureVersion() throws IOException {
    setUpDiskPaths();
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit(mbp);
    getMockLockssDaemon().getNodeManager(mau).startService();
    AuState aus = AuUtil.getAuState(mau);
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Substance));
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Metadata));
    mbp.setFeatureVersionMap(MapUtil.map(Plugin.Feature.Substance, "3"));
    assertFalse(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Substance));
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Metadata));
    aus.setFeatureVersion(Plugin.Feature.Substance, "3");
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Substance));
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Metadata));
    mbp.setFeatureVersionMap(MapUtil.map(Plugin.Feature.Substance, "3",
					 Plugin.Feature.Metadata, "18"));
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Substance));
    assertFalse(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Metadata));
    aus.setFeatureVersion(Plugin.Feature.Metadata, "18");
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Substance));
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Metadata));
    mbp.setFeatureVersionMap(MapUtil.map(Plugin.Feature.Substance, "4",
					 Plugin.Feature.Metadata, "18"));
    assertFalse(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Substance));
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Metadata));
    aus.setSubstanceState(SubstanceChecker.State.Yes);
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Substance));
    assertTrue(AuUtil.isCurrentFeatureVersion(mau, Plugin.Feature.Metadata));
  }

  public void testHasCrawled() throws IOException {
    setUpDiskPaths();
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit(mbp);
    getMockLockssDaemon().getNodeManager(mau).startService();
    assertFalse(AuUtil.hasCrawled(mau));
    AuState aus = AuUtil.getAuState(mau);
    aus.newCrawlFinished(Crawler.STATUS_ERROR, "foo");
    assertFalse(AuUtil.hasCrawled(mau));
    aus.newCrawlFinished(Crawler.STATUS_SUCCESSFUL, "foo");
    assertTrue(AuUtil.hasCrawled(mau));
  }

  public void testGetPluginDefinition() throws Exception {
    ExternalizableMap map = new ExternalizableMap();
    map.setMapElement("foo", "bar");
    DefinablePlugin dplug = new DefinablePlugin();
    dplug.initPlugin(getMockLockssDaemon(), "FooPlugin", map, null);
    Plugin plug = dplug;
    assertSame(map, AuUtil.getPluginDefinition(plug));

    plug = new MockPlugin();
    assertEquals(0, AuUtil.getPluginDefinition(plug).size());
  }

  public void testIsClosed() throws Exception {
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit();
    assertFalse(AuUtil.isClosed(mau));
    mau.setConfiguration(ConfigurationUtil.fromArgs(ConfigParamDescr.AU_CLOSED.getKey(), "true"));
    assertTrue(AuUtil.isClosed(mau));
    mau.setTitleConfig(makeTitleConfig(ConfigParamDescr.AU_CLOSED, "false"));
    assertTrue(AuUtil.isClosed(mau));
    mau.setConfiguration(ConfigurationUtil.fromArgs("foo", "bar"));
    assertFalse(AuUtil.isClosed(mau));
    mau.setTitleConfig(makeTitleConfig(ConfigParamDescr.AU_CLOSED, "true"));
    assertTrue(AuUtil.isClosed(mau));
    mau.setTitleConfig(makeTitleConfig(ConfigParamDescr.AU_CLOSED, "false"));
    assertFalse(AuUtil.isClosed(mau));
  }

  public void testIsPubDown() throws Exception {
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit();
    assertFalse(AuUtil.isPubDown(mau));
    assertFalse(AuUtil.isPubNever(mau));
    mau.setConfiguration(ConfigurationUtil.fromArgs(ConfigParamDescr.PUB_DOWN.getKey(), "true"));
    assertTrue(AuUtil.isPubDown(mau));
    assertFalse(AuUtil.isPubNever(mau));
    mau.setTitleConfig(makeTitleConfig(ConfigParamDescr.PUB_DOWN, "false"));
    assertTrue(AuUtil.isPubDown(mau));
    mau.setConfiguration(ConfigurationUtil.fromArgs("foo", "bar"));
    assertFalse(AuUtil.isPubDown(mau));
    mau.setTitleConfig(makeTitleConfig(ConfigParamDescr.PUB_DOWN, "true"));
    assertTrue(AuUtil.isPubDown(mau));
    mau.setTitleConfig(makeTitleConfig(ConfigParamDescr.PUB_DOWN, "false"));
    assertFalse(AuUtil.isPubDown(mau));
  }

  public void testIsPubDownTC() throws Exception {
    assertTrue(AuUtil.isPubDown(makeTitleConfig(ConfigParamDescr.PUB_DOWN,
						"true")));
    assertFalse(AuUtil.isPubDown(makeTitleConfig(ConfigParamDescr.PUB_DOWN,
						 "false")));
    assertFalse(AuUtil.isPubDown(makeTitleConfig(ConfigParamDescr.BASE_URL,
						 "http://foo.bar/")));
  }

  public void testIsPubNever() throws Exception {
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit();
    assertFalse(AuUtil.isPubNever(mau));
    assertFalse(AuUtil.isPubDown(mau));
    mau.setConfiguration(ConfigurationUtil.fromArgs(ConfigParamDescr.PUB_NEVER.getKey(), "true"));
    assertTrue(AuUtil.isPubNever(mau));
    assertTrue(AuUtil.isPubDown(mau));
    mau.setTitleConfig(makeTitleConfig(ConfigParamDescr.PUB_NEVER, "false"));
    assertTrue(AuUtil.isPubNever(mau));
    assertTrue(AuUtil.isPubDown(mau));
    mau.setConfiguration(ConfigurationUtil.fromArgs("foo", "bar"));
    assertFalse(AuUtil.isPubNever(mau));
    assertFalse(AuUtil.isPubDown(mau));
    mau.setTitleConfig(makeTitleConfig(ConfigParamDescr.PUB_NEVER, "true"));
    assertTrue(AuUtil.isPubNever(mau));
    assertTrue(AuUtil.isPubDown(mau));
    mau.setTitleConfig(makeTitleConfig(ConfigParamDescr.PUB_NEVER, "false"));
    assertFalse(AuUtil.isPubNever(mau));
    assertFalse(AuUtil.isPubDown(mau));
  }

  public void testIsPubNeverTC() throws Exception {
    assertTrue(AuUtil.isPubNever(makeTitleConfig(ConfigParamDescr.PUB_NEVER,
						 "true")));
    assertFalse(AuUtil.isPubNever(makeTitleConfig(ConfigParamDescr.PUB_NEVER,
						  "false")));
    assertFalse(AuUtil.isPubNever(makeTitleConfig(ConfigParamDescr.BASE_URL,
						  "http://foo.bar/")));
  }

  public void testIsDeleteExtraFiles() throws Exception {
    ExternalizableMap map = new ExternalizableMap();
    DefinablePlugin dplug = new DefinablePlugin();
    dplug.initPlugin(getMockLockssDaemon(), "FooPlugin", map, null);
    DefinableArchivalUnit au = new LocalDefinableArchivalUnit(dplug, map);
    assertFalse(AuUtil.isDeleteExtraFiles(au, false));
    assertTrue(AuUtil.isDeleteExtraFiles(au, true));
    map.putBoolean(DefinablePlugin.KEY_PLUGIN_DELETE_EXTRA_FILES, true);
    assertTrue(AuUtil.isDeleteExtraFiles(au, false));
    map.putBoolean(DefinablePlugin.KEY_PLUGIN_DELETE_EXTRA_FILES, false);
    assertFalse(AuUtil.isDeleteExtraFiles(au, true));
  }

  public void testGetTitleAttribute() {
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit();
    TitleConfig tc = makeTitleConfig(ConfigParamDescr.PUB_DOWN, "false");
    mau.setTitleConfig(tc);
    assertNull(AuUtil.getTitleAttribute(mau, null));
    assertNull(AuUtil.getTitleAttribute(mau, "foo"));
    assertEquals("7", AuUtil.getTitleAttribute(mau, null, "7"));
    assertEquals("7", AuUtil.getTitleAttribute(mau, "foo", "7"));
    Map<String,String> attrs = new HashMap<String,String>();
    tc.setAttributes(attrs);
    assertNull(AuUtil.getTitleAttribute(mau, null));
    assertNull(AuUtil.getTitleAttribute(mau, "foo"));
    assertEquals("7", AuUtil.getTitleAttribute(mau, null, "7"));
    assertEquals("7", AuUtil.getTitleAttribute(mau, "foo", "7"));
    attrs.put("foo", "bar");
    assertEquals("bar", AuUtil.getTitleAttribute(mau, "foo"));
    assertEquals("bar", AuUtil.getTitleAttribute(mau, "foo", "7"));
  }

  public void testGetSubstanceTestThreshold() throws Exception {
    String key = ConfigParamDescr.CRAWL_TEST_SUBSTANCE_THRESHOLD.getKey();
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit();
    assertEquals(-1, AuUtil.getSubstanceTestThreshold(mau));
    mau.setConfiguration(ConfigurationUtil.fromArgs(key, ""));
    assertEquals(-1, AuUtil.getSubstanceTestThreshold(mau));
    mau.setConfiguration(ConfigurationUtil.fromArgs(key, "foo"));
    assertEquals(-1, AuUtil.getSubstanceTestThreshold(mau));
    mau.setConfiguration(ConfigurationUtil.fromArgs(key, "0"));
    assertEquals(0, AuUtil.getSubstanceTestThreshold(mau));
    mau.setConfiguration(ConfigurationUtil.fromArgs(key, "3"));
    assertEquals(3, AuUtil.getSubstanceTestThreshold(mau));
    // title attr should override au config
    TitleConfig tc1 =
      makeTitleConfig(ConfigParamDescr.BASE_URL, "http://example.com");
    mau.setTitleConfig(tc1);
    assertEquals(3, AuUtil.getSubstanceTestThreshold(mau));
    tc1.setAttributes(MapUtil.map(key, "2"));
    assertEquals(2, AuUtil.getSubstanceTestThreshold(mau));
    tc1.setAttributes(MapUtil.map(key, "0"));
    assertEquals(0, AuUtil.getSubstanceTestThreshold(mau));
    tc1.setAttributes(MapUtil.map(key, "xxx"));
    assertEquals(3, AuUtil.getSubstanceTestThreshold(mau));
  }

  public void testGetTitleDefault() {
    TitleConfig tc = makeTitleConfig(ConfigParamDescr.CRAWL_PROXY, "foo:47");
    assertEquals(null, AuUtil.getTitleDefault(tc, ConfigParamDescr.BASE_URL));
    assertEquals("foo:47",
		 AuUtil.getTitleDefault(tc, ConfigParamDescr.CRAWL_PROXY));
  }

  public void testGetAuParamOrTitleDefault() throws Exception {
    LocalMockArchivalUnit mau = new LocalMockArchivalUnit();
    TitleConfig tc = makeTitleConfig(ConfigParamDescr.CRAWL_PROXY, "foo:47");
    assertNull(AuUtil.getAuParamOrTitleDefault(mau,
 					       ConfigParamDescr.CRAWL_PROXY));
    mau.setTitleConfig(tc);
    assertEquals("foo:47",
		 AuUtil.getAuParamOrTitleDefault(mau,
						 ConfigParamDescr.CRAWL_PROXY));
    Configuration config =
      ConfigurationUtil.fromArgs(ConfigParamDescr.CRAWL_PROXY.getKey(),
				 "abc:8080");
    mau.setConfiguration(config);
    assertEquals("abc:8080",
		 AuUtil.getAuParamOrTitleDefault(mau,
						 ConfigParamDescr.CRAWL_PROXY));
  }

  void setGlobalProxy(String host, int port) {
    Properties p = new Properties();
    if (host != null) {
      p.put(BaseCrawler.PARAM_PROXY_ENABLED, "true");
      p.put(BaseCrawler.PARAM_PROXY_HOST, host);
      p.put(BaseCrawler.PARAM_PROXY_PORT, ""+port);
    } else {
      p.put(BaseCrawler.PARAM_PROXY_ENABLED, "false");
    }
    ConfigurationUtil.setCurrentConfigFromProps(p);
  }
				    
  public void testGetAuProxyInfo() throws Exception {
    AuUtil.AuProxyInfo aupi;
    TitleConfig tc;

    LocalMockArchivalUnit mau = new LocalMockArchivalUnit();
    aupi = AuUtil.getAuProxyInfo(mau);
    assertEquals(null, aupi.getHost());
    assertFalse(aupi.isAuOverride());

    setGlobalProxy("host", 1111);
    aupi = AuUtil.getAuProxyInfo(mau);
    assertFalse(aupi.isAuOverride());
    assertEquals("host", aupi.getHost());
    assertEquals(1111, aupi.getPort());

    tc = makeTitleConfig(ConfigParamDescr.CRAWL_PROXY, "foo:47");
    mau.setTitleConfig(tc);
    aupi = AuUtil.getAuProxyInfo(mau);
    assertTrue(aupi.isAuOverride());
    assertEquals("foo", aupi.getHost());
    assertEquals(47, aupi.getPort());

    tc = makeTitleConfig(ConfigParamDescr.CRAWL_PROXY, "HOST:1111");
    mau.setTitleConfig(tc);
    aupi = AuUtil.getAuProxyInfo(mau);
    assertFalse(aupi.isAuOverride());
    assertEquals("HOST", aupi.getHost());
    assertEquals(1111, aupi.getPort());

    setGlobalProxy(null, 0);
    aupi = AuUtil.getAuProxyInfo(mau);
    assertTrue(aupi.isAuOverride());
    assertEquals("HOST", aupi.getHost());
    assertEquals(1111, aupi.getPort());

    tc = makeTitleConfig(ConfigParamDescr.CRAWL_PROXY, "HOST:1112");
    mau.setTitleConfig(tc);
    aupi = AuUtil.getAuProxyInfo(mau);
    assertTrue(aupi.isAuOverride());
    assertEquals("HOST", aupi.getHost());
    assertEquals(1112, aupi.getPort());
  }

  public void testIsConfigCompatibleWithPlugin() {
    mbp.setConfigDescrs(ListUtil.list(PD_VOL, PD_YEAR, PD_OPT));
    Configuration auconf;
    Properties p = new Properties();

    // missing definitional param
    p.put(AUPARAM_VOL, "42");
    auconf = ConfigurationUtil.fromProps(p);
    assertFalse(AuUtil.isConfigCompatibleWithPlugin(auconf, mbp));

    // has all definitional params
    p.put(AUPARAM_YEAR, "1942");
    auconf = ConfigurationUtil.fromProps(p);
    assertTrue(AuUtil.isConfigCompatibleWithPlugin(auconf, mbp));

    // extra non-definitional
    p.put(AUPARAM_OPT, "foo");
    auconf = ConfigurationUtil.fromProps(p);
    assertTrue(AuUtil.isConfigCompatibleWithPlugin(auconf, mbp));

    // wrong type
    p.put(AUPARAM_YEAR, "foo");
    auconf = ConfigurationUtil.fromProps(p);
    assertFalse(AuUtil.isConfigCompatibleWithPlugin(auconf, mbp));
  }

  public void testOkDeleteExtraFiles() {
    assertTrue(AuUtil.okDeleteExtraFiles(new MockArchivalUnit()));
    assertFalse(AuUtil.okDeleteExtraFiles(new ExplodedArchivalUnit(new ExplodedPlugin(), null)));
  }

  public void testGetCu() {
    String url = "http://foo/";
    MockArchivalUnit mau = new MockArchivalUnit();
    CachedUrl mcu = new MockCachedUrl(url, mau);
    assertSame(mcu, AuUtil.getCu(mcu));
    MockCachedUrlSet mcus = new MockCachedUrlSet(url);
    mcus.setArchivalUnit(mau);
    assertNull(AuUtil.getCu(mcus));
    mau.addUrl(url, "foo");
    CachedUrl cu2 = AuUtil.getCu(mcus);
    assertEquals(url, cu2.getUrl());
  }

  public void assertGetCharsetOrDefault(String expCharset, Properties props) {
    MockUrlCacher muc = new MockUrlCacher("url", new MockArchivalUnit());
    if (props != null) {
      CIProperties cip = CIProperties.fromProperties(props);
      muc.setUncachedProperties(cip);
    }
    assertEquals(expCharset, AuUtil.getCharsetOrDefault(muc));
  }

  static String DEF = Constants.DEFAULT_ENCODING;
  static String CT_PROP = CachedUrl.PROPERTY_CONTENT_TYPE;

  public void testGetCharsetOrDefault() {
    assertGetCharsetOrDefault(DEF, null);
    assertGetCharsetOrDefault(DEF, PropUtil.fromArgs("foo", "bar"));
    assertGetCharsetOrDefault(DEF, PropUtil.fromArgs(CT_PROP,
						     "text/html"));
    assertGetCharsetOrDefault(DEF, PropUtil.fromArgs(CT_PROP,
						     "text/html;charset"));
    assertGetCharsetOrDefault("utf-8",
			      PropUtil.fromArgs(CT_PROP,
						"text/html;charset=utf-8"));
    assertGetCharsetOrDefault("utf-8",
			      PropUtil.fromArgs(CT_PROP,
						"text/html;charset=\"utf-8\""));
  }

  private static class LocalMockArchivalUnit extends MockArchivalUnit {
    TitleConfig tc = null;

    LocalMockArchivalUnit() {
      super();
    }

    LocalMockArchivalUnit(Plugin plugin) {
      super(plugin);
    }

    public TitleConfig getTitleConfig() {
      return tc;
    }
    public void setTitleConfig(TitleConfig tc) {
      this.tc = tc;
    }
  }

  private static class LocalMockPlugin extends BasePlugin {
    String name;
    String version;
    List<ConfigParamDescr> configDescrs;
    Map<Plugin.Feature,String> featureVersion;

    public LocalMockPlugin() {
      super();
    }

    public void setPluginName(String name) {
      this.name = name;
    }

    public void setVersion(String version) {
      this.version = version;
    }

    public void setFeatureVersionMap(Map<Plugin.Feature,String> featureVersion) {
      this.featureVersion = featureVersion;
    }

    public void setConfigDescrs(List<ConfigParamDescr> configDescrs) {
      this.configDescrs = configDescrs;
    }

    protected ArchivalUnit createAu0(Configuration auConfig) throws
        ConfigurationException {
      MockArchivalUnit mau = new MockArchivalUnit();
      mau.setConfiguration(auConfig);
      return mau;
    }

    public String getVersion() {
      return version;
    }

    public String getFeatureVersion(Plugin.Feature feat) {
      if (featureVersion == null) {
	return null;
      }
      return featureVersion.get(feat);
    }

    public String getPluginName() {
      return name;
    }

    public List<ConfigParamDescr> getLocalAuConfigDescrs() {
      return configDescrs;
    }
  }

  private static class LocalDefinableArchivalUnit
    extends DefinableArchivalUnit {

    protected LocalDefinableArchivalUnit(DefinablePlugin plugin,
					 ExternalizableMap definitionMap) {
      super(plugin, definitionMap);
    }
  }
}
