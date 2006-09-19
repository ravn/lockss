/*
 * $Id: TransformSelectedPages.java,v 1.6 2006-09-19 16:54:53 thib_gc Exp $
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

import org.lockss.filter.pdf.DocumentTransformUtil.PageTransformWrapper;
import org.lockss.util.*;
import org.lockss.util.PdfUtil.ResultPolicy;

/**
 * <p>A document transform that applies a page transform to selected
 * pages of the PDF document.</p>
 * @author Thib Guicherd-Callin
 * @see #getSelectedPages
 */
public abstract class TransformSelectedPages extends PageTransformWrapper {

  /**
   * <p>A result policy determining the boolean result of the
   * transform.</p>
   */
  protected ResultPolicy resultPolicy;

  /**
   * <p>Builds a new document transform using the default result
   * policy, based on the given page transform.</p>
   * @param pageTransform A page transform.
   * @see #TransformSelectedPages(ResultPolicy, PageTransform)
   * @see #POLICY_DEFAULT
   */
  protected TransformSelectedPages(PageTransform pageTransform) {
    this(POLICY_DEFAULT,
         pageTransform);
  }

  /**
   * <p>Builds a new document transform using the default result
   * policy, based on the aggregation of the given page transforms
   * (using the default aggregation result policy).</p>
   * @param pageTransform1 A page transform.
   * @param pageTransform2 A page transform.
   * @see #TransformSelectedPages(ResultPolicy, PageTransform, PageTransform)
   * @see #POLICY_DEFAULT
   */
  protected TransformSelectedPages(PageTransform pageTransform1,
                                   PageTransform pageTransform2) {
    this(POLICY_DEFAULT,
         pageTransform1,
         pageTransform2);
  }

  /**
   * <p>Builds a new document transform using the default result
   * policy, based on the aggregation of the given page transforms
   * (using the default aggregation result policy).</p>
   * @param pageTransform1 A page transform.
   * @param pageTransform2 A page transform.
   * @param pageTransform3 A page transform.
   * @see #TransformSelectedPages(ResultPolicy, PageTransform, PageTransform, PageTransform)
   * @see #POLICY_DEFAULT
   */
  protected TransformSelectedPages(PageTransform pageTransform1,
                                   PageTransform pageTransform2,
                                   PageTransform pageTransform3) {
    this(POLICY_DEFAULT,
         pageTransform1,
         pageTransform2,
         pageTransform3);
  }

  /**
   * <p>Builds a new document transform using the default result
   * policy, based on the aggregation of the given page transforms
   * (using the default aggregation result policy).</p>
   * @param pageTransforms An array of page transforms.
   * @see #TransformSelectedPages(ResultPolicy, PageTransform[])
   * @see #POLICY_DEFAULT
   */
  protected TransformSelectedPages(PageTransform[] pageTransforms) {
    this(POLICY_DEFAULT,
         pageTransforms);
  }

  /**
   * <p>Builds a new document transform using the given result
   * policy, based on the given page transform.</p>
   * @param resultPolicy  A result policy.
   * @param pageTransform A page transform.
   * @see PageTransformWrapper#PageTransformWrapper(PageTransform)
   */
  protected TransformSelectedPages(ResultPolicy resultPolicy,
                                   PageTransform pageTransform) {
    super(pageTransform);
    this.resultPolicy = resultPolicy;
  }

  /**
   * <p>Builds a new document transform using the given result
   * policy, based on the aggregation of the given page transforms
   * (using the default aggregation result policy).</p>
   * @param resultPolicy   A result policy.
   * @param pageTransform1 A page transform.
   * @param pageTransform2 A page transform.
   * @see #TransformSelectedPages(ResultPolicy, PageTransform)
   * @see AggregatePageTransform#AggregatePageTransform(PageTransform, PageTransform)
   */
  protected TransformSelectedPages(ResultPolicy resultPolicy,
                                   PageTransform pageTransform1,
                                   PageTransform pageTransform2) {
    this(resultPolicy,
         new AggregatePageTransform(pageTransform1,
                                    pageTransform2));
  }

  /**
   * <p>Builds a new document transform using the given result
   * policy, based on the aggregation of the given page transforms
   * (using the default aggregation result policy).</p>
   * @param resultPolicy   A result policy.
   * @param pageTransform1 A page transform.
   * @param pageTransform2 A page transform.
   * @param pageTransform3 A page transform.
   * @see #TransformSelectedPages(ResultPolicy, PageTransform)
   * @see AggregatePageTransform#AggregatePageTransform(PageTransform, PageTransform, PageTransform)
   */
  protected TransformSelectedPages(ResultPolicy resultPolicy,
                                   PageTransform pageTransform1,
                                   PageTransform pageTransform2,
                                   PageTransform pageTransform3) {
    this(resultPolicy,
         new AggregatePageTransform(pageTransform1,
                                    pageTransform2,
                                    pageTransform3));
  }

  /**
   * <p>Builds a new document transform using the given result
   * policy, based on the aggregation of the given page transforms
   * (using the default aggregation result policy).</p>
   * @param resultPolicy   A result policy.
   * @param pageTransforms An array of page transforms.
   * @see #TransformSelectedPages(ResultPolicy, PageTransform)
   * @see AggregatePageTransform#AggregatePageTransform(PageTransform[])
   */
  protected TransformSelectedPages(ResultPolicy resultPolicy,
                                   PageTransform[] pageTransforms) {
    this(resultPolicy,
         new AggregatePageTransform(pageTransforms));
  }

  /**
   * <p>Builds a new document transform using the given result
   * policy, based on the aggregation of the given page transforms
   * (using the given aggregation result policy).</p>
   * @param pageIterationResultPolicy A result policy (for the result
   *                                  of transforming selected pages).
   * @param pageTransformResultPolicy A result policy (for the result
   *                                  of the aggregate page transform).
   * @param pageTransform1            A page transform.
   * @param pageTransform2            A page transform.
   * @see #TransformSelectedPages(ResultPolicy, PageTransform)
   * @see AggregatePageTransform#AggregatePageTransform(ResultPolicy, PageTransform, PageTransform)
   */
  protected TransformSelectedPages(ResultPolicy pageIterationResultPolicy,
                                   ResultPolicy pageTransformResultPolicy,
                                   PageTransform pageTransform1,
                                   PageTransform pageTransform2) {
    this(pageIterationResultPolicy,
         new AggregatePageTransform(pageTransformResultPolicy,
                                    pageTransform1,
                                    pageTransform2));
  }

  /**
   * <p>Builds a new document transform using the given result
   * policy, based on the aggregation of the given page transforms
   * (using the given aggregation result policy).</p>
   * @param pageIterationResultPolicy A result policy (for the result
   *                                  of transforming selected pages).
   * @param pageTransformResultPolicy A result policy (for the result
   *                                  of the aggregate page transform).
   * @param pageTransform1            A page transform.
   * @param pageTransform2            A page transform.
   * @param pageTransform3            A page transform.
   * @see #TransformSelectedPages(ResultPolicy, PageTransform)
   * @see AggregatePageTransform#AggregatePageTransform(ResultPolicy, PageTransform, PageTransform, PageTransform)
   */
  protected TransformSelectedPages(ResultPolicy pageIterationResultPolicy,
                                   ResultPolicy pageTransformResultPolicy,
                                   PageTransform pageTransform1,
                                   PageTransform pageTransform2,
                                   PageTransform pageTransform3) {
    this(pageIterationResultPolicy,
         new AggregatePageTransform(pageTransformResultPolicy,
                                    pageTransform1,
                                    pageTransform2,
                                    pageTransform3));
  }

  /**
   * <p>Builds a new document transform using the given result
   * policy, based on the aggregation of the given page transforms
   * (using the given aggregation result policy).</p>
   * @param pageIterationResultPolicy A result policy (for the result
   *                                  of transforming selected pages).
   * @param pageTransformResultPolicy A result policy (for the result
   *                                  of the aggregate page transform).
   * @param pageTransforms            An array of page transforms.
   * @see #TransformSelectedPages(ResultPolicy, PageTransform)
   * @see AggregatePageTransform#AggregatePageTransform(ResultPolicy, PageTransform[])
   */
  protected TransformSelectedPages(ResultPolicy pageIterationResultPolicy,
                                   ResultPolicy pageTransformResultPolicy,
                                   PageTransform[] pageTransforms) {
    this(pageIterationResultPolicy,
         new AggregatePageTransform(pageTransformResultPolicy,
                                    pageTransforms));
  }

  /* Inherit documentation */
  public boolean transform(PdfDocument pdfDocument) throws IOException {
    boolean success = resultPolicy.initialValue();
    for (Iterator iter = getSelectedPages(pdfDocument) ; iter.hasNext() ; ) {
      PdfPage pdfPage = (PdfPage)iter.next();
      try {
        success = resultPolicy.updateResult(success, pageTransform.transform(pdfPage));
      }
      catch (PageTransformException pte) {
        throw new DocumentTransformException(pte);
      }
      if (!resultPolicy.shouldKeepGoing(success)) {
        break;
      }
    }
    return success;
  }

  /**
   * <p>Gets an iterator of the pages selected for transformation by
   * this transform.</p>
   * @param pdfDocument A PDF document.
   * @return An iterator of PDF pages ({@link PdfPage}).
   * @throws IOException if any processing error occurs.
   */
  protected abstract ListIterator /* of PdfPage */ getSelectedPages(PdfDocument pdfDocument)
      throws IOException;

  /**
   * <p>The default result policy used by this class.</p>
   * @see #TransformSelectedPages(PageTransform)
   * @see #TransformSelectedPages(PageTransform, PageTransform)
   * @see #TransformSelectedPages(PageTransform, PageTransform, PageTransform)
   * @see #TransformSelectedPages(PageTransform[])
   */
  public static final ResultPolicy POLICY_DEFAULT = PdfUtil.AND;

}
