/*
 * $Id: BlackwellHtmlFilterRule.java,v 1.2 2006-08-07 07:34:50 tlipkis Exp $
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.blackwell;

import java.io.*;
import java.util.List;
import org.htmlparser.*;
import org.htmlparser.filters.*;

import org.lockss.util.*;
import org.lockss.filter.*;
import org.lockss.plugin.FilterRule;

public class BlackwellHtmlFilterRule implements FilterRule {

  // Remove everything on the line after these comments
  static HtmlTagFilter.TagPair[] tagpairs = {
    new HtmlTagFilter.TagPair("<!-- Institution/Society Banners -->", "\n"),
    new HtmlTagFilter.TagPair("<!-- Ad Placeholder", "\n"),
  };
  static List tagList = ListUtil.fromArray(tagpairs);

  public Reader createFilteredReader(Reader reader) {
    Reader tagFilter = HtmlTagFilter.makeNestedFilter(reader, tagList);
    return tagFilter;
//     return new WhiteSpaceFilter(tagFilter);
  }


//   public Reader xcreateFilteredReader(Reader reader) {
//     /*
//      * Remove inverse citations
//      */

//     // <option value="#citart1">This article is cited by the following
//     // articles in Blackwell Synergy and CrossRef</option>

//     NodeFilter invCiteSelOption =
//       HtmlNodeFilters.tagWithText("article is cited by", true);
//     HtmlTransform xform1 =
//       HtmlNodeFilterTransform.exclude(invCiteSelOption);

//     // Still need to remove actual inverse citation section

//     Reader htmlFilter = new HtmlFilterReader(reader, xform1);
//     return new WhiteSpaceFilter(htmlFilter);
//   }
}
