/*
 * $Id: SEGHtmlHashFilterFactory.java,v 1.1 2014-09-04 03:14:49 ldoan Exp $
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

package org.lockss.plugin.atypon.seg;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.plugin.atypon.BaseAtyponHtmlHashFilterFactory;

public class SEGHtmlHashFilterFactory extends BaseAtyponHtmlHashFilterFactory {

  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
      InputStream in, String encoding) {
    
    NodeFilter[] segFilters = new NodeFilter[] {
        // <header> filtered in BaseAtypon
        
        // top right of issue toc - links to previous or next issue
	HtmlNodeFilters.tagWithAttribute("div", "id", "prevNextNav"),
	
	// top right of an article - links to previous or next article
        HtmlNodeFilters.tagWithAttribute("div", "id", "articleToolsNav"),
        
        // left column - all except Download Citations
        HtmlNodeFilters.allExceptSubtree(
            HtmlNodeFilters.tagWithAttribute(
                "div", "class", "yui3-u yui3-u-1-4 leftColumn"),
                HtmlNodeFilters.tagWithAttributeRegex(
                    "a", "href", "/action/showCitFormats\\?")),                           
                	    
	// right column ads - <div class="mainAd">
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "mainAd"),
        
        // ??might need:
        // some issue toc page has 'ContribStored', other 'ContribAuthorStored'
        // <a class="entryAuthor" href="/action/doSearch?displaySummary=true
        //      &amp;ContribAuthorStored=Campbell%2C+B">Bruce Campbell</a>
        // HtmlNodeFilters.tagWithAttribute("a", "class", "entryAuthor"),
        // from an article
        // <a href="/action/doSearch?ContribStored=Doll%2C+W+E">William E. Doll</a>
        // <a href="/action/doSearch?ContribAuthorStored=Hardage%2C+B">Bob Hardage</a>
        // div class="artAuthors"
        // HtmlNodeFilters.tagWithAttribute("div", "class", "artAuthors"),

	// footer and footer_message filtered in BaseAtypon
    };
    
    // super.createFilteredInputStream adds segFilters to the baseAtyponFilters
    // and returns the filtered input stream using an array of NodeFilters that 
    // combine the two arrays of NodeFilters.
    return super.createFilteredInputStream(au, in, encoding, segFilters);
    }
    
}
