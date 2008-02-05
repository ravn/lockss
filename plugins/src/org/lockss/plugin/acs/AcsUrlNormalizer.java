/*
 * $Id: AcsUrlNormalizer.java,v 1.3 2008-02-05 20:07:21 thib_gc Exp $
 */

/*

Copyright (c) 2000-2008 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.acs;

import org.lockss.daemon.PluginException;
import org.lockss.plugin.*;

/** ACS site redirects to
 * <code><i>orig-url</i>?sessid=<blah></code> when you try to fetch a PDF.
 * This removes the session id if it it is present.
 */

public class AcsUrlNormalizer implements UrlNormalizer {
//  public String QUERY_ARG = "cookieSet=1";
//  private String ONLY_QUERY = "?" + QUERY_ARG;
//  private String FIRST_QUERY = "?" + QUERY_ARG + "&";
//  private String NTH_QUERY = "&" + QUERY_ARG;
//
//  public String normalizeUrl (String url, ArchivalUnit au) {
//    if (-1 == url.indexOf(QUERY_ARG)) {
//      return url;
//    }
//    url = StringUtil.replaceFirst(url, FIRST_QUERY, "?");
//    url = StringUtil.replaceFirst(url, ONLY_QUERY, "");
//    url = StringUtil.replaceFirst(url, NTH_QUERY, "");
//    return url;
//  }

  private static final String QUERY_ARG = "?sessid=";

  public String normalizeUrl(String url, ArchivalUnit au)
      throws PluginException {
    int idx = url.indexOf(QUERY_ARG);
    return idx == -1 ? url : url.replaceFirst("sessid=[0-9]+", "sessid=LOCKSS-FAKE-SESSION-ID");
  }

}
