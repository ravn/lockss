/*
 * $Id: TestTaylorAndFrancisHtmlFilterFactory.java,v 1.24 2015-01-27 22:30:13 thib_gc Exp $
 */

/*

Copyright (c) 2000-20154 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.taylorandfrancis;

import java.io.*;

import junit.framework.Test;

import org.apache.commons.lang.RandomStringUtils;
import org.lockss.util.*;
import org.lockss.plugin.FilterFactory;
import org.lockss.test.*;

public class TestTaylorAndFrancisHtmlFilterFactory extends LockssTestCase {

  public FilterFactory fact;

  private static MockArchivalUnit mau;

  //Example instances mostly from pages of strings of HTMl that should be filtered
  //and the expected post filter HTML strings. These are shared crawl & hash filters

  private static final String newsArticlesHtml =
      "<div id=\"newsArticles\" class=\"module widget\"><h2>Journal news</h2><ul><li>" +
          "<a href=\"http://www.tandfonline.com/doi/abs/10.1080/00018732.2011.598293\"><b>2010 Impact Factor: 21.214 / 2nd in Condensed Matter Physics</b></a>" +
          "</li></ul></div>";
  private static final String newsArticlesHtmlFiltered =
      "";

  private static final String relatedHtml =
      "<div id=\"relatedArticles\" class=\"module widget\"><h2>Related Articles</h2><div class=\"jqueryTab tabs clear\">" +
          "<ul class=\"tabsNav clear\"><li class=\"active\" id=\"readTab\"><a class='jslink' href=\"#read\">Most read</a>" +
          "</li></ul></div><div class=\"tabsPanel articleSummaries hide\" id=\"citedPanel\"></div>";
  private static final String relatedHtmlFiltered =
      "";

  private static final String moduleHtml =
      "<div class=\"ad module\"><!-- Literatum Advertisement --><!-- width:200 -->" +
          "<!-- placeholder id=null, description=Journal right column 1 --></div>";
  private static final String moduleHtmlFiltered =
      "";

  /**
   * Variant to test with Crawl Filter
   */
  public static class TestCrawl extends TestTaylorAndFrancisHtmlFilterFactory {

    public void testCorrections() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<a href=\"/doi/abs/10.1234/" + rand() + "\">Correction</a>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<a href=\"/doi/abs/10.1234/" + rand() + "\">  \n\n  Correction  \n\n  </a>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<a href=\"/doi/abs/10.1234/" + rand() + "\"><nobr>Correction</nobr></a>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<a href=\"/doi/abs/10.1234/" + rand() + "\">Corrigendum</a>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<a href=\"/doi/abs/10.1234/" + rand() + "\">Original Article</a>"),
              Constants.DEFAULT_ENCODING)));
    }

    // This is crawled out but not hashed out - list of references for an article could lead to other T&F content outside this AU
    private static final String referencesHtml =
        "<div class=\"tabsPanel hide\" id=\"fulltextPanel\">" +
            "    </div>" +
            "    <div class=\"tabsPanel \" id=\"referencesPanel\">" +
            "        <div class=\"gutter\"><div class=\"summationSection\"><ul class=\"references\">" +
            "<li id=\"CIT0001\"><strong>1." +
            "</strong> <a href=\"/action/doSearch?action=blah\">Borg, Simon</a>. " +
            "<span class=\"NLM_year\">1998</span>. TITLE, 7(4): 159�175.   " +
            "<a href=\"/servlet/linkout?suffix=CIT0001&amp;dbid=20&amp;doi=10.1080%2FXXXX&amp;key=10.1080%2FXXXX\" target=\"_blank\">[Taylor &amp;Francis Online]</a>, " +
            "</li><li id=\"CIT0002\"><strong>2." +
            "</strong> <a href=\"/action/doSearch?action=blah\">Borg, Simon</a>. " +
            "<span class=\"NLM_year\">2003</span>. Another Title, 36: 81�109.   " +
            "<a href=\"/servlet/linkout?suffix=CIT0002&amp;dbid=16&amp;doi=10.1080%2FJJJJ&amp;key=10.1017%2FJJJJ\" target=\"_blank\">[CrossRef]</a>" +
            "</li>" +
            "</ul></div></div> </div>" +
            "<div class=\"tabsPanel hide\" id=\"permissionsPanel\">" +
            "" +
            "</div>DONE";

    private static final String referencesFiltered =
        "<div class=\"tabsPanel hide\" id=\"fulltextPanel\">" +
            "    </div>" +
            "    " +
            "<div class=\"tabsPanel hide\" id=\"permissionsPanel\">" +
            "" +
            "</div>DONE";

    // unmarked links to corrigendum <--> original article on abstract and full text pages
    private static final String articleCorrigendumListing =
        "<div class=\"access\">" +
            "<ul class=\"clear\">" +
            "<li><a class=\"txt\" href=\"/doi/full/10.1080/19325611003733229\">View full text</a></li>" +
            "<li><a class=\"pdf\" target=\"_blank\" href=\"/doi/pdf/10.1080/19325611003733229\">Download full text</a></li>" +
            "<li><a href=\"/doi/abs/10.1080/19325610903015661\"><nobr>Original Article</nobr></a></li>" +
            "</ul>" +
            "</div>" +
            "<div class=\"access\">" +
            "<ul class=\"clear\">" +
            "<li><a class=\"txt\" href=\"/doi/full/10.1080/19325610903015661\">View full text</a></li>" +
            "<li><a class=\"pdf\" target=\"_blank\" href=\"/doi/pdf/10.1080/19325610903015661\">Download full text</a></li>" +
            "<li><a href=\"/doi/abs/10.1080/19325611003733229\"><nobr>Correction</nobr></a></li>" +
            "</ul>" +
            "</div>";
    private static final String articleCorrigendumFiltered = 
        "<div class=\"access\">" +
            "<ul class=\"clear\">" +
            "<li><a class=\"txt\" href=\"/doi/full/10.1080/19325611003733229\">View full text</a></li>" +
            "<li><a class=\"pdf\" target=\"_blank\" href=\"/doi/pdf/10.1080/19325611003733229\">Download full text</a></li>" +
            "<li></li>" +
            "</ul>" +
            "</div>" +
            "<div class=\"access\">" +
            "<ul class=\"clear\">" +
            "<li><a class=\"txt\" href=\"/doi/full/10.1080/19325610903015661\">View full text</a></li>" +
            "<li><a class=\"pdf\" target=\"_blank\" href=\"/doi/pdf/10.1080/19325610903015661\">Download full text</a></li>" +
            "<li></li>" +
            "</ul>" +
            "</div>";

    private static final String inLineRefHtml =
        "<div>" +
            "The article starts and then. In a previous paper, Author et al. " +
            "(<span class=\"referenceDiv\">" +
            "<a class=\"dropDownLabel\" href=\"#CIT0020\">" +
            "2011</a>" +
            "<!--RIDCIT0020-->" +
            "<span class=\"dropDown dropDownAlt\" style=\"left: -17px; top: 22px\">" +
            "<span class=\"blockspan bd\">" +
            "<strong>" +
            "20." +
            "</strong>" +
            "Author  ,   Q. 2011  . Title of Referenced Article  .   Acta  ,   1  �  19  .  " +
            "Accessed 16 December 2011, available at: " +
            "<a href=\"http://www.tandfonline.com/doi/full/10.1080/09064702.2011.642000\" target=\"_blank\">" +
            "http://www.tandfonline.com/doi/full/10.1080/09064702.2011.642000</a>" +
            "   .<br>" +
            "</br>" +
            "<br>" +
            "</br>" +
            "<a href=\"/servlet/linkout?suffix=&amp;dbid=16384&amp;doi=10.1080/09064702.2012.670665\" " +
            "title=\"OpenURL University of British Columbia\" onclick=\"newWindow(this.href);return false\" " +
            "class=\"sfxLink\">" +
            "<img src=\"/userimages/98671/sfxbutton\" alt=\"OpenURL University of British Columbia\" />" +
            "</a>" +
            "<a href=\"/servlet/linkout?suffix=&amp;dbid=16384&amp;doi=10.1080/09064702.2012.670665\" " +
            "title=\"OpenURL Stanford University\" onclick=\"newWindow(this.href);return false\" " +
            "class=\"sfxLink\">" +
            "<img src=\"/userimages/98617/sfxbutton\" alt=\"OpenURL Stanford University\" />" +
            "</a>" +
            "<a href=\"#inline_references\">" +
            "View all references</a>" +
            "</span>" +
            "<span class=\"blockspan ft\">" +
            "</span>" +
            "</span>" +
            "</span>" +
            ") and the rest of the article continues</div>";
    private static final String inLineRefFiltered =
        "<div>" +
            "The article starts and then. In a previous paper, Author et al. " +
            "(" +
            ") and the rest of the article continues</div>";

    private static final String fullRefListing = 
        "<div>" +
            "<ul class=\"references\">" +
            "<li id=\"CIT0001\">" +
            "<strong>" +
            "20." +
            "</strong>" +
            "   <span class=\"NLM_year\">" +
            "2011</span>" +
            "  .   Title of Referenced Article .   Reference Journal  ,   1  �  19  .     Accessed 16 December 2011, available at: " +
            "<a href=\"http://www.tandfonline.com/doi/full/10.1080/09064702.2011.642000\" target=\"_blank\">" +
            "http://www.tandfonline.com/doi/full/10.1080/09064702.2011.642000</a>" +
            "   . <a href=\"/servlet/linkout?suffix=CIT0020&amp;dbid=16384&amp;doi=10.1080/09064702.2012.670665\" " +
            "title=\"OpenURL University of British Columbia\" onclick=\"newWindow(this.href);return false\" class=\"sfxLink\">" +
            "<img src=\"/userimages/98671/sfxbutton\" alt=\"OpenURL University of British Columbia\" />" +
            "</a>" +
            "</li>" +
            "</ul>" +
            "</div>";

    private static final String fullRefListingFiltered =
        "<div>" +
            "</div>";
    
    private static final String issueTOCNav=
        "<div class=\"hd\">" +
            "                    <h2>" +
            "                    <a href=\"/loi/xxx20?open=7#vol_7\">Volume 7</a>," +
            "            Issue 1," +
            "2013" +
            "<span style=\"float: right;margin-right: 5px\">" +
            "        <a title=\"Previous issue\" href=\"http://www.tandfonline.com/toc/xxx20/6/1\">&lt; Prev</a>" +
            "        |" +
            "        <a title=\"Next issue\" href=\"http://www.tandfonline.com/toc/xxx20/8/1\">Next &gt;</a>" +
            "</span>" +
            "                    </h2>" +
            "                </div>";
    private static final String issueTOCNavFiltered=
"";

    private static final String breadcrumb=
        "<div id=\"breadcrumb\">" +
            " <a href=\"/\">Home</a>" +
            "       &gt; " +
            "        <a href=\"/loi/xxx20\">List of Issues</a>" +
            "         &gt; " +
            "        <a href=\"/toc/xxx20/7/1\">Table Of Contents</a>" +
            "         &gt; " +
            "        A Critical Assessment" +
            "</div>";
    private static final String breadcrumbFiltered=
        "";
    public void setUp() throws Exception {
      super.setUp();
      fact = new TaylorAndFrancisHtmlCrawlFilterFactory();
    }

    public void testReferencesFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(referencesHtml),
              Constants.DEFAULT_ENCODING);
      assertEquals(StringUtil.fromInputStream(actIn),
          referencesFiltered);
    }

    public void testCorrigendumFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(articleCorrigendumListing),
              Constants.DEFAULT_ENCODING);
      assertEquals(StringUtil.fromInputStream(actIn),
          articleCorrigendumFiltered);
    }

    public void testMoreReferences() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(inLineRefHtml),
              Constants.DEFAULT_ENCODING);
      assertEquals(StringUtil.fromInputStream(actIn),
          inLineRefFiltered);

      actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(fullRefListing),
              Constants.DEFAULT_ENCODING);
      assertEquals(StringUtil.fromInputStream(actIn),
          fullRefListingFiltered);
    } 
    
    public void testNavFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(issueTOCNav),
              Constants.DEFAULT_ENCODING);
      assertEquals(StringUtil.fromInputStream(actIn),
          issueTOCNavFiltered);

      actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(breadcrumb),
              Constants.DEFAULT_ENCODING);
      assertEquals(StringUtil.fromInputStream(actIn),
          breadcrumbFiltered);
    } 

  }

  /**
   * Variant to test with Hash Filter
   */
  public static class TestHash extends TestTaylorAndFrancisHtmlFilterFactory {

    //Example instances mostly from pages of strings of HTMl that should be filtered
    //and the expected post filter HTML strings.
    private static final String sfxLinkHtmlHash = 
        "<a class=\"sfxLink\"></a>";
    private static final String sfxLinkHtmlHashFiltered = 
        "";

    private static final String brandingHtmlHash = 
        "<div id=\"hd\">\n<div id=\"branding\">XYZ</div>\n</div>";
    private static final String brandingHtmlHashFiltered = 
        "";

    private static final String rssHtmlHash = 
        "<link type=\"application/rss+xml\" rel=\"hello\" href=\"world\"/>";
    private static final String rssHtmlHashFiltered = 
        "";

    private static final String creditsHtmlHash = 
        "<div id=\"ft\">\n<div class=\"credits\">\n<p>(c) 2012</p>\n</div>\n</div>";
    private static final String creditsHtmlHashFiltered = 
        "";

    private static final String rssLinkHtmlHash = 
        "<a href=\"/example&feed=rss&foo=bar\">Example</a>";
    private static final String rssLinkHtmlHashFiltered = 
        "";

    private static final String accessHtmlHash = 
        "<div class=\"foo accessIconWrapper bar\">Yes</div>";
    private static final String accessHtmlHashFiltered = 
        "";

    private static final String linkoutHtmlHash = 
        "<a href=\"/servlet/linkout?yes=no&foo=bar\">Example</a>";
    private static final String linkoutHtmlHashFiltered = 
        "";

    private static final String articleUsageHtmlHash =
        "<div id=\"footer\">"
            + "<div class=\"block-1\">"
            + "<div class=\"articleUsage\">"
            + "<strong>Article Views:</strong> 179"
            + "<div class=\"auPopUp hidden\">"
            + "<div class=\"pointyEdge\">"
            + "</div>Article usage statistics combine cumulative total PDF"
            + " downloads and full-text HTML views from publication"
            + " date (but no earlier than 25 Jun 2011, launch date of"
            + " this website) to 08 Oct 2012. Article views are only"
            + " counted from this site. Although these data are updated"
            + " every 24 hours, there may be a 48-hour delay before the"
            + " most recent numbers are available."
            + "</div>"
            + "</div>"
            + "<div class=\"block-2 sb-div\"></div>"
            + "</div>";

    private static final String articleUsageHtmlHashFiltered = "";


    private static final String javascriptHtmlHash = 
        "<noscript> onversion/1070139620/?label=_s1RCLTo-QEQ5JGk_gM&amp;amp;guid=ON&amp;amp; </noscript>\n" +
            "<script type=\"text/javascript\" src=\"http://nejm.resultspage.com/autosuggest/searchbox_suggest_v1.js\" language=\"javascript\">Hello</script>";
    // Trailing space is due to the WhiteSpaceFilter
    private static final String javascriptHtmlHashFiltered = 
        " ";

    private static final String googleStuffHtml = 
        "<td colspan=\"1\" style=\"border-style: none\">" +
            "<div>" +
            "<!-- placeholder id=null, description=abstract-googleTranslate -->" +
            "<div id=\"google_translate_element\">" +
            "<div class=\"skiptranslate goog-te-gadget\" dir=\"ltr\" style=\"\">" +
            "<div id=\":0.targetLanguage\" style=\"white-space: nowrap; \" class=\"goog-te-gadget-simple\">" +
            "</div></div></div>" +
            "<script type=\"text/javascript\">" +
            "function googleTranslateElementInit() {" +
            "  new google.translate.TranslateElement({pageLanguage: 'en', layout: google.translate.TranslateElement.InlineLayout.SIMPLE, gaTrack: true, gaId: 'foo'}, 'google_translate_element');" +
            "}" +
            "</script>" +
            "<script type=\"text/javascript\" src=\"//translate.google.com/translate_a/element.js?cb=googleTranslateElementInit\"></script>" +
            "<a href=\"/action/clickThrough?id=blah\">Translator&nbsp;disclaimer</a>" +
            "</div>" +
            "<div id=\"goog-gt-tt\" class=\"skiptranslate\" dir=\"ltr\">" +
            "<div style=\"padding: 8px;\">" +
            "<div>" +
            "<div class=\"logo\"><img src=\"http://www.foo.png\" width=\"20\" height=\"20\"></div>" +
            "<iframe style=\"width:348px;height:1.1em;float:right;\" id=\"signin_status\" border=\"0\" frameborder=\"0\" " +
            "src=\"//translate.google.com/translate/tminfo?continue=http%3A%2F%2Ftranslate.google.com%2Fmanager%2Fwebsite%2Fstatic_files%2Fhtml%2Freload.html\"></iframe>" +
            "</div></div>" +
            "<div class=\"top\" style=\"padding: 8px; float: left; width: 100%;\"><h1 class=\"title gray\">Original text</h1></div>" +
            "<div class=\"middle\" style=\"padding: 8px;\">" +
            "<div class=\"original-text\"></div></div>" +
            "<div class=\"bottom\" style=\"padding: 8px;\">" +
            "<div class=\"activity-links\"><span class=\"activity-link\">Contribute a better translation</span><span class=\"activity-link\"></span></div>" +
            "<div class=\"started-activity-container\"><hr style=\"color: #CCC; background-color: #CCC; height: 1px; border: none;\">" +
            "<div class=\"activity-root\"></div></div></div>" +
            "<div class=\"status-message\" style=\"display: none; \"></div>" +
            "</div>" +
            "</td>";
    private static final String googleStuffFiltered = "";

    private static final String socialMediaHtml = 
        "<div class=\"social clear\">" +
            "<ul>" +
            " <li class=\"share\">" +
            "   </li>" +
            "   <li class=\"add\">" +
            "   <a href=\"/action/addFavoritePublication?doi=10.1080%2Fblah\">" +
            "     Add to shortlist" +
            "   </a>" +
            "    </li>" +
            "    <li class=\"link\">" +
            "    <a href=\"#\" class=\"permaLink dropDownLabel\">" +
            "    Link" +
            "    </a>" +
            "    <div class=\"dropDown dropDownAlt\">" +
            "    <div class=\"bd\">" +
            "    <h6>Permalink</h6>" +
            "    <p>" +
            "     <a href=\"http://dx.doi.org/10.1080/blah\" class=\"url\">" +
            "    http://dx.doi.org/10.1080/blah" +
            "    </a>" +
            "    </p>" +
            "    </div>" +
            "    <div class=\"ft\"></div>" +
            "    <div class=\"shadow\"></div>" +
            "    </div>" +
            "    </li>" +
            "    <li class=\"cite\">" +
            "    <a href=\"/action/showCitFormats?doi=10.1080%2Fblah\">" +
            "    Download Citation" +
            "    </a>" +
            "   </li>" +
            "  </ul>" +
            "  <ul class=\"recommend\">" +
            "  <li class=\"rec\">" +
            "        Recommend to:" +
            "    </li>" +
            "  <li class=\"friend last\">" +
            "    <a href=\"/action/showMailPage?href=%2Fdoi%2Fabs%2F10.1080%2Fblah&amp;title=Glorious+Adventure&amp;type=article&amp;doi=10.1080%2Fblah\">" +
            "     A friend" +
            "    </a>" +
            "   </li>" +
            "  </ul>" +
            "</div>KEEP ME";

    private static final String socialMediaFiltered = 
        "KEEP ME";

    private static final String secondaryHtml =
        "<div class=\"secondarySubjects module grid3 clear\">" +
            "<div class=\"subUnit\">" +
            "    <h3>Librarians</h3>" +
            "<ul>" +
            "    <li>" +
            "    ONE" +
            "    </li>" +
            "    <li>" +
            "    TWO" +
            "    </li>" +
            "</ul>" +
            "</div>" +
            "<div class=\"subUnit\">" +
            "    <h3>Help and Information</h3>" +
            "<ul>" +
            "    <li>" +
            "    ONE" +
            "    </li>" +
            "    <li>" +
            "    TWO" +
            "    </li>" +
            "</ul>" +
            "</div>" +
            "</div>";

    private static final String secondaryFiltered =
        "";

    private static final String tocEntry = 
        "<!--ARTICLE: primary_article-->" +
            "<div class=\"article original as2\">" +
            "<div class=\"gutter\">" +
            "<label for=\"10.1080/xxxx\">" +
            "<a class=\"entryTitle\" href=\"/doi/full/10.1080/xxxx\">" +
            "<h3>" +
            "TITLE OF ARTICLE</h3>" +
            "</a>" +
            "</label>" +
            "<span class=\"subtitle\">" +
            "</span>" +
            "<h4>" +
            "<a href=\"/action/doSearch?Contrib=author\">" +
            "Author</a>" +
            " &amp; <a href=\"/action/doSearch?otherauthor\">" +
            "OtherAuthor</a>" +
            "<br />" +
            "pages 1-60</h4>" +
            "<div class=\"access accessmodule access_full\">" +
            "<a class=\"txt\" href=\"/doi/full/10.1080/xxxx\">" +
            "View full text</a>" +
            "<a class=\"pdf last\" target=\"_blank\" href=\"/doi/pdf/10.1080/xxxx\">" +
            "Download full text</a>" +
            "<div class=\"accessIconWrapper access_full accessIcon\">" +
            "Full access</div>" +
            "</div>" +
            "<ul class=\"clear doimetalist\">" +
            "<li>" +
            "<strong>" +
            "DOI:</strong>" +
            "10.1080/xxxx</li>" +
            "<li>" +
            "<strong>" +
            "Published online:</strong>" +
            " 25 Jan 2013</li>" +
            "<li>" +
            "<div>" +
            "<strong>" +
            "Citing Articles:</strong>" +
            "</div>" +
            "<a href=\"/doi/citedby/10.1080/xxxx\" >" +
            "CrossRef (14)</a>" +
            "&nbsp; | <a target='_blank' href=\"http://gateway.webofknowledge.com/gateway/Gateway.cgi\" >" +
            "Web of Science (9)</a>" +
            "&nbsp; | <a target='_blank' href=\"http://www.scopus.com/inward/citedby.url?partnerID=xxx\" >" +
            "Scopus (10)</a>" +
            "&nbsp;</li>" +
            "<li>" +
            "<div class=\"articleUsage\">" +
            "<strong>" +
            "Article Views:</strong>" +
            " 5712<div class=\"auPopUp hidden\">" +
            "<div class=\"pointyEdge\">" +
            "</div>" +
            "description</div>" +
            "</div>" +
            "<noscript>" +
            "<br/>" +
            "<div class=\"articleUsage\">" +
            "description" +
            "</div>" +
            "</noscript>" +
            "</li>" +
            "</ul>" +
            "<div class=\"ft\">" +
            "<h6>" +
            "Further Information</h6>" +
            "<ul class=\"entryLinks clear\">" +
            "<li class=\"firstEntryLink\">" +
            "<a href=\"/doi/abs/10.1080/xxxx?queryID=%24%7BresultBean.queryID%7D\">" +
            "Abstract</a>" +
            "</li>" +
            "<li>" +
            "<a href=\"/doi/ref/10.1080/xxxx\">" +
            "References</a>" +
            "</li>" +
            "<li class=\"relatedArticleLink\">" +
            "<a href=\"/doi/mlt/10.1080/xxxx\">" +
            "Related articles" +
            "</a>" +
            "</li>" +
            "</ul>" +
            "</div>" +
            "<div class=\"clear floatclear\">" +
            "</div>" +
            "</div>" +
            "</div>";

    private static final String tocEntryFiltered = 
            "TITLE OF ARTICLEAuthor & OtherAuthorpages 1-60"
            + "DOI:10.1080/xxxx 25 Jan 2013Further Information"
            + "AbstractReferences";

    // note lower case of "articles"
    private static final String citArticlesCase1 =
    "<li>" +
    "<strong>" +
    "Published online:</strong>" +
    " 25 Jan 2013</li>" +
    "<li>" +
    "<div>" +
    "<strong>" +
    "Citing articles:</strong> 0</div>" +
    "</li>";
    
    // note upper case of "Articles" when there are results
    private static final String citArticlesCase2 =
    "<li>" +
    "<strong>" +
    "Published online:</strong>" +
    " 25 Jan 2013</li>" +
    "<li>" +
    "<div>" +
    "<strong>" +
    "Citing Articles:</strong>" +
    "</div>" +
    "<a href=\"/doi/citedby/10.1080/xxxx\" >" +
    "CrossRef (14)</a>" +
    "&nbsp; | <a target='_blank' href=\"http://gateway.webofknowledge.com/gateway/Gateway.cgi\" >" +
    "Web of Science (9)</a>" +
    "&nbsp; | <a target='_blank' href=\"http://www.scopus.com/inward/citedby.url?partnerID=xxx\" >" +
    "Scopus (10)</a>" +
    "&nbsp;</li>";
   
    public void setUp() throws Exception {
      super.setUp();
      fact = new TaylorAndFrancisHtmlHashFilterFactory();
    }

    public void testSfxLinkHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(sfxLinkHtmlHash),
              Constants.DEFAULT_ENCODING);
      assertEquals(sfxLinkHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testBrandingHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(brandingHtmlHash),
              Constants.DEFAULT_ENCODING);
      assertEquals(brandingHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testRssLinkHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(rssLinkHtmlHash),
              Constants.DEFAULT_ENCODING);
      assertEquals(rssLinkHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testCreditsHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(creditsHtmlHash),
              Constants.DEFAULT_ENCODING);
      assertEquals(creditsHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testarticleUsageHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau, 
              new StringInputStream(articleUsageHtmlHash),
              Constants.DEFAULT_ENCODING);
      assertEquals(articleUsageHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testJavascriptHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau, 
              new StringInputStream(javascriptHtmlHash),
              Constants.DEFAULT_ENCODING);
      assertEquals(javascriptHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testAccessHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau, 
              new StringInputStream(accessHtmlHash),
              Constants.DEFAULT_ENCODING);
      assertEquals(accessHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testLinkoutHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau, 
              new StringInputStream(linkoutHtmlHash),
              Constants.DEFAULT_ENCODING);
      assertEquals(linkoutHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testCitations() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<li><strong>Citations:" + rand() + "\n\n\n</strong></li>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<li><strong><a href=\"/doi/citedby/10.1080/13607863.2010.551339\">Citations:" + rand() + "\n\n\n</strong></li>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testDivIdContent() throws Exception {
      assertEquals("ABCDEFGHIJKLM",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div id=\"content\">ABCDEFGHIJKLM</div>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("ABCDEFGHIJKLM",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div id=\"journal_content\">ABCDEFGHIJKLM</div>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("ABCDEFGHIJKLM",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div id=\"tandf_content\">ABCDEFGHIJKLM</div>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("ABCDEFGHIJKLM",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<a href=\"#content\">ABCDEFGHIJKLM</a>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("ABCDEFGHIJKLM",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<a href=\"#tandf_content\">ABCDEFGHIJKLM</a>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testOverlay() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div id=\"overlay\">" + rand() + "</div>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div class=\"overlay clear overlayHelp\">" + rand() + "</div>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals(" ", // Note the single space (WhiteSpaceFilter)
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("\n\n\n   <div id=\"overlay\">" + rand() + "</div>   \n\n\n   <div class=\"overlay clear overlayHelp\">" + rand() + "</div>   \n\n\n"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testDivAlertDiv() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div id=\"alertDiv\">" + rand() + "</div>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div class=\"alertDiv\">" + rand() + "</div>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div class=\"script_only alertDiv\">" + rand() + "</div>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testIdFpi() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<a href=\"\" id=\"fpi\">" + rand() + "</a>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div id=\"fpi\">" + rand() + "</div>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testCitationsTab() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<li id=\"citationsTab\">" + rand() + "</li>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testCitationsPanel() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div id=\"citationsPanel\">" + rand() + "</div>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testHtmlComments() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<!-- " + rand() + "-->"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testStyle() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<style>" + rand() + "</style>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testHd() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div id=\"hd\">\n" + rand() + "\n</div>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testFt() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div id=\"ft\">\n" + rand() + "\n</div>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testRelatedLink() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<a class=\"relatedLink\" href=\"/action/doSearch?target=related&amp;doi=10.0000/123456789\">" + rand() + "</a>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testDoiMeta() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<div class=\"doiMeta " + rand() + "\">\n" + rand() + "\n</div>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testSingleHighlightColor() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<input type=\"hidden\" id=\"singleHighlightColor\" value=\"true\" />"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testVersionOfRecordFirstPublished() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<strong>Published online:</strong>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<strong>Available online:</strong>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<strong>Version of record first published:</strong>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testImgClassCover() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<p><img src=\"" + rand() + "\" alt=\"" + rand() + "\" class=\"cover\"/></p>"),
              Constants.DEFAULT_ENCODING)));
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<p><img src=\"" + rand() + "\" alt=\"" + rand() + "\" class=\"cover\" /></p>"),
              Constants.DEFAULT_ENCODING)));
    }

    public void testGoogleStuff() throws Exception {
      assertEquals(googleStuffFiltered,
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream(googleStuffHtml),
              Constants.DEFAULT_ENCODING)));
    }
    public void testSocialMedia() throws Exception {
      assertEquals(socialMediaFiltered,
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream(socialMediaHtml),
              Constants.DEFAULT_ENCODING)));
    }
    public void testBottomRemoval() throws Exception {
      assertEquals(secondaryFiltered,
          StringUtil.fromInputStream(fact.createFilteredInputStream(null, 
              new StringInputStream(secondaryHtml),
              Constants.DEFAULT_ENCODING)));
    }

    public void testHead() throws Exception {
      assertEquals("",
          StringUtil.fromInputStream(fact.createFilteredInputStream(null,
              new StringInputStream("<html>"
                  + "<head>"
                  + "<title>" + rand() + "</title>"
                  + "<link type=\"application/rss+xml\" rel=\"hello\" href=\"world\"/>"
                  + "<link rel=\"stylesheet\" type=\"text/css\" media=\"screen, projection\" href=\"/cssJawr/1107879557/style.css\" />"
                  + "</head>"
                  + "</html>"),
                  Constants.DEFAULT_ENCODING)));

    }

    public void testTocEntry() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
              new StringInputStream(tocEntry),
              Constants.DEFAULT_ENCODING);
      assertEquals(tocEntryFiltered, StringUtil.fromInputStream(actIn));
      InputStream inA =
          fact.createFilteredInputStream(mau,
              new StringInputStream(citArticlesCase1),
              Constants.DEFAULT_ENCODING);
      InputStream inB =
          fact.createFilteredInputStream(mau,
              new StringInputStream(citArticlesCase2),
              Constants.DEFAULT_ENCODING);
      assertEquals(StringUtil.fromInputStream(inA), StringUtil.fromInputStream(inB));

    }
    
  }

  public static Test suite() {
    return variantSuites(new Class[] {
        TestCrawl.class,
        TestHash.class
    });
  }

  public void testRelatedHtmlFiltering() throws Exception {
    InputStream actIn =
        fact.createFilteredInputStream(mau,
            new StringInputStream(relatedHtml),
            Constants.DEFAULT_ENCODING);
    assertEquals(relatedHtmlFiltered, StringUtil.fromInputStream(actIn));
  }

  public void testNewsArticlesHtmlFiltering() throws Exception {
    InputStream actIn =
        fact.createFilteredInputStream(mau,
            new StringInputStream(newsArticlesHtml),
            Constants.DEFAULT_ENCODING);
    assertEquals(newsArticlesHtmlFiltered, StringUtil.fromInputStream(actIn));
  }

  public void testModuleHtmlFiltering() throws Exception {
    InputStream actIn =
        fact.createFilteredInputStream(mau, 
            new StringInputStream(moduleHtml),
            Constants.DEFAULT_ENCODING);
    assertEquals(moduleHtmlFiltered, StringUtil.fromInputStream(actIn));
  }

  protected static String rand() {
    return RandomStringUtils.randomAlphabetic(30);
  }

}
