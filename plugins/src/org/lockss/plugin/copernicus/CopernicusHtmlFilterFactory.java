/*
* $Id: CopernicusHtmlFilterFactory.java,v 1.6 2013-02-19 18:12:37 alexandraohlson Exp $
*/

/*

 Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
 all rights reserved.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of his software and associated documentation files (the "Software"), to deal
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

package org.lockss.plugin.copernicus;

import java.io.*;
import java.util.List;
import java.util.Vector;

import org.htmlparser.*;
import org.htmlparser.filters.*;
import org.htmlparser.tags.*;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.visitors.NodeVisitor;
import org.lockss.daemon.PluginException;
import org.lockss.filter.FilterUtil;
import org.lockss.filter.HtmlTagFilter;
import org.lockss.filter.StringFilter;
import org.lockss.filter.WhiteSpaceFilter;
import org.lockss.filter.HtmlTagFilter.TagPair;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.plugin.hindawi.HindawiPublishingCorporationHtmlFilterFactory;
import org.lockss.util.ListUtil;
import org.lockss.util.Logger;
import org.lockss.util.ReaderInputStream;

public class CopernicusHtmlFilterFactory implements FilterFactory {

        Logger log = Logger.getLogger("CopernicusHtmlFilterFactoryy");
        
  public static class FilteringException extends PluginException {
    public FilteringException() { super(); }
    public FilteringException(String msg, Throwable cause) { super(msg, cause); }
    public FilteringException(String msg) { super(msg); }
    public FilteringException(Throwable cause) { super(cause); }
  }

  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
          
    // First filter with HtmlParser
    NodeFilter[] filters = new NodeFilter[] {
        /*
         * Hash filter
         */
        new TagNameFilter("script"),
        new TagNameFilter("noscript"),
        // Right column contains search, news, and recent papers
        HtmlNodeFilters.tagWithAttribute("div", "id", "page_colum_right"),
        // Journal metrics block - variable values
        HtmlNodeFilters.tagWithAttribute("div", "id", "journal_metrics"),
        HtmlNodeFilters.tagWithAttribute("iframe", "id", "co_auth_check_authiframecontainer"),
        HtmlNodeFilters.tagWithAttribute("div", "id", "page_navigation_left"),    
        
    };    
    HtmlTransform xform = new HtmlTransform() {
      @Override
      public NodeList transform(NodeList nodeList) throws IOException {
        try {
          nodeList.visitAllNodesWith(new NodeVisitor() {
            @Override
            public void visitTag(Tag tag) {
              String tagName = tag.getTagName().toLowerCase();
              // addition of new <span style="white-space;nowrap"> but we need the content
              if ( ("span".equals(tagName))  && (tag.getAttribute("style") != null) ){
                Tag endTag = tag.getEndTag();
                // we need the contents in place, so rename the tags for subsequent text based removal
                tag.setAttributesEx(new Vector()); //empty attribs Vector. Even clears out tag name
                tag.setTagName("REMOVE");
                endTag.setTagName("REMOVE");
              }
            }
          });
        }
        catch (Exception exc) {
          log.debug2("Internal error (visitor)", exc); // Ignore this tag and move on
        }
        return nodeList;
      }
    };
    InputStream htmlFilter = new HtmlFilterInputStream(in,
        encoding,
        new HtmlCompoundTransform(HtmlNodeFilterTransform.exclude(new OrFilter(filters)), xform));

    Reader reader = FilterUtil.getReader(htmlFilter, encoding);
    Reader tagFilter = HtmlTagFilter.makeNestedFilter(reader,
        ListUtil.list(   
            // some comments contain a timestamp
            new TagPair("<!--", "-->"),
            // <REMOVE> tags were created in place of specific span tags that needed removal
            new TagPair("<REMOVE", ">")
            ));   
    // so some replace on strings
    String[][] findAndReplace = new String[][] {
        // use of &nbsp; or " " inconsistent over time
        {"&nbsp;", " "}, 
        // out of sync - some versions have extraneous single spaces, so remove between tags
        {"> <", "><"},
    };
    Reader stringFilter = StringFilter.makeNestedFilter(tagFilter,
                                                          findAndReplace,
                                                          false);

    return new ReaderInputStream(new WhiteSpaceFilter(stringFilter));   
    
  }
}
