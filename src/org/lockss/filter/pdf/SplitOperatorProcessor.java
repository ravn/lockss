/*
 * $Id: SplitOperatorProcessor.java,v 1.3 2006-09-02 00:18:12 thib_gc Exp $
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
import java.util.List;

import org.pdfbox.util.PDFOperator;

/**
 * <p>A PDF operator processor that unconditionally splits the output
 * list of the PDF page stream transform being applied, then simply
 * passes its operands and operator into the output sublist
 * unconditionally.</p>
 * <p>This operator processor is to be associated with operators,
 * typically no-operand operators such as "begin text object"
 * (<code>BT</code>), that start sublists and accumulate content up
 * until some end condition, typically an end marker such as "end
 * text object" (<code>ET</code>). The matching end condition will
 * then need to merge the output sublist, or split/merge mismatches will
 * occur.</p>
 * <p>For example, this operator processor could be associated with
 * "begin text object", and the operator processor for "end text
 * object" could merge with or without replacement based on a
 * condition derived from the contents of the output sublist.</p>
 * @author Thib Guicherd-Callin
 * @see PdfPageStreamTransform#splitOutputList
 * @see PdfPageStreamTransform#mergeOutputList()
 * @see PdfPageStreamTransform#mergeOutputList(List)
 */
public class SplitOperatorProcessor extends SimpleOperatorProcessor {

  /* Inherit documentation */
  public void process(PDFOperator operator,
                      List operands,
                      PdfPageStreamTransform pdfPageStreamTransform)
      throws IOException {
    pdfPageStreamTransform.splitOutputList();
    super.process(operator, operands, pdfPageStreamTransform);
  }

}