/*
 * $Id: TestAgreementType.java,v 1.2 2014-06-17 05:09:15 tlipkis Exp $
 */

/*

Copyright (c) 2014 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.protocol;

import org.lockss.test.*;

public class TestAgreementType extends LockssTestCase {

  public void testGetHintType() {
    assertEquals(AgreementType.POR_HINT,
		 AgreementType.getHintType(AgreementType.POR));
    assertEquals(AgreementType.POP_HINT,
		 AgreementType.getHintType(AgreementType.POP));
    assertEquals(AgreementType.SYMMETRIC_POR_HINT,
		 AgreementType.getHintType(AgreementType.SYMMETRIC_POR));
    assertEquals(AgreementType.SYMMETRIC_POP_HINT,
		 AgreementType.getHintType(AgreementType.SYMMETRIC_POP));

    assertEquals(AgreementType.POP_HINT,
		 AgreementType.getHintType(AgreementType.POP_HINT));
  }

  public void testAllTypes() {
    assertEquals(new AgreementType[] {
	AgreementType.POR,
	AgreementType.POP,
	AgreementType.SYMMETRIC_POR,
	AgreementType.SYMMETRIC_POP,
	AgreementType.POR_HINT,
	AgreementType.POP_HINT,
	AgreementType.SYMMETRIC_POR_HINT,
	AgreementType.SYMMETRIC_POP_HINT,
      },
      AgreementType.allTypes());
  }

  public void testPrimaryTypes() {
    assertEquals(new AgreementType[] {
	AgreementType.POR,
	AgreementType.POP,
	AgreementType.SYMMETRIC_POR,
	AgreementType.SYMMETRIC_POP,
      },
      AgreementType.primaryTypes());
  }
}
