
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

Except as contained in this notice, tMassachusettsMedicalSocietyHtmlFilterFactoryhe name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.plugin.maffey;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.htmlparser.filters.*;
import org.lockss.daemon.PluginException;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;

public class MaffeyHtmlHashFilterFactory implements FilterFactory {

  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
	  
    // First filter with HtmlParser
    NodeFilter[] filters = new NodeFilter[] {
        /*
        * Crawl filter
        */
        //Related articles
        HtmlNodeFilters.tagWithAttribute("div", "class", "alsoRead"),
        /*
        * Hash filter
        */
        // Contains ad-specific cookies
        new TagNameFilter("script"),
        // Ad
        HtmlNodeFilters.tagWithAttribute("div", "id", "ad_holder"),
        // Dicussion and comments
        HtmlNodeFilters.tagWithAttribute("div", "id", "commentsBoxes"),
        // Constantly changing reference to css file: css/grid.css?1337026463
        HtmlNodeFilters.tagWithAttributeRegex("link", "href", "css/grid.css\\?[0-9]+"),
        // Article views <p class="article_views_p">
        HtmlNodeFilters.tagWithAttribute("p", "class", "article_views_p"),
        // Latest News items change over time <div id="news_holder">
        //HtmlNodeFilters.tagWithAttribute("div", "id", "news_holder"),   // rightcolumn
        // remove entire right column
        HtmlNodeFilters.tagWithAttribute("div", "class", "rightcolumn"), // headerright
        HtmlNodeFilters.tagWithAttribute("div", "id", "sharing"), // the Sharing/social media
        // author services, including author testimonials
        HtmlNodeFilters.tagWithAttribute("div", "class", "hideForPrint"), 
        // comments
        HtmlNodeFilters.comment(),
        
        // Chat with support availability status changes
        HtmlNodeFilters.tagWithAttribute("div", "class", "searchleft"),

        // Rotating user testimonials	
        HtmlNodeFilters.tagWithAttribute("div", "class", "categoriescolumn4"),
        // # article views
        HtmlNodeFilters.tagWithAttribute("div", "class", "yellowbgright1"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "article_meta_stats"),
        // # total libertas academica article views
        HtmlNodeFilters.tagWithAttribute("p", "class", "laarticleviews"),
        // dynamic css/js urls
        new TagNameFilter("link"),
        // Libertas Academica: number of journal views
        HtmlNodeFilters.tagWithAttribute("div", "class", "journal_heading_stats"),
        // Libertas Academica: what your colleagues are saying about Libertas Academica
        HtmlNodeFilters.tagWithAttribute("div", "id", "colleagues"),
        // Libertas Academica: our service promise
        HtmlNodeFilters.tagWithAttribute("div", "id", "ourservicepromise"),
    };
    
    return new HtmlFilterInputStream(in,
              		             encoding,
              			     HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
  }
  
}
