/* $Id
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

package org.lockss.plugin.iop;

import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.Stack;
import java.util.regex.Pattern;

import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.plugin.*;
import org.lockss.plugin.simulated.SimulatedArchivalUnit;
import org.lockss.plugin.simulated.SimulatedContentGenerator;
import org.lockss.test.*;
import org.lockss.util.*;

/*
 * PDF Full Text: http://iopscience.iop.org/1758-5090/1/1/010201/pdf/1758-5090_1_1_010201.pdf
 * HTML Abstract: http://iopscience.iop.org/1758-5090/1/1/010201
 */
public class TestIOPScienceArticleIteratorFactory extends ArticleIteratorTestCase {
  
  private SimulatedArchivalUnit sau;  // Simulated AU to generate content
  private final String ARTICLE_FAIL_MSG = "Article files not created properly";
  private final String PATTERN_FAIL_MSG = "Article file URL pattern changed or incorrect";
  private final String PLUGIN_NAME = "org.lockss.plugin.iop.ClockssIOPSciencePlugin";
  static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
  static final String JOURNAL_ISSN_KEY = ConfigParamDescr.JOURNAL_ISSN.getKey();
  static final String VOLUME_NAME_KEY = ConfigParamDescr.VOLUME_NAME.getKey();
  private final String BASE_URL = "http://www.example.com/";
  private final String JOURNAL_ISSN = "1758-5090";
  private final String VOLUME_NAME = "2";
  private final String AU_URL = BASE_URL + JOURNAL_ISSN + "/" + VOLUME_NAME;
  private final Configuration AU_CONFIG = ConfigurationUtil.fromArgs(
      BASE_URL_KEY, BASE_URL,
      VOLUME_NAME_KEY, VOLUME_NAME,
      JOURNAL_ISSN_KEY, JOURNAL_ISSN);
  private static final int DEFAULT_FILESIZE = 3000;
  
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
    conf.put("depth", "1");
    conf.put("branch", "2");
    conf.put("numFiles", "6");
    conf.put("fileTypes",
        "" + (  SimulatedContentGenerator.FILE_TYPE_HTML
            | SimulatedContentGenerator.FILE_TYPE_PDF));
    conf.put("binFileSize", ""+DEFAULT_FILESIZE);
    return conf;
  }
  
  public void testRoots() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    assertEquals("Article file root URL pattern changed or incorrect", 
        ListUtil.list(AU_URL), getRootUrls(artIter));
  }
  
  public void testUrlsWithPrefixes() throws Exception {
    SubTreeArticleIterator artIter = createSubTreeIter();
    Pattern pat = getPattern(artIter);
    // http://iopscience.iop.org/1758-5090/1/1/010201/pdf/1758-5090_1_1_010201.pdf
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, "http://www.wrong.com/" +
        JOURNAL_ISSN + "/" + VOLUME_NAME);
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/1");
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/1/");
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/1/010201/pdf");
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/1/010201/pdf/");
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/2/55656/full");
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/2/55656/fulltextual");
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/3/010201/pdf/" + 
        JOURNAL_ISSN + "_" + VOLUME_NAME + "_3_010201");
    assertNotMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/4/010201/pdf/" + 
        JOURNAL_ISSN + "_" + VOLUME_NAME + "_3_010201");
    assertMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/4/010201");
    assertMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/4/010201/fulltext");
    assertMatchesRE(PATTERN_FAIL_MSG, pat, AU_URL + "/5/010201/pdf/" +
        JOURNAL_ISSN + "_" + VOLUME_NAME + "_5_010201.pdf");
  }
  
  /*
   * PDF Full Text: http://www.igi-global.com/gateway/contentowned/article.aspx?titleid=55656
   * HTML Abstract: http://www.igi-global.com/viewtitle.aspx?titleid=55656
   */
  public void testCreateArticleFiles() throws Exception {
    PluginTestUtil.crawlSimAu(sau);
    String[] urls = {
        AU_URL + "/1/11",
        AU_URL + "/1/11/refs",
        AU_URL + "/1/11/cites",
        AU_URL + "/1/11/fulltext",
        AU_URL + "/1/11/pdf/1758-5090_2_1_11.pdf",
        AU_URL + "/2/2222",
        AU_URL + "/2/2222/fulltext",
        AU_URL + "/2/2222/refs",
        AU_URL + "/2/2222/media",
        AU_URL + "/3/33333",
        AU_URL + "/3/33333/pdf/1758-5090_2_3_33333.pdf",
        AU_URL + "/3/33333/refs",
        AU_URL + "/4/44444",
        AU_URL + "/4/44444/cites",
        AU_URL + "/5/5/fulltext",
        AU_URL
    };
    CachedUrl cuPdf = null;
    CachedUrl cuHtml = null;
    for (CachedUrl cu : AuUtil.getCuIterable(sau)) {
      if (cuPdf == null && 
          cu.getContentType().toLowerCase().startsWith(
              Constants.MIME_TYPE_PDF)) {
    	  cuPdf = cu;
    	}
      else if (cuHtml == null && 
          cu.getContentType().toLowerCase().startsWith(
              Constants.MIME_TYPE_HTML)) {
      	cuHtml = cu;
      }
      if (cuPdf != null && cuHtml != null) {
      	break;
      }
    }
    for (String url : urls) {
      if (url.contains("pdf")) {
        storeContent(cuPdf.getUnfilteredInputStream(), cuPdf.getProperties(), url);
      }
      else {
        storeContent(cuHtml.getUnfilteredInputStream(), cuHtml.getProperties(), url);
      }
    }
    
    Stack<String[]> expStack = new Stack<String[]>();
    
    String [] af1 = {
        AU_URL + "/1/11/fulltext",
        AU_URL + "/1/11/fulltext",
        AU_URL + "/1/11/pdf/1758-5090_2_1_11.pdf",
        AU_URL + "/1/11",
        AU_URL + "/1/11/refs",
        AU_URL + "/1/11"};
    
    String [] af2 = {
        AU_URL + "/2/2222/fulltext",
        AU_URL + "/2/2222/fulltext",
        null,
        AU_URL + "/2/2222",
        AU_URL + "/2/2222/refs",
        AU_URL + "/2/2222"};
    
    String [] af3 = {
        AU_URL + "/3/33333/pdf/1758-5090_2_3_33333.pdf",
        null,
        AU_URL + "/3/33333/pdf/1758-5090_2_3_33333.pdf",
        AU_URL + "/3/33333",
        AU_URL + "/3/33333/refs",
        AU_URL + "/3/33333"};
    
    String [] af4 = {
        null,
        null,
        null,
        AU_URL + "/4/44444",
        null,
        AU_URL + "/4/44444"};
    
    String [] af5 = {
        AU_URL + "/5/5/fulltext",
        AU_URL + "/5/5/fulltext",
        null,
        AU_URL + "/5/5/fulltext",
        null,
        null};
    
    expStack.push(af5);
    expStack.push(af4);
    expStack.push(af3);
    expStack.push(af2);
    expStack.push(af1);
    
    for ( SubTreeArticleIterator artIter = createSubTreeIter(); artIter.hasNext(); ) 
    {
      ArticleFiles af = artIter.next();
      String[] act = {
          af.getFullTextUrl(),
          af.getRoleUrl(ArticleFiles.ROLE_FULL_TEXT_HTML),
          af.getRoleUrl(ArticleFiles.ROLE_FULL_TEXT_PDF),
          af.getRoleUrl(ArticleFiles.ROLE_ARTICLE_METADATA),
          af.getRoleUrl(ArticleFiles.ROLE_REFERENCES),
          af.getRoleUrl(ArticleFiles.ROLE_ABSTRACT)
      };
      String[] exp = expStack.pop();
      if(act.length == exp.length){
        for(int i = 0;i< act.length; i++){
          assertEquals(ARTICLE_FAIL_MSG + " Expected: " + exp[i] + " Actual: " + act[i], exp[i],act[i]);
        }
      }
      else fail(ARTICLE_FAIL_MSG + " length of expected and actual ArticleFiles content not the same:" + exp.length + "!=" + act.length);
    }
    
  }
}
