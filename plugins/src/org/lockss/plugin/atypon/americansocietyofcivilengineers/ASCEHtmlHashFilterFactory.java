/*
 * $Id: ASCEHtmlHashFilterFactory.java,v 1.1 2013-05-13 21:10:25 ldoan Exp $
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

package org.lockss.plugin.atypon.americansocietyofcivilengineers;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.htmlparser.filters.*;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;

public class ASCEHtmlHashFilterFactory implements FilterFactory {

  public InputStream createFilteredInputStream(ArchivalUnit au,
      InputStream in, String encoding) {
    NodeFilter[] filters = new NodeFilter[] {
        // <div class="institutionBanner">Access provided by STANFORD UNIV </div>
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "institutionBanner"),
        // infrastructure assessment ad
        // http://ascelibrary.org/doi/abs/10.1061/%28ASCE%291076-0431%282010%2916%3A1%2837%29
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "widget type-ad-placeholder ui-helper-clearfix"),
        // left column section history
        // <div class="sessionViewed">
	// http://ascelibrary.org/toc/jaeied/18/4
        // necessary since some urls are opaque (not including year and/or volume)
        // so can't differentiate urls from different AUs.
        // http://ascelibrary.org/doi/full/10.1061/(ASCE)CO.1943-7862.0000372
	HtmlNodeFilters.tagWithAttribute("div", "class", "sessionViewed"),
         // footer copyright @ 1996-2013
        // <div id="copyright">
        // http://ascelibrary.org/toc/jaeied/18/4
        HtmlNodeFilters.tagWithAttribute("div", "id", "footer_message"),
        new TagNameFilter("script"),
    };
    return new HtmlFilterInputStream(in, encoding,
        HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
    }
    
}
