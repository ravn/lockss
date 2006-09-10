/*
 * $Id: TransformEachPageExceptFirst.java,v 1.3 2006-09-10 07:50:51 thib_gc Exp $
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.filter.pdf;

import java.io.IOException;
import java.util.*;

import org.lockss.util.*;
import org.lockss.util.PdfUtil.ResultPolicy;

/**
 * <p>A PDF transform that applies a PDF page transform to each page
 * of the PDF document except the first page.</p>
 * @author Thib Guicherd-Callin
 */
public class TransformEachPageExceptFirst extends TransformSelectedPages {

  public TransformEachPageExceptFirst(PageTransform pageTransform) {
    super(pageTransform);
  }

  /**
   * <p>Builds a new PDF transform with the given PDF page
   * transform.</p>
   * @param pageTransform A PDF page transform.
   */
  public TransformEachPageExceptFirst(ResultPolicy resultPolicy,
                                      PageTransform pageTransform) {
    super(resultPolicy,
          pageTransform);
  }

  /* Inherit documentation */
  protected ListIterator /* of PdfPage */ getSelectedPages(PdfDocument pdfDocument)
      throws IOException {
    ListIterator iter = pdfDocument.getPageIterator();
    iter.next();
    // FIXME: technically, the client could rewind to the first page
    return iter;
  }

}