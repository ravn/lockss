/*
 * $Id: TestDaemonStatusService.java,v 1.2 2014-04-04 22:00:45 fergaloy-sf Exp $
 */

/*

 Copyright (c) 2013-2014 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.ws.status;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.PluginException;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.plugin.ArticleIteratorFactory;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.PluginManager;
import org.lockss.plugin.PluginTestUtil;
import org.lockss.plugin.SubTreeArticleIterator;
import org.lockss.plugin.simulated.SimulatedArchivalUnit;
import org.lockss.plugin.simulated.SimulatedContentGenerator;
import org.lockss.plugin.simulated.SimulatedDefinablePlugin;
import org.lockss.test.ConfigurationUtil;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockLockssDaemon;
import org.lockss.util.ExternalizableMap;
import org.lockss.util.Logger;
import org.lockss.ws.entities.IdNamePair;
import org.lockss.ws.entities.PluginWsResult;

/**
 * Test class for org.lockss.ws.status.DaemonStatusService
 * 
 * @author Fernando Garcia-Loygorri
 */
public class TestDaemonStatusService extends LockssTestCase {
  static Logger log = Logger.getLogger(TestDaemonStatusService.class);

  private MockLockssDaemon theDaemon;
  private DaemonStatusServiceImpl service;

  public void setUp() throws Exception {
    super.setUp();

    String tempDirPath = getTempDir().getAbsolutePath();

    Properties props = new Properties();
    props.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST,
		      tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    theDaemon = getMockLockssDaemon();
    theDaemon.setDaemonInited(true);

    PluginManager pluginManager = theDaemon.getPluginManager();
    pluginManager.startService();

    SimulatedArchivalUnit sau0 =
	PluginTestUtil.createAndStartSimAu(MySimulatedPlugin0.class,
	    simAuConfig(tempDirPath + "/0"));

    PluginTestUtil.crawlSimAu(sau0);

    SimulatedArchivalUnit sau1 =
	PluginTestUtil.createAndStartSimAu(MySimulatedPlugin1.class,
	    simAuConfig(tempDirPath + "/1"));

    PluginTestUtil.crawlSimAu(sau1);

    service = new DaemonStatusServiceImpl();
  }

  /**
   * Runs the tests that verify whether the daemon is ready.
   * 
   * @throws Exception
   */
  public void testIsDaemonReady() throws Exception {
    theDaemon.setAusStarted(true);
    assertTrue(service.isDaemonReady());
    theDaemon.setAusStarted(false);
    assertFalse(service.isDaemonReady());
  }

  /**
   * Runs the test that gets the identifiers of all the AUs.
   * 
   * @throws Exception
   */
  public void testGetAuIds() throws Exception {
    Collection<IdNamePair> auIds = service.getAuIds();
    assertEquals(2, auIds.size());
  }

  /**
   * Runs the test that queries plugins.
   * 
   * @throws Exception
   */
  public void testQueryPlugins() throws Exception {
    String pluginIdStart =
	"org.lockss.ws.status.TestDaemonStatusService$MySimulatedPlugin";
    String query = "select *";
    List<PluginWsResult> plugins = service.queryPlugins(query);
    assertEquals(2, plugins.size());
    PluginWsResult plugin = plugins.get(0);
    assertTrue(plugin.getPluginId().startsWith(pluginIdStart));
    assertEquals("Simulated Content", plugin.getName());
    assertEquals("SimulatedVersion", plugin.getVersion());
    assertEquals("Builtin", plugin.getType());
    assertNull(plugin.getRegistry());
    plugin = plugins.get(1);
    assertTrue(plugin.getPluginId().startsWith(pluginIdStart));
    assertEquals("Simulated Content", plugin.getName());
    assertEquals("SimulatedVersion", plugin.getVersion());
    assertEquals("Builtin", plugin.getType());
    assertNull(plugin.getRegistry());

    query = "select pluginId";
    plugins = service.queryPlugins(query);
    assertEquals(2, plugins.size());
    plugin = plugins.get(0);
    assertTrue(plugin.getPluginId().startsWith(pluginIdStart));
    assertNull(plugin.getName());
    assertNull(plugin.getVersion());
    assertNull(plugin.getType());
    assertNull(plugin.getDefinition());
    assertNull(plugin.getRegistry());
    plugin = plugins.get(0);
    assertTrue(plugin.getPluginId().startsWith(pluginIdStart));
    assertNull(plugin.getName());
    assertNull(plugin.getVersion());
    assertNull(plugin.getType());
    assertNull(plugin.getDefinition());
    assertNull(plugin.getRegistry());

    query = "select name";
    plugins = service.queryPlugins(query);
    assertEquals(2, plugins.size());
    plugin = plugins.get(0);
    assertNull(plugin.getPluginId());
    assertEquals("Simulated Content", plugin.getName());
    assertNull(plugin.getVersion());
    assertNull(plugin.getType());
    assertNull(plugin.getDefinition());
    assertNull(plugin.getRegistry());
    plugin = plugins.get(1);
    assertNull(plugin.getPluginId());
    assertEquals("Simulated Content", plugin.getName());
    assertNull(plugin.getVersion());
    assertNull(plugin.getType());
    assertNull(plugin.getDefinition());
    assertNull(plugin.getRegistry());

    query = "select version, type";
    plugins = service.queryPlugins(query);
    assertEquals(2, plugins.size());
    plugin = plugins.get(0);
    assertNull(plugin.getPluginId());
    assertNull(plugin.getName());
    assertEquals("SimulatedVersion", plugin.getVersion());
    assertEquals("Builtin", plugin.getType());
    assertNull(plugin.getDefinition());
    assertNull(plugin.getRegistry());
    plugin = plugins.get(1);
    assertNull(plugin.getPluginId());
    assertNull(plugin.getName());
    assertEquals("SimulatedVersion", plugin.getVersion());
    assertEquals("Builtin", plugin.getType());
    assertNull(plugin.getDefinition());
    assertNull(plugin.getRegistry());

    String pluginIdStart0 =
	"org.lockss.ws.status.TestDaemonStatusService$MySimulatedPlugin0";
    query = "select * where pluginId like '%Plugin0'";
    plugins = service.queryPlugins(query);
    assertEquals(1, plugins.size());
    plugin = plugins.get(0);
    assertEquals(pluginIdStart0, plugin.getPluginId());
    assertEquals("Simulated Content", plugin.getName());
    assertEquals("SimulatedVersion", plugin.getVersion());
    assertEquals("Builtin", plugin.getType());
    assertEquals(1, plugin.getDefinition().size());
    assertNull(plugin.getRegistry());

    String pluginIdStart1 =
	"org.lockss.ws.status.TestDaemonStatusService$MySimulatedPlugin1";
    query = "select * where pluginId like '%Plugin1'";
    plugins = service.queryPlugins(query);
    assertEquals(1, plugins.size());
    plugin = plugins.get(0);
    assertEquals(pluginIdStart1, plugin.getPluginId());
    assertEquals("Simulated Content", plugin.getName());
    assertEquals("SimulatedVersion", plugin.getVersion());
    assertEquals("Builtin", plugin.getType());
    assertEquals(2, plugin.getDefinition().size());
    assertNull(plugin.getRegistry());

    query = "select * where name = 'Real Content'";
    plugins = service.queryPlugins(query);
    assertEquals(0, plugins.size());

    query = "select * where pluginId like '%Plugin0' or name = 'Real Content'";
    plugins = service.queryPlugins(query);
    assertEquals(1, plugins.size());
    plugin = plugins.get(0);
    assertEquals(pluginIdStart0, plugin.getPluginId());
    assertEquals("Simulated Content", plugin.getName());
    assertEquals("SimulatedVersion", plugin.getVersion());
    assertEquals("Builtin", plugin.getType());
    assertEquals(1, plugin.getDefinition().size());
    assertNull(plugin.getRegistry());

    query = "select * where pluginId like '%Plugin0' and name = 'Real Content'";
    plugins = service.queryPlugins(query);
    assertEquals(0, plugins.size());

    query = "select pluginId,definition where type='Builtin' order by pluginId";
    plugins = service.queryPlugins(query);
    assertEquals(2, plugins.size());
    plugin = plugins.get(0);
    assertEquals(pluginIdStart0, plugin.getPluginId());
    assertNull(plugin.getName());
    assertNull(plugin.getVersion());
    assertNull(plugin.getType());
    assertEquals(1, plugin.getDefinition().size());
    assertNull(plugin.getRegistry());
    plugin = plugins.get(1);
    assertEquals(pluginIdStart1, plugin.getPluginId());
    assertNull(plugin.getName());
    assertNull(plugin.getVersion());
    assertNull(plugin.getType());
    assertEquals(2, plugin.getDefinition().size());
    assertNull(plugin.getRegistry());
  }

  private Configuration simAuConfig(String rootPath) {
    Configuration conf = ConfigManager.newConfiguration();
    conf.put("root", rootPath);
    conf.put("depth", "2");
    conf.put("branch", "1");
    conf.put("numFiles", "3");
    conf.put("fileTypes", "" + (SimulatedContentGenerator.FILE_TYPE_PDF +
                                SimulatedContentGenerator.FILE_TYPE_HTML));
    conf.put("binFileSize", "7");
    return conf;
  }

  public static class MySubTreeArticleIteratorFactory
      implements ArticleIteratorFactory {
    String pat;
    public MySubTreeArticleIteratorFactory(String pat) {
      this.pat = pat;
    }
    
    /**
     * Create an Iterator that iterates through the AU's articles, pointing
     * to the appropriate CachedUrl of type mimeType for each, or to the
     * plugin's choice of CachedUrl if mimeType is null
     * @param au the ArchivalUnit to iterate through
     * @return the ArticleIterator
     */
    @Override
    public Iterator<ArticleFiles> createArticleIterator(
        ArchivalUnit au, MetadataTarget target) throws PluginException {
      Iterator<ArticleFiles> ret;
      SubTreeArticleIterator.Spec spec = 
        new SubTreeArticleIterator.Spec().setTarget(target);
      
      if (pat != null) {
       spec.setPattern(pat);
      }
      
      ret = new SubTreeArticleIterator(au, spec);
      log.debug(  "creating article iterator for au " + au.getName() 
                    + " hasNext: " + ret.hasNext());
      return ret;
    }
  }

  private static class MySimulatedPlugin extends SimulatedDefinablePlugin {
    ArticleMetadataExtractor simulatedArticleMetadataExtractor = null;
    int version = 2;
    /**
     * Returns the article iterator factory for the mime type, if any
     * @param contentType the content type
     * @return the ArticleIteratorFactory
     */
    @Override
    public ArticleIteratorFactory getArticleIteratorFactory() {
      return new MySubTreeArticleIteratorFactory(null);
    }
    @Override
    public ArticleMetadataExtractor 
      getArticleMetadataExtractor(MetadataTarget target, ArchivalUnit au) {
      return simulatedArticleMetadataExtractor;
    }

    @Override
    public String getFeatureVersion(Plugin.Feature feat) {
      if (Feature.Metadata == feat) {
	return feat + "_" + version;
      } else {
	return null;
      }
    }
  }

  public static class MySimulatedPlugin0 extends MySimulatedPlugin {
    public MySimulatedPlugin0() {
      simulatedArticleMetadataExtractor = new ArticleMetadataExtractor() {
        public void extract(MetadataTarget target, ArticleFiles af,
            Emitter emitter) throws IOException, PluginException {
          ArticleMetadata md = new ArticleMetadata();
          emitter.emitMetadata(af, md);
        }
      };
    }
    public ExternalizableMap getDefinitionMap() {
      ExternalizableMap map = new ExternalizableMap();
      map.putString("au_start_url", "\"%splugin0/%s\", base_url, volume");
      return map;
    }
  }

  public static class MySimulatedPlugin1 extends MySimulatedPlugin {
    public MySimulatedPlugin1() {
      simulatedArticleMetadataExtractor = new ArticleMetadataExtractor() {
        public void extract(MetadataTarget target, ArticleFiles af,
            Emitter emitter) throws IOException, PluginException {
          ArticleMetadata md = new ArticleMetadata();
          emitter.emitMetadata(af, md);
        }
      };
    }
    public ExternalizableMap getDefinitionMap() {
      ExternalizableMap map = new ExternalizableMap();
      map.putString("key1", "value1");
      map.putString("key2", "value2");
      return map;
    }
  }
}
