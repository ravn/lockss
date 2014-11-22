/*
 * $Id: BaseUrlFetcher.java,v 1.3 2014-11-22 08:41:50 tlipkis Exp $
 */

/*

Copyright (c) 2000-2014 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.base;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.lockss.app.LockssDaemon;
import org.lockss.config.Configuration;
import org.lockss.config.CurrentConfig;
import org.lockss.crawler.CrawlRateLimiter;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.daemon.Crawler;
import org.lockss.daemon.LockssWatchdog;
import org.lockss.daemon.LoginPageChecker;
import org.lockss.daemon.PluginBehaviorException;
import org.lockss.daemon.PluginException;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.AuUtil;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.FetchedUrlData;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.UrlCacher;
import org.lockss.plugin.UrlConsumerFactory;
import org.lockss.plugin.UrlFetcher;
import org.lockss.util.CIProperties;
import org.lockss.util.Deadline;
import org.lockss.util.IOUtil;
import org.lockss.util.IPAddr;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;
import org.lockss.util.TimeBase;
import org.lockss.util.UrlUtil;
import org.lockss.util.urlconn.CacheException;
import org.lockss.util.urlconn.CacheResultMap;
import org.lockss.util.urlconn.LockssUrlConnection;
import org.lockss.util.urlconn.LockssUrlConnectionPool;

/**
 * Non abstract base class that holds all the basic logic for fetching
 * urls and some http specific logic
 */
public class BaseUrlFetcher implements UrlFetcher {
  protected static Logger logger = Logger.getLogger("BaseUrlFetcher");
  
  /** Limit on rewinding the network input stream after checking for a
   * login page.  If LoginPageChecker returns false after reading father
   * than this the page will be refetched. */
  public static final String PARAM_LOGIN_CHECKER_MARK_LIMIT =
    Configuration.PREFIX + "baseuc.loginPageCheckerMarkLimit";
  public static final int DEFAULT_LOGIN_CHECKER_MARK_LIMIT = 24 * 1024;
  
  /** Maximum number of redirects that will be followed */
  static final int MAX_REDIRECTS = 10;
  
  /** If true, normalize redirect targets (location header). */
  public static final String PARAM_NORMALIZE_REDIRECT_URL =
    Configuration.PREFIX + "baseuc.normalizeRedirectUrl";
  public static final boolean DEFAULT_NORMALIZE_REDIRECT_URL = true;
  
  public static final String SET_COOKIE_HEADER = "Set-Cookie";
  private static final String SHOULD_REFETCH_ON_SET_COOKIE =
      "refetch_on_set_cookie";
  private static final boolean DEFAULT_SHOULD_REFETCH_ON_SET_COOKIE = true;
  
  /** If true, any thread watchdog will be stopped while waiting on a rate
   * limiter. */
  public static final String PARAM_STOP_WATCHDOG_DURING_PAUSE =
    Configuration.PREFIX + "baseuc.stopWatchdogDuringPause";
  public static final boolean DEFAULT_STOP_WATCHDOG_DURING_PAUSE = false;

  
  protected final String origUrl;		// URL with which I was created
  protected String fetchUrl;		// possibly affected by redirects
  protected RedirectScheme redirectScheme = REDIRECT_SCHEME_FOLLOW;
  protected LockssUrlConnectionPool connectionPool;
  protected LockssUrlConnection conn;
  protected String proxyHost = null;
  protected int proxyPort;
  protected IPAddr localAddr = null;
  protected BitSet fetchFlags;
  protected CIProperties uncachedProperties;
  protected List<String> redirectUrls;
  protected ArchivalUnit au;
  protected Properties reqProps;
  protected final CacheResultMap resultMap;
  protected CrawlRateLimiter crl;
  protected String previousContentType;
  protected UrlConsumerFactory urlConsumerFactory;
  protected CrawlerStatus crawlStatus;
  protected Crawler.CrawlerFacade crawlFacade;
  protected LockssWatchdog wdog;
  
  public BaseUrlFetcher(Crawler.CrawlerFacade crawlFacade, String url) {
		this.origUrl = url;
		this.fetchUrl = url;
		this.crawlFacade = crawlFacade;
		this.au = crawlFacade.getAu();
		Plugin plugin = au.getPlugin();
		resultMap = plugin.getCacheResultMap();
		crawlStatus = crawlFacade.getCrawlerStatus();
		fetchFlags = new BitSet();
  }
  
  public void setUrlConsumerFactory(UrlConsumerFactory cunsumer){
    urlConsumerFactory = cunsumer;
  }
  
  protected UrlConsumerFactory getUrlConsumerFactory() {
    if(urlConsumerFactory == null) {
      urlConsumerFactory = au.getUrlConsumerFactory();
    }
    return urlConsumerFactory;
  }
  
  public FetchResult fetch() throws CacheException {
    String lastModified = null;
    try{
      if(!forceRefetch()) {
        lastModified = getLastModified();
      }
      return fetchWithRetries(lastModified);
    } catch (CacheException.RepositoryException ex) {
      // Failed.  Don't try this one again during this crawl.
      crawlFacade.addToFailedUrls(origUrl);
      logger.error("Repository error with "+this, ex);
      crawlStatus.signalErrorForUrl(origUrl,
            "Can't store page: " + ex.getMessage(),
            Crawler.STATUS_REPO_ERR);
    } catch (CacheException.RedirectOutsideCrawlSpecException ex) {
      // Count this as an excluded URL
      crawlStatus.signalUrlExcluded(origUrl, ex.getMessage());
    } catch (CacheException ex) {
      // Failed.  Don't try this one again during this crawl.
      crawlStatus.signalErrorForUrl(origUrl, ex);
      crawlFacade.addToFailedUrls(origUrl);
      if (ex.isAttributeSet(CacheException.ATTRIBUTE_FAIL)) {
        logger.siteError("Problem caching "+this+". Continuing", ex);
        crawlStatus.signalErrorForUrl(origUrl, ex.getMessage(),
            Crawler.STATUS_FETCH_ERROR);
      } else {
        crawlStatus.signalErrorForUrl(origUrl, ex.getMessage());
      }
      if (ex.isAttributeSet(CacheException.ATTRIBUTE_FATAL)) {
        throw ex;
      }
    } catch (Exception ex) {
      if (crawlFacade.isAborted()) {
        logger.debug("Expected exception while aborting crawl: " + ex);
      } else {
        crawlFacade.addToFailedUrls(origUrl);
        crawlStatus.signalErrorForUrl(origUrl, ex.toString(),
            Crawler.STATUS_FETCH_ERROR);
        //XXX not expected
        logger.error("Unexpected Exception during crawl, continuing", ex);
      }
    } 
    return FetchResult.NOT_FETCHED;
  }
  
  protected FetchResult fetchWithRetries(String lastModified)
      throws IOException {
    int retriesLeft = -1;
    int totalRetries = -1;
    InputStream input = null;
    CIProperties headers = null;
    logger.debug2("Fetching " + origUrl);
    while (true) {
      try {
        if (wdog != null) {
          wdog.pokeWDog();
        }
        input = getUncachedInputStream(lastModified);
        headers = getUncachedProperties();
        if(input == null){
          //If input is null then ifModifiedSince returned not modified
          return FetchResult.FETCHED_NOT_MODIFIED;
        } else if (headers == null) {
          return FetchResult.NOT_FETCHED;
        } else {
          FetchedUrlData fud = new FetchedUrlData(origUrl, fetchUrl, input, headers,
              redirectUrls, this);
          fud.setStoreRedirects(redirectScheme.isRedirectOption(
              RedirectScheme.REDIRECT_OPTION_STORE_ALL));
          fud.setFetchFlags(fetchFlags);
          getUrlConsumerFactory().createUrlConsumer(crawlFacade, fud).consume();
          return FetchResult.FETCHED;
        }
      } catch (CacheException e) {
        if (!e.isAttributeSet(CacheException.ATTRIBUTE_RETRY)) {
          throw e;
        }
        if (retriesLeft < 0) {
          retriesLeft = crawlFacade.getRetryCount(e);
          totalRetries = retriesLeft;
        }
        if (logger.isDebug2()) {
          logger.debug("Retryable (" + retriesLeft + ") exception caching "
              + origUrl, e);
        } else {
          logger.debug("Retryable (" + retriesLeft + ") exception caching "
              + origUrl + ": " + e.toString());
        }
        if (--retriesLeft > 0) {
          long delayTime = crawlFacade.getRetryDelay(e);
          Deadline wait = Deadline.in(delayTime);
          logger.debug3("Waiting " +
              StringUtil.timeIntervalToString(delayTime) +
              " before retry");
          while (!wait.expired()) {
            try {
              wait.sleep();
            } catch (InterruptedException ie) {
              // no action
            }
          }
          reset();
        } else {
          logger.warning("Failed to cache (" + totalRetries + "), skipping: "
              + origUrl);
          throw e;
        }
      } finally {
        IOUtil.safeClose(input);
      }
    }
  }
  
  protected String getLastModified(){
    String lastModified = null;
    CachedUrl cachedVersion = au.makeCachedUrl(origUrl);
    if ((cachedVersion!=null) && cachedVersion.hasContent()) {
      CIProperties cachedProps = cachedVersion.getProperties();
      lastModified =
          cachedProps.getProperty(CachedUrl.PROPERTY_LAST_MODIFIED);
      cachedVersion.release();
    }
    return lastModified;
  }
  
  protected boolean forceRefetch(){
    return fetchFlags.get(UrlCacher.REFETCH_FLAG);
  }
  
  public void setConnectionPool(LockssUrlConnectionPool connectionPool) {
    this.connectionPool = connectionPool;
  }
  
  public void setRedirectScheme(RedirectScheme scheme) {
    if (logger.isDebug3()) logger.debug3("setRedirectScheme: " + scheme);
    this.redirectScheme = scheme;
  }
  
  public void setProxy(String proxyHost, int proxyPort) {
    this.proxyHost = proxyHost;
    this.proxyPort = proxyPort;
  }

  public void setLocalAddress(IPAddr localAddr) {
    this.localAddr = localAddr;
  }

  public void setFetchFlags(BitSet fetchFlags) {
    this.fetchFlags = fetchFlags;
  }

  public BitSet getFetchFlags() {
    return fetchFlags;
  }

  public String getUrl() {
    return origUrl;
  }
  
  public ArchivalUnit getArchivalUnit() {
    return au;
  }
  
  public void setCrawlRateLimiter(CrawlRateLimiter crl) {
    this.crl = crl;
  }
  
  public void setRequestProperty(String key, String value) {
    if (reqProps == null) {
      reqProps = new Properties();
    }
    reqProps.put(key, value);
  }
  
  public void setPreviousContentType(String previousContentType) {
    this.previousContentType = previousContentType;
  }
  
  public final InputStream getUncachedInputStream()
      throws IOException {
    String lastModified = null;
    if(!forceRefetch()) {
      lastModified = getLastModified();
    }
    return getUncachedInputStream(lastModified);
  }
  /**
   * Gets an InputStream for this URL, using the last modified time as
   * 'if-modified-since'.  If a 304 is generated (not modified), it returns
   * null.
   * @return the InputStream, or null
   * @throws IOException
   */
  protected final InputStream getUncachedInputStream(String lastModified)
      throws IOException {
    InputStream input = getUncachedInputStreamOnly(lastModified);
    CIProperties headers = getUncachedProperties();
    if (headers.get(SET_COOKIE_HEADER) != null) {
      if (au.shouldRefetchOnCookies()) {
        logger.debug3("Found set-cookie header, refetching");
        IOUtil.safeClose(input);
        input = null; // ensure don't reclose in finally if next line throws
        releaseConnection();
        input = getUncachedInputStreamOnly(lastModified);
        if (input == null) {
          logger.warning("Got null input stream on second call to "
              + "getUncachedInputStream");
        }
        headers = getUncachedProperties();
      }
    }
    if (input != null) {
      // Check for login page if got new content
      input = checkLoginPage(input, headers, lastModified);
    }
    return input;
  }
  
  protected InputStream getUncachedInputStreamOnly(String lastModified)
	      throws IOException {
    InputStream input = null;
    try {
      openConnection(lastModified);
      if (conn.isHttp()) {
        // http connection; check response code
        int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
          logger.debug2("Unmodified content not cached for url '" +
              origUrl + "'");
          return null;
        }
      }
      input = conn.getUncompressedResponseInputStream();
      if (input == null) {
        logger.warning("Got null input stream back from conn.getResponseInputStream");
      }
    } finally {
      if (conn != null && input == null) {
        logger.debug3("Releasing connection");
        IOUtil.safeRelease(conn);
      }
    }
    return input;
  }
  
  /**
   * If we haven't already connected, creates a connection from url, setting
   * the user-agent and ifmodifiedsince values.  Then actually connects to the
   * site and throws if we get an error code
   */
  protected void openConnection(String lastModified) throws IOException {
    if (conn==null) {
      if (redirectScheme.isRedirectOption(
          RedirectScheme.REDIRECT_OPTION_IF_CRAWL_SPEC +
          RedirectScheme.REDIRECT_OPTION_ON_HOST_ONLY)) {
        openWithRedirects(lastModified);
      } else {
        openOneConnection(lastModified);
      }
    }
  }
  
  protected void openWithRedirects(String lastModified) throws IOException {
    int retry = 0;
    while (true) {
      try {
        openOneConnection(lastModified);
        break;
      } catch (CacheException.NoRetryNewUrlException e) {
        if (++retry >= MAX_REDIRECTS) {
          logger.warning("Max redirects hit, not redirecting " + origUrl +
              " past " + fetchUrl);
          throw e;
        } else if (!processRedirectResponse()) {
          throw e;
        }
      }
    }
  }
  
  /**
   * Create a connection object from url, set the user-agent and
   * ifmodifiedsince values.  Then actually connect to the site and throw
   * if we get an error response
   */
  protected void openOneConnection(String lastModified) throws IOException {
    if (conn != null) {
      throw new IllegalStateException(
    		  "Must call reset() before reusing UrlCacher");
    }
    try {
      conn = makeConnection(fetchUrl, connectionPool);
      if (proxyHost != null) {
        if (logger.isDebug3()) logger.debug3("Proxying through " + proxyHost
					     + ":" + proxyPort);
        conn.setProxy(proxyHost, proxyPort);
      }
      if (localAddr != null) {
        conn.setLocalAddress(localAddr);
      }
      for (String cookie : au.getHttpCookies()) {
        int pos = cookie.indexOf("=");
        if (pos > 0) {
          conn.addCookie(cookie.substring(0, pos), cookie.substring(pos + 1));
        } else {
          logger.error("Illegal cookie: " + cookie);
        }
      }
      String userPass = getUserPass();
      if (userPass != null) {
        List<String> lst = StringUtil.breakAt(userPass, ':');
        if (lst.size() == 2) {
          conn.setCredentials(lst.get(0), lst.get(1));
        }
      }
      addPluginRequestHeaders();
      if (reqProps != null) {
        for (Iterator iter = reqProps.keySet().iterator(); iter.hasNext(); ) {
          String key = (String)iter.next();
          conn.setRequestProperty(key, reqProps.getProperty(key));
        }
      }
      conn.setFollowRedirects(redirectScheme.isRedirectOption(
          RedirectScheme.REDIRECT_OPTION_FOLLOW_AUTO));
      conn.setRequestProperty("user-agent", LockssDaemon.getUserAgent());

      if (lastModified != null) {
        conn.setIfModifiedSince(lastModified);
      }
      pauseBeforeFetch();
      conn.execute();
    } catch (MalformedURLException ex) {
      logger.debug2("openConnection", ex);
      throw resultMap.getMalformedURLException(ex);
    } catch (IOException ex) {
      logger.debug2("openConnection", ex);
      throw resultMap.mapException(au, conn, ex, null);
    } catch (RuntimeException e) {
      logger.warning("openConnection: unexpected exception", e);
      throw e;
    }
    checkConnectException(conn);
  }
  
  protected InputStream checkLoginPage(InputStream input, Properties headers,
		     String lastModified)
		    		 throws IOException {
    LoginPageChecker checker = au.getLoginPageChecker();
    if (checker != null) {
      logger.debug3("Found a login page checker");
      if (!input.markSupported()) {
        input = new BufferedInputStream(input);
      }
      input.mark(CurrentConfig.getIntParam(PARAM_LOGIN_CHECKER_MARK_LIMIT,
			   DEFAULT_LOGIN_CHECKER_MARK_LIMIT));
      Reader reader = new InputStreamReader(input,
			    AuUtil.getCharsetOrDefault(uncachedProperties));
      try {
        if (checker.isLoginPage(headers, reader)) {
          throw new CacheException.PermissionException("Found a login page");
        } else {
          input = resetInputStream(input, lastModified);
        }
      } catch (PluginException e) {
        //XXX: this should be changed so that plugin exception perpetuates
        throw new RuntimeException(e);
      }	
    } else {
      logger.debug3("Didn't find a login page checker");
    }
    return input;
  }
  
  /**
   * Try to reset the provided input stream, if we can't then return
   * new input stream for the given url
   */
  public InputStream resetInputStream(InputStream is, 
      String lastModified) throws IOException {
    try {
      if (wdog != null) {
        wdog.pokeWDog();
      }
      is.reset();
    } catch (IOException e) {
      logger.debug("Couldn't reset input stream, so getting new one", e);
      is.close();
      releaseConnection();
      is = new BufferedInputStream(getUncachedInputStreamOnly(lastModified));
    }
    return is;
  }

  protected LockssUrlConnection makeConnection(String url,
					       LockssUrlConnectionPool pool)
      throws IOException {
    LockssUrlConnection res = makeConnection0(url, pool);
    String cookiePolicy = au.getCookiePolicy();
    if (cookiePolicy != null) {
      res.setCookiePolicy(cookiePolicy);
    }
    return res;
  }

  /** Overridable so testing code can return a MockLockssUrlConnection */
  protected LockssUrlConnection makeConnection0(String url,
						LockssUrlConnectionPool pool)
      throws IOException {
    return UrlUtil.openConnection(url, pool);
  }
  
  protected String getUserPass() {
    Configuration auConfig = au.getConfiguration();
    if (auConfig != null) {		// can be null in unit tests
      return auConfig.get(ConfigParamDescr.USER_CREDENTIALS.getKey());
	}
	return null;
  }
  
  protected void pauseBeforeFetch() {
    if (crl != null) {
      long wDogInterval = 0;
      if (wdog != null &&
    CurrentConfig.getBooleanParam(PARAM_STOP_WATCHDOG_DURING_PAUSE,
          DEFAULT_STOP_WATCHDOG_DURING_PAUSE)) {
  wDogInterval = wdog.getWDogInterval();
      }
      try {
  if (wDogInterval > 0) {
    wdog.stopWDog();
  }
  crl.pauseBeforeFetch(fetchUrl, previousContentType);
      } finally {
  if (wDogInterval > 0) {
    wdog.startWDog(wDogInterval);
  }
      }
    }
  }
  
  protected void checkConnectException(LockssUrlConnection conn) throws IOException {
    if(conn.isHttp()) {
      if (logger.isDebug3()) {
        logger.debug3("Response: " + conn.getResponseCode() + ": " +
            conn.getResponseMessage());
      }
      CacheException c_ex = resultMap.checkResult(au, conn);
      if(c_ex != null) {
        // The stack below here is misleading.  Makes more sense for it
        // to reflect the point at which it's thrown
        c_ex.fillInStackTrace();
        throw c_ex;
      }
    }
  }
  
  protected void addPluginRequestHeaders() {
    for (String hdr : au.getHttpRequestHeaders()) {
	  int pos = hdr.indexOf(":");
	  if (pos > 0 && pos < hdr.length() - 1) {
		setRequestProperty(hdr.substring(0, pos), hdr.substring(pos + 1));
	  }
	}
  }
  
  /** Handle a single redirect response: determine whether it should be
   * followed and change the state (fetchUrl) to set up for the next fetch.
   * @return true if another request should be issued, false if not. */
  protected boolean processRedirectResponse() throws CacheException {
    //get the location header to find out where to redirect to
    String location = conn.getResponseHeaderValue("location");
    if (location == null) {
      // got a redirect response, but no location header
      logger.siteError("Received redirect response " + conn.getResponseCode()
		       + " but no location header");
      return false;
    }
    if (logger.isDebug3()) {
      logger.debug3("Redirect requested from '" + fetchUrl +
		    "' to '" + location + "'");
    }
    // update the current location with the redirect location.
    try {
      String newUrlString = UrlUtil.resolveUri(fetchUrl, location);
      if (CurrentConfig.getBooleanParam(PARAM_NORMALIZE_REDIRECT_URL,
					DEFAULT_NORMALIZE_REDIRECT_URL)) {
        try {
          newUrlString = UrlUtil.normalizeUrl(newUrlString, au);
          logger.debug3("Normalized to '" + newUrlString + "'");
        } catch (PluginBehaviorException e) {
          logger.warning("Couldn't normalize redirect URL: " + newUrlString, e);
        }
      }
      // Check redirect to login page *before* crawl spec, else plugins
      // would have to include login page URLs in crawl spec
      if (au.isLoginPageUrl(newUrlString)) {
        String msg = "Redirected to login page: " + newUrlString;
        throw new CacheException.PermissionException(msg);
      }
      if (redirectScheme.isRedirectOption(
          RedirectScheme.REDIRECT_OPTION_IF_CRAWL_SPEC)) {
        if (!au.shouldBeCached(newUrlString)) {
          String msg = "Redirected to excluded URL: " + newUrlString;
          logger.warning(msg + " redirected from: " + origUrl);
          throw new CacheException.RedirectOutsideCrawlSpecException(msg);
        }
      }

      if(!UrlUtil.isSameHost(fetchUrl, newUrlString)) {
        if (redirectScheme.isRedirectOption(
            RedirectScheme.REDIRECT_OPTION_ON_HOST_ONLY)) {
          logger.warning("Redirect to different host: " + newUrlString +
              " from: " + origUrl);
          return false;
        } else if(!crawlFacade.hasPermission(newUrlString)) {
          logger.warning("No permission for redirect to different host: "
                         + newUrlString + " from: " + origUrl);
          return false;
        }
      }
      releaseConnection();

      // XXX
      // The names .../foo and .../foo/ map to the same repository node, so
      // the case of a slash-appending redirect requires special handling.
      // (Still. sigh.)  The node should be written only once, so don't add
      // another entry for the slash redirection.

      if (!UrlUtil.isDirectoryRedirection(fetchUrl, newUrlString)) {
        if (redirectUrls == null) {
          redirectUrls = new ArrayList();
        }
        redirectUrls.add(newUrlString);
      }
      fetchUrl = newUrlString;
      logger.debug2("Following redirect to " + newUrlString);
      return true;
    } catch (MalformedURLException e) {
      logger.siteWarning("Redirected location '" + location +
          "' is malformed", e);
      return false;
    }
  }
  
  public CIProperties getUncachedProperties()
	      throws UnsupportedOperationException {
	  if (conn == null) {
	    throw new UnsupportedOperationException("Called getUncachedProperties "
	        + "before calling getUncachedInputStream.");
	  }
	  if (uncachedProperties == null) {
	    CIProperties props = new CIProperties();
	    // set header properties in which we have interest
	    String ctype = conn.getResponseContentType();
	    if (ctype != null) {
	      props.setProperty(CachedUrl.PROPERTY_CONTENT_TYPE, ctype);
	    }
	    props.setProperty(CachedUrl.PROPERTY_FETCH_TIME,
	        Long.toString(TimeBase.nowMs()));
	    if (origUrl != fetchUrl &&
	        !UrlUtil.isDirectoryRedirection(origUrl, fetchUrl)) {
	      // XXX this property does not have consistent semantics.  It will be
	      // set to the first url in a chain of redirects that led to content,
	      // which could be different depending on fetch order.
	      props.setProperty(CachedUrl.PROPERTY_ORIG_URL, origUrl);
	    }
	    conn.storeResponseHeaderInto(props, CachedUrl.HEADER_PREFIX);
	    String actualURL = conn.getActualUrl();
	    if (!origUrl.equals(actualURL)) {
	      props.setProperty(CachedUrl.PROPERTY_CONTENT_URL, actualURL);
	    }
	    if (redirectUrls != null && !redirectUrls.isEmpty()) {
	      props.setProperty(CachedUrl.PROPERTY_REDIRECTED_TO,
				redirectUrls.get(0));
	    } else if (!origUrl.equals(actualURL)) {
	      props.setProperty(CachedUrl.PROPERTY_REDIRECTED_TO, actualURL);
	    }
	    uncachedProperties = props;
	  }
	  return uncachedProperties;
	}
  
  protected void releaseConnection() {
    if (conn != null) {
      logger.debug3("conn isn't null, releasing");
      conn.release();
      conn = null;
    }
  }
  
  /**
   * Reset the UrlFetcher to its pre-opened state, so that it can be
   * reopened.
   */
  public void reset() {
    releaseConnection();
    fetchUrl = origUrl;
    redirectUrls = null;
    uncachedProperties = null;
  }

  @Override
  public void setWatchdog(LockssWatchdog wdog) {
    this.wdog = wdog;
  }
  
  public LockssWatchdog getWatchdog() {
    return wdog;
  }
 
}
