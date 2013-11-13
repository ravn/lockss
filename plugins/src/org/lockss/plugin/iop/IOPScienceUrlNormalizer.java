/*
 * $Id: IOPScienceUrlNormalizer.java,v 1.2 2013-11-13 02:00:08 thib_gc Exp $
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

package org.lockss.plugin.iop;

import java.util.regex.*;

import org.lockss.daemon.PluginException;
import org.lockss.plugin.*;

/**
 * <p>This URL normalizer is only for org.lockss.plugin.iop.(Clockss)IOPSciencePlugin</p>
 */
public class IOPScienceUrlNormalizer implements UrlNormalizer {

  private static final Pattern PATTERN =
      Pattern.compile("((\\?rel=(ref|sem)|;jsessionid=).*)$",
                      Pattern.CASE_INSENSITIVE);
  
  @Override
  public String normalizeUrl(String url, ArchivalUnit au) throws PluginException {
    if (url == null) {
      return null;
    }
    Matcher mat = PATTERN.matcher(url);
    if (mat.find()) {
      return url.substring(0, mat.start());
    }
    else {
      return url;
    }
  }
  
}
