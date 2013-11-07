/* 
 * $Id: CanadianYoungScientistHtmlCrawlFilterFactory.java,v 1.1 2013-11-07 00:57:27 ldoan Exp $
 */

/*

Copyright (c) 2000-2013 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.atypon.canadianyoungscientist;

import java.io.InputStream;
import org.htmlparser.NodeFilter;
import org.htmlparser.filters.OrFilter;
import org.lockss.daemon.PluginException;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.plugin.atypon.BaseAtyponHtmlCrawlFilterFactory;

/*
 * html pages reviews for filtering:
 * issue toc: http://www.cysjournal.ca/toc/cysj/2013/2
 * abstract:  http://www.cysjournal.ca/doi/abs/10.13034/cysj-2013-007
 * full text: http://www.cysjournal.ca/doi/full/10.13034/cysj-2013-006
 * pdf:       
 *
 */
public class CanadianYoungScientistHtmlCrawlFilterFactory 
  extends BaseAtyponHtmlCrawlFilterFactory {
  
    NodeFilter[] filters = new NodeFilter[] {
      // all stuff in the left sidebar
      HtmlNodeFilters.tagWithAttribute("div", "id", "sidebar-left"),
      // strip below the main title with links to Home, About, Journals, etc...
      HtmlNodeFilters.tagWithAttribute("div", "id", "nav-wrapper"),
      // from issue TOC (e.g. http://www.cysjournal.ca/toc/cysj/2013/2)
      // center area above the current issue (has links 
      // to current/full/list of issues)
      HtmlNodeFilters.tagWithAttribute(
          "div", "class", "box-pad border-gray margin-bottom clearfix"),
      // spider link in this tag
      HtmlNodeFilters.tagWithAttribute("span", "id", "hide"),
      // Remove link to "also read" 
      HtmlNodeFilters.tagWithAttribute("a",  "class", "icon-recommended"), 
      // Cannot filter out entire sidebar-right because we need to pick up the 
      // download citations -so remove smaller chunk
    };
    @Override
    public InputStream createFilteredInputStream(ArchivalUnit au,
        InputStream in, String encoding) throws PluginException{ 
      return super.createFilteredInputStream(au, in, encoding, filters);
    }


}
