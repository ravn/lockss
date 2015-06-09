/* $Id:$
 */

/*

Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.clockss;

/* File-Transfer plugins
 * Do not follow external links (such as xml dtd files) in XML fileså 
 */
import java.io.IOException;
import java.io.InputStream;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.LinkExtractor;
import org.lockss.extractor.LinkExtractorFactory;
import org.lockss.extractor.XmlLinkExtractor;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.Logger;

public class ClockssSourceXmlLinkExtractorFactory 
implements LinkExtractorFactory {
  protected static final Logger log = Logger.getLogger(ClockssSourceXmlLinkExtractorFactory.class);

  @Override
  public LinkExtractor createLinkExtractor(String mimeType)
      throws PluginException {
    return new ClockssSourceXmlLinkExtractor();
  }

  /* an implementation of default XmlLinkExtractor to handle utf-8 character encodings */
  protected class ClockssSourceXmlLinkExtractor extends XmlLinkExtractor {

    @Override
    public void extractUrls(ArchivalUnit au,
        InputStream in,
        String encoding,
        String srcUrl,
        Callback cb)
            throws IOException, PluginException {
      /* do not extract anything from the XML file */
      log.debug3("NOT extracting from "+srcUrl);
    }

  }

}