/*
 * $Id: DigitalCommonsRepositoryHtmlHashFilterFactory.java,v 1.1 2013-11-19 21:40:57 ldoan Exp $
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

package org.lockss.plugin.bepress;

import java.io.InputStream;
import org.htmlparser.NodeFilter;
import org.htmlparser.filters.OrFilter;
import org.htmlparser.filters.TagNameFilter;
import org.lockss.daemon.PluginException;
import org.lockss.filter.html.HtmlFilterInputStream;
import org.lockss.filter.html.HtmlNodeFilterTransform;
import org.lockss.filter.html.HtmlNodeFilters;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.FilterFactory;
import org.lockss.util.Logger;

public class DigitalCommonsRepositoryHtmlHashFilterFactory
  implements FilterFactory {

  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in, String encoding)
      throws PluginException {
    
    Logger log = Logger.getLogger(
        DigitalCommonsRepositoryHtmlHashFilterFactory.class);
    
    NodeFilter[] filters = new NodeFilter[] {
        // filter out javascript
        new TagNameFilter("script"),
        //filter out comments
        HtmlNodeFilters.commentWithRegex(".*"),
        // stylesheets
        HtmlNodeFilters.tagWithAttribute("link", "rel", "stylesheet"),
        // top banner
        HtmlNodeFilters.tagWithAttribute("div", "id", "header"),
        // breadcrumb - Home > Dietrich College > Statistics
        HtmlNodeFilters.tagWithAttribute("div", "id", "breadcrumb"),
        // skip to main
        HtmlNodeFilters.tagWithAttribute("a", "class", "skiplink"),
        // near top - navigation
        HtmlNodeFilters.tagWithAttribute("div", "id", "navigation"),
        // left sidebar
        HtmlNodeFilters.tagWithAttribute("div", "id", "sidebar"),
        // top right of the article for the year - <previous> and <next>
        // http://repository.cmu.edu/statistics/68/
        HtmlNodeFilters.tagWithAttribute("ul", "id", "pager"),
        // footer
        HtmlNodeFilters.tagWithAttribute("div", "id", "footer"),
        // publication 'follow'
        HtmlNodeFilters.tagWithAttribute("a", "rel", "nofollow"),
        // right side box 'Include in'
        HtmlNodeFilters.tagWithAttribute("div", "id", "beta-disciplines"),
        // social media - share
        HtmlNodeFilters.tagWithAttribute("div", "id", "share"),
        // some strange class named Z3988
        HtmlNodeFilters.tagWithAttribute("span", "class", "Z3988")
    };
    return new HtmlFilterInputStream(
        in, encoding,HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
  }

}
