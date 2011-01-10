/*
 * $Id: TestMetadataField.java,v 1.1 2011-01-10 09:12:40 tlipkis Exp $
 */

/*

Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
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

import java.io.*;
import java.net.*;
import java.util.*;

import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import static org.lockss.extractor.MetadataField.*;

public class TestMetadataField extends LockssTestCase {

  public void setUp() throws Exception {
    super.setUp();
  }

  void assertField(String expKey, Cardinality expCard, MetadataField field) {
    assertEquals(expKey, field.getKey());
    assertEquals(expCard, field.getCardinality());
  }

  public void testPredefined() {
    assertField(KEY_VOLUME, Cardinality.Single, FIELD_VOLUME);
  }

  public void testFindField() {
    assertSame(FIELD_VOLUME, MetadataField.findField(KEY_VOLUME));
    assertNull(MetadataField.findField("nosuchfield"));
  }

  public void testPutMulti() {
    assertSame(FIELD_VOLUME, MetadataField.findField(KEY_VOLUME));
    assertNull(MetadataField.findField("nosuchfield"));
  }
}
