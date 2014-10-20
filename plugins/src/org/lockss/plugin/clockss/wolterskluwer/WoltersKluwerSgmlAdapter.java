/*
 * $Id: WoltersKluwerSgmlAdapter.java,v 1.5 2014-10-20 18:18:54 thib_gc Exp $
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

package org.lockss.plugin.clockss.wolterskluwer;

import java.io.Reader;
import java.util.regex.Pattern;

import org.lockss.util.LineRewritingReader;

/*
 * Turn the SGML file into valid XML by in-line terminating the following unclosed
 * tags. For example,
 * <COVER foo="blah">..... becomes
 * <COVER foo="blah"/>
 */
public class WoltersKluwerSgmlAdapter extends LineRewritingReader {

  public static final Pattern STRAY_AMPERSANDS = Pattern.compile("& ");

  public static final String STRAY_AMPERSANDS_REPLACEMENT = "&amp; ";

  public static final Pattern UNCLOSED_SGML_TAGS =
      Pattern.compile("<((COVER|SPP|TGP|XUI|MATH) [^>]+)>", Pattern.CASE_INSENSITIVE);
  
  public static final String UNCLOSED_SGML_TAGS_REPLACEMENT = "<$1 />";
  
  public WoltersKluwerSgmlAdapter(Reader sgmlReader) {
    super(sgmlReader);
  }
  
  @Override
  public String rewriteLine(String line) {
    line = STRAY_AMPERSANDS.matcher(line).replaceAll(STRAY_AMPERSANDS_REPLACEMENT);
    line = UNCLOSED_SGML_TAGS.matcher(line).replaceAll(UNCLOSED_SGML_TAGS_REPLACEMENT);
    return line;
  }

}
