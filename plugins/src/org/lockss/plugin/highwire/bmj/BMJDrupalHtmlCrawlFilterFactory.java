/*
 * $Id: BMJDrupalHtmlCrawlFilterFactory.java,v 1.1 2015-02-11 09:15:03 etenbrink Exp $
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

package org.lockss.plugin.highwire.bmj;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.lockss.daemon.PluginException;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.highwire.HighWireDrupalHtmlCrawlFilterFactory;
import org.lockss.util.Logger;

public class BMJDrupalHtmlCrawlFilterFactory extends HighWireDrupalHtmlCrawlFilterFactory {
  
  private static final Logger log = Logger.getLogger(BMJDrupalHtmlCrawlFilterFactory.class);
  
  private static final NodeFilter[] filters = new NodeFilter[] {
      // while we collect rapid-responses, do not crawl for links
//      HtmlNodeFilters.tagWithAttributeRegex("div", "class", "rapid-responses"),
      // BMJ had tags on http://www.bmj.com/content/330/7506/0.8/rapid-responses
//      HtmlNodeFilters.tagWithAttribute("div", "id", "skip-link"),
//      HtmlNodeFilters.tagWithAttribute("div", "id", "cookie-notice"),
//      HtmlNodeFilters.tagWithAttribute("div", "class", "vote-widget"),
      // <div class="panel-panel panel-region-content-header">
      // <div class="panel-pane pane-panels-mini pane-oup-explore-citing-articles">
      // <div class="panel-pane pane-panels-mini pane-oup-explore-related-articles">
      // <div class="ui-dialog ui-widget
  };
  
  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    
    InputStream filtered = super.createFilteredInputStream(au, in, encoding, null);
    return filtered;
  }
}
