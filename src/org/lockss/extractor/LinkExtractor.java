/*
 * $Id: LinkExtractor.java,v 1.2 2007-02-07 19:32:21 thib_gc Exp $
 */

/*

Copyright (c) 2000-2007 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.extractor;

import java.io.*;

import org.lockss.daemon.*;
import org.lockss.plugin.ArchivalUnit;

/** Content parser that extracts URLs from links */
public interface LinkExtractor {
  /**
   * Parse content on InputStream,  call cb.foundUrl() for each URL found
   * @param au
   * @param in
   * @param srcUrl The URL at which the content lives.  Used as the base
   * for resolving relative URLs (unless/until base set otherwise by
   * content)
   * @param cb
   */
  public void extractUrls(ArchivalUnit au, InputStream in, String encoding,
			  String srcUrl, LinkExtractor.Callback cb)
      throws IOException, PluginException;

  /**
   * Callback for a LinkExtractor to call each time it finds a url
   */
  public interface Callback {
    public void foundLink(String url);
  }
}
