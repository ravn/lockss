/*
 * $Id: CrawlSpec.java,v 1.8 2003-10-30 23:56:49 troberts Exp $
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

package org.lockss.daemon;
import java.util.*;
import org.lockss.util.*;

/**
 * Specification for a crawl: a list of starting URLs and a rule
 * that determines whether a candidate URL should be included in the crawl.
 */
public final class CrawlSpec {
  private List startList;
  private CrawlRule rule;
  private CrawlWindow window;
  private int refetchDepth = -1;

  /**
   * Create a CrawlSpec with the specified start list and rule.  Defaults to
   * refetchDepth of 1.
   * @param startUrls a list of Strings specifying starting points
   * for the crawl
   * @param rule filter to determine which URLs encountered in the crawl
   * should themselves be crawled.  A null rule is always true.
   * @throws IllegalArgumentException if the url list is empty.
   * @throws NullPointerException if any elements of startUrls is null.
   * @throws ClassCastException if any elements of startUrls is not a String.
   */
  public CrawlSpec(List startUrls, CrawlRule rule) throws ClassCastException {
    int len = startUrls.size();
    if (len == 0) {
      throw new IllegalArgumentException("CrawlSpec starting point list must not be empty");
    }
    startList = ListUtil.immutableListOfType(startUrls, String.class);
    this.rule = rule;
    this.refetchDepth = 1;
  }

  /**
   * Create a CrawlSpec with the specified start list and rule.
   * @param startUrls a list of Strings specifying starting points
   * for the crawl
   * @param rule filter to determine which URLs encountered in the crawl
   * should themselves be crawled.  A null rule is always true.
   * @param refetchDepth depth to always refetch
   * @throws IllegalArgumentException if the url list is empty.
   * @throws NullPointerException if any elements of startUrls is null.
   * @throws ClassCastException if any elements of startUrls is not a String.
   */
  public CrawlSpec(List startUrls, CrawlRule rule, int refetchDepth)
      throws ClassCastException {
    int len = startUrls.size();
    if (len == 0) {
      throw new IllegalArgumentException("CrawlSpec starting point list must not be empty");
    }
    if (refetchDepth < 1) {
      throw new IllegalArgumentException("refetchDepth must be at least 1");
    }
    startList = ListUtil.immutableListOfType(startUrls, String.class);
    this.rule = rule;
    this.refetchDepth = refetchDepth;
  }

  /**
   * Create a CrawlSpec with the specified single start url and rule.
   * Defaults to recrawl depth of 1.
   * @param url specifies the starting point for the crawl
   * @param rule filter to determine which URLs encountered in the crawl
   * should themselves be crawled.  A null rule is always true.
   * @throws NullPointerException if the url is null.
   */
  public CrawlSpec(String url, CrawlRule rule) {
    this(ListUtil.list(url), rule);
  }

  /**
   * Create a CrawlSpec with the specified single start url and rule.
   * @param url specifies the starting point for the crawl
   * @param rule filter to determine which URLs encountered in the crawl
   * should themselves be crawled.  A null rule is always true.
   * @param refetchDepth depth to always refetch
   * @throws NullPointerException if the url is null.
   */
  public CrawlSpec(String url, CrawlRule rule, int refetchDepth) {
    this(ListUtil.list(url), rule, refetchDepth);
  }


  /**
   * Returns the CrawlWindow, or null.
   * @return the {@link CrawlWindow}
   */
  public CrawlWindow getCrawlWindow() {
    return window;
  }

  /**
   * Sets the CrawlWindow (null for none) to determine when crawling is
   * permitted.  A null window always allows.
   * @param window the {@link CrawlWindow}
   */
  public void setCrawlWindow(CrawlWindow window) {
    this.window = window;
  }

  /**
   * Get the starting point list.
   * @return an immutable list of URLs, as Strings
   */
  public List getStartingUrls() {
    return startList;
  }

  /**
   * Determine whether a url is part of this CrawlSpec.
   * @param url The url to test
   * @return true iff the url matches the rule
   * @throws NullPointerException if the url is null.
   */
  public boolean isIncluded(String url) {
    return (rule == null) ? true : (rule.match(url) == CrawlRule.INCLUDE);
  }

  public boolean canCrawl() {
    return (window==null) ? true : window.canCrawl();
  }

  /**
   * @return depth to recrawl when doing a new content crawl.
   * 1 means just the starting urls, 2 is all of them and everything
   * they link directly to, etc.
   */
  public int getRefetchDepth() {
    return refetchDepth;
  }


}
