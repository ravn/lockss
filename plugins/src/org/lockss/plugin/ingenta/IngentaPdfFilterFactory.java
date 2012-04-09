/*
 * $Id: IngentaPdfFilterFactory.java,v 1.3 2012-04-09 18:45:08 thib_gc Exp $
 */ 

/*

Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.ingenta;

import java.io.InputStream;

import org.lockss.daemon.PluginException;
import org.lockss.filter.pdf.OutputDocumentTransform;
import org.lockss.plugin.*;
import org.lockss.plugin.ingenta.IngentaPdfUtil.NeedsArchivalUnit;
import org.lockss.util.*;

public class IngentaPdfFilterFactory implements FilterFactory {

  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    logger.debug2("PDF filter factory for: " + au.getName());
    OutputDocumentTransform documentTransform = PdfUtil.getOutputDocumentTransform(au);
    if (documentTransform == null) {
      logger.debug2("Unfiltered");
      return in;
    }
    else {
      logger.debug2("Filtered with " + documentTransform.getClass().getName());
      if (documentTransform instanceof NeedsArchivalUnit) {
        ((NeedsArchivalUnit)documentTransform).setArchivalUnit(au);
      }
      return PdfUtil.applyFromInputStream(documentTransform, in);
    }
  }

  /**
   * <p>A logger for use by this class.</p>
   */
  static Logger logger = Logger.getLogger("IngentaPdfFilterFactory");

}
