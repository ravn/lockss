/* $Id: TestPensoftArticleIteratorFactory.java,v 1.3 2013-03-20 22:03:27 aishizaki Exp $  */
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

package org.lockss.plugin.pensoft;

import java.io.File;
import java.util.Iterator;
import java.util.Stack;
import java.util.regex.Pattern;

import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.plugin.*;
import org.lockss.plugin.simulated.SimulatedArchivalUnit;
import org.lockss.plugin.simulated.SimulatedContentGenerator;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.test.*;
import org.lockss.util.*;

public class TestPensoftArticleIteratorFactory extends ArticleIteratorTestCase {
        
  private SimulatedArchivalUnit sau;    // Simulated AU to generate content
        
  private final String PLUGIN_NAME = "org.lockss.plugin.pensoft.PensoftPlugin";
  private final String ARTICLE_FAIL_MSG = "Article files not created properly";
  private final String PATTERN_FAIL_MSG = "Article file URL pattern changed or incorrect";
  static final String YEAR_KEY = ConfigParamDescr.YEAR.getKey();
  static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
  static final String JOURNAL_NAME_KEY = "journal_name";
  private final String BASE_URL = "http://www.example.com/";
  private final String YEAR = "2008";
  private final String JOURNAL_NAME = "PR0";

  private static final int DEFAULT_FILESIZE = 3000;
  private static Pattern regexpattern;
  private final Configuration AU_CONFIG = ConfigurationUtil.fromArgs(BASE_URL_KEY, BASE_URL,
                        JOURNAL_NAME_KEY, JOURNAL_NAME,
                        YEAR_KEY, YEAR);

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
      PluginTestUtil.createAndStartAu(PLUGIN_NAME, AU_CONFIG);
  }
  
  Configuration simAuConfig(String rootPath) {
            Configuration conf = ConfigManager.newConfiguration();
            conf.put("root", rootPath);
            conf.put("base_url", BASE_URL);
            conf.put("journal_name", JOURNAL_NAME);
            conf.put("year", YEAR);
            conf.put("depth", "1");
            conf.put("branch", "4");
            conf.put("numFiles", "7");
            conf.put("fileTypes",
                     "" + (  SimulatedContentGenerator.FILE_TYPE_HTML
                           | SimulatedContentGenerator.FILE_TYPE_PDF));
            conf.put("binFileSize", ""+DEFAULT_FILESIZE);
            return conf;
  }
 

  public void testRoots() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
  }

  /*

  */
  public void testCrawlRules() throws Exception {
    //SubTreeArticleIterator artIter = createSubTreeIter();
    //Pattern pat = getPattern(artIter);
    // use a substance pattern from the crawl rules

    String CRAWLRULE0 = String.format("%sinc/journals/download.php\\?fileId=[\\d]+&fileTable=J_GALLEYS", BASE_URL);
    String CRAWLRULE1 = String.format("%sjournals/%s/article/(\\d)+(/abstract/[\\w-]+)?",BASE_URL,JOURNAL_NAME);

    Pattern pat0 = Pattern.compile(String.format(CRAWLRULE0));
    Pattern pat1 = Pattern.compile(String.format(CRAWLRULE1));

    assertNotMatchesRE(pat0, "http://www.wrong.com/doi/abs/10.2446/01.02.03.PR0.108.4.567-589");
    assertNotMatchesRE(pat1, BASE_URL + "journals/PR0/articles/1453-63/zzz-name");
    assertNotMatchesRE(pat0, BASE_URL + "hello/PR0.108/article/67-589");
    assertNotMatchesRE(pat1, BASE_URL + "journals/PR0/2006/680-159.pdf");

    
    assertMatchesRE(pat1, BASE_URL + "journals/PR0/article/1453/abstract/article-title");
    assertMatchesRE(pat1, BASE_URL + "journals/PR0/article/1453");
    
    assertNotMatchesRE(pat0, BASE_URL + "journals/PR0/article/1453");
    assertMatchesRE(pat0, BASE_URL + "inc/journals/download.php?fileId=1825&fileTable=J_GALLEYS");
  }

  public void testCreateArticleFiles() throws Exception {
    PluginTestUtil.crawlSimAu(sau);
    String[] urls = {
        BASE_URL + "journals/PR0/article/145363",
        BASE_URL + "journals/PR0/article/145363/abs",
        BASE_URL + "journals/PR0/article/145363/cta",
        BASE_URL + "journals/PR0/article/145363/ref",
        BASE_URL + "journals/PR0/article/123456",
        BASE_URL + "journals/PR0/article/123456/abs",
        BASE_URL + "journals/PR0/article/123456/cta",
        BASE_URL + "journals/PR0/article/123456/ref",
        BASE_URL + "journals/PR0/article/123456.pdf",
        BASE_URL + "journals/PR0/article/680159.pdf",
    };
    Iterator<CachedUrlSetNode> cuIter = sau.getAuCachedUrlSet().contentHashIterator();
    
    if(cuIter.hasNext()){
            CachedUrlSetNode cusn = cuIter.next();
            CachedUrl cuPdf = null;
            CachedUrl cuHtml = null;
            UrlCacher uc;
            while(cuIter.hasNext() && (cuPdf == null || cuHtml == null))
                {
                if(cusn.getType() == CachedUrlSetNode.TYPE_CACHED_URL && cusn.hasContent())
                {
                        CachedUrl cu = (CachedUrl)cusn;
                        if(cuPdf == null && cu.getContentType().toLowerCase().startsWith(Constants.MIME_TYPE_PDF))
                        {
                                cuPdf = cu;
                        }
                        else if (cuHtml == null && cu.getContentType().toLowerCase().startsWith(Constants.MIME_TYPE_HTML))
                        {
                                cuHtml = cu;
                        }
                }
                cusn = cuIter.next();
                }
            for(String url : urls)
            {
                    uc = au.makeUrlCacher(url);
                    if(url.contains("pdf")){
                        uc.storeContent(cuPdf.getUnfilteredInputStream(), cuPdf.getProperties());
                    }
                    else if(url.contains("full") || url.contains("ris")){
                        uc.storeContent(cuHtml.getUnfilteredInputStream(), cuHtml.getProperties());
                    }
            }
    }
    /*

     */  
    Stack<String[]> expStack = new Stack<String[]>();
    String [] af1 = {
                              BASE_URL + "journals/PR0/article/145363",
                              BASE_URL + "journals/PR0/article/145363/abs",
                              BASE_URL + "journals/PR0/article/145363/cta",
                              null,
                              BASE_URL + "journals/PR0/article/680159.pdf",
                              null
                              };
    String [] af2 = {
                              BASE_URL + "journals/PR0/article/123456",
                              null,
                              BASE_URL + "journals/PR0/article/123456/cta",
                              null,
                              null,
                              null
                              };
    String [] af3 = {
        BASE_URL + "journals/PR0/article/654321",
        BASE_URL + "journals/PR0/article/654321/abs",
        BASE_URL + "journals/PR0/article/654321/cta",
                              null,
                              BASE_URL + "journals/PR0/article/654321.pdf",
                              BASE_URL + "journals/PR0/article/654321.pdf"
                              };
    String [] af4 = {
                              BASE_URL + "journals/PR0/article/222333",
                              BASE_URL + "journals/PR0/article/222333/abs",
                              null,
                              null,
                              BASE_URL + "journals/PR0/article/222333.pdf",
                              null
                              };
    expStack.push(af4);
    expStack.push(af3);
    expStack.push(af2);
    expStack.push(af1);
  
    for ( SubTreeArticleIterator artIter = createSubTreeIter(); artIter.hasNext(); ) {
              ArticleFiles af = artIter.next();
              String[] act = {
                                      af.getFullTextUrl(),
                                      af.getRoleUrl(ArticleFiles.ROLE_FULL_TEXT_PDF),
                                      af.getRoleUrl(ArticleFiles.ROLE_FULL_TEXT_HTML),
                                      af.getRoleUrl(ArticleFiles.ROLE_ABSTRACT),
                                      af.getRoleUrl(ArticleFiles.ROLE_ARTICLE_METADATA)
                                      };
              String[] exp = expStack.pop();
              if(act.length == exp.length){
                      for(int i = 0;i< act.length; i++){
                              assertEquals(ARTICLE_FAIL_MSG + " Expected: " + exp[i] + " Actual: " + act[i], exp[i],act[i]);
                      }
              }
              else fail(ARTICLE_FAIL_MSG + " length of expected and actual ArticleFiles content not the same");
    }     
  
  }                     

}