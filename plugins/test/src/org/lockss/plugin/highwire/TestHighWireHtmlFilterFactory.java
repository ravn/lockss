/*
 * $Id: TestHighWireHtmlFilterFactory.java,v 1.4 2013-08-03 00:05:00 etenbrink Exp $
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

package org.lockss.plugin.highwire;

import java.io.*;

import org.lockss.util.*;
import org.lockss.test.*;

public class TestHighWireHtmlFilterFactory extends LockssTestCase {
  static String ENC = Constants.DEFAULT_ENCODING;

  private HighWireHtmlFilterFactory fact;
  private MockArchivalUnit mau;

  public void setUp() throws Exception {
    super.setUp();
    fact = new HighWireHtmlFilterFactory();
    mau = new MockArchivalUnit();
  }

  private static final String inst1 = "<FONT SIZE=\"-2\" FACE=\"verdana,arial,helvetica\">\n	" +
      "<NOBR><STRONG>Institution: Periodicals Department/Lane Library</STRONG></NOBR>\n	" +
      "<NOBR><A TARGET=\"_top\" HREF=\"/cgi/login?uri=%2Fcgi%2Fcontent%2Ffull%2F4%2F1%2F121\">" +
      "Sign In as Personal Subscriber</A></NOBR>";

  private static final String inst2 = "<FONT SIZE=\"-2\" FACE=\"verdana,arial,helvetica\">\n	" +
      "<NOBR><STRONG>Institution: Stanford University Libraries</STRONG></NOBR>\n	" +
      "<NOBR><A TARGET=\"_top\" HREF=\"/cgi/login?uri=%2Fcgi%2Fcontent%2Ffull%2F4%2F1%2F121\">" +
      "Sign In as Personal Subscriber</A></NOBR>";

  private static final String inst3 = "<FONT SIZE=\"-2\" FACE=\"verdana,arial,helvetica\">\n    " +
      "<NOBR><STRONG>Institution: Stanford University Libraries</STRONG></NOBR>\n      " +
      "<NOBR><A TARGET=\"_top\" HREF=\"/cgi/login?uri=%2Fcgi%2Fcontent%2Ffull%2F4%2F1%2F121\">" +
      "Sign In as SOMETHING SOMETHING</A></NOBR>";

  String test[] = {

      // Contains variable ad-generating code
      "<script type=\"text/javascript\" src=\"http://nejm.resultspage.com/" +
      "autosuggest/searchbox_suggest_v1.js\" language=\"javascript\">Hello</script>xxyyzz",
      // Contains variable ad-generating code
      "<noscript> onversion/1070139620/?label=_s1RCLTo-QEQ5JGk_gM&amp;amp;guid=" +
      "ON&amp;amp; </noscript>\nxxyyzz",
      
      // Typically contains ads (e.g. American Academy of Pediatrics)
      "<object width=\"100%\" height=\"100%\" type=\"video/x-ms-asf\" " +
      "url=\"3d.wmv\" data=\"3d.wmv\" classid=\"CLSID:6BF52A52-394A-11d3-B153-00C04F79FAA6\">" +
      "</object>xxyyzz",
      // Typically contains ads
      "<iframe src=\"demo_iframe.htm\" width=\"20\" height=\"200\"></iframe>xxyyzz",
      // Contains ads (e.g. American Medical Association)
      "<div id = \"advertisement\"></div>xxyyzz",
      "<div id = \"authenticationstring\"></div>xxyyzz",
      // Contains institution name (e.g. SAGE Publications)
      "<div id = \"universityarea\"></div>xxyyzz",
      // Contains institution name (e.g. Oxford University Press)
      "<div id = \"inst_logo\"></div>xxyyzz",
      // Contains institution name (e.g. American Medical Association)
      "<p id = \"UserToolbar\"></p>xxyyzz",
      "<div id = \"user_nav\"></div>xxyyzz",
      "<table class = \"content_box_inner_table\"></table>xxyyzz",
      "<a class = \"contentbox\"></a>xxyyzz",
      "<div id = \"ArchivesNav\"></div>xxyyzz",
      // lowestLevelMatchFilter(HtmlNodeFilters.tagWithText("table", "Related Content", false)),
      "<table class=\"content_box_outer_table\" align=\"right\">" +
      "  <tr><td>xx" +
      "<!-- beginning of inner table -->"+
      "<table class=\"content_box_inner_table\">" +
      "   <tr><td width=\"4\" class=\"content_box_arrow\" valign=\"top\"><img alt=\"Right arrow\" width=\"4\" height=\"11\" border=\"0\" src=\"/icons/shared/misc/arrowTtrim.gif\" /></td><td class=\"content_box_item\">" +
      "             <strong><a target=\"_blank\" href=\"http://scholar.google.com/scholar?q=%22author%3AM. L.+author%3AWahl%22\">" +
      "             Articles by Wahl, M. L.</a></strong></td></tr>" +
      "   <tr><td width=\"4\" class=\"content_box_arrow\" valign=\"top\"><img alt=\"Right arrow\" width=\"4\" height=\"11\" border=\"0\" src=\"/icons/shared/misc/arrowTtrim.gif\" /></td><td class=\"content_box_item\">" +
      "             <strong><a target=\"_blank\" href=\"http://scholar.google.com/scholar?q=%22author%3AS. V.+author%3APizzo%22\">" +
      "             Articles by Pizzo, S. V.</a></strong></td></tr>" +
      "   <tr><td width=\"4\" class=\"content_box_arrow\" valign=\"top\"><img alt=\"Right arrow\" width=\"4\" height=\"11\" border=\"0\" src=\"/icons/shared/misc/arrowTtrim.gif\" /></td><td class=\"content_box_item\">" +
      "             <strong><a target=\"_blank\" href=\"/cgi/external_ref?access_num=" +
      "           http://rphr.endojournals.org" +
      "           /cgi/content/abstract/59/1/73" +
      "         &link_type=GOOGLESCHOLARRELATED\">Search for Related Content</a></strong>" +
      "           </td></tr>    " +
      "not found</td></tr></table>yy</td></tr></table>zz", 
      // Contains the current year (e.g. Oxford University Press)
      "<div id = \"copyright\"></div>xxyyzz",
      // Contains the current year (e.g. SAGE Publications)
      "<div id = \"footer\"></div>xxyyzz",
      // Contains the current date and time (e.g. American Medical Association)
      "<a target = \"help\"></a>xxyyzz",
      // Contains the name and date of the current issue (e.g. Oxford University Press)
      "<li id = \"nav_current_issue\"></li>xxyyzz",
      // Contains ads or variable banners (e.g. Oxford University Press)
      "<div id = \"oas_top\"></div>xxyyzz",
      // Contains ads or variable banners (e.g. Oxford University Press)
      "<div id = \"oas_bottom\"></div>xxyyzz",
      // Optional institution-specific citation resolver (e.g. SAGE Publications)
      "<a href = \"^/cgi/openurl\"></a>xxyyzz",
      // Contains ad-dependent URLs (e.g. American Academy of Pediatrics)
      "<a href = \"^http://ads.adhostingsolutions.com/\"></a>xxyyzz",
      // alt for less/greater than confuses WhiteSpace filter
      "<img alt = \"[<>]\"></img>xxyyzz",
      //CMAJ (c)year tag
      "<div class = \"slugline-copyright\"></div>xxyyzz",

  };
  
  public void testFiltering() throws Exception {
    InputStream in;
    InputStream inA;
    InputStream inB;

    for (String t : test){
      in = fact.createFilteredInputStream(mau, new StringInputStream(t), ENC);
      String test_in = StringUtil.fromInputStream(in);
      // trim leading spaces
      test_in = test_in.trim();
      assertEquals("xxyyzz", test_in);
    }

    inA = fact.createFilteredInputStream(mau,
        new StringInputStream(inst1), ENC);
    inB = fact.createFilteredInputStream(mau,
        new StringInputStream(inst2), ENC);
    assertEquals(StringUtil.fromInputStream(inA),
        StringUtil.fromInputStream(inB));

    inA = fact.createFilteredInputStream(mau,
        new StringInputStream(inst1), ENC);
    inB = fact.createFilteredInputStream(mau,
        new StringInputStream(inst3), ENC);
    assertEquals(StringUtil.fromInputStream(inA),
        StringUtil.fromInputStream(inB));
  }

}
