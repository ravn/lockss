/*
 * $Id: TestBaseAuParamFunctor.java,v 1.1 2014-08-25 08:57:02 tlipkis Exp $
 */

/*

Copyright (c) 2000-2014 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.base;

import java.io.*;
import java.util.*;
import org.lockss.app.*;
import org.lockss.config.*;
import org.lockss.config.Tdb.TdbException;
import org.lockss.daemon.*;
import org.lockss.test.*;
import org.lockss.plugin.*;
import org.lockss.plugin.ArchivalUnit.ConfigurationException;
import org.lockss.extractor.*;
import org.lockss.util.*;

public class TestBaseAuParamFunctor extends LockssTestCase {

  AuParamFunctor fn;

  public void setUp() throws Exception {
    super.setUp();
    fn = new BaseAuParamFunctor();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testEval() throws PluginException {
    assertEquals("WWW.foo.bar",
		 fn.eval(null, "url_host",
			 "http://WWW.foo.bar/path/foo", AuParamType.String));
    assertEquals("/path/foo",
		 fn.eval(null, "url_path",
			 "http://WWW.foo.bar/path/foo", AuParamType.String));
    assertEquals("http://foo.bar/",
		 fn.eval(null, "del_www",
			 "http://WWW.foo.bar/", AuParamType.String));
    assertEquals("http://www.FOO.BAR/",
		 fn.eval(null, "add_www",
			 "http://FOO.BAR/", AuParamType.String));
  }

  public void testType() throws PluginException {
    assertEquals(AuParamType.String, fn.type(null, "url_host"));
  }
}
