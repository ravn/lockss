/*
 * $Id: AIAAHtmlHashFilterFactory.java,v 1.2 2013-04-05 00:34:09 alexandraohlson Exp $
 */

/*

Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.aiaa;

import java.io.InputStream;
import java.io.Reader;

import org.htmlparser.NodeFilter;
import org.htmlparser.filters.*;
import org.lockss.daemon.PluginException;
import org.lockss.filter.FilterUtil;
import org.lockss.filter.HtmlTagFilter;
import org.lockss.filter.WhiteSpaceFilter;
import org.lockss.filter.HtmlTagFilter.TagPair;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.util.ListUtil;
import org.lockss.util.ReaderInputStream;

public class AIAAHtmlHashFilterFactory implements FilterFactory {

  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    NodeFilter[] filters = new NodeFilter[] {
        // Variable identifiers - institution doing the crawl
        new TagNameFilter("script"),
        // Contains name and logo of institution
        HtmlNodeFilters.tagWithAttribute("div", "class", "institutionBanner"),
        // Contains shopping cart, login button
        HtmlNodeFilters.tagWithAttribute("div", "class", "loginIdentity"),
        // Contains the current year
        HtmlNodeFilters.tagWithAttribute("div", "class", "copyright"),
        // Contains the changeable list of citations
        HtmlNodeFilters.tagWithAttribute("div", "class", "citedBySection"),
        // Contains link to current issue which changes over time
        HtmlNodeFilters.tagWithAttribute("div", "class", "issueNavigator"),
        // Contains subscription info/pricing which may change over time
        HtmlNodeFilters.tagWithAttribute("div", "class", "product"),
    };
    InputStream filtered = new HtmlFilterInputStream(in,
                                     encoding,
                                     HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
    Reader filteredReader = FilterUtil.getReader(filtered,encoding);
    Reader tagFilter = HtmlTagFilter.makeNestedFilter(filteredReader,
        ListUtil.list(   
            new TagPair("<!--", "-->")
            ));   
    return new ReaderInputStream(tagFilter);
  }

}

