/*
 * $Id: UrlCacher.java,v 1.16 2004-09-23 03:38:04 tlipkis Exp $
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

package org.lockss.plugin;
import java.io.*;
import java.util.Properties;

import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.crawler.*;

/**
 * UrlCacher is used to store the contents and
 * meta-information of a single url being cached.  It is implemented by the
 * plug-in, which provides a static method taking a String url and
 * returning an object implementing the UrlCacher interface.
 */
public interface UrlCacher {

  /** Follow redirects */
  public static final int REDIRECT_OPTION_FOLLOW_AUTO = 1;
  /** Follow redirects only if match crawl spec */
  public static final int REDIRECT_OPTION_IF_CRAWL_SPEC = 2;
  /** Store content under all redirected names */
  public static final int REDIRECT_OPTION_STORE_ALL = 4;
  /** Follow redirects only within same host */
  public static final int REDIRECT_OPTION_ON_HOST_ONLY = 8;

  /** Don't follow redirects; throw CacheException.RetryNewUrlException if
   * redirect response received */
  public static final RedirectScheme REDIRECT_SCHEME_DONT_FOLLOW =
    new RedirectScheme(0);
  /** Automatically follow all redirects */
  public static final RedirectScheme REDIRECT_SCHEME_FOLLOW =
    new RedirectScheme(REDIRECT_OPTION_FOLLOW_AUTO);
  /** Follow redirects only in crawl spec */
  public static final RedirectScheme REDIRECT_SCHEME_FOLLOW_IN_SPEC =
    new RedirectScheme(REDIRECT_OPTION_IF_CRAWL_SPEC);
  /** Follow redirects only on same host */
  public static final RedirectScheme REDIRECT_SCHEME_FOLLOW_ON_HOST =
    new RedirectScheme(REDIRECT_OPTION_ON_HOST_ONLY);
  /** Follow redirects only in crawl spec and on host */
  public static final RedirectScheme REDIRECT_SCHEME_FOLLOW_IN_SPEC_ON_HOST =
    new RedirectScheme(REDIRECT_OPTION_IF_CRAWL_SPEC +
		       REDIRECT_OPTION_ON_HOST_ONLY);
  /** Follow redirects iff within the crawl spec, store under all names */
  public static final RedirectScheme REDIRECT_SCHEME_STORE_ALL_IN_SPEC =
    new RedirectScheme(REDIRECT_OPTION_IF_CRAWL_SPEC +
		       REDIRECT_OPTION_STORE_ALL);

  // Return codes from cache()
  /** 304 not modified */
  public static final int CACHE_RESULT_NOT_MODIFIED = 1;
  /** fetched */
  public static final int CACHE_RESULT_FETCHED = 2;

  /**
   * Return the url being represented
   * @return the {@link String} url being represented.
   */
  public String getUrl();

  /**
   * Return the {@link CachedUrlSet} to which this UrlCacher belongs.
   * @return the parent set
   */
  public CachedUrlSet getCachedUrlSet();

  /**
   * Return <code>true</code> if the underlying url is one that
   * the plug-in believes should be preserved.
   * @return <code>true</code> if the underlying url is one that
   *         the plug-in believes should be preserved.
   */
  public boolean shouldBeCached();

  /**
   * Return a {@link CachedUrl} for the content stored.  May be
   * called only after the content is completely written.
   * @return {@link CachedUrl} for the content stored.
   */
  public CachedUrl getCachedUrl();

  /** Set the shared connection pool object to be used by this UrlCacher */
  public void setConnectionPool(LockssUrlConnectionPool connectionPool);

  /** Set the host and port the UrlCache should proxy through */
  public void setProxy(String proxyHost, int proxyPort);

  /** Determines whether content will be refetched even if already present
   * and up-to-date.  The default behavior is to not refetch if not
   * necessary (by sending an If-Modified-Since header with the date of the
   * content currently on disk, if any).
   * @param force if true, fetches the URL unconditionally.
   */
  public void setForceRefetch(boolean force);

  /** Set a request header, overwriting any previous value */
  public void setRequestProperty(String key, String value);

  /** Determines the behavior if a redirect response is received. */
  public void setRedirectScheme(RedirectScheme scheme);

  /**
   * Copies the content and properties from the source into the cache.
   * If forceRefetch is false, only caches if the content has been modified.
   * @return CACHE_RESULT_FETCHED if the content was fetched and stored,
   * CACHE_RESULT_NOT_MODIFIED if the server reported the contents as
   * unmodified.
   * @throws java.io.IOException on many possible I/O problems.
   */
  public int cache() throws IOException;

  /**
   * Gets an InputStream for this URL.
   * @return the InputStream
   * @throws IOException
   */
  public InputStream getUncachedInputStream() throws IOException;

  /**
   * Gets the Properties for this URL, if any.
   * @return the {@link CIProperties}
   * @throws IOException
   */
  public CIProperties getUncachedProperties() throws IOException;


  public void storeContent(InputStream input, CIProperties headers)
      throws IOException;

  public void setPermissionMap(PermissionMap permissionMap);

  public static class RedirectScheme {
    private int options = 0;
    private RedirectScheme(int options) {
      this.options = options;
    }
    public int getOptions() {
      return options;
    }
  }
}
