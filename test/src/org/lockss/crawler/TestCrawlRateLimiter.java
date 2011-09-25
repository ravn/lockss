/*
 * $Id: TestCrawlRateLimiter.java,v 1.1 2011-09-25 04:20:39 tlipkis Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.crawler.*;
import org.lockss.plugin.*;

public class TestCrawlRateLimiter extends LockssTestCase {
  public void testMime() {
    RateLimiterInfo rli = new RateLimiterInfo("key1", 1, 50000);
    Map<String,String> mimes =
      MapUtil.map("text/html,text/x-html,application/pdf", "10/1m",
		  "image/*", "5/1s");
    rli.setMimeRates(mimes);
    CrawlRateLimiter crl = new CrawlRateLimiter(rli);
    RateLimiter limiter = crl.getRateLimiterFor("url", "text/html");
    assertEquals("10/1m", limiter.getRate());
    assertSame(limiter, crl.getRateLimiterFor("url", "text/html"));
    assertSame(limiter, crl.getRateLimiterFor("url",
					      "text/html; charset=utf-8"));
    assertSame(limiter, crl.getRateLimiterFor("url", "text/x-html"));
    assertSame(limiter, crl.getRateLimiterFor("url", "application/pdf"));
    RateLimiter defLimiter = crl.getRateLimiterFor("url", "text/xml");
    assertEquals("1/50000", defLimiter.getRate());
    assertSame(defLimiter, crl.getRateLimiterFor("url", "foo/bar"));
    assertSame(defLimiter, crl.getRateLimiterFor("url", null));

    limiter = crl.getRateLimiterFor("url", "image/gif");
    assertEquals("5/1s", limiter.getRate());
    assertSame(limiter, crl.getRateLimiterFor("url", "image/png"));
  }

  public void testUrl() {
    RateLimiterInfo rli = new RateLimiterInfo("key1", 1, 50000);
    Map<String,String> urlPats =
      MapUtil.map("(\\.gif)|(\\.jpeg)|(\\.png)", "5/1s",
			  "(\\.html)|(\\.pdf)", "10/1m");
    rli.setUrlRates(urlPats);
    CrawlRateLimiter crl = new CrawlRateLimiter(rli);
    RateLimiter limiter = crl.getRateLimiterFor("http://foo.bar/x.png",
						"text/html");
    assertEquals("5/1s", limiter.getRate());
    assertSame(limiter, crl.getRateLimiterFor("http://foo.bar/x.jpeg",
					      "text/html"));
    assertSame(limiter, crl.getRateLimiterFor("http://foo.bar/x.gif",
					      "text/html"));
    RateLimiter defLimiter = crl.getRateLimiterFor("http://foo.bar/x.toc",
						   "text/xml");
    assertEquals("1/50000", defLimiter.getRate());
    assertSame(defLimiter, crl.getRateLimiterFor("http://foo.bar/y.toc",
						 "foo/bar"));
    assertSame(defLimiter, crl.getRateLimiterFor("url", null));
  }

  public void testPause() {
    TimeBase.setSimulated(1000);
    RateLimiterInfo rli = new RateLimiterInfo("key1", 1, 50000);
    Map<String,String> urlPats =
      MapUtil.map("(\\.gif)|(\\.jpeg)|(\\.png)", "5/1s",
			  "(\\.html)|(\\.pdf)", "10/1m");
    rli.setUrlRates(urlPats);
    CrawlRateLimiter crl = new MyCrawlRateLimiter(rli);
    MockRateLimiter defLimiter =
      (MockRateLimiter)crl.getRateLimiterFor("random.file", null);
    MockRateLimiter imageLimiter =
      (MockRateLimiter)crl.getRateLimiterFor("foo.png", null);
    MockRateLimiter artLimiter =
      (MockRateLimiter)crl.getRateLimiterFor("bar.pdf", null);
    assertEmpty(defLimiter.eventList);
    crl.pauseBeforeFetch("foo.bar", null);
    assertEquals(ListUtil.list("fifoWaitAndSignalEvent"), defLimiter.eventList);
    assertEmpty(imageLimiter.eventList);
    assertEmpty(artLimiter.eventList);
    crl.pauseBeforeFetch("Mao.jpeg", null);
    assertEquals(ListUtil.list("fifoWaitAndSignalEvent"), defLimiter.eventList);
    assertEquals(ListUtil.list("fifoWaitAndSignalEvent"),
		 imageLimiter.eventList);
    crl.pauseBeforeFetch("bar.foo", null);
    assertEquals(ListUtil.list("fifoWaitAndSignalEvent"),
		 imageLimiter.eventList);
    assertEquals(ListUtil.list("fifoWaitAndSignalEvent",
			       "fifoWaitAndSignalEvent"),
		 defLimiter.eventList);
    crl.pauseBeforeFetch("Lenin.png", null);
    assertEquals(ListUtil.list("fifoWaitAndSignalEvent",
			       "fifoWaitAndSignalEvent"),
		 defLimiter.eventList);
    assertEquals(ListUtil.list("fifoWaitAndSignalEvent",
			       "fifoWaitAndSignalEvent"),
		 imageLimiter.eventList);
    assertEmpty(artLimiter.eventList);
    crl.pauseBeforeFetch("Lenin.html", null);
    assertEquals(ListUtil.list("fifoWaitAndSignalEvent"),
		 artLimiter.eventList);
  }

  static class MyCrawlRateLimiter extends CrawlRateLimiter {
  
    public MyCrawlRateLimiter(ArchivalUnit au) {
      super(au);
    }

    MyCrawlRateLimiter(RateLimiterInfo rli) {
      super(rli);
    }

    @Override
    protected RateLimiter newRateLimiter(String rate) {
      return new MockRateLimiter(rate);
    }

    @Override
    protected RateLimiter newRateLimiter(int events, long interval) {
      return new MockRateLimiter(events, interval);
    }
  }


}
