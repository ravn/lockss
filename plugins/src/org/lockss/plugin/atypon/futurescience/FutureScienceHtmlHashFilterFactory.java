/*
 * $Id: FutureScienceHtmlHashFilterFactory.java,v 1.6 2013-08-07 22:57:23 alexandraohlson Exp $
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

package org.lockss.plugin.atypon.futurescience;

import java.io.InputStream;

import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Remark;
import org.htmlparser.Text;
import org.htmlparser.filters.OrFilter;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.tags.Div;
import org.htmlparser.tags.TableColumn;
import org.lockss.daemon.PluginException;
import org.lockss.filter.html.HtmlFilterInputStream;
import org.lockss.filter.html.HtmlNodeFilterTransform;
import org.lockss.filter.html.HtmlNodeFilters;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.FilterFactory;

/* Don't extend BaseAtyponHtmlHashFilterFactory because 1) FutureScienceGroup is somewhat different
 * AND because we need to keep the comments for some context specific filtering and then
 * we can remove them at the end 
 */
public class FutureScienceHtmlHashFilterFactory implements FilterFactory {

  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
      InputStream in,
      String encoding)
          throws PluginException {
    NodeFilter[] filters = new NodeFilter[] {

        //Stuff on both toc and article pages
        // Variable identifiers - institution doing the crawl  - including genSfxLinks() which is the institution button
        new TagNameFilter("script"),
        // contains the institution banner on both TOC and article pages
        HtmlNodeFilters.tagWithAttribute("div", "class", "institutionBanner"),
        // welcome and login
        HtmlNodeFilters.tagWithAttribute("td", "class", "identitiesBar"),
        // footer at the bottom with copyright, etc.
        HtmlNodeFilters.tagWithAttribute("div", "class", "bottomContactInfo"),
        // footer at the bottom 
        HtmlNodeFilters.tagWithAttribute("div", "class", "bottomSiteMapLink"),
        // Left side columns has list of Journals (might change over time) and current year's catalog
        HtmlNodeFilters.tagWithAttribute("table", "class", "sideMenu mceItemTable"),
        // rss feed link can have variable text in it; exists both as "link" and "a"
        HtmlNodeFilters.tagWithAttributeRegex("link", "href", "feed=rss"),
        HtmlNodeFilters.tagWithAttributeRegex("a", "href", "feed=rss"),

        //TOC stuff
        HtmlNodeFilters.tagWithAttribute("td",  "class", "MarketingMessageArea"),
        HtmlNodeFilters.tagWithAttribute("table",  "class", "quickLinks"), 


        // article page - right side column - no identifier to remove entire column
        // but we can remove the specific contents
        HtmlNodeFilters.tagWithAttribute("td", "class", "quickLinks_content"),
        HtmlNodeFilters.tagWithAttribute("td", "class", "quickSearch_content"),

        // article pages (abstract, reference, full) have a "cited by" section which will change over time
        HtmlNodeFilters.tagWithAttribute("div", "class", "citedBySection"),

        // articles have a section "Users who read this also read..." which is tricky to isolate
        // It's a little scary, but <div class="full_text"> seems only to be used for this section (not to be confused with fulltext)
        // though I could verify that it is followed by <div class="header_divide"><h3>Users who read this article also read:</h3></div>
        HtmlNodeFilters.tagWithAttribute("div", "class", "full_text"),

        // Some article listings have a "free" glif. That change status over time, so remove the image
        //NOTE - there may still be an issue with extra spaces added when image is present
        HtmlNodeFilters.tagWithAttributeRegex("img", "src", "/images/free.gif$", true),

        new NodeFilter() {      
          // look for a <td> that has a comment <!-- placeholder id=null....--> child somewhere in it. If it's there remove it.
          @Override public boolean accept(Node node) {
            if (!(node instanceof TableColumn) && (!(node instanceof Div))) return false;
            // Add placeholder is in a comment which may have associated enclosing <td></td> if there is an ad
            // Look for <!-- placeholder id=null...--> comment and if there is one, remove the enclosing <td></td>
            if (node instanceof TableColumn) {
              Node childNode = node.getFirstChild();
              while (childNode != null) {
                if (childNode instanceof Remark) {
                  String remarkText = childNode.getText();
                  if ( (remarkText != null) && remarkText.contains("placeholder id=null") ) return true;
                }
                childNode = childNode.getNextSibling();
              }
              return false;
            } else {
              // Expert Reviews puts copyright info in unmarked section but it is immediately preceeded by <!--contact info-->
              Node prevSib = node.getPreviousSibling();
              // there may be text nodes before the comment (for newlines)
              while ((prevSib != null) && (prevSib instanceof Text)) {
                prevSib = prevSib.getPreviousSibling();
              }
              if ((prevSib != null) && (prevSib instanceof Remark)) {
                String remarkText = prevSib.getText();
                if ( (remarkText != null) && remarkText.contains("contact info")) return true;
              }
              return false;
            }
          }
        }

    };
    return new HtmlFilterInputStream(in,
        encoding,
        HtmlNodeFilterTransform.exclude(new OrFilter(filters)));

  }
}

