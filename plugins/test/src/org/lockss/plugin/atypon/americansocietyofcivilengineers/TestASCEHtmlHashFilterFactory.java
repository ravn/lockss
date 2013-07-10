/*  $Id: TestASCEHtmlHashFilterFactory.java,v 1.3 2013-07-10 16:54:54 aishizaki Exp $
 
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

package org.lockss.plugin.atypon.americansocietyofcivilengineers;

import java.io.InputStream;

import org.lockss.test.LockssTestCase;
import org.lockss.test.MockArchivalUnit;
import org.lockss.test.StringInputStream;
import org.lockss.util.Constants;
import org.lockss.util.StringUtil;

public class TestASCEHtmlHashFilterFactory extends LockssTestCase {
  private ASCEHtmlHashFilterFactory fact;
  private MockArchivalUnit mau;

  public void setUp() throws Exception {
    super.setUp();
    fact = new ASCEHtmlHashFilterFactory();
  }
  
  private static final String withStackContents =
      "<div class=\"block\">"
          + "<div class=\"welcome stackContents\">"
          + "<div class=\"stackedReverse\">"
          + "<div class=\"loginIdentity\">"
          + "Welcome"
          + "<a href=\"https://boguspublisherlibrary.org/action/showLogin?"
          + "uri=%2Ftoc%2Fijgnai%2F12%2F1\">"
          + "     Log in"
          + "</a>"
          + "<a href=\"https://boguspublisherlibrary.org/action/registration\">"
          + "    Register"
          + "</a>"
          + "<a href=\"https://secure.boguspublisher.org/BOGUSPUBLISHERWebSite/Secure/"
          + "CheckOut/AddToCart.aspx\">View Cart</a>"
          + "</div>"
          + "<ul class=\"linkList topLinks menu\">"
          + ""
          + "<li class=\"\">"
          + "         <a href=\"http://www.boguspublisher.org\" target=\"new\">BOGUSPUBLISHER</a>"
          + "     </li>"
          + "<li class=\"\">"
          + "         <a href=\"http://www.boguspublisher.org/About-Civil-Engineering/\""
          + " target=\"new\">About Civil Engineering</a>"
          + "     </li>"
          + "     <li class=\"\">"
          + "         <a href=\"/page/contactus\">Contact Us</a>"
          + "     </li>"
          + "     <li class=\"\">"
          + "         <a href=\"https://go.boguspublisher.org/forms/foundation/"
          + "boguspublisher-foundation\" target=\"new\">Donate Now</a>"
          + "     </li>"
          + "     <li class=\"\">"
          + "        <a href=\"/page/about/mobile\">Mobile</a>"
          + "     </li>"
          + "     <li class=\"\">"
          + "        <a href=\"http://www.boguspublisher.org/members/\""
          + " target=\"new\">MyBOGUSPUBLISHER</a>"
          + "     </li>"
          + "     <li class=\"\">"
          + "        <a href=\"http://www.boguspublisher.org/Content.aspx?id=2147487201\""
          + " target=\"new\">Shop BOGUSPUBLISHER</a>"
          + "     </li>"
          + "</ul>"
          + "</div>"
          + "</div>"
          + "</div>";
  
  private static final String withoutStackContents =
      "<div class=\"block\"></div>";
      
  private static final String withPrevNextNav =
      "<div class=\"block\">"
          + "<div id=\"prevNextNav\">"
          + "<div id=\"issueSearch\">"
          + "<form action=\"/action/doSearch\" method=\"get\">"
          + "<input type=\"text\" name=\"searchText\" value=\"\" size=\"17\">"
          + "<input type=\"hidden\" name=\"issue\" value=\"1\">"
          + "<input type=\"hidden\" name=\"journalCode\" value=\"ijgnai\">"
          + "<input type=\"hidden\" name=\"volume\" value=\"12\">"
          + "<input type=\"hidden\" name=\"filter\" value=\"issue\">"
          + "<input type=\"submit\" value=\"Search Issue\"></form>"
          + "</div>"
          + "<a href=\"javascript:toggleSlide('issueSearch')\">"
          + "Search Issue</a> |"
          + "<img src=\"/templates/jsp/_style2/_pagebuilder/_c3/"
          + "images/rss_32.png\">"
          + "<a href=\"http://boguspublisherlibrary.org/action/showFeed?"
          + "ui=0&amp;mi=3f0cur&amp;ai=s4&amp;jc=ijgnai&amp;type=etoc&amp;"
          + "feed=rss\">RSS</a>"
          + "<br>"
          + "<a href=\"/toc/ijgnai/11/6\">Previous Issue</a>"
          + "<a href=\"/toc/ijgnai/12/2\"> Next Issue</a>"
          + "</div>"
          + "</div>";

  private static final String withoutPrevNextNav =
      "<div class=\"block\"></div>";
  
  private static final String withTocTools =
      "<div class=\"block\">"
          + "<div id=\"tocTools\">"
          + "<div class=\"body\">"
          + "<span id=\"selected\">"
          + "<div id=\"selectedCount\">0</div>"
          + "<img src=\"/templates/jsp/_style2/_pagebuilder/_c3/"
          + "images/actionsarrow.png\">"
          + "</span>"
          + "<span>"
          + "<ul class=\"linkList blockLinks separators toclinks\">"
          + "<li class=\"noBorder label\">SELECTED:</li>"
          + "<li class=\"noBorder\">"
          + "<a href=\"javascript:submitArticles(document.frmAbs, "
          + "'/action/showCitFormats');\">Export Citations</a></li>"
          + "<li><a href=\"javascript:expandAbstracts();\">"
          + "Show/Hide Abstracts</a></li>"
          + "<li><a href=\"javascript:submitArticles(document.frmAbs, "
          + "'/personalize/addFavoriteArticle');\">Add to MyArticles</a></li>"
          + "<li><a href=\"javascript:submitArticles(document.frmAbs, "
          + "'/action/showMailPage');\">Email</a></li>"
          + "<li class=\"noBorder cart\">"
          + "<a href=\"https://secure.boguspublisher.org/BOGUSPUBLISHERWebSite/Secure/"
          + "CheckOut/AddToCart.aspx\">View</a>"
          + "<img class=\"cart-image\" src=\"/templates/jsp/_style2/"
          + "_pagebuilder/_c3/images/cartview.gif\">"
          + "</li>"
          + "</ul>"
          + "</span>"
          + "</div>"
          + "</div>"
          + "</div>";
  
  private static final String withoutTocTools =
      "<div class=\"block\"></div>";

  
  private static final String withToggle =
      "<div class=\"block\">"
          + "<td class=\"toggle\"><p>"
          + "<a href=\"javascript:showHideAbs"
          + "('10.1061/(BOGUSPUBLISHER)GM.1943-5622.0000074', "
          + "'AbsBOGUSPUBLISHERGM194356220000074', "
          + "'showAbsBOGUSPUBLISHERGM194356220000074');\">+</a></p></td>"
          + "</div>";
  
  private static final String withoutToggle =
      "<div class=\"block\"></div>";

  private static final String withDropzoneLeftSidebar =
      "<div class=\"block\">"
          + "<div class=\"dropzone ui-corner-all \" id=\"dropzone-Left-Sidebar\">"
          + "<div id=\"widget-11339\" class=\"widget type-ad-placeholder ui-helper-clearfix\">"
          + "<div class=\"view\">"
          + "<div class=\"view-inner\">"
          + "<a title=\"Rebuilding After a Tornado\" "
          + "href=\"/action/clickThrough?id=1245&amp;url=%2Fpage%2Fnhrefo%2F"
          + "rebuildingafteratornado&amp;loc=%2Fdoi%2Fabs%2F10.1061%2F%2528"
          + "BOGUSPUBLISHER%2529GM.1943-5622.0000040&amp;pubId=40224104\">"
          + "<center><img src=\"/sda/1245/Tornados.jpg\" "
          + "alt=\"Rebuilding After a Tornado\"></center></a><br>"
          + "</div>"
          + "</div>"
          + "</div>"
          + "</div>"
          + "</div>";

  private static final String withoutDropzoneLeftSidebar =
      "<div class=\"block\"></div>";
  
  private static final String withCitedBySection =
      "<div class=\"block\">"
          + "<div class=\"citedBySection\"><a name=\"citedBySection\"></a>"
          + "<br><br><h2>Cited by</h2>"
          + "<div class=\"citedByEntry\">"
          + "<a class=\"entryAuthor\" href=\"blahblah\"></a>"
          + "</div>"
          + "<div class=\"citedByEntry\">"
          + "<a class=\"entryAuthor\" href=\"blahAuthor\">"
          + "</div>"
          + "</div>"
          + "</div>";
  
  private static final String withoutCitedBySection =
      "<div class=\"block\"></div>";

  private static final String withFooter =
      "<div class=\"block\">"
          + "<div id=\"footer\">"
          + "<div class=\"footer\">"
          + "<img src=\"pubpartners1.jpg\" alt=\"pubpartner1\" "
          + "usemap=\"#pubpartner1.jpg\">"
          + "<map name=\"pubpartners1.jpg\">"
          + "<area shape=\"rect\" coords=\"184,50,277,92\" "
          + "href=\"http://www.blah.org/\" title=\"blah.org\" "
          + "alt=\"blah.org\">"
          + "</map>"
          + "<div id=\"copyright\">"
          + "<div id=\"footer_links_grey\"><span class=\"fontSize2\">"
          + "<span style=\"font-family: arial,helvetica,sans-serif;\">"
          + "<span style=\"color: rgb(0, 0, 0);\">"
          + "<a href=\"http://www.blahboguspublisher.org/\" target=\"_blank\">"
          + "Blahboguspublisher Home</a>&nbsp;&nbsp; | &nbsp; "
          + "<a href=\"http://blah.blah.org/\" target=\"_blank\">"
          + "Civil Engineering Database</a>&nbsp;&nbsp; |&nbsp;&nbsp; "
          + "<a href=\"http://www.boguspublisher.org/PPLContent.aspx?id=2147486529\" "
          + "target=\"_blank\">Bookstore</a>&nbsp;&nbsp; | &nbsp; "
          + "<a href=\"/page/journalsubscriptioninformation\" "
          + "target=\"_blank\">Subscribe</a> &nbsp; |&nbsp;&nbsp; "
          + "<a href=\"/page/termsofuse\" target=\"_blank\">"
          + "BOGUSPUBLISHER Library Terms of Use</a>&nbsp;&nbsp; | &nbsp; "
          + "<a href=\"/page/contactus\" target=\"_blank\">Contact Us</a>"
          + "<br>"
          + "</span></span></span></div>"
          + "</div>"
          + "<div id=\"footer_message\"><span style=\"color: rgb(0, 0, 0);\">"
          + "<span class=\"fontSize2\"><span style=\"color: rgb(0, 0, 0);\">"
          + "Copyright � 1996-2013, American So</span>"
          + "ciety of Civil Engineers</span>"
          + "<br>"
          + "</span></div>"
          + "<span style=\"color: rgb(0, 0, 0);\">"
          + "<span style=\"font-family: arial,helvetica,sans-serif;\">"
          + "<span class=\"fontSize2\"><a title=\"Copyright\" "
          + "href=\"http://www.boguspublisher.org/copyright\" target=\"_blank\">"
          + "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "
          + "BOGUSPUBLISHER Copyright</a> &nbsp; <a title=\"FAQs\" "
          + "href=\"http://www.boguspublisher.org/faqs\" target=\"_blank\">"
          + "BOGUSPUBLISHER FAQs</a>&nbsp;&nbsp; <a title=\"Privacy\" "
          + "href=\"http://www.boguspublisher.org/privacy\" target=\"_blank\">"
          + "BOGUSPUBLISHER Privacy</a>&nbsp;&nbsp; <a title=\"Questions\" "
          + "href=\"http://www.boguspublisher.org/questions\" target=\"_blank\">"
          + "BOGUSPUBLISHER Questions</a>&nbsp;&nbsp; "
          + "<a title=\"Terms &amp; Conditions\""
          + "href=\"http://www.boguspublisher.org/terms-conditions\""
          + "target=\"_blank\">BOGUSPUBLISHER Terms &amp; Conditions</a></span></span>"
          + "<br>"
          + "</span>"
          + "</div>"        
          + "</div>"
          + "</div>";

  private static final String withoutFooter =
      "<div class=\"block\"></div>";

  private static final String withMainMenu = 
      "<div class=\"mainMenu\">  <ul class=\"nav menu\"             <li class=\"\">"+
      "    <a href=\"/journals\">Journals</a>"+
      "</li>" +       
      "<li class=\"\">" +
      "    <a href=\"/ebooks\">E-Books</a>" +
      "</li>>"+
      "</div>Hello World";
  private static final String withoutMainMenu = "Hello World";
  
  private static final String withStylesheets = 
    "<link rel=\"stylesheet\" type=\"text/css\" href=\"/templates/jsp/_style2/_pagebuilder/reset-fonts-grids.css\">Hello World";
  private static final String withoutStylesheets ="Hello World";
  
  /*
   *  Compare Html and HtmlHashFiltered
   */
  
  public void testStackContentsHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(withStackContents), Constants.DEFAULT_ENCODING);
    assertEquals(withoutStackContents, StringUtil.fromInputStream(actIn));
  }

  public void testPrevNextNavHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(withPrevNextNav), Constants.DEFAULT_ENCODING);
    assertEquals(withoutPrevNextNav, StringUtil.fromInputStream(actIn));
  }
  
  public void testTocToolsHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(withTocTools), Constants.DEFAULT_ENCODING);
    assertEquals(withoutTocTools, StringUtil.fromInputStream(actIn));
  }

  public void testToggleHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(withToggle), Constants.DEFAULT_ENCODING);
    assertEquals(withoutToggle, StringUtil.fromInputStream(actIn));
  }

  public void testDropzoneLeftSidebarHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(withDropzoneLeftSidebar),
                              Constants.DEFAULT_ENCODING);
    assertEquals(withoutDropzoneLeftSidebar,
                 StringUtil.fromInputStream(actIn));
  }
  
  public void testCitedBySectionHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(withCitedBySection), Constants.DEFAULT_ENCODING);
    assertEquals(withoutCitedBySection, StringUtil.fromInputStream(actIn));
  }
        
  public void testFooterHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(withFooter), Constants.DEFAULT_ENCODING);
    assertEquals(withoutFooter, StringUtil.fromInputStream(actIn));
  }
  
  public void testWithStyleSheets() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(withStylesheets), Constants.DEFAULT_ENCODING);
    assertEquals(withoutStylesheets, StringUtil.fromInputStream(actIn));
  }
  
  public void testWithMainMenu() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(withMainMenu), Constants.DEFAULT_ENCODING);
    assertEquals(withoutMainMenu, StringUtil.fromInputStream(actIn));
  }
}
