/*
 * $Id: TestWhiteSpaceFilter.java,v 1.2 2004-04-05 07:58:01 tlipkis Exp $
 */

/*

Copyright (c) 2000-2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.filter;
import java.util.*;
import java.io.*;
import org.lockss.test.*;

public class TestWhiteSpaceFilter extends LockssTestCase {


  public void testCollapseWhiteSpace() throws IOException {
    InputStream is = new WhiteSpaceFilter(new StringInputStream("Test  test"));
    assertEquals("Test test", inputStreamToString(is));
  }

  public void testDoesntCollapseSingleSpace() throws IOException {
    InputStream is = new WhiteSpaceFilter(new StringInputStream("Test test"));
    assertEquals("Test test", inputStreamToString(is));
  }

  public void testHandlesMultipleChunks() throws IOException {
    String testString = "Test   test         test\n     test";
    InputStream is = new WhiteSpaceFilter(new StringInputStream(testString));
    assertEquals("Test test test test", inputStreamToString(is));
  }

  // Ensure test buffer refill
  public void testSmallBuffer() throws IOException {
    String testString = "Test   test         test\n     test";
    InputStream is = new WhiteSpaceFilter(new StringInputStream(testString),
					  3);
    assertEquals("Test test test test", inputStreamToString(is));
  }
  
  private String inputStreamToString(InputStream is) throws IOException {
    StringBuffer sb = new StringBuffer();
    Reader reader = new InputStreamReader(is);
    int kar;
    while ((kar = reader.read()) != -1) {
      sb.append((char)kar);
    }
    return sb.toString();
  }
}
