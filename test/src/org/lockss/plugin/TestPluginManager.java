/*
 * $Id: TestPluginManager.java,v 1.55 2005-01-04 03:00:30 tlipkis Exp $
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

package org.lockss.plugin;

import java.io.*;
import java.util.*;
import java.security.KeyStore;
import junit.framework.*;
import org.lockss.app.*;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.plugin.definable.*;
import org.lockss.poller.*;
import org.lockss.repository.*;
import org.lockss.util.*;
import org.lockss.test.*;
import org.lockss.repository.*;
import org.lockss.state.HistoryRepositoryImpl;

/**
 * Test class for org.lockss.plugin.PluginManager
 */
public class TestPluginManager extends LockssTestCase {
  private MyMockLockssDaemon theDaemon;

  static String mockPlugKey = "org|lockss|test|MockPlugin";
  static Properties props1 = new Properties();
  static Properties props2 = new Properties();
  static {
    props1.setProperty(MockPlugin.CONFIG_PROP_1, "val1");
    props1.setProperty(MockPlugin.CONFIG_PROP_2, "val2");
    props2.setProperty(MockPlugin.CONFIG_PROP_1, "val1");
    props2.setProperty(MockPlugin.CONFIG_PROP_2, "va.l3");//auid contains a dot
  }

  static String mauauidKey1 = PropUtil.propsToCanonicalEncodedString(props1);
  static String mauauid1 = mockPlugKey+"&"+ mauauidKey1;
  //  static String m

  static String mauauidKey2 = PropUtil.propsToCanonicalEncodedString(props2);
  static String mauauid2 = mockPlugKey+"&"+mauauidKey2;

  static String p1param =
    PluginManager.PARAM_AU_TREE + "." + mockPlugKey + ".";

  static String p1a1param = p1param + mauauidKey1 + ".";
  static String p1a2param = p1param + mauauidKey2 + ".";

  private String pluginJar;
  private String signAlias = "goodguy";
  private String pubKeystore = "org/lockss/test/public.keystore";
  private String password = "f00bar";

  private String tempDirPath;

  PluginManager mgr;

  public TestPluginManager(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    super.setUp();

    tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    theDaemon = new MyMockLockssDaemon();
    mgr = new MyMockPluginManager();
    theDaemon.setPluginManager(mgr);
    theDaemon.setDaemonInited(true);

    // Prepare the loadable plugin directory property, which is
    // created by mgr.startService()
    Properties p = new Properties();
    p.setProperty(PluginManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    p.setProperty(PluginManager.PARAM_PLUGIN_LOCATION, "plugins");
    ConfigurationUtil.setCurrentConfigFromProps(p);

    mgr.setLoadablePluginsReady(true);
    mgr.initService(theDaemon);
    mgr.startService();
  }

  public void tearDown() throws Exception {
    mgr.stopService();
    theDaemon.stopDaemon();
    super.tearDown();
  }

  private void doConfig() throws Exception {
    // String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    Properties p = new Properties();
    p.setProperty(p1a1param+MockPlugin.CONFIG_PROP_1, "val1");
    p.setProperty(p1a1param+MockPlugin.CONFIG_PROP_2, "val2");
    p.setProperty(p1a2param+MockPlugin.CONFIG_PROP_1, "val1");
    p.setProperty(p1a2param+MockPlugin.CONFIG_PROP_2, "va.l3");
    p.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    p.setProperty(HistoryRepositoryImpl.PARAM_HISTORY_LOCATION, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(p);
  }

  private void minimalConfig() throws Exception {
    // String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    ConfigurationUtil.setFromArgs(LockssRepositoryImpl.PARAM_CACHE_LOCATION,
				  tempDirPath,
				  HistoryRepositoryImpl.PARAM_HISTORY_LOCATION,
				  tempDirPath);
  }

  public void testNameFromKey() {
    assertEquals("org.lockss.Foo", PluginManager.pluginNameFromKey("org|lockss|Foo"));
  }

  public void testKeyFromName() {
    assertEquals("org|lockss|Foo", PluginManager.pluginKeyFromName("org.lockss.Foo"));
  }

  public void testKeyFromId() {
    assertEquals("org|lockss|Foo", PluginManager.pluginKeyFromId("org.lockss.Foo"));
  }

  public void testGetPreferredPluginType() throws Exception {
    // Prefer XML plugins.
    ConfigurationUtil.setFromArgs(PluginManager.PARAM_PREFERRED_PLUGIN_TYPE,
				  "xml");
    assertEquals(PluginManager.PREFER_XML_PLUGIN,
		 mgr.getPreferredPluginType());

    // Prefer CLASS plugins.
    ConfigurationUtil.setFromArgs(PluginManager.PARAM_PREFERRED_PLUGIN_TYPE,
				  "class");
    assertEquals(PluginManager.PREFER_CLASS_PLUGIN,
		 mgr.getPreferredPluginType());

    // Illegal type.
    ConfigurationUtil.setFromArgs(PluginManager.PARAM_PREFERRED_PLUGIN_TYPE,
				  "foo");
    assertEquals(PluginManager.PREFER_XML_PLUGIN,
		 mgr.getPreferredPluginType());

    // No type specified.
    ConfigurationUtil.setFromArgs("foo", "bar");
    assertEquals(PluginManager.PREFER_XML_PLUGIN,
		 mgr.getPreferredPluginType());
  }

  public void testEnsurePluginLoaded() throws Exception {
    // non-existent class shouldn't load
    String key = "org|lockss|NoSuchClass";
    // OK if this logs FileNotFoundException in the log.
    assertFalse(mgr.ensurePluginLoaded(key));
    assertNull(mgr.getPlugin(key));
    // MockPlugin should load
    assertTrue(mgr.ensurePluginLoaded(mockPlugKey));
    Plugin p = mgr.getPlugin(mockPlugKey);
    assertTrue(p.toString(), p instanceof MockPlugin);
    MockPlugin mpi = (MockPlugin)mgr.getPlugin(mockPlugKey);
    assertNotNull(mpi);
    assertEquals(1, mpi.getInitCtr());	// should have been inited once

    // second time shouldn't reload, reinstantiate, or reinitialize plugin
    assertTrue(mgr.ensurePluginLoaded(mockPlugKey));
    MockPlugin mpi2 = (MockPlugin)mgr.getPlugin(mockPlugKey);
    assertSame(mpi, mpi2);
    assertEquals(1, mpi.getInitCtr());
  }

  public void testInitPluginRegistry() {
    String n1 = "org.lockss.test.MockPlugin";
    String n2 = ThrowingMockPlugin.class.getName();
    assertEmpty(mgr.getRegisteredPlugins());
    ConfigurationUtil.setFromArgs(PluginManager.PARAM_PLUGIN_REGISTRY,
				  n1 + ";" + n2);
    Plugin p1 = mgr.getPlugin(mgr.pluginKeyFromName(n1));
    assertNotNull(p1);
    assertTrue(p1.toString(), p1 instanceof MockPlugin);
    Plugin p2 = mgr.getPlugin(mgr.pluginKeyFromName(n2));
    assertNotNull(p2);
    assertTrue(p2.toString(), p2 instanceof ThrowingMockPlugin);
    assertEquals(2, mgr.getRegisteredPlugins().size());
    ConfigurationUtil.setFromArgs(PluginManager.PARAM_PLUGIN_REGISTRY, n1);
    assertEquals(1, mgr.getRegisteredPlugins().size());
    assertNull(mgr.getPlugin(mgr.pluginKeyFromName(n2)));
    assertNotNull(mgr.getPlugin(mgr.pluginKeyFromName(n1)));
    assertTrue(mgr.getPlugin(mgr.pluginKeyFromName(n1)) instanceof MockPlugin);
  }

  public void testEnsurePluginLoadedXml() throws Exception {
    String pname = "org.lockss.test.TestXmlPlugin";
    String key = PluginManager.pluginKeyFromId(pname);
    assertTrue(mgr.ensurePluginLoaded(key));
    Plugin p = mgr.getPlugin(key);
    assertTrue(p.toString(), p instanceof DefinablePlugin);
    MyMockConfigurablePlugin mcpi = (MyMockConfigurablePlugin)mgr.getPlugin(key);
    assertNotNull(mcpi);
    List initArgs = mcpi.getInitArgs();
    assertEquals(1, initArgs.size());
    List args = (List)initArgs.get(0);
    assertEquals(3, args.size());
    assertEquals(pname, args.get(1));
  }

  public void testStop() throws Exception {
    doConfig();
    MockPlugin mpi = (MockPlugin)mgr.getPlugin(mockPlugKey);
    assertEquals(0, mpi.getStopCtr());
    mgr.stopService();
    assertEquals(1, mpi.getStopCtr());
  }

  public void testAuConfig() throws Exception {
    doConfig();
    MockPlugin mpi = (MockPlugin)mgr.getPlugin(mockPlugKey);
    // plugin should be registered
    assertNotNull(mpi);
    // should have been inited once
    assertEquals(1, mpi.getInitCtr());

    // get the two archival units
    ArchivalUnit au1 = mgr.getAuFromId(mauauid1);
    ArchivalUnit au2 = mgr.getAuFromId(mauauid2);

    // verify the plugin's set of all AUs is {au1, au2}
    assertEquals(SetUtil.set(au1, au2), new HashSet(mgr.getAllAus()));

    // verify au1's configuration
    assertEquals(mauauid1, au1.getAuId());
    MockArchivalUnit mau1 = (MockArchivalUnit)au1;
    Configuration c1 = mau1.getConfiguration();
    assertEquals("val1", c1.get(MockPlugin.CONFIG_PROP_1));
    assertEquals("val2", c1.get(MockPlugin.CONFIG_PROP_2));

    // verify au1's configuration
    assertEquals(mauauid2, au2.getAuId());
    MockArchivalUnit mau2 = (MockArchivalUnit)au2;
    Configuration c2 = mau2.getConfiguration();
    assertEquals("val1", c2.get(MockPlugin.CONFIG_PROP_1));
    assertEquals("va.l3", c2.get(MockPlugin.CONFIG_PROP_2));

    assertEquals(au1, mgr.getAuFromId(mauauid1));
  }

  public void testCreateAu() throws Exception {
    minimalConfig();
    String pid = new ThrowingMockPlugin().getPluginId();
    String key = PluginManager.pluginKeyFromId(pid);
    assertTrue(mgr.ensurePluginLoaded(key));
    ThrowingMockPlugin mpi = (ThrowingMockPlugin)mgr.getPlugin(key);
    assertNotNull(mpi);
    Configuration config = ConfigurationUtil.fromArgs("a", "b");
    ArchivalUnit au = mgr.createAu(mpi, config);

    // verify put in PluginManager map
    String auid = au.getAuId();
    ArchivalUnit aux = mgr.getAuFromId(auid);
    assertSame(au, aux);

    // verify got right config
    Configuration auConfig = au.getConfiguration();
    assertEquals("b", auConfig.get("a"));
    assertEquals(1, auConfig.keySet().size());
    assertEquals(mpi, au.getPlugin());

    // verify turns RuntimeException into ArchivalUnit.ConfigurationException
    mpi.setCfgEx(new ArchivalUnit.ConfigurationException("should be thrown"));
    try {
      ArchivalUnit au2 = mgr.createAu(mpi, config);
      fail("createAU should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) {
    }

    mpi.setRtEx(new ExpectedRuntimeException("Ok if in log"));
    try {
      ArchivalUnit au2 = mgr.createAu(mpi, config);
      fail("createAU should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) {
      // this is what's expected
    } catch (RuntimeException e) {
      fail("createAU threw RuntimeException", e);
    }

  }

  public void testConfigureAu() throws Exception {
    minimalConfig();
    String pid = new ThrowingMockPlugin().getPluginId();
    String key = PluginManager.pluginKeyFromId(pid);
    assertTrue(mgr.ensurePluginLoaded(key));
    ThrowingMockPlugin mpi = (ThrowingMockPlugin)mgr.getPlugin(key);
    assertNotNull(mpi);
    Configuration config = ConfigurationUtil.fromArgs("a", "b");
    ArchivalUnit au = mgr.createAu(mpi, config);

    String auid = au.getAuId();
    ArchivalUnit aux = mgr.getAuFromId(auid);
    assertSame(au, aux);

    // verify can reconfig
    mgr.configureAu(mpi, ConfigurationUtil.fromArgs("a", "c"), auid);
    Configuration auConfig = au.getConfiguration();
    assertEquals("c", auConfig.get("a"));
    assertEquals(1, auConfig.keySet().size());

    // verify turns RuntimeException into ArchivalUnit.ConfigurationException
    mpi.setCfgEx(new ArchivalUnit.ConfigurationException("should be thrown"));
    try {
      mgr.configureAu(mpi, config, auid);
      fail("configureAU should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) {
    }

    mpi.setRtEx(new ExpectedRuntimeException("Ok if in log"));
    try {
      mgr.configureAu(mpi, config, auid);
      fail("configureAU should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) {
      // this is what's expected
    } catch (RuntimeException e) {
      fail("createAU threw RuntimeException", e);
    }

  }

  public void testConfigureAuWithBogusAuid() throws Exception {
    minimalConfig();
    String pid = new ThrowingMockPlugin().getPluginId();
    String key = PluginManager.pluginKeyFromId(pid);
    assertTrue(mgr.ensurePluginLoaded(key));
    ThrowingMockPlugin mpi = (ThrowingMockPlugin)mgr.getPlugin(key);
    assertNotNull(mpi);
    // verify doesn't get created if auid doesn't match config
    try {
      mgr.configureAu(mpi, ConfigurationUtil.fromArgs("a", "c"), "bogos_auid");
      fail("Should have thrown ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) {
      log.debug(e.toString());
    }
    assertEmpty(theDaemon.getAuMgrsStarted());
  }

  public void testDeactivateAu() throws Exception {
    minimalConfig();
    String pid = new ThrowingMockPlugin().getPluginId();
    String key = PluginManager.pluginKeyFromId(pid);

    assertTrue(mgr.ensurePluginLoaded(key));
    ThrowingMockPlugin mpi = (ThrowingMockPlugin)mgr.getPlugin(key);
    assertNotNull(mpi);
    Configuration config = ConfigurationUtil.fromArgs("a", "b");
    ArchivalUnit au = mgr.createAu(mpi, config);
    assertNotNull(au);
    String auId = au.getAuId();
    mgr.configureAu(mpi, config, auId);

    // should not throw.
    try {
      assertFalse(mgr.getInactiveAuIds().contains(auId));
      mgr.deactivateAu(au);
      assertTrue(mgr.getInactiveAuIds().contains(auId));
    } catch (Exception ex) {
      fail("Deactivating au should not have thrown", ex);
    }

  }

  public void testCreateAndSaveAndDeleteAuConfiguration() throws Exception {
    minimalConfig();
    String pid = new ThrowingMockPlugin().getPluginId();
    String key = PluginManager.pluginKeyFromId(pid);

    assertTrue(mgr.ensurePluginLoaded(key));
    ThrowingMockPlugin mpi = (ThrowingMockPlugin)mgr.getPlugin(key);
    assertNotNull(mpi);

    Properties props = new Properties();
    props.put("a", "b");

    // Test creating and deleting by au reference.
    ArchivalUnit au1 = mgr.createAndSaveAuConfiguration(mpi, props);
    assertNotNull(au1);
    try {
      mgr.deleteAuConfiguration(au1);
    } catch (Exception e) {
      fail("Deleting au config by AU reference should not have thrown", e);
    }

    // Test creating and deleting by au ID.
    ArchivalUnit au2 = mgr.createAndSaveAuConfiguration(mpi, props);
    assertNotNull(au2);
    try {
      mgr.deleteAuConfiguration(au2.getAuId());
    } catch (Exception e) {
      fail("Deleting au config by AU ID should not have thrown", e);
    }

    // Test setAndSaveAuConfiguration
    ArchivalUnit au3 = mgr.createAu(mpi,
				    ConfigurationUtil.fromArgs("foo", "bar"));
    try {
      mgr.setAndSaveAuConfiguration(au3, props);

      mgr.deleteAu(au3);
    } catch (Exception e) {
      fail("Deleting AU should not have thrown", e);
    }
  }

  // ensure getAllAus() returns AUs in title sorted order
  public void testGetAllAus() throws Exception {
    MockArchivalUnit mau1 = new MockArchivalUnit();
    MockArchivalUnit mau2 = new MockArchivalUnit();
    MockArchivalUnit mau3 = new MockArchivalUnit();
    MockArchivalUnit mau4 = new MockArchivalUnit();
    MockArchivalUnit mau5 = new MockArchivalUnit();
    // make sure it sorts by name, not auid
    mau1.setName("foobarm1"); mau1.setAuId("5");
    mau2.setName("foom2"); mau2.setAuId("3");
    mau3.setName("gunt"); mau3.setAuId("4");
    mau4.setName("m4"); mau4.setAuId("1");
    mau5.setName("zzyzx"); mau5.setAuId("2");
    mgr.putAuInMap(mau5);
    mgr.putAuInMap(mau4);
    mgr.putAuInMap(mau2);
    mgr.putAuInMap(mau3);
    mgr.putAuInMap(mau1);
    assertEquals(ListUtil.list(mau1, mau2, mau3, mau4, mau5), mgr.getAllAus());
  }

  public void testTitleSets() throws Exception {
    String ts1p = PluginManager.PARAM_TITLE_SETS + ".s1.";
    String ts2p = PluginManager.PARAM_TITLE_SETS + ".s2.";
    String title1 = "Title Set 1";
    String title2 = "Set of Titles";
    String path1 = "[journalTitle='Dog Journal']";
    String path2 = "[journalTitle=\"Dog Journal\" or pluginName=\"plug2\"]";
    Properties p = new Properties();
    p.setProperty(ts1p+"class", "xpath");
    p.setProperty(ts1p+"name", title1);
    p.setProperty(ts1p+"xpath", path1);
    p.setProperty(ts2p+"class", "xpath");
    p.setProperty(ts2p+"name", title2);
    p.setProperty(ts2p+"xpath", path2);
    ConfigurationUtil.setCurrentConfigFromProps(p);
    Map map = mgr.getTitleSetMap();
    assertEquals(new TitleSetXpath(theDaemon, title1, path1), map.get(title1));
    assertEquals(new TitleSetXpath(theDaemon, title2, path2), map.get(title2));
  }

  static class MyMockLockssDaemon extends MockLockssDaemon {
    List auMgrsStarted = new ArrayList();

    public void startOrReconfigureAuManagers(ArchivalUnit au,
					     Configuration auConfig)
	throws Exception {
      auMgrsStarted.add(au);
    }
    List getAuMgrsStarted() {
      return auMgrsStarted;
    }

    // For testLoadLoadablePlugins -- need a mock repository
    public LockssRepository getLockssRepository(ArchivalUnit au) {
      return new MyMockLockssRepository();
    }
  }

  static class MyMockLockssRepository extends MockLockssRepository {
    public RepositoryNode getNode(String url) {
      return new MyMockRepositoryNode();
    }
  }

  static class MyMockRepositoryNode extends MockRepositoryNode {
    public int getCurrentVersion() {
      return 1;
    }
  }

  static class MyMockPluginManager extends PluginManager {
    protected String getConfigurablePluginName() {
      return MyMockConfigurablePlugin.class.getName();
    }
  }

  static class MyMockConfigurablePlugin extends DefinablePlugin {
    private List initArgs = new ArrayList();

    public void initPlugin(LockssDaemon daemon, String extMapName, ClassLoader loader)
	throws FileNotFoundException {
      initArgs.add(ListUtil.list(daemon, extMapName, loader));
      super.initPlugin(daemon, extMapName, loader);
    }

    List getInitArgs() {
      return initArgs;
    }
  }

  static class ThrowingMockPlugin extends MockPlugin {
    RuntimeException rtEx;
    ArchivalUnit.ConfigurationException cfgEx;;

    public void setRtEx(RuntimeException rtEx) {
      this.rtEx = rtEx;
    }
    public void setCfgEx(ArchivalUnit.ConfigurationException cfgEx) {
      this.cfgEx = cfgEx;
    }
    public ArchivalUnit createAu(Configuration config)
	throws ArchivalUnit.ConfigurationException {
      if (rtEx != null) {
	throw rtEx;
      } else if (cfgEx != null) {
	throw cfgEx;
      } else {
	return super.createAu(config);
      }
    }
    public ArchivalUnit configureAu(Configuration config, ArchivalUnit au)
	throws ArchivalUnit.ConfigurationException {
      if (rtEx != null) {
	throw rtEx;
      } else if (cfgEx != null) {
	throw cfgEx;
      } else {
	return super.configureAu(config, au);
      }
    }
  }


  public void testFindCus() throws Exception {
    String url = "http://foo.bar/";
    String lower = "abc";
    String upper = "xyz";

    doConfig();
    MockPlugin mpi = (MockPlugin)mgr.getPlugin(mockPlugKey);

    // make a PollSpec with info from a manually created CUS, which should
    // match one of the registered AUs
    CachedUrlSet protoCus = makeCus(mpi, mauauid1, url, lower, upper);
    PollSpec ps1 = new PollSpec(protoCus, Poll.CONTENT_POLL);

    // verify PluginManager can make a CUS for the PollSpec
    CachedUrlSet cus = mgr.findCachedUrlSet(ps1);
    assertNotNull(cus);
    // verify the CUS's CUSS
    CachedUrlSetSpec cuss = cus.getSpec();
    assertEquals(url, cuss.getUrl());
    RangeCachedUrlSetSpec rcuss = (RangeCachedUrlSetSpec)cuss;
    assertEquals(lower, rcuss.getLowerBound());
    assertEquals(upper, rcuss.getUpperBound());

    assertEquals(mauauid1, cus.getArchivalUnit().getAuId());
    // can't test protoCus.getArchivalUnit() .equals( cus.getArchivalUnit() )
    // as we made a fake mock one to build PollSpec, and PluginManager will
    // have created & configured a real mock one.

    CachedUrlSet protoAuCus = makeAuCus(mpi, mauauid1);
    PollSpec ps2 = new PollSpec(protoAuCus, Poll.CONTENT_POLL);

    CachedUrlSet aucus = mgr.findCachedUrlSet(ps2);
    assertNotNull(aucus);
    CachedUrlSetSpec aucuss = aucus.getSpec();
    assertTrue(aucuss instanceof AuCachedUrlSetSpec);
  }

  public void testFindSingleNodeCus() throws Exception {
    String url = "http://foo.bar/";
    String lower = PollSpec.SINGLE_NODE_LWRBOUND;

    doConfig();
    MockPlugin mpi = (MockPlugin)mgr.getPlugin(mockPlugKey);

    // make a PollSpec with info from a manually created CUS, which should
    // match one of the registered AUs
    CachedUrlSet protoCus = makeCus(mpi, mauauid1, url, lower, null);
    PollSpec ps1 = new PollSpec(protoCus, Poll.CONTENT_POLL);

    // verify PluginManager can make a CUS for the PollSpec
    CachedUrlSet cus = mgr.findCachedUrlSet(ps1);
    assertNotNull(cus);
    // verify the CUS's CUSS
    CachedUrlSetSpec cuss = cus.getSpec();
    assertTrue(cuss instanceof SingleNodeCachedUrlSetSpec);
    assertEquals(url, cuss.getUrl());
  }

  public void testFindMostRecentCachedUrl() throws Exception {
    String prefix = "http://foo.bar/";
    String url1 = "http://foo.bar/baz";
    String url2 = "http://foo.bar/not";
    doConfig();
    MockPlugin mpi = (MockPlugin)mgr.getPlugin(mockPlugKey);

    // get the two archival units
    MockArchivalUnit au1 = (MockArchivalUnit)mgr.getAuFromId(mauauid1);
//     ArchivalUnit au2 = mgr.getAuFromId(mauauid2);
    assertNull(mgr.findMostRecentCachedUrl(url1));
    CachedUrlSetSpec cuss = new MockCachedUrlSetSpec(prefix, null);
    MockCachedUrlSet mcuss = new MockCachedUrlSet(au1, cuss);
    au1.addUrl(url1, true, true, null);
    au1.setAuCachedUrlSet(mcuss);
    CachedUrl cu = mgr.findMostRecentCachedUrl(url1);
    assertNotNull(cu);
    assertEquals(url1, cu.getUrl());
    assertNull(mgr.findMostRecentCachedUrl(url2));
  }

  public void testFindMostRecentCachedUrlWithNormalization()
      throws Exception {
    final String prefix = "http://foo.bar/"; // pseudo crawl rule prefix
    String url0 = "http://foo.bar/xxx/baz"; // normal form of test url
    String url1 = "http://foo.bar/SESSION/xxx/baz"; // should normalize to url0
    String url2 = "http://foo.bar/SESSION/not";	// should not
    // manually create necessary pieces, no config
    MockPlugin mpi = new MockPlugin();
    MockArchivalUnit mau = new MockArchivalUnit() {
	// shouldBeCached() is true of anything starting with prefix
	public boolean shouldBeCached(String url) {
	  return StringUtil.startsWithIgnoreCase(url, prefix);
	}
	// siteNormalizeUrl() removes "SESSION/" from url
	public String siteNormalizeUrl(String url) {
	  return StringUtil.replaceString(url, "SESSION/", "");
	}
      };
    mau.setPlugin(mpi);
    mau.setAuId("mauauidddd");
    mgr.putAuInMap(mau);
    // neither url is found
    assertNull(mgr.findMostRecentCachedUrl(url1));
    assertNull(mgr.findMostRecentCachedUrl(url2));
    // create mock structure so that url0 exists with content
    CachedUrlSetSpec cuss = new MockCachedUrlSetSpec(prefix, null);
    MockCachedUrlSet mcuss = new MockCachedUrlSet(mau, cuss);
    mau.addUrl(url0, true, true, null);
    mau.setAuCachedUrlSet(mcuss);
    // url1 should now be found, as url0
    CachedUrl cu = mgr.findMostRecentCachedUrl(url1);
    assertNotNull(cu);
    assertEquals(url0, cu.getUrl());
    // url2 still not found
    assertNull(mgr.findMostRecentCachedUrl(url2));
  }

  public void testGenerateAuId() {
    Properties props = new Properties();
    props.setProperty("key&1", "val=1");
    props.setProperty("key2", "val 2");
    props.setProperty("key.3", "val:3");
    props.setProperty("key4", "val.4");
    String pluginId = "org|lockss|plugin|Blah";

    String actual = PluginManager.generateAuId(pluginId, props);
    String expected =
      pluginId+"&"+
      "key%261~val%3D1&"+
      "key%2E3~val%3A3&"+
      "key2~val+2&"+
      "key4~val%2E4";
    assertEquals(expected, actual);
  }


  public void testConfigKeyFromAuId() {
    String pluginId = "org|lockss|plugin|Blah";
    String auId = "base_url~foo&volume~123";

    String totalId = PluginManager.generateAuId(pluginId, auId);
    String expectedStr = pluginId + "." + auId;
    assertEquals(expectedStr, PluginManager.configKeyFromAuId(totalId));
  }

  public CachedUrlSet makeCus(Plugin plugin, String auid, String url,
			       String lower, String upper) {
    MockArchivalUnit au = new MockArchivalUnit();
    au.setAuId(auid);
    au.setPlugin(plugin);
    au.setPluginId(plugin.getPluginId());

    CachedUrlSet cus = new MockCachedUrlSet(au,
					    new RangeCachedUrlSetSpec(url,
								      lower,
								      upper));
    return cus;
  }

  public CachedUrlSet makeAuCus(Plugin plugin, String auid) {
    MockArchivalUnit au = new MockArchivalUnit();
    au.setAuId(auid);
    au.setPlugin(plugin);
    au.setPluginId(plugin.getPluginId());

    CachedUrlSet cus = new MockCachedUrlSet(au, new AuCachedUrlSetSpec());
    return cus;
  }

  // ensure that wrapper is harmless when not enabled
  public void testWrappedAu() {
    if (!WrapperState.isUsingWrapping()) {
      try {
	// String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
	Properties p = new Properties();
	p.setProperty(p1a1param + MockPlugin.CONFIG_PROP_1, "val1");
	p.setProperty(p1a1param + MockPlugin.CONFIG_PROP_2, "val2");
	p.setProperty(p1a1param + "reserved.wrapper", "true");
	p.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
	p.setProperty(HistoryRepositoryImpl.PARAM_HISTORY_LOCATION, tempDirPath);
	ConfigurationUtil.setCurrentConfigFromProps(p);
	ArchivalUnit wau = (ArchivalUnit) mgr.getAuFromId(
	    mauauid1);
	Plugin wplug = (Plugin) wau.getPlugin();
	MockPlugin mock = (MockPlugin) WrapperState.getOriginal(wplug);
	assertSame(mock,wplug);
	MockArchivalUnit mau = (MockArchivalUnit) WrapperState.getOriginal(wau);
	assertSame(mock, mau.getPlugin());
	assertSame(mau,wau);
	//      } catch (IOException e) {
	//	fail(e.getMessage());
      } catch (ClassCastException e) {
	fail("WrappedArchivalUnit not found.");
      }
    }
  }

  private KeyStore getKeystoreResource(String name, String pass)
      throws Exception {
    KeyStore ks = KeyStore.getInstance("JKS", "SUN");
    ks.load(ClassLoader.getSystemClassLoader().
	    getResourceAsStream(name), pass.toCharArray());
    return ks;
  }

  private void prepareLoadablePluginTests() throws Exception {
    pluginJar = "org/lockss/test/good-plugin.jar";
    Properties p = new Properties();
    p.setProperty(PluginManager.PARAM_KEYSTORE_LOCATION,
		  pubKeystore);
    p.setProperty(PluginManager.PARAM_KEYSTORE_PASSWORD,
		  password);
    p.setProperty(PluginManager.PARAM_PLUGIN_REGISTRIES,
		  "");
    ConfigurationUtil.setCurrentConfigFromProps(p);
  }


  /** Test loading a loadable plugin. */
  public void testLoadLoadablePlugin() throws Exception {
    prepareLoadablePluginTests();
    String pluginKey = "org|lockss|test|MockConfigurablePlugin";
    // Set up a MyMockRegistryArchivalUnit with the right data.
    List plugins =
      ListUtil.list(pluginJar);
    List registryAus =
      ListUtil.list(new MyMockRegistryArchivalUnit(plugins));
    assertNull(mgr.getPlugin(pluginKey));
    mgr.processRegistryAus(registryAus);
    Plugin mockPlugin = mgr.getPlugin(pluginKey);
    assertNotNull(mockPlugin);
    assertEquals("1", mockPlugin.getVersion());
  }

  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTestSuite(TestPluginManager.class);
    return suite;
  }

  /**
   * a mock Archival Unit used for testing loadable plugin loading.
   */
  private static class MyMockRegistryArchivalUnit extends MockArchivalUnit {
    private MyMockRegistryCachedUrlSet cus;

    public MyMockRegistryArchivalUnit(List jarFiles) {
      super(null);
      cus = new MyMockRegistryCachedUrlSet();
      int n = 0;
      for (Iterator iter = jarFiles.iterator(); iter.hasNext(); ) {
	n++;
	cus.addCu(new MockCachedUrl("http://foo.bar/test" + n + ".jar",
				    (String)iter.next(), true));
      }
    }

    public CachedUrlSet getAuCachedUrlSet() {
      return cus;
    }
  }

  /**
   * a mock CachedUrlSet used for testing loadable plugin loading.
   */
  private static class MyMockRegistryCachedUrlSet extends MockCachedUrlSet {
    List cuList;

    public MyMockRegistryCachedUrlSet() {
      cuList = new ArrayList();
    }

    public void addCu(MockCachedUrl cu) {
      cuList.add(cu);
    }

    public Iterator contentHashIterator() {
      return cuList.iterator();
    }
  }
}
