/*
 * $Id: FuncTarExploder.java,v 1.2 2007-09-24 18:37:13 dshr Exp $
 */

/*

Copyright (c) 2007 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.crawler;

import java.io.*;
import java.util.*;

import org.apache.commons.collections.Bag;
import org.apache.commons.collections.bag.*;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.simulated.*;
import org.lockss.plugin.exploded.*;
import org.lockss.repository.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.state.*;

/**
 * Functional tests for the TAR file exploder.  It
 * does not test the non-TAR file functionality,
 * which is provided by FollowLinkCrawler.
 *
 * Uses SimulatedTarContentGenerator to create a
 * web site with a permission page that links to
 * a TAR file containing the rest of the content
 * that in the FollowLinkCrawler case would have
 * been generated by SimulatedContentGenerator.
 *
 * @author  David S. H. Rosenthal
 * @version 0.0
 */

public class FuncTarExploder extends LockssTestCase {
  static Logger log = Logger.getLogger("FuncTarExploder");

  private SimulatedArchivalUnit sau;
  private MockLockssDaemon theDaemon;
  private static final int DEFAULT_MAX_DEPTH = 1000;
  private static final int DEFAULT_FILESIZE = 3000;
  private static int fileSize = DEFAULT_FILESIZE;
  private static int maxDepth=DEFAULT_MAX_DEPTH;

  public static void main(String[] args) throws Exception {
    // XXX should be much simpler.
    FuncTarExploder test = new FuncTarExploder();
    if (args.length>0) {
      try {
        maxDepth = Integer.parseInt(args[0]);
      } catch (NumberFormatException ex) { }
    }

    log.info("Setting up for depth " + maxDepth);
    test.setUp(maxDepth);
    log.info("Running up for depth " + maxDepth);
    test.testRunSelf();
    test.tearDown();
  }

  public void setUp() throws Exception {
    super.setUp();
    this.setUp(DEFAULT_MAX_DEPTH);
  }

  public void setUp(int max) throws Exception {

    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    String auId = "org|lockss|crawler|FuncTarExploder$MySimulatedPlugin.root~" +
      PropKeyEncoder.encode(tempDirPath);
    Properties props = new Properties();
    props.setProperty(FollowLinkCrawler.PARAM_MAX_CRAWL_DEPTH, ""+max);
    maxDepth=max;
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);

    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_ROOT, tempDirPath);
    // the simulated Content's depth will be (AU_PARAM_DEPTH + 1)
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_DEPTH, "3");
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_BRANCH, "1");
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_NUM_FILES, "2");
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_FILE_TYPES,
                      ""+SimulatedContentGenerator.FILE_TYPE_BIN);
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_BIN_FILE_SIZE, ""+fileSize);
    props.setProperty("org.lockss.plugin.simulated.SimulatedContentGenerator.doTarFile", "true");

    props.setProperty(FollowLinkCrawler.PARAM_EXPLODE_ARCHIVES, "true");
    props.setProperty(FollowLinkCrawler.PARAM_STORE_ARCHIVES, "true");
    props.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props.setProperty(HistoryRepositoryImpl.PARAM_HISTORY_LOCATION, tempDirPath);
    

    theDaemon = getMockLockssDaemon();
    theDaemon.getAlertManager();
    // theDaemon.getPluginManager().setLoadablePluginsReady(true);
    theDaemon.setDaemonInited(true);
    theDaemon.getPluginManager().startService();
    theDaemon.getPluginManager().startLoadablePlugins();

    ConfigurationUtil.setCurrentConfigFromProps(props);

    sau =
      (SimulatedArchivalUnit)theDaemon.getPluginManager().getAllAus().get(0);
    theDaemon.getLockssRepository(sau).startService();
    theDaemon.setNodeManager(new MockNodeManager(), sau);
  }

  public void tearDown() throws Exception {
    theDaemon.stopDaemon();
    super.tearDown();
  }

  public void testRunSelf() throws Exception {
    log.debug3("About to create content");
    createContent();

    // get the root of the simContent
    String simDir = sau.getSimRoot();

    log.debug3("About to crawl content");
    crawlContent();

    // read all the files links from the root of the simcontent
    // check the link level of the file and see if it contains
    // in myCUS (check if the crawler crawl within the max. depth)
    CachedUrlSet myCUS = sau.getAuCachedUrlSet();
    File dir = new File(simDir);
    if(dir.isDirectory()) {
      File f[] = dir.listFiles();
      log.debug("Checking simulated content.");
      checkThruFileTree(f, myCUS);
      log.debug("Checking simulated content done.");
      checkExplodedUrls();
      checkUnExplodedUrls();

      log.debug("Check finished.");
    } else {
      log.error("Error: The root path of the simulated" +
		" content ["+ dir +"] is not a directory");
    }

    // Test PluginManager.getAuContentSize(), just because this is a
    // convenient place to do it.  (And ArcCrawler calls it at the
    // end.)  If the simulated AU params are changed, or
    // SimulatedContentGenerator is changed, this number may have to
    // change.  NB - because the TAR files are compressed,  their
    // size varies randomly by a small amount.
    long expected = 41399;
    long actual = AuUtil.getAuContentSize(sau, true);
    long error = expected - actual;
    long absError = (error < 0 ? -error : error);
    assertTrue("size mismatch " + expected + " vs. " + actual, absError < 60);

    List sbc = ((MySimulatedArchivalUnit)sau).sbc;
    Bag b = new HashBag(sbc);
    Set uniq = new HashSet(b.uniqueSet());
    for (Iterator iter = uniq.iterator(); iter.hasNext(); ) {
      b.remove(iter.next(), 1);
    }
    // Permission pages get checked twice.  Hard to avoid that, so allow it
    b.removeAll(sau.getCrawlSpec().getPermissionPages());
    // archives get checked twice - from checkThruFileTree & checkExplodedUrls
    b.remove("http://www.example.com/content.tar");
    // This test is screwed up by the use of shouldBeCached() in
    // TarExploder() to find the AU to store the URL in.
    //assertEmpty("shouldBeCached() called multiple times on same URLs.", b);

  }

  //recursive caller to check through the whole file tree
  private void checkThruFileTree(File f[], CachedUrlSet myCUS){
    for (int ix=0; ix<f.length; ix++) {
      log.debug3("Check: " + f[ix].getAbsolutePath());
      if (f[ix].isDirectory()) {
	// get all the files and links there and iterate
	checkThruFileTree(f[ix].listFiles(), myCUS);
      } else {

	// get the f[ix] 's level information
	String fileUrl = sau.mapContentFileNameToUrl(f[ix].getAbsolutePath());
	int fileLevel = sau.getLinkDepth(fileUrl);
	log.debug2("File: " + fileUrl + " in Level " + fileLevel);

	CachedUrl cu = theDaemon.getPluginManager().findCachedUrl(fileUrl);
	if (fileLevel <= maxDepth) {
	  assertTrue(cu + " has no content", cu.hasContent());
	} else {
	  assertFalse(cu + " has content when it shouldn't",
		      cu.hasContent());
	}
      }
    }
    return; // when all "File" in the array are checked
  }

  String[] url = {
    "http://www.content.org/001file.bin",
    "http://www.content.org/002file.bin",
    "http://www.content.org/branch1/001file.bin",
    "http://www.content.org/branch1/002file.bin",
    "http://www.content.org/branch1/branch1/001file.bin",
    "http://www.content.org/branch1/branch1/002file.bin",
    "http://www.content.org/branch1/branch1/branch1/001file.bin",
    "http://www.content.org/branch1/branch1/branch1/002file.bin",
    "http://www.content.org/branch1/branch1/branch1/index.html",
    "http://www.content.org/branch1/branch1/index.html",
    "http://www.content.org/branch1/index.html",
  };


  private void checkExplodedUrls() {
    log.debug2("Checking Exploded URLs.");
    for (int i = 0; i < url.length; i++) {
      CachedUrl cu = theDaemon.getPluginManager().findCachedUrl(url[i]);
      assertTrue(url[i] + " not in any AU", cu != null);
      log.debug2("Check: " + url[i] + " cu " + cu + " au " + cu.getArchivalUnit().getAuId());
      assertTrue(cu + " has no content", cu.hasContent());
      assertTrue(cu + " isn't ExplodedArchivalUnit",
		 !(cu instanceof ExplodedArchivalUnit));
      assertNotEquals(sau, cu.getArchivalUnit());
    }
    log.debug2("Checking Exploded URLs done.");
  }

  String[] url2 = {
    "http://www.example.com/index.html",
    "http://www.example.com/content.tar",
  };

  private void checkUnExplodedUrls() {
    log.debug2("Checking UnExploded URLs.");
    for (int i = 0; i < url2.length; i++) {
      CachedUrl cu = theDaemon.getPluginManager().findCachedUrl(url2[i]);
      assertTrue(url2[i] + " not in any AU", cu != null);
      log.debug2("Check: " + url2[i] + " cu " + cu + " au " + cu.getArchivalUnit().getAuId());
      assertTrue(cu + " has no content", cu.hasContent());
      assertTrue(cu + " isn't MySimulatedArchivalUnit",
		 !(cu instanceof MySimulatedArchivalUnit));
      assertEquals(sau, cu.getArchivalUnit());
    }
    log.debug2("Checking UnExploded URLs done.");
  }


  private void createContent() {
    log.debug("Generating tree of size 3x1x2 with "+fileSize
	      +"byte files...");
    sau.generateContentTree();
  }

  private void crawlContent() {
    log.debug("Crawling tree...");
    List urls = ListUtil.list(SimulatedArchivalUnit.SIMULATED_URL_START);
    CrawlSpec spec =
      new SpiderCrawlSpec(urls,
			  urls, // permissionUrls
			  new MyCrawlRule(), // crawl rules
			  1,    // refetch depth
			  null, // PermissionChecker
			  null, // LoginPageChecker
			  ".tar$", // exploder pattern
			  new MyExploderHelper() );
    Crawler crawler = new NewContentCrawler(sau, spec, new MockAuState());
    crawler.doCrawl();
  }

  public static class MySimulatedPlugin extends SimulatedPlugin {
    public ArchivalUnit createAu0(Configuration auConfig)
	throws ArchivalUnit.ConfigurationException {
      ArchivalUnit au = new MySimulatedArchivalUnit(this);
      if (log.isDebug3()) {
	
	for (Iterator it = auConfig.keyIterator(); it.hasNext(); ) {
	  String key = (String)it.next();
	  String value = auConfig.get(key);
	  log.debug3("auConfig: " + key + " value " + value);
	}
      }
      au.setConfiguration(auConfig);
      return au;
    }
  }

  public static class MySimulatedArchivalUnit extends SimulatedArchivalUnit {
    List sbc = new ArrayList();

    public MySimulatedArchivalUnit(Plugin owner) {
      super(owner);
    }

    protected CrawlRule makeRules() {
      return new MyCrawlRule();
    }

    public boolean shouldBeCached(String url) {
      sbc.add(url);
      if (false) {
	// This can be helpful to track down problems - h/t TAL.
	log.debug3("shouldBeCached: " + url, new Throwable());
      } else {
	log.debug3("shouldBeCached: " + url);
      }
      return super.shouldBeCached(url);
    }
  }

  public static class MyCrawlRule implements CrawlRule {
    public int match(String url) {
      if (url.startsWith("http://www.example.com")) {
	return CrawlRule.INCLUDE;
      }
      return CrawlRule.EXCLUDE;
    }
  }
  public static class MyExploderHelper implements ExploderHelper {
    public MyExploderHelper() {
    }

    private static final String suffix[] = {
      ".txt",
      ".html",
      ".pdf",
      ".jpg",
      ".bin",
    };
    public static final String[] mimeType = {
      "text/plain",
      "text/html",
      "application/pdf",
      "image/jpg",
      "application/octet-stream",
    };

    public void process(ArchiveEntry ae) {
      String baseUrl = "http://www.content.org/";
      String restOfUrl = ae.getName();
      CIProperties headerFields = new CIProperties();
      for (int i = 0; i < suffix.length; i++) {
	if (restOfUrl.endsWith(suffix[i])) {
	  headerFields.setProperty("Content-Type", mimeType[i]);
	}
      }
      headerFields.setProperty("Content-Length",
			       Long.toString(ae.getSize()));
      log.debug(ae.getName() + " mapped to " +
		   baseUrl + " plus " + restOfUrl +
		" bytes " + ae.getSize());
      for (Enumeration e = headerFields.propertyNames();
	   e.hasMoreElements(); ) {
	String key = (String)e.nextElement();
	String value = (String)headerFields.get(key);
	log.debug(key + "=" + value);
      }
      ae.setBaseUrl(baseUrl);
      ae.setRestOfUrl(restOfUrl);
      ae.setHeaderFields(headerFields);
    }
  }

}
