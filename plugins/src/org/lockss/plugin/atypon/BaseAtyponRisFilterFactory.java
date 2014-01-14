/*
 * $Id: BaseAtyponRisFilterFactory.java,v 1.4 2014-01-14 22:59:11 alexandraohlson Exp $
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

package org.lockss.plugin.atypon;

/* 
 * Using a local version of RisFilterInputStream until the org.lockss.filter version is released
 * (probably 1.63).  Until then you cannot include org.lockss.filter or there will be ambiguity.
 */
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;

import org.apache.commons.io.input.BoundedInputStream;
import org.lockss.daemon.PluginException;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

/*
 * Some Atypon RIS files have a line that contains the creation date of the file in the form:
 * Y2 - 2012/03/26, which changes based on current download date
 * Reads through RIS files line by line and removes the line with the start tag Y2
 */
public class BaseAtyponRisFilterFactory implements FilterFactory {
  private static Logger log = Logger.getLogger(BaseAtyponRisFilterFactory.class);
  
  Pattern RIS_PATTERN = Pattern.compile("^\\s*TY\\s*-", Pattern.CASE_INSENSITIVE);

  public InputStream createFilteredInputStream(ArchivalUnit au,
      InputStream in,
      String encoding)
          throws PluginException {

    InputStream inBuf = null; // to make sure mark() is supported
    /* 
     * RIS files are collected with content type text/plain (encoding) and so
     * we have to filter all text/plain and then determine if they're a RIS file 
     * here.  We are working on a different long-term solution by allowing us to verify
     * the URL against a regexp.
     */

    BufferedReader bReader;

    if (in.markSupported() != true) {
      inBuf = new BufferedInputStream(in); //wrap the one that came in
    } else {  
      inBuf =  in; //use the one passed in
    }
    int BUF_LEN = 2000;
    inBuf.mark(BUF_LEN); // not sure about the limit...just reading far enough to identify file type

    try {
      //Now create a BoundedInputReader to make sure that we don't overstep our reset mark
      bReader = new BufferedReader(new InputStreamReader(new BoundedInputStream(inBuf, BUF_LEN), encoding));
      
      String aLine = bReader.readLine();
      // The first tag in a RIS file must be "TY - "; be nice about WS
      // The specification doesn't allow for comments or other preceding characters

      // skip over empty lines
      while (aLine !=  null && aLine.trim().length() == 0) {
        aLine = bReader.readLine(); // get the next line
      }
      
      // do NOT close bReader - it would also close underlying inBuf!
      inBuf.reset();
      // if we have  data, see if it matches the RIS pattern
      if (aLine != null && RIS_PATTERN.matcher(aLine).find()) {
        return new RisFilterInputStream(inBuf, encoding, "Y2");
      } 
      return inBuf; // If not a RIS file, just return reset file
    } catch (UnsupportedEncodingException e) {
      log.debug2("Internal error (unsupported encoding)");
      throw new PluginException("Unsupported encoding looking ahead in input stream");
    } catch (IOException e) {
      log.debug2("Internal error (IO exception");
      throw new PluginException("IO exception looking ahead in input stream");
    }
  }
}
