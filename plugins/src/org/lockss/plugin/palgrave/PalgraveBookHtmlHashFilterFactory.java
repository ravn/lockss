/* $Id: PalgraveBookHtmlHashFilterFactory.java,v 1.2 2014-06-06 17:33:41 aishizaki Exp $
 
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

package org.lockss.plugin.palgrave;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.htmlparser.filters.*;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;

public class PalgraveBookHtmlHashFilterFactory implements FilterFactory {

  public InputStream createFilteredInputStream(ArchivalUnit au,
      InputStream in, String encoding) {
    NodeFilter[] filters = new NodeFilter[] {
	// http://www.palgraveconnect.com/pc/doifinder/10.1057/9780230597655
	//HtmlNodeFilters.tagWithAttribute("div", "class", "pnl1a related"),
	new TagNameFilter("script"),
	// added by audrey: Extreme Hashing
	// header, footer in http://www.palgraveconnect.com/pc/doifinder/10.1057/9781137283351
        // institutional info in the constrain-header
	HtmlNodeFilters.tagWithAttribute("div", "id", "constrain-header"),
        HtmlNodeFilters.tagWithAttribute("div", "id", "constrain-footer"),
        // right sidebar
        HtmlNodeFilters.tagWithAttribute("div", "class", "column-width-sidebar column-r"),
        // only keeping stuff in the left sidebar
    };
    return new HtmlFilterInputStream(in, encoding,
        HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
    }
    
}
