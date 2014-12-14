/* 
 * $Id: CYSHtmlCrawlFilterFactory.java,v 1.1 2014-12-14 01:16:40 ldoan Exp $
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

package org.lockss.plugin.atypon.cys;

import java.io.InputStream;
import org.htmlparser.NodeFilter;
import org.lockss.daemon.PluginException;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.plugin.atypon.BaseAtyponHtmlCrawlFilterFactory;

public class CYSHtmlCrawlFilterFactory 
  extends BaseAtyponHtmlCrawlFilterFactory {
  
    NodeFilter[] filters = new NodeFilter[] {
        // from  toc, abs, full - strip below the main title with links to 
        // Home, About, Journals, etc...
        // http://www.cysjournal.ca/toc/cysj/2013/2
        HtmlNodeFilters.tagWithAttribute("div", "id", "nav-wrapper"),        
        // from toc, abs, full - breadcrumbs leading to
        // journal home, list of issues 
        // http://www.cysjournal.ca/toc/cysj/2013/2
        HtmlNodeFilters.tagWithAttribute("div",  "id", "breadcrumbs"),        
        // from toc, abs, full - all left sidebar
        // http://www.cysjournal.ca/toc/cysj/2013/2
        // http://www.cysjournal.ca/doi/abs/10.13034/cysj-2013-006
        HtmlNodeFilters.tagWithAttribute("div", "id", "sidebar-left"),        
        // from abs, full - previous/toc/next article
        // http://www.cysjournal.ca/doi/full/10.13034/cysj-2013-006
        HtmlNodeFilters.tagWithAttribute("a", "class", "white-link-right"),      
        // from toc - center area above the current issue has links 
        // to current/full/list of issues
        // http://www.cysjournal.ca/toc/cysj/2013/2
        HtmlNodeFilters.tagWithAttribute("div", "class", 
            "box-pad border-gray margin-bottom clearfix"),       
       // spider trap link in this tag
       // <a href="/doi/pdf/10.1046/9999-9999.99999">
       // http://www.cysjournal.ca/toc/cysj/2013/2
      HtmlNodeFilters.tagWithAttribute("span", "id", "hide")

    };
    @Override
    public InputStream createFilteredInputStream(ArchivalUnit au,
        InputStream in, String encoding) throws PluginException{ 
      return super.createFilteredInputStream(au, in, encoding, filters);
    }


}
