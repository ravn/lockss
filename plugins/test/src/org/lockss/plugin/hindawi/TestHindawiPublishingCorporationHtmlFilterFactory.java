/*
 * $Id: TestHindawiPublishingCorporationHtmlFilterFactory.java,v 1.10 2013-10-22 20:51:22 thib_gc Exp $
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

package org.lockss.plugin.hindawi;

import java.io.*;

import org.lockss.util.*;
import org.lockss.test.*;

public class TestHindawiPublishingCorporationHtmlFilterFactory extends LockssTestCase {
  
  private HindawiPublishingCorporationHtmlFilterFactory fact;
  
  public void setUp() throws Exception {
    super.setUp();
    fact = new HindawiPublishingCorporationHtmlFilterFactory();
  }
  
  private static final String[] DOCTYPE_STATEMENTS = {
    "<!DOCTYPE html>",
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD MathML 2.0//EN\" \"http://www.w3.org/Math/DTD/mathml2/mathml2.dtd\">",
  };
  
  private static final String[] HTML_TAGS = {
    "<html xmlns=\"http://www.w3.org/1999/xhtml\">",
    "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:mml=\"http://www.w3.org/1998/Math/MathML\">",
  };
  
  private static final String[] PRE_TAGS = {
    "<pre>Journal of Foo<br/>Volume 1 (2001), Issue 2, Pages 33-44</pre>",
    "<pre>Journal of Foo<br />Volume 1 (2001), Issue 2, Pages 33-44</pre>",
  };
  
  private static final String[] LICENSE_STATEMENTS = {
    "<p>Copyright &copy; 2001 Author N. One et al. This is an open access article distributed under the <a rel=\"license\" href=\"http://creativecommons.org/licenses/by/3.0/\">Creative Commons Attribution License</a>, which permits unrestricted use, distribution, and reproduction in any medium, provided the original work is properly cited.</p>",
    "<p>Copyright &copy; 2001 Author N. One et al. This is an open access article distributed under the <a rel=\"license\" href=\"http://creativecommons.org/licenses/by/3.0/\">Creative Commons Attribution License</a>, which permits unrestricted use, distribution, and reproduction in any medium, provided the original work is properly cited. </p>",
  };
  
  private static final String[] XML_CONTENTS_TAGS = {
    "<div class=\"xml-content\">",
    "<div id=\"divXMLContent\" class=\"xml-content\">",
    "<div id=\"ctl00_ContentPlaceHolder1_divXMLContent\" class=\"xml-content\">",
  };
  
  private static final String PAGE_TEMPLATE =
      "@DOCTYPE_STATEMENT\n" +
      "@HTML_TAG\n" +
      "<head>\n" +
      "  <meta charset=\"UTF-8\" />\n" +
      "  <title>!!!title!!!</title>\n" +
      "  <link href=\"/stylesheet1.css\" rel=\"stylesheet\" type=\"text/css\" />\n" +
      "  <meta name=\"key1\" content=\"value1\"/>\n" + 
      "  <script type=\"text/javascript\" src=\"/script1.js\" />\n" + 
      "  <script type=\"text/javascript\">!!!javascript!!!</script>\n" + 
      "</head>\n" +
      "<body>\n" +
      "  <div id=\"container\">\n" +
      "    <div id=\"site_head\">!!!site_head!!!</div>\n" +
      "    <div id=\"dvLinks\" class=\"hindawi_links\">!!!dvLinks!!!</div>\n" +
      "    <div id=\"ctl00_dvLinks\" class=\"hindawi_links\">!!!ctl00_dvLinks!!!</div>\n" +
      "    <div id=\"banner\">!!!banner!!!</div>\n" +
      "    <div id=\"journal_navigation\">!!!journal_navigation!!!</div>\n" +
      "    <div id=\"content\">\n" +
      "      <div id=\"left_column\">!!!left_column!!!</div>\n" +
      "      <div id=\"middle_content\">\n" + 
      "        <div class=\"right_column_actions\">!!!right_column_actions!!!</div>" +
      "        <div>\n" + 
      "          @PRE_TAG\n" + 
      "          <div class=\"article_type\">Research Article</div>\n" +
      "          <h2>!!!h2!!!</h2>\n" +
      "          <div class=\"author_gp\">!!!author_gp!!!</div>\n" +
      "          <p>!!!_author_affiliations!!!</p>\n" +
      "          <p>Received 1 January 2001; Accepted 15 January 2001</p>\n" +
      "          <p>Academic Editor: John Q. Smith </p>\n" +
      "          <div class=\"xml-content\">@LICENSE_STATEMENT</div>\n" +
      "          @XML_CONTENT_TAG!!!_article_contents!!!</div>\n" +
      "        </div>\n" +
      "      </div>\n" +
      "    </div>\n" +
      "    <div class=\"footer_space\">!!!footer_space!!!</div>\n" +
      "  </div>\n" +
      "  <div id=\"footer\">!!!footer!!!</div>\n" +
      "</body>\n" +
      "</html>\n";
  
  private static final String RESULT = " Journal of FooVolume 1 (2001), Issue 2, Pages 33-44 Research Article !!!h2!!! !!!author_gp!!! !!!_author_affiliations!!! Received 1 January 2001; Accepted 15 January 2001 Academic Editor: John Q. Smith Copyright &copy; 2001 Author N. One et al. This is an open access article distributed under the Creative Commons Attribution License, which permits unrestricted use, distribution, and reproduction in any medium, provided the original work is properly cited. @XML_CONTENT_TAG!!!_article_contents!!! ";

  public void testFilterWithTemplate() throws Exception {
    String input = PAGE_TEMPLATE;
    for (String doctypeStatement : DOCTYPE_STATEMENTS) {
      for (String htmlTag : HTML_TAGS) {
        for (String preTag : PRE_TAGS) {
          for (String licenseStatement : LICENSE_STATEMENTS) {
            for (String xmlContentsTag : XML_CONTENTS_TAGS) {
              input = PAGE_TEMPLATE.replaceAll("@DOCTYPE_STATEMENT", doctypeStatement)
                                   .replaceAll("@HTML_TAG", htmlTag)
                                   .replaceAll("@PRE_TAG", preTag)
                                   .replaceAll("@LICENSE_STATEMENT", licenseStatement)
                                   .replaceAll("@XML_CONTENTS_TAG", xmlContentsTag);
              InputStream actIn = fact.createFilteredInputStream(null,
                                                                 new StringInputStream(input),
                                                                 Constants.DEFAULT_ENCODING);
              assertEquals(RESULT, StringUtil.fromInputStream(actIn));
            }
          }
        }
      }
    }
    
  }

}

