/*
 * $Id: CacheResultMap.java,v 1.2.18.1 2004-11-11 00:55:02 troberts Exp $
 */

/*

Copyright (c) 2000-2004 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.util.urlconn;


//import java.net.HttpURLConnection;

/**
 * Maps the result of a cache (fetch and store locally) operation, to
 * success (null) or an exception understood by LOCKSS, usually one under
 * CacheException
 */

public interface CacheResultMap {
  public CacheException getHostException(Exception nestedException);

  public CacheException getMalformedURLException(Exception nestedException);

  public CacheException getRepositoryException(Exception nestedException);

  public CacheException checkResult(LockssUrlConnection connection);
}
