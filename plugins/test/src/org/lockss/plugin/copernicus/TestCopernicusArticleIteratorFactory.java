/*
 * $Id: TestCopernicusArticleIteratorFactory.java,v 1.4 2014-07-21 03:28:30 tlipkis Exp $
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

package org.lockss.plugin.copernicus;

import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.CachedUrlSetSpec;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.daemon.SingleNodeCachedUrlSetSpec;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.FileMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.*;
import org.lockss.plugin.simulated.SimulatedArchivalUnit;
import org.lockss.plugin.simulated.SimulatedContentGenerator;
import org.lockss.state.NodeManager;
import org.lockss.test.*;
import org.lockss.util.*;

public class TestCopernicusArticleIteratorFactory extends ArticleIteratorTestCase {
	
	private SimulatedArchivalUnit sau;	// Simulated AU to generate content
	
	private final String PLUGIN_NAME = "org.lockss.plugin.copernicus.ClockssCopernicusPublicationsPlugin";
        static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
        static final String YEAR_KEY = ConfigParamDescr.YEAR.getKey();
        static final String VOLUME_NAME_KEY = ConfigParamDescr.VOLUME_NAME.getKey();
        private final String BASE_URL = "http://www.clim-past.net/";
        private final String HOME_URL = "http://www.climate-of-the-past.net/";
        private final String VOLUME_NAME = "8";
        private final String YEAR = "2012";
        private final Configuration AU_CONFIG = ConfigurationUtil.fromArgs(
                                                                                                BASE_URL_KEY, BASE_URL,
                                                                                                "home_url", HOME_URL,
                                                                                                VOLUME_NAME_KEY, VOLUME_NAME,
                                                                                                YEAR_KEY, YEAR);
	private static final int DEFAULT_FILESIZE = 3000;

	protected String cuRole = null;
	ArticleMetadataExtractor.Emitter emitter;
	protected boolean emitDefaultIfNone = false;
	FileMetadataExtractor me = null; 
	MetadataTarget target;

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = setUpDiskSpace();

    au = createAu();
    sau = PluginTestUtil.createAndStartSimAu(simAuConfig(tempDirPath));
  }
  
  public void tearDown() throws Exception {
	    sau.deleteContentTree();
	    super.tearDown();
	  }

  protected ArchivalUnit createAu() throws ArchivalUnit.ConfigurationException {
    return
      PluginTestUtil.createAndStartAu(PLUGIN_NAME,  AU_CONFIG);
  }
  
  Configuration simAuConfig(String rootPath) {
	    Configuration conf = ConfigManager.newConfiguration();
	    conf.put("root", rootPath);
	    conf.put(BASE_URL_KEY, BASE_URL);
	    conf.put("depth", "1");
	    conf.put("branch", "4");
	    conf.put("numFiles", "5");
	    conf.put("fileTypes",
	             "" + (SimulatedContentGenerator.FILE_TYPE_HTML |
	                 SimulatedContentGenerator.FILE_TYPE_PDF | 
	                 SimulatedContentGenerator.FILE_TYPE_TXT));
	    conf.put("binFileSize", ""+DEFAULT_FILESIZE);
	    return conf;
	  }
  

  public void testRoots() throws Exception {      
    SubTreeArticleIterator artIter = createSubTreeIter();
    assertEquals(ListUtil.list(BASE_URL + VOLUME_NAME + "/"),
		 getRootUrls(artIter));
  }
  
  
  
  public void testUrlsWithPrefixes() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    Pattern pat = getPattern(artIter);
    //"\"^%s%s/[0-9]+/[0-9]+/[A-Za-z0-9-]+\\.html\", base_url,volume_name"

    assertNotMatchesRE(pat, "http://www.clim-past.net/7/1/2012/cp-7-1-2012.html"); //wrong volume
    assertMatchesRE(pat, "http://www.clim-past.net/8/1/2012/cp-8-1-2012.pdf"); //pdf not abstract
    assertNotMatchesRE(pat, "http://www.clim-past.net/8/1/2012/cp-8-1-2012-supplement.pdf"); //supplement, not article
    assertNotMatchesRE(pat, "http://www.clim-past.net/8/1/2012/cp-8-1-2012-supplement.zip"); //zip not pdf
    assertMatchesRE(pat, "http://www.clim-past.net/8/1/2012/cp-8-1-2012.html");
    assertNotMatchesRE(pat, "http://www.clim-past.net/8/1/2012");
    assertNotMatchesRE(pat, "http://www.clim-past.net/8/1/2012/cp-8-1-2012.ris");// secondary, not primary
    assertNotMatchesRE(pat, "http://www.clim-past.net/8/1/2012/cp-8-1-2012.bib");// secondary, not primary
  }
  
  
   
  public void testCreateArticleFiles() throws Exception {
    // 1 depth, 4 branches, 5 files, but removing some later in test   
    PluginTestUtil.crawlSimAu(sau);
    
 /*
  *  Go through the simulated content you just crawled and modify the results to emulate
  *  what you would find in a "real" crawl with this plugin:
  *  <base_url>/48/<page#>/2011/art#file.{html, pdf & ris}   
  */
    
    // will become 8/#/2012/art#file.{html,pdf,ris}
    String pat1 = "branch(\\d+)/(\\d+file)\\.html";
    String rep1 = "8/$1/2012/art$2.html";
    PluginTestUtil.copyAu(sau, au, ".*\\.html$", pat1, rep1);
    String pat2 = "branch(\\d+)/(\\d+file)\\.pdf";
    String rep2 = "8/$1/2012/art$2.pdf";
    PluginTestUtil.copyAu(sau, au, ".*\\.pdf$", pat2, rep2);
    String pat3 = "branch(\\d+)/(\\d+file)\\.txt";
    String rep3 = "8/$1/2012/art$2.ris";
    PluginTestUtil.copyAu(sau, au, ".*\\.txt$", pat3, rep3);
    
    // Remove some of the URLs just created to make test more robust
    // Remove files art1file.html and art2file.ris in each branch (and there are 4 branches)
    // Remove art3file.pdf in only one of the branches

    int deleted = 0;
    for (CachedUrl cu : AuUtil.getCuIterable(au)) {
        String url = cu.getUrl();
        if (url.contains("/2012/")) {
          if (url.endsWith("1file.html") || url.endsWith("2file.ris") || url.endsWith("3file.pdf")) {
            deleteBlock(cu);
            ++deleted;
          }
          // and in the first branch only, create edge cases with only one type of file
          if (url.contains("1/2012") && (url.endsWith("1file.pdf") || url.endsWith("4file.pdf")
              || url.endsWith("4file.ris") || url.endsWith("5file.html") || url.endsWith("5file.ris"))) {
            //BRANCH ONE - art1 = only ris; art4=only html; art5=only pdf
            deleteBlock(cu);
            ++deleted;
          }
        }
    }
    assertEquals(17, deleted); // 4 branches, 3 files removed per branch - plus one additional PDF file

    Iterator<ArticleFiles> it = au.getArticleIterator(MetadataTarget.Any());
    int count = 0;
    int countFullText = 0;
    int countMetadata = 0;
    while (it.hasNext()) {
      ArticleFiles af = it.next();
      log.info(af.toString());
      count ++;
      CachedUrl cu = af.getFullTextCu();
      if ( cu != null) {
        String url = cu.getUrl();
        String contentType = cu.getContentType();
        log.debug("countFullText " + count + " url " + url + " " + contentType);
        ++countFullText;
      }
      cu = af.getRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA);
      if (cu != null) {
        ++countMetadata; //could be ris file or abstract
      }
    }      

    // 20 possible articles 
    //     count & countFullText - 1 for RIS only (branch1, file1)
    //     countMetadata - 2 (pdf only branch1, file 5)
    log.debug("Article count is " + count);
    assertEquals(19, count); //20 (5 x 4 branches; minus branch1, file1 which only has a ris version
    assertEquals(19, countFullText); // current builder counts abstract as full text if all there is
    assertEquals(18, countMetadata); // if you have an articlefiles and either ris or abstract
}

private void deleteBlock(CachedUrl cu) throws IOException {
  log.info("deleting " + cu.getUrl());
  CachedUrlSetSpec cuss = new SingleNodeCachedUrlSetSpec(cu.getUrl());
  ArchivalUnit au = cu.getArchivalUnit();
  CachedUrlSet cus = au.makeCachedUrlSet(cuss);
  NodeManager nm = au.getPlugin().getDaemon().getNodeManager(au);
  nm.deleteNode(cus);
}
}
