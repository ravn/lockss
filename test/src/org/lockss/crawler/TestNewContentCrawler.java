/*
 * $Id: TestNewContentCrawler.java,v 1.31 2005-03-18 18:05:12 troberts Exp $
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

package org.lockss.crawler;
import java.util.*;
import java.net.*;
import java.io.*;

import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.test.*;
import org.lockss.state.*;
import org.lockss.util.urlconn.*;

public class TestNewContentCrawler extends LockssTestCase {

  private MyMockArchivalUnit mau = null;
  private MockCachedUrlSet mcus = null;
  private CrawlSpec spec = null;
  private MockAuState aus = new MockAuState();
  private static List testUrlList = ListUtil.list("http://example.com");
  private MockCrawlRule crawlRule = null;
  private String startUrl = "http://www.example.com/index.html";
  private String permissionPage = "http://www.example.com/permission.html";
  private List startUrls = ListUtil.list(startUrl);
  private CrawlerImpl crawler = null;
  private MockContentParser parser = new MockContentParser();

  private static final String PARAM_RETRY_TIMES =
    Configuration.PREFIX + "CrawlerImpl.numCacheRetries";
  private static final int DEFAULT_RETRY_TIMES = 3;

  public void setUp() throws Exception {
    super.setUp();
    TimeBase.setSimulated(10);

    getMockLockssDaemon().getAlertManager();

    mau = new MyMockArchivalUnit();
    mau.setPlugin(new MockPlugin());
    mau.setAuId("MyMockTestAu");
    startUrls = ListUtil.list(startUrl);
    mcus = (MockCachedUrlSet)mau.getAuCachedUrlSet();

    crawlRule = new MockCrawlRule();
    crawlRule.addUrlToCrawl(startUrl);
    crawlRule.addUrlToCrawl(permissionPage);
    mau.addUrl(permissionPage);
    spec = new SpiderCrawlSpec(startUrls, ListUtil.list(permissionPage), crawlRule, 1);
    crawler = new MyNewContentCrawler(mau, spec, aus);
    ((CrawlerImpl)crawler).lockssCheckers = ListUtil.list(new MockPermissionChecker(1));

    mau.setParser(parser);
    Properties p = new Properties();
    p.setProperty(NewContentCrawler.PARAM_RETRY_PAUSE, "0");
    ConfigurationUtil.setCurrentConfigFromProps(p);
  }

  public void testMnccThrowsForNullAu() {
    try {
      crawler = new MyNewContentCrawler(null, spec, new MockAuState());
      fail("Constructing a NewContentCrawler with a null ArchivalUnit"
	   +" should throw an IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
    }
  }

  public void testMnccThrowsForNullCrawlSpec() {
    try {
      crawler = new MyNewContentCrawler(mau, null, new MockAuState());
      fail("Calling makeNewContentCrawler with a null CrawlSpec"
	   +" should throw an IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
    }
  }

  public void testMnccThrowsForNullAuState() {
    try {
      crawler = new MyNewContentCrawler(mau, spec, null);
      fail("Calling makeNewContentCrawler with a null AuState"
	   +" should throw an IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
    }
  }

  public void testReturnsProperType() {
    assertEquals(Crawler.NEW_CONTENT, crawler.getType());
  }

  public void testShouldFollowLink() {
    assertTrue(((NewContentCrawler)crawler).shouldFollowLink());
  }

  //Will try to fetch startUrl, content parser will return no urls,
  //so we should only cache the start url
  public void testDoCrawlOnePageNoLinks() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl);
    assertTrue(crawler.doCrawl());
    Set cachedUrls = cus.getCachedUrls();
    Set expected = SetUtil.set(startUrl, permissionPage);
    assertEquals(expected, cachedUrls);
  }

  public void testMultipleStartingUrls() {
    List urls = ListUtil.list("http://www.example.com/link1.html",
			      "http://www.example.com/link2.html",
			      "http://www.example.com/link3.html",
			      startUrl);

    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    for (int ix=0; ix<urls.size(); ix++) {
      String curUrl = (String)urls.get(ix);
      mau.addUrl(curUrl);
      crawlRule.addUrlToCrawl(curUrl);
    }

    spec = new SpiderCrawlSpec(urls, ListUtil.list(permissionPage), crawlRule, 1);
    crawler = new MyNewContentCrawler(mau, spec, new MockAuState());
    ((CrawlerImpl)crawler).lockssCheckers = ListUtil.list(new MockPermissionChecker(1));

    assertTrue(crawler.doCrawl());
    Set expected = SetUtil.fromList(urls);
    expected.add(permissionPage);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testDoCrawlOnePageWithOneLinkSuccessful() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    String url1="http://www.example.com/blah.html";
    mau.addUrl(startUrl, false, true);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1));
    mau.addUrl(url1, false, true);
    crawlRule.addUrlToCrawl(url1);

    assertTrue(crawler.doCrawl0());
    Set expected = SetUtil.set(permissionPage, startUrl, url1);
    assertEquals(expected, cus.getCachedUrls());
   }

  public void testReturnsFalseWhenFailingUnretryableExceptionThrownOnStartUrl() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    MyMockUnretryableCacheException exception =
       new MyMockUnretryableCacheException("Test exception");
    mau.addUrl(startUrl, exception, DEFAULT_RETRY_TIMES);

    assertFalse(crawler.doCrawl0());
    Set expected = SetUtil.set(permissionPage);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testReturnsTrueWhenNonFailingUnretryableExceptionThrownOnStartUrl() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    MyMockCacheException exception =
      new MyMockCacheException("Test exception");
    mau.addUrl(startUrl, exception, DEFAULT_RETRY_TIMES);

    assertTrue(crawler.doCrawl0());
    Set expected = SetUtil.set(permissionPage);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testReturnsFalseWhenIOExceptionThrownOnStartUrl() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl, new IOException("Test exception"), DEFAULT_RETRY_TIMES);

    assertFalse(crawler.doCrawl0());    
    Set expected = SetUtil.set(permissionPage);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testReturnsRetriesWhenRetryableExceptionThrownOnStartUrl() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    MyMockRetryableCacheException exception =
      new MyMockRetryableCacheException("Test exception");
    mau.addUrl(startUrl, exception, DEFAULT_RETRY_TIMES-1);
    String url1="http://www.example.com/blah.html";
    mau.addUrl(url1, false, true);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1));
    crawlRule.addUrlToCrawl(url1);
    
    assertTrue(crawler.doCrawl0());
    Set expected = SetUtil.set(permissionPage, startUrl, url1);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testReturnsFalseWhenOneOfStartUrlsFailedToBeFetched() {
    String startUrl2 = "http://www.foo.com/default.html";
    String permissionPage2 = "http://www.foo.com/permission.html";
    List permissionList = ListUtil.list(permissionPage, permissionPage2);
    mau.addUrl(permissionPage2);
    crawlRule.addUrlToCrawl(permissionPage2);

    List updatedStartUrls = ListUtil.list(startUrl, startUrl2);
    spec = new SpiderCrawlSpec(updatedStartUrls, permissionList, crawlRule, 1);
    crawler = new MyNewContentCrawler(mau, spec, new MockAuState());
    ((CrawlerImpl)crawler).lockssCheckers = ListUtil.list(new MockPermissionChecker(2));

    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    MyMockRetryableCacheException exception =
      new MyMockRetryableCacheException("Test exception");
    mau.addUrl(startUrl, exception, DEFAULT_RETRY_TIMES);
    mau.addUrl(startUrl2, true, true);
    crawlRule.addUrlToCrawl(startUrl2);

    assertFalse(crawler.doCrawl());
    Set expected = SetUtil.set(startUrl2, permissionPage, permissionPage2);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testReturnsDoesntRetryWhenUnretryableExceptionThrownOnStartUrl() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    MyMockUnretryableCacheException exception =
      new MyMockUnretryableCacheException("Test exception");
    mau.addUrl(startUrl, exception, DEFAULT_RETRY_TIMES-1);

    assertFalse(crawler.doCrawl0());
    Set expected = SetUtil.set(permissionPage);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testRetryNumSetByParamOnStartUrl() {
    int retryNum = DEFAULT_RETRY_TIMES + 3;
    assertTrue("Test is worthless unless retryNum is greater than "
	       +"DEFAULT_RETRY_TIMES", retryNum > DEFAULT_RETRY_TIMES);
    Properties p = new Properties();
    p.setProperty(PARAM_RETRY_TIMES, String.valueOf(retryNum));
    p.setProperty(NewContentCrawler.PARAM_RETRY_PAUSE, "0");
    ConfigurationUtil.setCurrentConfigFromProps(p);

    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl,
	       new MyMockRetryableCacheException("Test exception"),
	       retryNum-1);
    
    String url1="http://www.example.com/blah.html";
    mau.addUrl(url1, false, true);
    crawlRule.addUrlToCrawl(url1);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1));
    
    assertTrue(crawler.doCrawl());
    Set expected = SetUtil.set(permissionPage, startUrl, url1);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testReturnsTrueWhenNonFailingExceptionThrownOnStartUrl() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl, new CacheException.NoRetryDeadLinkException("Test exception"), 1);

    assertTrue(crawler.doCrawl());
  }

  public void testPluginThrowsRuntimeException() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl, new ExpectedRuntimeException("Test exception"), 1);

    assertFalse(crawler.doCrawl());
  }

  public void testGetStatusStartUrls() {
    Crawler.Status crawlStatus = crawler.getStatus();
    assertEquals(startUrls, crawlStatus.getStartUrls());
  }

  public void testGetStatusCrawlDone() {
    String url1 = "http://www.example.com/link1.html";
    String url2 = "http://www.example.com/link2.html";
    String url3 = "http://www.example.com/link3.html";

    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1, url2, url3));
    mau.addUrl(url1);
    mau.addUrl(url2);
    mau.addUrl(url3);
    crawlRule.addUrlToCrawl(url1);
    crawlRule.addUrlToCrawl(url2);
    crawlRule.addUrlToCrawl(url3);

    long expectedStart = TimeBase.nowMs();
    crawler.doCrawl();
    long expectedEnd = TimeBase.nowMs();
    Crawler.Status crawlStatus = crawler.getStatus();
    assertEquals(expectedStart, crawlStatus.getStartTime());
    assertEquals(expectedEnd, crawlStatus.getEndTime());
    assertEquals(5, crawlStatus.getNumFetched());
    assertEquals(SetUtil.set(startUrl, url1, url2, url3, permissionPage),
		 crawlStatus.getUrlsFetched());
    assertEquals(4, crawlStatus.getNumParsed());
  }

  public void testGetStatusCrawlDoneNotModified() {
    System.err.println("TEST START");
    String url1 = "http://www.example.com/link1.html";
    String url2 = "http://www.example.com/link2.html";
    String url3 = "http://www.example.com/link3.html";

    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl, true, true);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1, url2, url3));
    mau.addUrl(url1);
//     mau.addUrl(url1, true, true);
    mau.addUrl(url2);
    mau.addUrl(url3);
    crawlRule.addUrlToCrawl(url1);
    crawlRule.addUrlToCrawl(url2);
    crawlRule.addUrlToCrawl(url3);

    long expectedStart = TimeBase.nowMs();
    crawler.doCrawl();
    long expectedEnd = TimeBase.nowMs();
    Crawler.Status crawlStatus = crawler.getStatus();
    assertEquals(expectedStart, crawlStatus.getStartTime());
    assertEquals(expectedEnd, crawlStatus.getEndTime());
    assertEquals(4, crawlStatus.getNumFetched());
    assertEquals(1, crawlStatus.getNumNotModified());
    assertEquals(SetUtil.set(url1, url2, url3, permissionPage),
		 crawlStatus.getUrlsFetched());
    assertEquals(SetUtil.set(startUrl), crawlStatus.getUrlsNotModified());
    assertEquals(4, crawlStatus.getNumParsed());
  }

  public void testGetStatusCrawlDoneParsed() {
    String url1 = "http://www.example.com/link1.html";
    String url2 = "http://www.example.com/link2.html";
    String url3 = "http://www.example.com/link3.html";

    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1, url2, url3));
    mau.addUrl(url1);
    mau.addUrl(url2);
    mau.addUrl(url3);
    crawlRule.addUrlToCrawl(url1);
    crawlRule.addUrlToCrawl(url2);
    crawlRule.addUrlToCrawl(url3);

    long expectedStart = TimeBase.nowMs();
    crawler.doCrawl();
    long expectedEnd = TimeBase.nowMs();
    Crawler.Status crawlStatus = crawler.getStatus();
    assertEquals(expectedStart, crawlStatus.getStartTime());
    assertEquals(expectedEnd, crawlStatus.getEndTime());
    assertEquals(5, crawlStatus.getNumFetched());
    assertEquals(SetUtil.set(url1, url2, url3, startUrl),
		 crawlStatus.getUrlsParsed());
    assertEquals(4, crawlStatus.getNumParsed());
  }

  public void testGetStatusIncomplete() {
    assertEquals(Crawler.STATUS_INCOMPLETE,
		 crawler.getStatus().getCrawlStatus());
  }

  public void testGetStatusSuccessful() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl);
    crawler.doCrawl();
    assertEquals(Crawler.STATUS_SUCCESSFUL,
		 crawler.getStatus().getCrawlStatus());
  }

  public void testGetStatusError() {
    String url1="http://www.example.com/blah.html";
    mau.addUrl(startUrl, false, true);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1));
    mau.addUrl(url1, new IOException("Test exception"), DEFAULT_RETRY_TIMES);
    crawlRule.addUrlToCrawl(url1);

    crawler.doCrawl();
    assertEquals(Crawler.STATUS_ERROR,
		 crawler.getStatus().getCrawlStatus());
  }

  public void testOverwritesSingleStartingUrlsOneLevel() {
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl, true, true);

    assertTrue(crawler.doCrawl());
    Set expected = SetUtil.set(startUrl, permissionPage);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testOverwritesMultipleStartingUrlsOneLevel() {
    String startUrl2 = "http://www.foo.com/default.html";
    String permissionPage2 = "http://www.foo.com/default.html";
    List permissionList = ListUtil.list(permissionPage, permissionPage2);
    mau.addUrl(permissionPage2);
    crawlRule.addUrlToCrawl(permissionPage2);

    List updatedStartUrls = ListUtil.list(startUrl, startUrl2);
    spec = new SpiderCrawlSpec(updatedStartUrls, permissionList, crawlRule, 1);
    crawler = new MyNewContentCrawler(mau, spec, new MockAuState());
    ((CrawlerImpl)crawler).lockssCheckers = ListUtil.list(new MockPermissionChecker(2));

    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl, true, true);
    mau.addUrl(startUrl2, true, true);
    crawlRule.addUrlToCrawl(startUrl2);

    assertTrue(crawler.doCrawl());
    Set expected = SetUtil.set(startUrl, startUrl2, permissionPage, permissionPage2);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testOverwritesStartingUrlsMultipleLevels() {
    spec = new SpiderCrawlSpec(startUrls, ListUtil.list(permissionPage), crawlRule, 2);
    crawler = new MyNewContentCrawler(mau, spec, new MockAuState());
    ((CrawlerImpl)crawler).lockssCheckers = ListUtil.list(new MockPermissionChecker(1));

    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    String url1= "http://www.example.com/link1.html";
    String url2= "http://www.example.com/link2.html";
    String url3= "http://www.example.com/dir/link3.html";
    String url4= "http://www.example.com/dir/link9.html";

    //    mau.setParser(parser);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1, url2, url3));
    parser.addUrlSetToReturn(url1, SetUtil.set(url4));

    mau.addUrl(startUrl, true, true);
    mau.addUrl(url1, true, true);
    mau.addUrl(url2, true, true);
    mau.addUrl(url3, true, true);
    mau.addUrl(url4, true, true);
    crawlRule.addUrlToCrawl(url1);
    crawlRule.addUrlToCrawl(url2);
    crawlRule.addUrlToCrawl(url3);
    crawlRule.addUrlToCrawl(url4);

    assertTrue(crawler.doCrawl());
    // Refetched depth is 2 thus, url4 is not cached as it already exists.
    Set expected = SetUtil.set(startUrl, permissionPage, url1, url2, url3);
    assertEquals(expected, cus.getCachedUrls());
  }
  
  public void testOverwritesMultipleStartingUrlsMultipleLevel() {
    String startUrl2 = "http://www.foo.com/default.html";
    String permissionPage2 = "http://www.foo.com/default.html";
    List permissionList = ListUtil.list(permissionPage, permissionPage2);
    mau.addUrl(permissionPage2);
    crawlRule.addUrlToCrawl(permissionPage2);

    List updatedStartUrls = ListUtil.list(startUrl, startUrl2);
    spec = new SpiderCrawlSpec(updatedStartUrls, permissionList, crawlRule, 2);
    crawler = new MyNewContentCrawler(mau, spec, new MockAuState());
    ((CrawlerImpl)crawler).lockssCheckers = ListUtil.list(new MockPermissionChecker(2));

    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    String url1= "http://www.example.com/link1.html";
    String url2= "http://www.example.com/link2.html";
    String url3= "http://www.example.com/dir/link3.html";
    String url4= "http://www.foo.com/dir/link4.html";
    String url5= "http://www.foo.com/dir/link5.html";
    String url6= "http://www.foo.com/dir/link6.html";
    
    //    mau.setParser(parser);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1, url2, url3));
    parser.addUrlSetToReturn(startUrl2, SetUtil.set(url4, url5));
    parser.addUrlSetToReturn(url4, SetUtil.set(url6));
    
    mau.addUrl(startUrl, true, true);
    mau.addUrl(startUrl2, true, true);
    mau.addUrl(url1, true, true);
    mau.addUrl(url2, true, true);
    mau.addUrl(url3, true, true);
    mau.addUrl(url4, true, true);
    mau.addUrl(url5, true, true);
    mau.addUrl(url6, true, true);
    crawlRule.addUrlToCrawl(startUrl2);
    crawlRule.addUrlToCrawl(url1);
    crawlRule.addUrlToCrawl(url2);
    crawlRule.addUrlToCrawl(url3);
    crawlRule.addUrlToCrawl(url4);
    crawlRule.addUrlToCrawl(url5);
    crawlRule.addUrlToCrawl(url6);

    assertTrue(crawler.doCrawl());
    // url6 is not expected as refetch depth is 2 and it already exists.
    Set expected = SetUtil.set(permissionPage, permissionPage2, startUrl, startUrl2, 
			       url1, url2, url3, url4, url5);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testCrawlWindow() {
    String url1 = "http://www.example.com/link1.html";
    String url2 = "http://www.example.com/link2.html";
    String url3 = "http://www.example.com/link3.html";

    spec.setCrawlWindow(new MyMockCrawlWindow(3));
    mau.setCrawlSpec(spec);

    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1, url2, url3));
    mau.addUrl(url1);
    mau.addUrl(url2);
    mau.addUrl(url3);
    crawlRule.addUrlToCrawl(url1);
    crawlRule.addUrlToCrawl(url2);
    crawlRule.addUrlToCrawl(url3);


    crawler = new MyNewContentCrawler(mau, spec, aus);
    ((CrawlerImpl)crawler).lockssCheckers = ListUtil.list(new MockPermissionChecker(100));

    mau.setParser(parser);
    assertFalse(crawler.doCrawl());
    Set expected = SetUtil.set(startUrl, permissionPage, url1);
    assertEquals(expected, cus.getCachedUrls());
  }

  public void testOutsideOfWindow1() {
    String url1= "http://www.example.com/link1.html";
    spec.setCrawlWindow(new MyMockCrawlWindow(0));
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1));
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl, true, true);
    assertFalse(crawler.doCrawl());
  }

  public void testOutsideOfWindow2() {
    String url1= "http://www.example.com/link1.html";
    spec.setCrawlWindow(new MyMockCrawlWindow(1));
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1));
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    mau.addUrl(startUrl, true, true);
    mau.addUrl(url1);
    assertFalse(crawler.doCrawl());
  }
  
  public void testDoesCollectHttpsOnStartingUrls() {
    //we will collect ftp, gopher https eventually, 
    //it is not yet implemented though and this test 
    //is to make sure urls in this protocols will not 
    //break the whole system

    String startUrl = "https://www.example.com/index.html";
    String url1 = "https://www.example.com/link1.html";
    List startUrls = ListUtil.list(startUrl);

    //See if we're running in a version of java that throws on trying
    //to construct a https URL
    boolean httpsUrlThrows = false;
    try {
      URL url = new URL(startUrl);
    } catch (Exception e) {
      httpsUrlThrows = true;
    }


    spec = new SpiderCrawlSpec(startUrls, ListUtil.list(permissionPage), crawlRule, 1);
    crawler = new MyNewContentCrawler(mau, spec, aus);
    ((CrawlerImpl)crawler).lockssCheckers = ListUtil.list(new MockPermissionChecker(100));

    mau.addUrl(startUrl, false, true);
    crawlRule.addUrlToCrawl(startUrl);
    parser.addUrlSetToReturn(startUrl, SetUtil.set(url1));
    mau.addUrl(url1, false, true);    
    crawlRule.addUrlToCrawl(url1);
    
    mau.setParser(parser);
    if (httpsUrlThrows) {
      assertFalse("Crawler shouldn't succeed when trying system can't construct a https URL", crawler.doCrawl());
    } else {
      assertTrue("Crawler should succeed when system can construct a https URL",
		crawler.doCrawl());
    }
    
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    Set expectedSet = SetUtil.set(permissionPage);
    
    if (!httpsUrlThrows) {
      expectedSet.add(startUrl);
    }    
    assertEquals(expectedSet, cus.getCachedUrls());
  }

  private static void setProperty(String prop, String value) {
    Properties p = new Properties();
    p.setProperty(prop, value);
    ConfigurationUtil.setCurrentConfigFromProps(p);
  }

  private static class MyNewContentCrawler extends NewContentCrawler {
    protected MyNewContentCrawler(ArchivalUnit au, CrawlSpec spec,
				  AuState aus){
      super(au, spec, aus);
    }

    /** suppress these actions */
    protected void doCrawlEndActions() {
    }
  }

  private class MyMockArchivalUnit extends MockArchivalUnit {
    MyMockUrlCacher lastMmuc;

    protected MockUrlCacher makeMockUrlCacher(String url) {
      lastMmuc = new MyMockUrlCacher(url, this);
      return lastMmuc;
    }

  }


  private class MyMockCrawlWindow implements CrawlWindow {
    int numTimesToReturnTrue = 0;

    public MyMockCrawlWindow(int numTimesToReturnTrue) {
      this.numTimesToReturnTrue = numTimesToReturnTrue;
    }

    public boolean canCrawl() {
      if (numTimesToReturnTrue > 0) {
	numTimesToReturnTrue--;
	return true;
      }
      return false;
    }

    public boolean canCrawl(Date date) {
      throw new UnsupportedOperationException("not implemented");
    }

  }


  private class MyMockUrlCacher extends MockUrlCacher {
    private boolean abortCrawl = false;
    String proxyHost = null;
    int proxyPort;

    public MyMockUrlCacher(String url, MockArchivalUnit au) {
      super(url, au);
    }

    public InputStream getUncachedInputStream() {
      checkAbort();
      return new StringInputStream("");
    }
    public int cache() throws IOException {
      checkAbort();
      //System.out.println("Caching for : " + super.getUrl());
      return super.cache();
    }
    private void checkAbort() {
      if (abortCrawl) {
	crawler.abortCrawl();
      }
    }
    public void setProxy(String proxyHost, int proxyPort) {
      this.proxyHost = proxyHost;
      this.proxyPort = proxyPort;
    }
  }

  private class MyMockRetryableCacheException
    extends CacheException.RetryableException {
    public MyMockRetryableCacheException(String msg) {
      super(msg);
    }
  }

  private class MyMockUnretryableCacheException
    extends CacheException.UnretryableException {
    public MyMockUnretryableCacheException(String msg) {
      super(msg);
    }
  }

  private class MyMockCacheException
    extends CacheException {
    public MyMockCacheException(String msg) {
      super(msg);
    }
    public void setFailing() {
      attributeBits.set(CacheException.ATTRIBUTE_FAIL);
    }
  }

  public static void main(String[] argv) {
    String[] testCaseList = {TestNewContentCrawler.class.getName()};
    junit.textui.TestRunner.main(testCaseList);
  }

}

