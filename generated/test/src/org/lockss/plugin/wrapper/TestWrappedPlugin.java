/*
 * $Id: TestWrappedPlugin.java,v 1.3 2004-01-27 04:07:10 tlipkis Exp $
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

package org.lockss.plugin.wrapper;

import java.io.*;
import java.util.*;
import java.net.*;
import org.lockss.test.*;
import org.lockss.daemon.*;
import org.lockss.repository.TestLockssRepositoryImpl;
import org.lockss.plugin.highwire.*;
import org.lockss.plugin.*;
import org.lockss.util.*;

/**
 * This is the test class for org.lockss.plugin.WrappedPlugin.  Most of the code
 * is adapted from the TestHighWirePlugin class.
 *
 */


public class TestWrappedPlugin extends LockssTestCase {
  private static final String AUPARAM_BASE_URL = HighWirePlugin.AUPARAM_BASE_URL;
  private static final String AUPARAM_VOL = HighWirePlugin.AUPARAM_VOL;

  private WrappedPlugin plugin;

  public TestWrappedPlugin(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    super.setUp();
    HighWirePlugin hplug = new HighWirePlugin();
    plugin = (WrappedPlugin)WrapperState.getWrapper(hplug);
    MockLockssDaemon daemon = new MockLockssDaemon();
    plugin.initPlugin(daemon);
  }

  public void testGetAuNullConfig()
      throws ArchivalUnit.ConfigurationException {
    try {
      plugin.configureAu(null, null);
      fail("Didn't throw ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) {
    }
  }

  private WrappedArchivalUnit makeAuFromProps(Properties props)
      throws ArchivalUnit.ConfigurationException {
    Configuration config = ConfigurationUtil.fromProps(props);
    return (WrappedArchivalUnit)plugin.configureAu(config, null);
  }

  public void testGetAuHandlesBadUrl()
      throws ArchivalUnit.ConfigurationException, MalformedURLException {
    Properties props = new Properties();
    props.setProperty(HighWirePlugin.AUPARAM_VOL, "322");
    props.setProperty(HighWirePlugin.AUPARAM_BASE_URL, "blah");
    props.setProperty("reserved.wrapper","true");

    try {
      WrappedArchivalUnit au = makeAuFromProps(props);
      fail ("Didn't throw InstantiationException when given a bad url");
    } catch (ArchivalUnit.ConfigurationException auie) {
      MalformedURLException murle =
        (MalformedURLException)auie.getNestedException();
      assertNotNull(auie.getNestedException());
    }
  }

  public void testGetAuConstructsProperAu()
      throws ArchivalUnit.ConfigurationException, MalformedURLException {
    Properties props = new Properties();
    props.setProperty(AUPARAM_VOL, "322");
    props.setProperty(AUPARAM_BASE_URL, "http://www.example.com/");
    props.setProperty("reserved.wrapper","true");

    WrappedArchivalUnit au = (WrappedArchivalUnit)makeAuFromProps(props);
  }

  public void testGetPluginId() {
    assertEquals("org.lockss.plugin.wrapper.WrappedPlugin",
                 plugin.getPluginId());
  }

  public void testGetAuConfigDescrs() {
    assertEquals(ListUtil.list(ConfigParamDescr.BASE_URL,
                               ConfigParamDescr.VOLUME_NUMBER),
                 plugin.getAuConfigDescrs());
  }

}
