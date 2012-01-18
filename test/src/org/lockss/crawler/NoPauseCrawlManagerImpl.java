/*
 * $Id: NoPauseCrawlManagerImpl.java,v 1.2 2012-01-18 03:40:42 tlipkis Exp $
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

package org.lockss.crawler;

import java.util.*;
import java.io.*;
import java.text.*;
import java.util.regex.*;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;

/**
 * For testing, uses a CrawlRateLimiter that records args and doesn't pause
 */
public class NoPauseCrawlManagerImpl extends CrawlManagerImpl {
  static Logger log = Logger.getLogger("NoPauseCrawlManagerImpl");

  private Map<ArchivalUnit,CrawlRateLimiter> limiterMap =
    new HashMap<ArchivalUnit,CrawlRateLimiter>();

  protected CrawlRateLimiter newCrawlRateLimiter(ArchivalUnit au) {
    CrawlRateLimiter crl = new NoPauseCrawlRateLimiter(au);
    limiterMap.put(au, crl);
    return crl;
  }

  public CrawlRateLimiter getCrawlRateLimiter(ArchivalUnit au) {
    CrawlRateLimiter crl = limiterMap.get(au);
    if (crl == null) {
      crl = super.getCrawlRateLimiter(au);
    }
    return crl;
  }

  public List getPauseContentTypes(ArchivalUnit au) {
    return ((NoPauseCrawlRateLimiter)getCrawlRateLimiter(au)).getPauseContentTypes();
  }


  public static class NoPauseCrawlRateLimiter extends CrawlRateLimiter {
    List pauseContentTypes = new ArrayList();

    public NoPauseCrawlRateLimiter(ArchivalUnit au) {
      super(au);
    }

    @Override
    public void pauseBeforeFetch(String url, String previousContentType) {
      log.debug3("NoPausing: " + url + ", prev: " + previousContentType);
      pauseCounter++;
      pauseContentTypes.add(previousContentType);
    }

    public List getPauseContentTypes() {
      return pauseContentTypes;
    }
  }
}