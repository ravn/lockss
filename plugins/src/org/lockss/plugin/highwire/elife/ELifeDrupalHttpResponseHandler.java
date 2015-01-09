/*
 * $Id: ELifeDrupalHttpResponseHandler.java,v 1.3 2015-01-09 00:13:58 etenbrink Exp $
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

package org.lockss.plugin.highwire.elife;

import org.lockss.plugin.*;
import org.lockss.plugin.highwire.HighWireDrupalHttpResponseHandler;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;


public class ELifeDrupalHttpResponseHandler extends HighWireDrupalHttpResponseHandler {
  
  protected static Logger logger = Logger.getLogger(ELifeDrupalHttpResponseHandler.class);
  
  @Override
  public void init(CacheResultMap crmap) {
    logger.warning("Unexpected call to init()");
    throw new UnsupportedOperationException("Unexpected call to ELifeDrupalHttpResponseHandler.init()");
  }
  
  @Override
  public CacheException handleResult(ArchivalUnit au,
                                     String url,
                                     int responseCode) {
    logger.debug2(url);
    switch (responseCode) {
      case 403:
        logger.debug2("403");
        if (url.contains("/download")) {
          return new CacheException.RetryDeadLinkException("403 Forbidden (non-fatal)");
        }
        return super.handleResult(au, url, responseCode);
        
      case 500:
        logger.debug2("500");
        if (url.contains("lockss-manifest/")) {
          return new CacheException.RetrySameUrlException("500 Internal Server Error");
        }
        
        return new NoFailRetryableNetworkException_3_60S("500 Internal Server Error (non-fatal)");
        
      default:
        return super.handleResult(au, url, responseCode);
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
