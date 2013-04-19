/*
 * $Id: TestBaseAtyponArticleIteratorFactory.java,v 1.1 2013-04-19 22:49:44 alexandraohlson Exp $
 */

/*

Copyright (c) 2000-2013 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.atypon;

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
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.CachedUrlSet;
import org.lockss.plugin.CachedUrlSetNode;
import org.lockss.plugin.PluginTestUtil;
import org.lockss.plugin.SubTreeArticleIterator;
import org.lockss.plugin.simulated.SimulatedArchivalUnit;
import org.lockss.plugin.simulated.SimulatedContentGenerator;
import org.lockss.state.NodeManager;
import org.lockss.test.ArticleIteratorTestCase;
import org.lockss.test.ConfigurationUtil;
import org.lockss.util.ListUtil;

public class TestBaseAtyponArticleIteratorFactory extends ArticleIteratorTestCase {

  private SimulatedArchivalUnit sau;	// Simulated AU to generate content

  private final String PLUGIN_NAME = "org.lockss.plugin.atypon.BaseAtyponPlugin";
  static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
  static final String JOURNAL_ID_KEY = ConfigParamDescr.JOURNAL_ID.getKey();
  static final String VOLUME_NAME_KEY = ConfigParamDescr.VOLUME_NAME.getKey();
  private final String BASE_URL = "http://www.baseatypon.org/";
  private final String JOURNAL_ID = "xxxx";
  private final String VOLUME_NAME = "123";
  private final Configuration AU_CONFIG = ConfigurationUtil.fromArgs(
      BASE_URL_KEY, BASE_URL,
      JOURNAL_ID_KEY, JOURNAL_ID,
      VOLUME_NAME_KEY, VOLUME_NAME);
  private static final int DEFAULT_FILESIZE = 3000;

  protected String cuRole = null;
  ArticleMetadataExtractor.Emitter emitter;
  protected boolean emitDefaultIfNone = false;
  FileMetadataExtractor me = null;
  MetadataTarget target;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = setUpDiskSpace();

    au = createAu();
    sau = PluginTestUtil.createAndStartSimAu(simAuConfig(tempDirPath));
  }

  @Override
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
    conf.put("branch", "5");
    conf.put("numFiles", "4");
    conf.put("fileTypes",
        "" + (SimulatedContentGenerator.FILE_TYPE_HTML |
            SimulatedContentGenerator.FILE_TYPE_PDF));
    conf.put("binFileSize", ""+DEFAULT_FILESIZE);
    return conf;
  }


  public void testRoots() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    assertEquals(ListUtil.list(BASE_URL + "doi/"),
        getRootUrls(artIter));
  }


  //
  // We are set up to match any of abs|pdf|full|pdfplus
  //

  public void testUrlsWithPrefixes() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    Pattern pat = getPattern(artIter);
    
    // we match to doi/(abs|full|pdf|pdfplus)
    assertMatchesRE(pat, "http://www.baseatypon.org/doi/pdf/10.1137/100818522"); 
    assertMatchesRE(pat, "http://www.baseatypon.org/doi/abs/10.1137/100818522");
    assertMatchesRE(pat, "http://www.baseatypon.org/doi/full/10.1137/100818522");
    assertMatchesRE(pat, "http://www.baseatypon.org/doi/pdfplus/10.1137/100818522");
    // prefix of DOI can have additional dots
    assertMatchesRE(pat, "http://www.baseatypon.org/doi/abs/10.1137.12.13/100818522");
    // PATTERN will allow this but actual article pattern matches will not allow "/" in 2nd part of DOI
    assertMatchesRE(pat, "http://www.baseatypon.org/doi/abs/10.1137/ABC1234-3/fff");
    
    // but not to doi/(ref|suppl| which are supporting only
    assertNotMatchesRE(pat, "http://www.baseatypon.org/doi/ref/10.1137.12.13/100818522");
    assertNotMatchesRE(pat, "http://www.baseatypon.org/doi/suppl/10.1137.12.13/100818522");
    // must have both parts of DOI
    assertNotMatchesRE(pat, "http://www.baseatypon.org/doi/abs/10.1137");

    // prefix of DOI doesn't support letters though that is technically legal
    assertNotMatchesRE(pat, "http://www.baseatypon.org/doi/abs/10.1ABCD/12345");
    // wrong base url
    assertNotMatchesRE(pat, "http://ametsoc.org/doi/abs/10.1175/2009WCAS1006.1");
  }

  //
  // simAU was created with only one depth, but 5 branches
  // 2 filetypes (html & pdf) and 4 files of each type
  // So the total number of files of all types is 40 (5 * (4*2))
  // simAU file structures looks like this branch01/01file.html or branch04/08file.pdf, etc
  //
  public void testCreateArticleFiles() throws Exception {
        PluginTestUtil.crawlSimAu(sau);

    /*
     *  Go through the simulated content you just crawled and modify the results to emulate
     *  what you would find in a "real" crawl with Atypon:
     *  <base_url>/doi/{abs,pdf,full,pdfplus}/10.1137/X.XXXXXX
     *  Currently this test doesn't create any "ref" or "suppl" files
     */

    // turn xxfile.html in to both abstracts and fulls
    String pat1 = "branch(\\d+)/(\\d+)file\\.html";
    String repAbs = "doi/abs/10.1137/b$1.art$2";
    String repFull = "doi/full/10.1137/b$1.art$2";
    PluginTestUtil.copyAu(sau, au, ".*\\.html$", pat1, repAbs);
    PluginTestUtil.copyAu(sau, au, ".*\\.html$", pat1, repFull);

    // turn xxfile.pdf in to both pdf and pdfplus 
    String pat2 = "branch(\\d+)/(\\d+)file\\.pdf";
    String reppdf = "doi/pdf/10.1137/b$1.art$2";
    String reppdfplus = "doi/pdfplus/10.1137/b$1.art$2";
    
    PluginTestUtil.copyAu(sau, au, ".*\\.pdf$", pat2, reppdf);
    PluginTestUtil.copyAu(sau, au, ".*\\.pdf$", pat2, reppdfplus);

    // At this point we have 32 sets of 4 types of articles
    // Remove some of the URLs just created to make test more robust
    // create all the possible permutations 
    // (there are 16 of them) all, none, 1, combos of 2, combos of 3
    //
    // branch1: remove all aspects for art1; leave all 4 aspects for art 2,3,4 
    // branch2: (singletons) - art1 (abs only); art2 (full only); art3 (pdf only); art4 (pdfplus only)
    // branch3: (trios) - art1 (!abs); art2 (!full); art3 (!pdf); art3 (!pdfplus)
    // branch4: (doubles) - art1 (abs+full); art2 (abs+pdf); art3 (abs+pdfplus); art4 (ALL)
    // branch4: (doubles) - art1 (full+pdf); art2 (full+pdfplus); art3 (pdf+pdfplus); art4 (ALL)
    int deleted = 0;
    for (Iterator it = au.getAuCachedUrlSet().contentHashIterator() ; it.hasNext() ; ) {
      CachedUrlSetNode cusn = (CachedUrlSetNode)it.next();
      if (cusn instanceof CachedUrl) {
        CachedUrl cu = (CachedUrl)cusn;
        String url = cu.getUrl();
        // branch 1 - all or none
        if (url.contains("b1.art00")) {
          if (url.endsWith("art001")) {
            deleteBlock(cu);
            ++deleted;
          }
        } else if (url.contains("b2.art00")) {
        // branch 2 - singletons left
          if (url.contains("doi/abs/") && !(url.endsWith("art001")) ){
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/full/") && !(url.endsWith("art002")) ) {
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/pdf/") && !(url.endsWith("art003")) ) {
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/pdfplus/") && !(url.endsWith("art004")) ){
            deleteBlock(cu);
            ++deleted;
          }

        } else if (url.contains("b3.art00")) {
          // branch3 - trios left
          if (url.contains("doi/abs/") && url.endsWith("art001")) {
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/full/") && url.endsWith("art002")) {
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/pdf/") && url.endsWith("art003")) {
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/pdfplus/") && url.endsWith("art004")) {
            deleteBlock(cu);
            ++deleted;
          }   
        } else if (url.contains("b4.art00")) {
          // branch 4 - doubles that have abs    
          if (url.contains("doi/pdfplus/") &&  ( url.endsWith("art001") || url.endsWith("art002")) ) { 
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/full/") && ( url.endsWith("art002") || url.endsWith("art003")) ) {
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/pdf/") && ( url.endsWith("art001") || url.endsWith("art003")) ) {
            deleteBlock(cu);
            ++deleted;
          }
        } else {    
          // branch 5 - doubles without abs
          if (url.contains("doi/abs/") &&  !(url.endsWith("art004")) ) { 
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/full/") && url.endsWith("art003") ) {
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/pdfplus/") && url.endsWith("art001") ) {
            deleteBlock(cu);
            ++deleted;
          }
          if (url.contains("doi/pdf/") &&  url.endsWith("art002") ) {
            deleteBlock(cu);
            ++deleted;
          }
        }
      }
    }
    assertEquals(32, deleted); // trust me

    Iterator<ArticleFiles> it = au.getArticleIterator(MetadataTarget.Any());
    int count = 0;
    int countFullText= 0;
    int countMetadata = 0;
    while (it.hasNext()) {
      ArticleFiles af = it.next();
      count ++;
      log.info(af.toString());
      CachedUrl cu = af.getFullTextCu();
      if ( cu != null) {
        ++countFullText;
      }
      cu = af.getRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA);
      if (cu != null) {
        ++countMetadata;
      }
    }
    // potential article count is 20 (5 branches * 4 files each branch) = 20
    // subtract the one where we removed everything
    int expCount = 19; 

    log.debug("Article count is " + count);
    assertEquals(expCount, count);

    // you will only get a full text for combos with pdf, pdfplus or full
    // so there are only 2 cases - the one with nothing; the one with only abstract;
    //  assertEquals(18, countFullText);
    // For now - Builder counts any aspect that can trigger ArticleFiles as FTCU, even abstract
    // this will change later, but for now, our count will be 19
    assertEquals(19, countFullText);
    
    // you need to have either an abstract or a full text to have metadata 4 cases don't have either
    // we're only going to get the right information if the TARGET is not NULL and set to other than isArticle()
    assertEquals(16, countMetadata);
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
