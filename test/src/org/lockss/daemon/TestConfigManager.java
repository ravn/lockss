/*
 * $Id: TestConfigManager.java,v 1.1 2003-07-14 06:41:57 tlipkis Exp $
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

import java.util.*;
import java.io.*;
import java.net.*;
import org.lockss.util.*;
import org.lockss.test.*;

/**
 * Test class for <code>org.lockss.util.ConfigManager</code>
 */

public class TestConfigManager extends LockssTestCase {
//   public static Class testedClasses[] = {
//     org.lockss.daemon.ConfigManager.class
//   };

  ConfigManager mgr;

  public void setUp() throws Exception {
    super.setUp();
    mgr =  ConfigManager.makeConfigManager();
  }

  public void tearDown() throws Exception {
    ConfigManager.resetForTesting();
    super.tearDown();
  }

  static Logger log = Logger.getLogger("TestConfig");

  private static final String c1 = "prop1=12\nprop2=foobar\nprop3=true\n" +
    "prop5=False\n";
  private static final String c1a = "prop2=xxx\nprop4=yyy\n";

  private static final String c2 =
    "timeint=14d\n" +
    "prop.p1=12\n" +
    "prop.p2=foobar\n" +
    "prop.p3.a=true\n" +
    "prop.p3.b=false\n" +
    "otherprop.p3.b=foo\n";

  public void testParam() throws IOException, Configuration.InvalidParam {
    Configuration config = mgr.newConfiguration();
    config.load(FileUtil.urlOfString(c2));
    mgr.setCurrentConfig(config);
    assertEquals("12", ConfigManager.getParam("prop.p1"));
    assertEquals("foobar", ConfigManager.getParam("prop.p2"));
    assertTrue(ConfigManager.getBooleanParam("prop.p3.a", false));
    assertEquals(12, ConfigManager.getIntParam("prop.p1"));
    assertEquals(554, ConfigManager.getIntParam("propnot.p1", 554));
    assertEquals(2 * Constants.WEEK,
		 ConfigManager.getTimeIntervalParam("timeint", 554));
    assertEquals(554, ConfigManager.getTimeIntervalParam("noparam", 554));

    // these should go once static param methods are removed from Configuration
    assertEquals("12", Configuration.getParam("prop.p1"));
    assertEquals("foobar", Configuration.getParam("prop.p2"));
    assertTrue(Configuration.getBooleanParam("prop.p3.a", false));
    assertEquals(12, Configuration.getIntParam("prop.p1"));
    assertEquals(554, Configuration.getIntParam("propnot.p1", 554));
    assertEquals(2 * Constants.WEEK,
		 Configuration.getTimeIntervalParam("timeint", 554));
    assertEquals(554, Configuration.getTimeIntervalParam("noparam", 554));
  }

  boolean setCurrentConfigFromUrlList(List l) {
    Configuration config = mgr.readConfig(l);
    return mgr.installConfig(config);
  }

  boolean setCurrentConfigFromString(String s)
      throws IOException {
    return setCurrentConfigFromUrlList(ListUtil.list(FileUtil.urlOfString(s)));
  }

  public void testCurrentConfig() throws IOException {
    assertTrue(setCurrentConfigFromUrlList(ListUtil.
					   list(FileUtil.urlOfString(c1),
						FileUtil.urlOfString(c1a))));
    assertEquals("12", ConfigManager.getParam("prop1"));
    Configuration config = ConfigManager.getCurrentConfig();
    assertEquals("12", config.get("prop1"));
    assertEquals("12", config.get("prop1", "wrong"));
    assertEquals("xxx", config.get("prop2"));
    assertTrue(config.getBoolean("prop3", false));
    assertEquals("yyy", config.get("prop4"));
    assertEquals("def", config.get("noprop", "def"));
    assertEquals("def", ConfigManager.getParam("noprop", "def"));
  }

  volatile Set diffSet = null;
  List configs;

  public void testCallback() throws IOException {
    configs = new ArrayList();
    setCurrentConfigFromUrlList(ListUtil.list(FileUtil.urlOfString(c1),
					      FileUtil.urlOfString(c1a)));
    assertEquals(0, configs.size());
    mgr.registerConfigurationCallback(new Configuration.Callback() {
	public void configurationChanged(Configuration newConfig,
					 Configuration oldConfig,
					 Set changedKeys) {
	  assertNotNull(oldConfig);
	  configs.add(newConfig);
	}
      });
    assertEquals(1, configs.size());
  }

  public void testCallbackDiffs() throws IOException {
    setCurrentConfigFromUrlList(ListUtil.list(FileUtil.urlOfString(c1),
					      FileUtil.urlOfString(c1a)));
    System.out.println(mgr.getCurrentConfig().toString());
    mgr.registerConfigurationCallback(new Configuration.Callback() {
	public void configurationChanged(Configuration newConfig,
					 Configuration oldConfig,
					 Set changedKeys) {
	  System.out.println("Notify: " + changedKeys);
	  diffSet = changedKeys;
	}
      });
    assertTrue(setCurrentConfigFromUrlList(ListUtil.
					   list(FileUtil.urlOfString(c1a),
						FileUtil.urlOfString(c1))));
    assertEquals(SetUtil.set("prop2"), diffSet);
    System.out.println(mgr.getCurrentConfig().toString());
    assertTrue(setCurrentConfigFromUrlList(ListUtil.
					   list(FileUtil.urlOfString(c1),
						FileUtil.urlOfString(c1))));
    assertEquals(SetUtil.set("prop4"), diffSet);
    System.out.println(mgr.getCurrentConfig().toString());
    assertTrue(setCurrentConfigFromUrlList(ListUtil.
					   list(FileUtil.urlOfString(c1),
						FileUtil.urlOfString(c1a))));
    assertEquals(SetUtil.set("prop4", "prop2"), diffSet);
    System.out.println(mgr.getCurrentConfig().toString());

  }

  public void testPlatformProps() throws Exception {
    Properties props = new Properties();
    props.put("org.lockss.platform.localIPAddress", "1.2.3.4");
    props.put("org.lockss.platform.logdirectory", "/var/log/foo");
    props.put("org.lockss.platform.logfile", "bar");
    ConfigurationUtil.setCurrentConfigFromProps(props);
    Configuration config = mgr.getCurrentConfig();
    assertEquals("1.2.3.4", config.get("org.lockss.localIPAddress"));
    assertEquals("/var/log/foo/bar", config.get(FileTarget.PARAM_FILE));
  }
 
  public void testPlatformAccess1() throws Exception {
    // platform access set, ui and proxy access not set
    Properties props = new Properties();
    props.put("org.lockss.platform.accesssubnet", "1.2.3.*");
    ConfigurationUtil.setCurrentConfigFromProps(props);
    Configuration config = mgr.getCurrentConfig();
    assertEquals("1.2.3.*", config.get("org.lockss.ui.access.ip.include"));
    assertEquals("1.2.3.*", config.get("org.lockss.proxy.access.ip.include"));
  }

  public void testPlatformAccess2() throws Exception {
    // platform access not set, ui and proxy access set
    Properties props = new Properties();
    props.put("org.lockss.ui.access.ip.include", "1.2.3.0/22");
    props.put("org.lockss.proxy.access.ip.include", "1.2.3.0/21");
    ConfigurationUtil.setCurrentConfigFromProps(props);
    Configuration config = mgr.getCurrentConfig();
    assertEquals("1.2.3.0/22", config.get("org.lockss.ui.access.ip.include"));
    assertEquals("1.2.3.0/21",
		 config.get("org.lockss.proxy.access.ip.include"));
  }

  public void testPlatformAccess3() throws Exception {
    // platform access set, ui and proxy access set
    Properties props = new Properties();
    props.put("org.lockss.platform.accesssubnet", "1.2.3.*");
    props.put("org.lockss.ui.access.ip.include", "1.2.3.0/22");
    props.put("org.lockss.proxy.access.ip.include", "1.2.3.0/21");
    ConfigurationUtil.setCurrentConfigFromProps(props);
    Configuration config = mgr.getCurrentConfig();
    assertEquals("1.2.3.*;1.2.3.0/22",
		 config.get("org.lockss.ui.access.ip.include"));
    assertEquals("1.2.3.*;1.2.3.0/21",
		 config.get("org.lockss.proxy.access.ip.include"));
  }

  public void testPlatformSpace1() throws Exception {
    Properties props = new Properties();
    props.put("org.lockss.platform.diskSpacePaths", "/foo/bar");
    ConfigurationUtil.setCurrentConfigFromProps(props);
    Configuration config = mgr.getCurrentConfig();
    assertEquals("/foo/bar", config.get("org.lockss.cache.location"));
    assertEquals("/foo/bar", config.get("org.lockss.history.location"));
  }

  public void testPlatformSpace2() throws Exception {
    Properties props = new Properties();
    props.put("org.lockss.platform.diskSpacePaths", "/a/b;/foo/bar");
    ConfigurationUtil.setCurrentConfigFromProps(props);
    Configuration config = mgr.getCurrentConfig();
    assertEquals("/a/b", config.get("org.lockss.cache.location"));
    assertEquals("/a/b", config.get("org.lockss.history.location"));
    assertEquals("/a/b/iddb", config.get("org.lockss.id.database.dir"));
  }

  public void testPlatformSmtp() throws Exception {
    Properties props = new Properties();
    props.put("org.lockss.platform.smtphost", "smtp.example.com");
    props.put("org.lockss.platform.smtpport", "25");
    ConfigurationUtil.setCurrentConfigFromProps(props);
    Configuration config = mgr.getCurrentConfig();
    assertEquals("smtp.example.com", 
		 config.get("org.lockss.log.target.MailTarget.smtphost"));
    assertEquals("25", 
		 config.get("org.lockss.log.target.MailTarget.smtpport"));
  }

  public void testPlatformConfigDirSetup() throws Exception {
    String tmpdir = getTempDir().toString();
    Properties props = new Properties();
    props.put(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tmpdir);
    ConfigurationUtil.setCurrentConfigFromProps(props);
    String relConfigPath =
      Configuration.getParam(ConfigManager.PARAM_CONFIG_PATH,
			     ConfigManager.DEFAULT_CONFIG_PATH);
    File cdir = new File(tmpdir, relConfigPath);
    assertTrue(cdir.exists());
    Configuration config = Configuration.getCurrentConfig();
  }

  public void testPlatformConfigIpAccess() throws Exception {
    String tmpdir = getTempDir().toString();
    Properties props = new Properties();
    props.put(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tmpdir);
    ConfigurationUtil.setCurrentConfigFromProps(props);
    String relConfigPath =
      Configuration.getParam(ConfigManager.PARAM_CONFIG_PATH,
			     ConfigManager.DEFAULT_CONFIG_PATH);
    File cdir = new File(tmpdir, relConfigPath);
    assertTrue(cdir.exists());
    Properties acprops = new Properties();
    acprops.put("foo.bar" , "12345");
    mgr.writeCacheConfigFile(acprops,
				       ConfigManager.CONFIG_FILE_UI_IP_ACCESS,
				       "this is a header");
    File acfile = new File(cdir, ConfigManager.CONFIG_FILE_UI_IP_ACCESS);
    log.info("wrote ac file");
    assertTrue(acfile.exists());
    Configuration config = Configuration.getCurrentConfig();
    assertNull(config.get("foo.bar"));
    ConfigurationUtil.setCurrentConfigFromProps(props);
    Configuration config2 = Configuration.getCurrentConfig();
    assertEquals("12345", config2.get("foo.bar"));
  }
}
