/*
 * $Id: SilverchairHtmlLinkExtractor.java,v 1.3 2014-05-07 14:38:23 thib_gc Exp $
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

package org.lockss.plugin.silverchair;

import java.io.IOException;
import java.net.URL;
import java.util.regex.*;

import org.lockss.extractor.*;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.Logger;

public class SilverchairHtmlLinkExtractor extends GoslingHtmlLinkExtractor {

  private static final Logger logger = Logger.getLogger(SilverchairHtmlLinkExtractor.class);
  
  protected static final Pattern PATTERN_ARTICLE =
      Pattern.compile("/article\\.aspx\\?(articleid=[^&]+)$",
                      Pattern.CASE_INSENSITIVE);
  
  protected static final Pattern PATTERN_CITATION =
      Pattern.compile("/downloadCitation\\.aspx\\?(format=[^&]+)?$",
                      Pattern.CASE_INSENSITIVE);
  
  protected static final Pattern PATTERN_JQUERY =
      Pattern.compile("ajax\\.googleapis\\.com/ajax/libs/jquery/([^/]+)/jquery\\.min\\.js$",
                      Pattern.CASE_INSENSITIVE);
  
  @Override
  protected String extractLinkFromTag(StringBuffer link,
                                      ArchivalUnit au,
                                      Callback cb)
      throws IOException {
    char ch = link.charAt(0);

    if ((ch == 'a' || ch == 'A') && beginsWithTag(link, ATAG)) {
      String href = getAttributeValue(HREF, link);
      if (href == null) {
        href = "";
      }
      Matcher hrefMat = null;

      hrefMat = PATTERN_CITATION.matcher(href);
      if (hrefMat.find()) {
        logger.debug3("Found target citation URL");
        // Derive citation format; can be null
        String formatPair = hrefMat.group(1);
        Matcher srcUrlMat = PATTERN_ARTICLE.matcher(srcUrl);
        if (srcUrlMat.find()) {
          // Derive article ID
          String articleIdPair = srcUrlMat.group(1);
          // Generate correct citation URL
          String url = null;
          if (formatPair == null) {
            url = String.format("/downloadCitation.aspx?%s", articleIdPair);
          }
          else {
            url = String.format("/downloadCitation.aspx?%s&%s", formatPair, articleIdPair);
          }
          logger.debug3(String.format("Generated %s", url));
          if (baseUrl == null) { baseUrl = new URL(srcUrl); } // Copycat of parseLink()
          emit(cb, resolveUri(baseUrl, url));
        }
        return super.extractLinkFromTag(link, au, cb);
      }
      
    }

    else if ((ch == 's' || ch == 'S') && beginsWithTag(link, SCRIPTTAG)) {
      String src = getAttributeValue(SRC, link);
      if (src == null) {
        src = "";
      }
      Matcher srcMat = null;
      
      srcMat = PATTERN_JQUERY.matcher(src);
      if (srcMat.find()) {
        logger.debug3("Found target JQuery URL");
        String jqueryVersion = srcMat.group(1);
        // Generate local JQuery URL
        String url = String.format("/JS/plugins/jquery-%s.min.js", jqueryVersion);
        logger.debug3(String.format("Generated %s", url));
        if (baseUrl == null) { baseUrl = new URL(srcUrl); } // Copycat of parseLink()
        emit(cb, resolveUri(baseUrl, url));
        return super.extractLinkFromTag(link, au, cb);
      }
    }

    
    return super.extractLinkFromTag(link, au, cb);
  }

}
