/*
 * $Id: SEGHtmlCrawlFilterFactory.java,v 1.3 2014-09-20 04:07:12 ldoan Exp $
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
import org.lockss.daemon.PluginException;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.plugin.atypon.BaseAtyponHtmlCrawlFilterFactory;

// be sure not to CRAWL filter out entire left column "dropzone-Left-sidebar" 
// because we need to be able to pick up action/showCitFormats link

public class SEGHtmlCrawlFilterFactory 
  extends BaseAtyponHtmlCrawlFilterFactory {
  static NodeFilter[] filters = new NodeFilter[] {
    
    // top right of issue toc - links to previous or next issue
    HtmlNodeFilters.tagWithAttribute("div", "id", "prevNextNav"),
    
    // top right of an article - links to previous or next article
    HtmlNodeFilters.tagWithAttribute("div", "id", "articleToolsNav"),
    
    // left column of an article - all except Download Citations
    HtmlNodeFilters.allExceptSubtree(
        HtmlNodeFilters.tagWithAttribute( 
            "div", "class", "yui3-u yui3-u-1-4 leftColumn"),
            HtmlNodeFilters.tagWithAttributeRegex(
                "a", "href", "/action/showCitFormats\\?")),

    // external links within References section
    // Volume 17, Issue 1
    // http://library.seg.org/doi/full/10.2113/JEEG17.1.1
    // In the References section, there is a link to 
    // http://library.seg.org/doi/full/10.2113/www.estcp.org
    HtmlNodeFilters.tagWithAttribute("div", "class", "abstractReferences"),
    
    // external links from Acknowledgements section
    // Volume 78, Issue 2
    // http://library.seg.org/doi/full/10.1190/geo2012-0303.1
    // In the Acknowledge section, there are links to 
    // http://library.seg.org/doi/full/10.1190/go.egi.eu/pdnon and 
    // http://library.seg.org/doi/full/10.1190/www.mathworks.com
    //          /matlabcentral/fileexchange/24531-accurate-fast-marching
    // external link from Case Studies section
    // Volume 78, Issue 1
    // http://library.seg.org/doi/full/10.1190/geo2012-0113.1
    // In the Case Studies section, there is link to 
    // http://library.seg.org/doi/full/10.1190
    //          /www.rockphysics.ethz.ch/downloads
    HtmlNodeFilters.tagWithAttribute("a", "class", "ext-link"),
    
    // links within short-legend of a figure
    // http://library.seg.org/doi/full/10.1190/geo2012-0106.1
    // Part of a figure, there is a link the author info, which can already
    // be found in References section.
    HtmlNodeFilters.tagWithAttribute("div", "class", "short-legend"),
    
  };

  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
      InputStream in,
      String encoding)
  throws PluginException{
    return super.createFilteredInputStream(au, in, encoding, filters);
  }
}
