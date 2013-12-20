/*
 * $Id: JsoupTagExtractorFactory.java,v 1.4 2013-12-20 23:23:55 clairegriffin Exp $
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

package org.lockss.extractor;

import org.lockss.daemon.PluginException;
import org.lockss.util.HeaderUtil;

/**
 * Created with IntelliJ IDEA. User: claire Date: 30/08/2013 Time: 10:55 To
 * change this template use File | Settings | File Templates.
 */
public class JsoupTagExtractorFactory implements FileMetadataExtractorFactory {
  /**
   * Create a MetadataExtractor
   * @param target the purpose for which metadata is being extracted
   * @param contentType the content type type from which to extract URLs
   */
  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
                                                           String contentType)
      throws PluginException {
    String mimeType = HeaderUtil.getMimeTypeFromContentType(contentType);

    if ("text/html".equalsIgnoreCase(mimeType) ||
        "text/xml".equalsIgnoreCase(mimeType) ||
        "application/xml".equalsIgnoreCase(mimeType) ||
        "application/xhtml+xml".equalsIgnoreCase(mimeType)) {
      return new JsoupTagExtractor(mimeType);
    }
    return null;
  }
}
