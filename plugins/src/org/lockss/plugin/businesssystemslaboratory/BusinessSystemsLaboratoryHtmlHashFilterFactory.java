/*
 * $Id: BusinessSystemsLaboratoryHtmlHashFilterFactory.java,v 1.3 2014-07-25 19:50:47 ldoan Exp $
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

package org.lockss.plugin.businesssystemslaboratory;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.htmlparser.filters.*;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

/*
 * The htmls do not have descriptive tags. Urls are static currently. 
 * Hashing out javascript and comments.
 *
 * html pages reviewed for filtering:
 * start url - http://www.business-systems-review.org/bsr_archive.htm
 * issue toc - http://www.business-systems-review.org
 *                      /BSR.1.1.June-December.2012.htm
 * abstract  - http://www.business-systems-review.org
 *                      /Vladimirov.et.al.(2013).Globalization.SME.2.3..htm
 */
public class BusinessSystemsLaboratoryHtmlHashFilterFactory 
  implements FilterFactory {
  
  private static Logger log = 
      Logger.getLogger(BusinessSystemsLaboratoryHtmlHashFilterFactory.class);

  public InputStream createFilteredInputStream(ArchivalUnit au, 
                                          InputStream in, String encoding) {

    NodeFilter[] filters = new NodeFilter[] {
        new TagNameFilter("script"),
        // filter out comments
        // FIXME after 1.64: replace with HtmlNodeFilters.comment() 
        HtmlNodeFilters.comment(),
    };

    return new HtmlFilterInputStream(in, encoding, 
        HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
  }
    
}
