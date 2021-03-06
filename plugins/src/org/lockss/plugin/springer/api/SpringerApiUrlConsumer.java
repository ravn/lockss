/*
 * $Id$
 */

/*

Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.springer.api;

import java.io.IOException;
import java.util.regex.Pattern;

import org.lockss.daemon.*;
import org.lockss.daemon.Crawler.CrawlerFacade;
import org.lockss.plugin.*;
import org.lockss.plugin.base.SimpleUrlConsumer;

/**
 * <p>
 * A custom URL consumer that identifies specific redirect chains and stores the
 * content at the origin of the chain (e.g. to support collecting and repairing
 * redirect chains that begin with fixed URLs but end with one-time URLs).
 * </p>
 * 
 * @since 1.67.5
 */
public class SpringerApiUrlConsumer extends SimpleUrlConsumer {

  // Will become a definitional param
  public static final String CDN_URL = "http://download.springer.com/";

  protected Pattern origPdfPat;
  
  protected Pattern destPdfPat;
  
  public SpringerApiUrlConsumer(CrawlerFacade facade,
                                FetchedUrlData fud) {
    super(facade, fud);
    origPdfPat = makeOrigPdfPattern(au.getConfiguration().get(ConfigParamDescr.BASE_URL.getKey()));
    destPdfPat = makeDestPdfPattern(CDN_URL);
  }

  @Override
  public void consume() throws IOException {
    if (shouldStoreAtOrigUrl()) {
      // FIXME 1.68: call storeAtOrigUrl() instead of these 4 lines 
      fud.redirectUrls = null;
      fud.fetchUrl = null;
      fud.headers.remove(CachedUrl.PROPERTY_REDIRECTED_TO);
      fud.headers.put(CachedUrl.PROPERTY_CONTENT_URL, fud.origUrl);
    }
    super.consume();
  }

  /**
   * <p>
   * Determines if a particular redirect chain should cause content to be stored
   * only at the origin URL ({@link FetchedUrlData#origUrl}).
   * </p>
   * 
   * @return True if and only if the fetched URL data represents a particular
   *         redirect chain that should cause content to be stored only at the
   *         origin URL.
   * @since 1.67.5
   */
  protected boolean shouldStoreAtOrigUrl() {
    return fud.redirectUrls != null
        && fud.redirectUrls.size() == 1
        && fud.redirectUrls.get(0).equals(fud.fetchUrl)
        && destPdfPat.matcher(fud.fetchUrl).find()
        && origPdfPat.matcher(fud.origUrl).find();
  }
  
  /**
   * 
   * @param baseUrl
   * @return
   * @since 1.67.5
   */
  protected static Pattern makeOrigPdfPattern(String baseUrl) {
    return Pattern.compile(String.format("^%scontent/pdf/.*\\.pdf$",
                                         baseUrl),
                           Pattern.CASE_INSENSITIVE);
  }

  /**
   * 
   * @param cdnUrl
   * @return
   * @since 1.67.5
   */
  protected static Pattern makeDestPdfPattern(String cdnUrl) {
    return Pattern.compile(String.format("^%sstatic/pdf/.*\\.pdf\\?auth[^=]*=[^&]*(&ext=\\.pdf)?$",
                                         cdnUrl),
                           Pattern.CASE_INSENSITIVE);
  }
  
}
