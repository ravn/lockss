/*
 * $Id: TestPluginProxy.java,v 1.2 2004-09-21 21:25:01 dshr Exp $
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

package org.lockss.remote;

import java.io.*;
import java.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.test.*;

/**
 * Test class for org.lockss.remote.PluginProxy
 */
public class TestPluginProxy extends LockssTestCase {

  MockLockssDaemon daemon;
  MyMockRemoteApi mrapi;
  PluginProxy proxy;

  public void setUp() throws Exception {
    super.setUp();

    daemon = getMockLockssDaemon();
    mrapi = new MyMockRemoteApi();
    daemon.setRemoteApi(mrapi);
    daemon.setDaemonInited(true);
  }

  public void tearDown() throws Exception {
    daemon.stopDaemon();
    super.tearDown();
  }

  public void testConstructFromId()
      throws PluginProxy.NoSuchPlugin {
    MockPlugin mp = new MockPlugin();
    mrapi.setPluginFromId("idid", mp);
    PluginProxy pluginp = new PluginProxy("idid", mrapi);
    assertSame(mp, pluginp.getPlugin());
  }

  public void testConstructFromIdThrows() {
    try {
      PluginProxy pluginp = new PluginProxy("id1", mrapi);
      fail("Failed to throw PluginProxy.NoSuchPlogin");
    } catch (PluginProxy.NoSuchPlugin e) {
    }
  }

  class MyMockRemoteApi extends RemoteApi {
    Map pluginmap = new HashMap();

    Plugin getPluginFromId(String pluginid) {
      return (Plugin)pluginmap.get(pluginid);
    }

    void setPluginFromId(String pluginid, Plugin plugin) {
      pluginmap.put(pluginid, plugin);
    }
  }
}
