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

import org.lockss.config.Configuration;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.plugin.*;
import org.lockss.plugin.ArchivalUnit.ConfigurationException;
import org.lockss.state.AuState;
import org.lockss.test.*;
import org.lockss.util.Constants;
import org.lockss.util.ListUtil;
import org.lockss.util.TimeBase;

public class TestIOPSciencePlugin extends LockssPluginTestCase {
  
  protected MockLockssDaemon daemon;
  private final String PLUGIN_NAME = "org.lockss.plugin.iop.ClockssIOPSciencePlugin";
  static  final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
  static  final String JOURNAL_ISSN_KEY = ConfigParamDescr.JOURNAL_ISSN.getKey();
  static  final String VOLUME_NAME_KEY = ConfigParamDescr.VOLUME_NAME.getKey();
  private final String BASE_URL = "http://www.example.com/";
  private final String VOLUME = "21";
  private final String JOURNAL_ISSN = "1546-2234";
  private final Configuration AU_CONFIG = ConfigurationUtil.fromArgs(
      BASE_URL_KEY, BASE_URL,
      VOLUME_NAME_KEY, VOLUME,
      JOURNAL_ISSN_KEY, JOURNAL_ISSN);
  public void setUp() throws Exception {
    super.setUp();
  }
  
  public void tearDown() throws Exception {
    super.tearDown();
  }
  
  protected ArchivalUnit createAu() throws ArchivalUnit.ConfigurationException {
    return createAu(AU_CONFIG);
  }
  
  protected ArchivalUnit createAu(Configuration config)
      throws ArchivalUnit.ConfigurationException {
    return PluginTestUtil.createAndStartAu(PLUGIN_NAME, config);
  }
  
  public void testCreateAu() {
    
    try {
      createAu(ConfigurationUtil.fromArgs(
          BASE_URL_KEY, BASE_URL,
          VOLUME_NAME_KEY, VOLUME));
      fail("Bad AU configuration should throw configuration exception ");
    }
    catch (ConfigurationException ex) {
    }
    try {
      createAu();
    }
    catch (ConfigurationException ex) {
      fail("Unable to creat AU from valid configuration");
    }
  }
  
  // http://iopscience.iop.org/1758-5090/1/1/010201
  // http://iopscience.iop.org/1478-3975/8/1/015001
  // http://iopscience.iop.org/1478-3975/8/1/015001/refs
  // http://iopscience.iop.org/1478-3975/8/1/015001/cites
  // http://iopscience.iop.org/1478-3975/8/1/015001/fulltext
  // http://iopscience.iop.org/1478-3975/8/1/015001/pdf/1478-3975_8_1_015001.pdf
  // http://iopscience.iop.org/1758-5090/1/1/010201/pdf/1758-5090_1_1_010201.pdf
  public void testShouldCacheProperPages() throws Exception {
    ArchivalUnit au = createAu();
    // BaseCachedUrlSet cus = new BaseCachedUrlSet(au, new RangeCachedUrlSetSpec(BASE_URL));
    
    // start page
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME, true, au);
    
    assertShouldCache(BASE_URL + JOURNAL_ISSN, false, au);
    
    // issue and article pages
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1", true, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/2345", true, au);
    
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1/2345", true, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1/2345/fulltext", true, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1/2345/refs", true, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1/2345/media", true, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1/2345/pdf/" +
        JOURNAL_ISSN + "_" + VOLUME + "_1_12345", true, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1/011002?rel=sem&relno=1", true, au);
    
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1/2345/cites", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1/2345/metrics", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/1/2345", false, au);
//    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1/2345/pdf", false, au);
//    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/1/2345/pdf/", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/4/045001/refs/5/article", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/?rss=1", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/3/032001/refs/52/pubmed", false, au);
//    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/" + VOLUME + "/", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/page/Scope", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/page/Editorial%20Board", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/page/Abstracted%20in", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/page/Author%20benefits", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/page/Highlights%20of%202012", false, au);
    assertShouldCache(BASE_URL + JOURNAL_ISSN + "/page/Events%20calendar", false, au);
    
    // images, css, js
    assertShouldCache(BASE_URL + "img/close-icon.png", true, au);
    assertShouldCache(BASE_URL + "img/iop-publishing-footer-logo.gif", true, au);
    assertShouldCache(BASE_URL + "css/9.1.11/lib/jquery.pnotify.default.css", true, au);
    assertShouldCache(BASE_URL + "js/9.1.11/lib/functional.min.js", true, au);
    
    
    // images, css, js with bad formatting
    assertShouldCache(BASE_URL + "js/9.1.11/lib/modernizr.custom.01062.js?v=02242012b", false, au);
    assertShouldCache(BASE_URL + "css/9.1.11/lib/jquery.default.css?v=02162012", false, au);
    
    // specified bad pages
    assertShouldCache(BASE_URL + "page/subjects", false, au);
    assertShouldCache(BASE_URL + "collections?collection_type=SELECT", false, au);
    assertShouldCache(BASE_URL + "info/page/openaccess", false, au);
    assertShouldCache(BASE_URL + "info/page/developing-countries-access", false, au);
    assertShouldCache(BASE_URL + "page/copyright", false, au);
    assertShouldCache(BASE_URL + "info/page/nih", false, au);
    assertShouldCache(BASE_URL + "search?searchType=selectedPacsMscCode&primarypacs=87.80.-y", false, au);
    
    // facebook
    assertShouldCache("http://www.facebook.com/pages/IGI-Global/138206739534176?ref=sgm", false, au);
    
    // LOCKSS
    assertShouldCache("http://lockss.stanford.edu", false, au);
    
    // other site
    assertShouldCache("http://ioppublishing.org/", false, au);
    assertShouldCache("http://librarians.iop.org/instinfo", false, au);
    assertShouldCache("https://ticket.iop.org/login?return=http%3A%2F%2Fiopscience.iop.org" +
        "%2F1758-5090%2F1%2F3", false, au);
    assertShouldCache("", false, au);
    assertShouldCache("", false, au);
    assertShouldCache("", false, au);
    

    assertShouldCache("http://ajax.googleapis.com/ajax/libs/jquery/1.4.4/jquery.min.js", false, au);
    
  }
  
  private void assertShouldCache(String url, boolean shouldCache, ArchivalUnit au) {
    UrlCacher uc = au.makeUrlCacher(url);
    assertEquals("AU crawl rules applied incorrectly to " + url + " ",
        shouldCache, uc.shouldBeCached());
  }
  
  public void testStartUrlConstruction() throws Exception {
    String expectedStartUrl = BASE_URL + JOURNAL_ISSN + "/" + VOLUME;
    ArchivalUnit au = createAu();
    assertEquals(ListUtil.list(expectedStartUrl), au.getNewContentCrawlUrls());
  }
  
  public void testGetUrlStems() throws Exception {
    ArchivalUnit au = createAu();
    assertEquals(ListUtil.list(BASE_URL), au.getUrlStems());
  }
  
  public void testShouldDoNewContentCrawlTooEarly() throws Exception {
    ArchivalUnit au = createAu();
    AuState aus = new MockAuState(null, TimeBase.nowMs(), -1, -1, null);
    assertFalse(au.shouldCrawlForNewContent(aus));
  }
  
  public void testShouldDoNewContentCrawlForZero() throws Exception {
    ArchivalUnit au = createAu();
    AuState aus = new MockAuState(null, 0, -1, -1, null);
    assertTrue(au.shouldCrawlForNewContent(aus));
  }
  
  public void testShouldDoNewContentCrawlEachMonth() throws Exception {
    ArchivalUnit au = createAu();
    AuState aus = new MockAuState(null, 4 * Constants.WEEK, -1, -1, null);
    assertTrue(au.shouldCrawlForNewContent(aus));
  }
  
  public void testGetName() throws Exception {
    ArchivalUnit au = createAu();
    assertEquals("IOP Publishing Plugin (IOPscience for CLOCKSS), Base URL " + 
        BASE_URL + ", ISSN " + JOURNAL_ISSN + ", Volume " + VOLUME, au.getName());
  }
  
}
