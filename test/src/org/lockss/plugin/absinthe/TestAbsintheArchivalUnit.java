/*
 * $Id: TestAbsintheArchivalUnit.java,v 1.4 2004-02-10 01:09:09 clairegriffin Exp $
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

package org.lockss.plugin.absinthe;

import java.io.File;
import java.net.URL;
import java.util.Properties;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.test.*;
import org.lockss.plugin.*;
import org.lockss.state.AuState;
import org.lockss.plugin.base.BaseCachedUrlSet;
import org.lockss.repository.LockssRepositoryImpl;

public class TestAbsintheArchivalUnit extends LockssTestCase {
  private MockLockssDaemon theDaemon;
  private MockArchivalUnit mau;

  static final String ROOT_URL = "http://absinthe-literary-review.com/";

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    theDaemon = new MockLockssDaemon();
    theDaemon.getHashService();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  private AbsintheArchivalUnit makeAu(URL url, String year)
      throws ArchivalUnit.ConfigurationException {
    Properties props = new Properties();
    props.setProperty(AbsinthePlugin.AUPARAM_YEAR, year);
    if (url!=null) {
      props.setProperty(AbsinthePlugin.AUPARAM_BASE_URL, url.toString());
    }
    Configuration config = ConfigurationUtil.fromProps(props);
    AbsinthePlugin ap = new AbsinthePlugin();
    ap.initPlugin(theDaemon,ap.getClass().getName());
    AbsintheArchivalUnit au = new AbsintheArchivalUnit(ap);
    au.setConfiguration(config);
    return au;
  }

  public void testConstructNullUrl() throws Exception {
    try {
      makeAu(null, "2003");
      fail("Should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) { }
  }

  public void testConstructBadYear() throws Exception {
    URL url = new URL(ROOT_URL);

    // 1 digit
    try {
      makeAu(url, "3");
      fail("Should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) { }

    // 3 digits
    try {
      makeAu(url, "123");
      fail("Should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) { }

    // 5 digits
    try {
      makeAu(url, "12345");
      fail("Should have thrown ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) { }
  }

  public void testShouldCacheProperPages() throws Exception {
    URL base = new URL(ROOT_URL);
    ArchivalUnit pmAu = makeAu(base, "2003");
    theDaemon.getLockssRepository(pmAu);
    theDaemon.getNodeManager(pmAu);
    BaseCachedUrlSet cus = new BaseCachedUrlSet(pmAu,
        new RangeCachedUrlSetSpec(base.toString()));

    // root page
    shouldCacheTest(ROOT_URL+"archives03.htm", true, pmAu, cus);

    // story html
    shouldCacheTest(ROOT_URL+"stories/schneiderman.htm", true, pmAu, cus);

    // poetry html
    shouldCacheTest(ROOT_URL+"poetics/levy.htm", true, pmAu, cus);

    // essay html
    shouldCacheTest(ROOT_URL+"archives/sloan6.htm", true, pmAu, cus);

    // book reviews html
    shouldCacheTest(ROOT_URL+"book_reviews/gal.htm", true, pmAu, cus);

    // contributor page
    shouldCacheTest(ROOT_URL+"archives/contrib16.htm", true, pmAu, cus);

    // images
    shouldCacheTest(ROOT_URL+"images/bulley.jpg", true, pmAu, cus);
    shouldCacheTest(ROOT_URL+"images/dotclear.gif", true,
                    pmAu, cus);

    // should not cache these

    // other archived root page
    shouldCacheTest(ROOT_URL+"archives02.htm", false, pmAu, cus);

    // link page
    shouldCacheTest(ROOT_URL+"links.htm", false, pmAu, cus);
    // books page
    shouldCacheTest(ROOT_URL+"books.htm", false, pmAu, cus);
    // archives page
    shouldCacheTest(ROOT_URL+"archives.htm", false, pmAu, cus);
    // authors page
    shouldCacheTest(ROOT_URL+"authors.htm", false, pmAu, cus);
    // index page
    shouldCacheTest(ROOT_URL+"index.html", false, pmAu, cus);

    // book review index page
    shouldCacheTest(ROOT_URL+"book_reviews/book_reviews.htm", false, pmAu, cus);

    // cgi script
    shouldCacheTest(ROOT_URL+"cgi-bin/guestbook.cgi", false, pmAu, cus);

    // LOCKSS
    shouldCacheTest("http://lockss.stanford.edu", false, pmAu, cus);

    // other sites
    shouldCacheTest("http://www.dandelionbooks.net/", false, pmAu, cus);
    shouldCacheTest("http://www.sixgallerypress.com/", false, pmAu, cus);
  }

  private void shouldCacheTest(String url, boolean shouldCache,
                               ArchivalUnit au, CachedUrlSet cus) {
    UrlCacher uc = au.getPlugin().makeUrlCacher(cus, url);
    assertTrue(uc.shouldBeCached()==shouldCache);
  }

  public void testStartUrlConstruction() throws Exception {
    URL url = new URL(ROOT_URL);

    // 4 digit
    String expectedStr = ROOT_URL+"archives04.htm";
    AbsintheArchivalUnit pmAu = makeAu(url, "2004");
    assertEquals(expectedStr, pmAu.makeStartUrl());
  }

  public void testPathInUrlThrowsException() throws Exception {
    URL url = new URL(ROOT_URL+"path");
    try {
      makeAu(url, "2003");
      fail("Should have thrown ArchivalUnit.ConfigurationException");
    } catch(ArchivalUnit.ConfigurationException e) { }
  }

  public void testGetUrlStems() throws Exception {
    String stem1 = "http://muse.jhu.edu";
    AbsintheArchivalUnit pmAu1 = makeAu(new URL(stem1 + "/"), "2003");
    assertEquals(ListUtil.list(stem1), pmAu1.getUrlStems());
    String stem2 = "http://muse.jhu.edu:8080";
    AbsintheArchivalUnit pmAu2 = makeAu(new URL(stem2 + "/"), "2003");
    assertEquals(ListUtil.list(stem2), pmAu2.getUrlStems());
  }

  public void testShouldDoNewContentCrawlTooEarly() throws Exception {
    ArchivalUnit pmAu = makeAu(new URL(ROOT_URL), "2003");
    AuState aus = new MockAuState(null, TimeBase.nowMs(), -1, -1, null);
    assertFalse(pmAu.shouldCrawlForNewContent(aus));
  }

  public void testShouldDoNewContentCrawlForZero() throws Exception {
    ArchivalUnit pmAu = makeAu(new URL(ROOT_URL), "2003");
    AuState aus = new MockAuState(null, 0, -1, -1, null);
    assertTrue(pmAu.shouldCrawlForNewContent(aus));
  }

  public void testShouldDoNewContentCrawlEachMonth() throws Exception {
    ArchivalUnit pmAu = makeAu(new URL(ROOT_URL), "2003");
    AuState aus = new MockAuState(null, 4 * Constants.WEEK, -1, -1, null);
    assertTrue(pmAu.shouldCrawlForNewContent(aus));
  }

  public void testGetName() throws Exception {
    AbsintheArchivalUnit au = makeAu(new URL(ROOT_URL), "2003");
    assertEquals("absinthe-literary-review.com, 2003", au.getName());
    au = makeAu(new URL("http://www.bmj.com/"), "2005");
    assertEquals("www.bmj.com, 2005", au.getName());
  }

  public static void main(String[] argv) {
    String[] testCaseList = {TestAbsintheArchivalUnit.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

}
