/*
 * $Id: PalgraveBookPdfFilterFactory.java,v 1.5 2015-01-14 17:31:13 aishizaki Exp $
 */

/*

Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of his software and associated documentation files (the "Software"), to deal
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

package org.lockss.plugin.palgrave;

import java.util.List;
import org.lockss.filter.pdf.SimplePdfFilterFactory;
import org.lockss.pdf.*;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.Logger;
import java.util.regex.Pattern;

public class PalgraveBookPdfFilterFactory extends SimplePdfFilterFactory {
  private static final Logger logger = Logger.getLogger(PalgraveBookPdfFilterFactory.class);
  private static final String searchString = "^Copyright material from www.palgraveconnect.com";
  
  @Override
  public void transform(ArchivalUnit au, PdfDocument pdfDocument) throws PdfException {
    doBaseTransforms(pdfDocument);    
    removeDownloadStrip(pdfDocument);
  }

  /* and set the correct string to use for this publisher */
  public Pattern getDownloadStripPattern() {
    return Pattern.compile(searchString);
  }

  public static void doBaseTransforms(PdfDocument pdfDocument) throws PdfException {
    pdfDocument.unsetModificationDate();
    pdfDocument.unsetCreationDate();
    pdfDocument.unsetMetadata();
    PdfUtil.normalizeTrailerId(pdfDocument);
  }
  
  private void removeDownloadStrip(PdfDocument pdfDocument) throws PdfException {
    // using StateMachine which requires 1.67
   PalgraveDownloadedFromStateMachine worker = new PalgraveDownloadedFromStateMachine(getDownloadStripPattern());

   // check each page for an access date set on the rhs of the page
   for (PdfPage pdfPage : pdfDocument.getPages()) {
     // Pages seem to be made of concatenated token streams, and while most
     // target personalization is at the end (previously filtered the last
     // token stream), some books (9781137023803 - on pages with "Left Intentianally
     // Blank") had customization elsewhere in the token stream, so now checking all
     List<PdfTokenStream> pdfTokenstreams = pdfPage.getAllTokenStreams();
     for (PdfTokenStream pdfTokenStream : pdfTokenstreams) {
       worker.process(pdfTokenStream);
       if (worker.getResult()) {    // found it, fixing it
         List<PdfToken> pdfTokens = pdfTokenStream.getTokens();
         pdfTokens.subList(worker.getBegin(), worker.getEnd() + 1).clear();
         pdfTokenStream.setTokens(pdfTokens);
         break;        // now go check the next page
       }
     }
   }
  }
  
  public static class PalgraveDownloadedFromStateMachine extends PdfTokenStreamStateMachine {
    /* set when this worker is created */
    public static Pattern DOWNLOADED_FROM_PATTERN;

    // The footer is close to the bottom of each page
    public PalgraveDownloadedFromStateMachine(Pattern downloadPattern) {
      super(Direction.BACKWARD);
      DOWNLOADED_FROM_PATTERN = downloadPattern;
    }

    @Override
    public void state0() throws PdfException {
      if (isEndTextObject()) {
        setEnd(getIndex());
        setState(getState() + 1);
      }
    } 

    @Override
    public void state1() throws PdfException {
      if (isShowTextGlyphPositioningFind(DOWNLOADED_FROM_PATTERN))  {
        setState(getState() + 1);
      } else if (isBeginTextObject()) { // not the BT-ET we were looking for
        //COULD STOP HERE IF assuming only one bt/et in the stream we want
        stop();
      }
    }

    @Override
    public void state2() throws PdfException {
      if (isBeginTextObject()) {  
          setResult(true);
          setBegin(getIndex());
          stop(); // found what we needed, stop processing this page
      }  
    }
  }
}
