/*
 * $Id: ProjectMuseFilterRule.java,v 1.1.4.1 2004-01-20 22:58:04 eaalto Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.projmuse;

import java.io.*;
import java.util.*;
import org.lockss.util.*;
import org.lockss.filter.*;
import org.lockss.plugin.FilterRule;

/**
 * Filters out menu, comments, javascript, html tags, and whitespace.
 */
public class ProjectMuseFilterRule implements FilterRule {
  static final String MENU_START =
      "<!-- ================== BEGIN JUMP MENU ================== -->";
  static final String MENU_END =
      "<!-- =================== END JUMP MENU =================== -->";

  public InputStream createFilteredInputStream(Reader reader) {
    List tagList = ListUtil.list(
        new HtmlTagFilter.TagPair(MENU_START, MENU_END, true),
        new HtmlTagFilter.TagPair("<!--", "-->", true),
        new HtmlTagFilter.TagPair("<script", "</script>", true),
        new HtmlTagFilter.TagPair("<", ">")
        );
    Reader filteredReader = HtmlTagFilter.makeNestedFilter(reader, tagList);
    return new WhiteSpaceFilter(new ReaderInputStream(filteredReader));
  }
}
