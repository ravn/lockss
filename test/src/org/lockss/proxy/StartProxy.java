/*
 * $Id: StartProxy.java,v 1.9 2003-09-26 23:49:01 eaalto Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.proxy;

import java.io.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.app.*;
import org.lockss.test.*;

public class StartProxy {
  public static void main(String args[]) {
    MockLockssDaemon daemon = new MockLockssDaemon(null);
    ArchivalUnit au = PTestPlugin.makeTestAu();
    PluginUtil.registerArchivalUnit(au);

    ProxyManager manager = new ProxyManager();
    try {
      manager.initService(null);
    }
    catch (LockssDaemonException ex) {
      System.err.println("Init called twice!");
    }
//    System.out.println("pm.findArchivalUnit(http://foo.bar/one) = " +
//		       pm.findArchivalUnit("http://foo.bar/one"));

    manager.startProxy();
    System.err.println("Proxy started");
  }
}
