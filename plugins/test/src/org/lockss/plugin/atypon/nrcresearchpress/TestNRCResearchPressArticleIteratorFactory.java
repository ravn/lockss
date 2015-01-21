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

package org.lockss.plugin.atypon.nrcresearchpress;

import java.util.regex.Pattern;

import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.FileMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.*;
import org.lockss.plugin.simulated.SimulatedArchivalUnit;
import org.lockss.plugin.simulated.SimulatedContentGenerator;
import org.lockss.test.*;
import org.lockss.util.*;

public class TestNRCResearchPressArticleIteratorFactory extends ArticleIteratorTestCase {
        
  private SimulatedArchivalUnit sau;    // Simulated AU to generate content
        
  private final String PLUGIN_NAME = "org.lockss.plugin.atypon.nrcresearchpress.ClockssNRCResearchPressPlugin";
  static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
  static final String JOURNAL_ID_KEY = ConfigParamDescr.JOURNAL_ID.getKey();
  static final String VOLUME_NAME_KEY = ConfigParamDescr.VOLUME_NAME.getKey();
  private final String BASE_URL = "http://www.example.com/";
  private final String VOLUME_NAME = "108";
  private final String JOURNAL_ID = "PR0";
  private final String CRAWLRULE0 = String.format("toc/%s/%s/", JOURNAL_ID, VOLUME_NAME);
  private final String CRAWLRULE1 = String.format("doi/(pdf|pdfplus|abs|full|suppl|ref)/\\d+\\.\\d+/[^/]+", JOURNAL_ID, VOLUME_NAME);
  private static final int DEFAULT_FILESIZE = 3000;
  private final Configuration AU_CONFIG = ConfigurationUtil.fromArgs(BASE_URL_KEY, BASE_URL,
            VOLUME_NAME_KEY, VOLUME_NAME,
            JOURNAL_ID_KEY, JOURNAL_ID);
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
    PluginTestUtil.createAndStartAu(PLUGIN_NAME, AU_CONFIG);
  }
  
  Configuration simAuConfig(String rootPath) {
            Configuration conf = ConfigManager.newConfiguration();
            conf.put("root", rootPath);
            conf.put(BASE_URL_KEY, BASE_URL);
            conf.put("journal_id", JOURNAL_ID);
            conf.put("volume_name", VOLUME_NAME);
            conf.put("depth", "1");
            conf.put("branch", "2");
            conf.put("numFiles", "2");
            conf.put("fileTypes",
                     "" + (  SimulatedContentGenerator.FILE_TYPE_HTML
                           | SimulatedContentGenerator.FILE_TYPE_PDF));
            conf.put("binFileSize", ""+DEFAULT_FILESIZE);
            return conf;
          }
 

  public void testRoots() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    assertEquals(ListUtil.list(BASE_URL + "doi/"), getRootUrls(artIter));
  }
  
  public void testArticleIteratorCrawlRules() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    Pattern pat = getPattern(artIter);

    assertNotMatchesRE(pat, "http://www.wrong.com/doi/abs/10.2446/01.02.03.PR0.108.4.567-589");
    assertNotMatchesRE(pat, BASE_URL + "dooi/abs/10.2446/extra/t99-063");
    assertNotMatchesRE(pat, BASE_URL + "doi/pdfplus/x.2446/t99-063");
    assertNotMatchesRE(pat, BASE_URL + "doi/hello/10.2446/g567-589");  
    //assertNotMatchesRE(pat, BASE_URL + "doi/pdf/10.2466/9999-9999.99999");
    assertMatchesRE(pat, BASE_URL + "doi/full/10.2446/h237-589");
    assertMatchesRE(pat, BASE_URL + "doi/pdf/10.2446/jello567-54489");
    assertMatchesRE(pat, BASE_URL + "doi/pdf/10.2466/g353-354");
   
    assertNotMatchesRE(pat, BASE_URL + "toc/PR0/108/3");
    assertMatchesRE(pat, BASE_URL + "doi/full/10.2446/000/1.2.98.PR0.108.4.567-589");

  }
  public void testCrawlRules() throws Exception {
    // use a substance pattern from the crawl rules
    Pattern regexpattern;

    String re_pattern =BASE_URL+String.format(CRAWLRULE1);    
    regexpattern = Pattern.compile(re_pattern);
    Pattern pat = regexpattern;


    assertNotMatchesRE(pat, "http://www.wrong.com/doi/abs/10.2446/01.02.03.PR0.108.4.567-589");
    assertNotMatchesRE(pat, BASE_URL + "dooi/abs/10.2446/extra/t99-063");
    assertNotMatchesRE(pat, BASE_URL + "doi/pdfplus/x.2446/t99-063");
    assertNotMatchesRE(pat, BASE_URL + "doi/hello/10.2446/g567-589");  
    //assertNotMatchesRE(pat, BASE_URL + "doi/pdf/10.2466/9999-9999.99999");
    assertMatchesRE(pat, BASE_URL + "doi/abs/10.2446/t567-589");
    assertMatchesRE(pat, BASE_URL + "doi/pdf/10.2466/g353-354");
    assertMatchesRE(pat, BASE_URL + "doi/pdfplus/10.2446/jello567-54489");
    assertMatchesRE(pat, BASE_URL + "doi/full/10.2446/h237-589");
   
    re_pattern =BASE_URL+String.format(CRAWLRULE0);    
    regexpattern = Pattern.compile(re_pattern);
    pat = regexpattern;

    assertMatchesRE(pat, BASE_URL + "toc/PR0/108/3");
    assertNotMatchesRE(pat, BASE_URL + "doi/abs/10.2446/000/1.2.98.PR0.108.4.567-589");

  }      
  
  public void testCreateArticleFiles() throws Exception {
    PluginTestUtil.crawlSimAu(sau);
    /*
     *  Go through the simulated content you just crawled and modify the results to emulate
     *  what you would find in a "real" crawl with this plugin:
     *  <base_url>doi/abs/10.2446/XXXX
     *  <base_url>doi/full/10.2446/XXXX
     *  <base_url>doi/pdf/10.2446/XXXX
     *  <base_url>doi/pdfplus/10.2446/XXXX
     *  <base_url>doi/pdf/10.2446/YYYY
     *  <base_url>doi/abs/10.2446/YYYY
     */
       String pat1 = "branch(\\d+)/(\\d+)file\\.html";
       String rep1 = "doi/full/$1\\.$2/x$1-$2"; // make the pdf files abstract
       PluginTestUtil.copyAu(sau, au, ".*\\.html$", pat1, rep1);
       
       String pat2 = "branch(\\d+)/(\\d+)file\\.pdf";
       String rep2 = "doi/pdf/$1\\.$2/y$1-$2"; //make the pdf files one level down pdf content
       PluginTestUtil.copyAu(sau, au, ".*pdf.*$", pat2, rep2);
             
       SubTreeArticleIterator artIter = createSubTreeIter();
       assertNotNull(artIter);
       
       int count = 0;
       int fullHtmlCount = 0;
       int fullPdfCount = 0;
       while (artIter.hasNext()) {
         ArticleFiles af = artIter.next();
         log.debug3(af.toString());
         CachedUrl cu = af.getRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA);
         CachedUrl fullCu = af.getFullTextCu();
         if ( cu != null) {
            String url = cu.getUrl();
            String contentType = cu.getContentType();
            log.debug3("count " + count + " url " + url + " " + contentType);
            count++;
         }
         if ( fullCu != null) {
           String url = fullCu.getUrl();
           String contentType = fullCu.getContentType();
           /* if full html is available, it will be this, otherwise pdf*/
           if (contentType.equals("application/pdf")) {
             fullPdfCount++;
             log.debug3("pdf count " + fullPdfCount + " url " + url + " " + contentType);
           } else {
             fullHtmlCount++;
             log.debug3("html count " + fullHtmlCount + " url " + url + " " + contentType);
           }
        }
       }
       // missing HTM file will mean no article is picked up at all
       log.debug("Article count is " + count);
       //assertEquals(expCount, count);
       /* when no full text html, pdf is the fullTextCU */
       assertEquals(4, fullPdfCount);
       /* when there is a full html, this is the fullTextCU */
       //assertEquals(2, fullHtmlCount);      

   }

}