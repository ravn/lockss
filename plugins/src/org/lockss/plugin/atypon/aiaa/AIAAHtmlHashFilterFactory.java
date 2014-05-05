/*
 * $Id: AIAAHtmlHashFilterFactory.java,v 1.5 2014-05-05 19:07:30 alexandraohlson Exp $
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

package org.lockss.plugin.atypon.aiaa;

import java.io.InputStream;
import java.io.Reader;
import java.util.regex.Pattern;

import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Remark;
import org.htmlparser.Text;
import org.htmlparser.tags.Bullet;
import org.htmlparser.tags.CompositeTag;
import org.htmlparser.tags.Div;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.tags.TableColumn;
import org.lockss.filter.FilterUtil;
import org.lockss.filter.WhiteSpaceFilter;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.plugin.atypon.BaseAtyponHtmlHashFilterFactory;
import org.lockss.util.ReaderInputStream;

public class AIAAHtmlHashFilterFactory extends BaseAtyponHtmlHashFilterFactory {
  
  protected static final Pattern citedBy = Pattern.compile("Cited By", Pattern.CASE_INSENSITIVE);


  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
      InputStream in,
      String encoding) {
    NodeFilter[] afilters = new NodeFilter[] {
 
        // the entire left column which can have browseVolumes, browsing history, tools, etc
        HtmlNodeFilters.tagWithAttribute("div", "id", "dropzone-Left-Sidebar"),
        // not necessarily used, but we wouldn't want an ad
        HtmlNodeFilters.tagWithAttribute("div",  "class", "mainAd"),
        // these mark out sections that may or may not get filled and we 
        // were caught by a system maintenance temporary message
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "widget type-ad-placeholder"),
        
        // When the <li> item has the text "Cited By" then it should be removed 
        new NodeFilter() {
          @Override public boolean accept(Node node) {
            if (!(node instanceof Bullet)) return false;
            String allText = ((CompositeTag)node).toPlainTextString();
            return citedBy.matcher(allText).find();
          }
        },
        
    };
    // super.createFilteredInputStream adds aiaa filter to the baseAtyponFilters
    // and returns the filtered input stream using an array of NodeFilters that 
    // combine the two arrays of NodeFilters.
    InputStream superFiltered = super.createFilteredInputStream(au, in, encoding, afilters);

    // Also need white space filter to condense multiple white spaces down to 1
    Reader reader = FilterUtil.getReader(superFiltered, encoding);
    return new ReaderInputStream(new WhiteSpaceFilter(reader));

  }

}

