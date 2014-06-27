/*
 * $Id: TestRSC2014HtmlHashFilterFactory.java,v 1.1 2014-06-27 22:37:48 etenbrink Exp $
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

package org.lockss.plugin.royalsocietyofchemistry;

import java.io.InputStream;

import org.lockss.util.*;
import org.lockss.test.*;

public class TestRSC2014HtmlHashFilterFactory extends LockssTestCase {
  static String ENC = Constants.DEFAULT_ENCODING;

  private RSC2014HtmlHashFilterFactory fact;
  private MockArchivalUnit mau;

  public void setUp() throws Exception {
    super.setUp();
    fact = new RSC2014HtmlHashFilterFactory();
    mau = new MockArchivalUnit();
  }

  private static final String withScript = "<html>" +
      "  	<head>" +
      "<script type=\"text/javascript\">\n" + 
      "  var _gaq = _gaq || [];\n" + 
      "</script>" +
      "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>" +
      "</head>\n" +
      "<script type=\"text/javascript\" src=\"/camcos/etc/cover.js\"></script>" +
      "</html>";
  
  private static final String withoutScript = "<html>" +
      " " +
      "</html>";
  
  private static final String withStuff = "" +
      "<html lang=\"en\" xml:lang=\"en\" xmlns:rsc=\"urn:rsc.org\" xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\" xmlns:art=\"http://www.rsc.org/schema/rscart38\" xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:epub=\"http://www.idpf.org/2007/ops\">" +
      "<head><!--v5_4_9--></head>" +
      "<body>" +
      "<div class=\"footer\">\n" + 
      "        <div class=\"links\">\n" + 
      "            <a title=\"Login to RSC Publishing\" tabindex=\"5\" href=\"/en/account/logon?returnurl=http%3A%2F%2Fpubs.rsc.org%2Fen%2FContent%2FArticleLanding%2F2008%2FGC%2FB710041H\" accesskey=\"l\">Login</a> \n" + 
      "<span class=\"seperator\">|</span>\n" + 
      "        </div>\n" + 
      "    </div>" +
      "" +
      "</body>" +
      "</html>";
  
  private static final String withoutStuff = "" +
      "<html>" +
      "<body>" +
      "</body>" +
      "</html>";
  
  public void testFiltering() throws Exception {
    assertFilterToSame(withScript, withoutScript);
    assertFilterToSame(withStuff, withoutStuff);
  }
  
  private void assertFilterToSame(String str1, String str2) throws Exception {
    InputStream inA = fact.createFilteredInputStream(mau, new StringInputStream(str1),
        Constants.DEFAULT_ENCODING);
    InputStream inB = fact.createFilteredInputStream(mau, new StringInputStream(str2),
        Constants.DEFAULT_ENCODING);
    String a = StringUtil.fromInputStream(inA);
    String b = StringUtil.fromInputStream(inB);
    assertEquals(a, b);
    assertEquals(a, str2);
  }
  
}
