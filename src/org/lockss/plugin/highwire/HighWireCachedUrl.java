/*
 * $Id: HighWireCachedUrl.java,v 1.7 2003-09-04 23:41:11 troberts Exp $
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

package org.lockss.plugin.highwire;

import java.io.*;
import java.util.*;
import java.net.MalformedURLException;
import org.lockss.app.*;
import org.lockss.crawler.*;
import org.lockss.filter.*;
import org.lockss.daemon.*;
import org.lockss.repository.*;
import org.lockss.util.*;
import org.lockss.plugin.base.*;
import org.lockss.plugin.*;

public class HighWireCachedUrl extends GenericFileCachedUrl {
  public HighWireCachedUrl(CachedUrlSet owner, String url) {
    super(owner, url);
  } 

  public InputStream openForHashing() {
    /*
     * Needs to be better (TSR 9-2-03):  
     * 1)Filtering out everything in a table is pretty good, but over filters
     * 2)Need to have a filter that just removes a fixed string, rather than
     *  tags
     */

    logger.debug3("Filtering on, returning filtered stream");
    Properties props = getProperties();
    if ("text/html".equals(props.getProperty("content-type"))) {
      logger.debug2("Filtering "+url);
      List tagList =
	ListUtil.list(
		      new HtmlTagFilter.TagPair("<script", "</script>", true),
		      new HtmlTagFilter.TagPair("<table", "</table>", true),
		      new HtmlTagFilter.TagPair("This article has been cited by",
						" other articles:", true),
		      new HtmlTagFilter.TagPair("[Medline", "]", true),
		      new HtmlTagFilter.TagPair("<", ">")
		      );


      Reader filteredReader =
	HtmlTagFilter.makeNestedFilter(getReader(), tagList);
      return new ReaderInputStream(filteredReader);
    }
    logger.debug2("Not filtering "+url);
    return openForReading();
  }
}
