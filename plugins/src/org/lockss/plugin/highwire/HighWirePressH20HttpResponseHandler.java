/*
 * $Id: HighWirePressH20HttpResponseHandler.java,v 1.7 2015-01-15 03:51:05 etenbrink Exp $
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

package org.lockss.plugin.highwire;

import org.lockss.plugin.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;


public class HighWirePressH20HttpResponseHandler implements CacheResultHandler {
  
  private static final Logger logger = Logger.getLogger(HighWirePressH20HttpResponseHandler.class);

  @Override
  public void init(CacheResultMap crmap) {
    logger.warning("Unexpected call to init()");
    throw new UnsupportedOperationException("Unexpected call to HighWirePressH20HttpResponseHandler.init()");
  }

  @Override
  public CacheException handleResult(ArchivalUnit au,
                                     String url,
                                     int responseCode) {
    logger.debug2(url);
    switch (responseCode) {
      case 500:
        logger.debug2("500");
        if (   url.endsWith("_manifest.dtl")
            || url.endsWith(".toc")
            || (   url.contains("/content/vol")
                && url.contains("/issue")
                && (url.endsWith("/index.dtl") || url.endsWith("/")))) {
          return new CacheException.RetrySameUrlException("500 Internal Server Error");
        }
        
        return new CacheException.NoRetryDeadLinkException("500 Internal Server Error (non-fatal)");
      case 509:
        logger.debug2("509");
        return new CacheException.RetrySameUrlException("509 Bandwidth Limit Exceeded");
      default:
        logger.warning("Unexpected responseCode (" + responseCode + ") in handleResult(): AU " + au.getName() + "; URL " + url);
        throw new UnsupportedOperationException("Unexpected responseCode (" + responseCode + ")");
    }
  }

  @Override
  public CacheException handleResult(ArchivalUnit au,
                                     String url,
                                     Exception ex) {
    logger.warning("Unexpected call to handleResult(): AU " + au.getName() + "; URL " + url, ex);
    throw new UnsupportedOperationException("Unexpected call to handleResult(): AU " + au.getName() + "; URL " + url, ex);
  }
  
}