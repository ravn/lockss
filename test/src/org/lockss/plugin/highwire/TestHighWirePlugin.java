/*

Copyright (c) 2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.highwire;
import java.net.*;
import java.util.*;
import org.lockss.test.*;
import org.lockss.plugin.*;
import org.lockss.daemon.*;

public class TestHighWirePlugin extends LockssTestCase {
  private static final String BASE_URL_PROP = HighWirePlugin.BASE_URL_PROP;
  private static final String VOL_PROP = HighWirePlugin.VOL_PROP;

  private HighWirePlugin plugin;

  public TestHighWirePlugin(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    super.setUp();
    plugin = new HighWirePlugin();
    plugin.initPlugin();
  }

  public void testGetAUNullConfig() 
      throws ArchivalUnit.ConfigurationException {
    try {
      plugin.configureAU(null);
      fail("Didn't throw ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) {
    }
  }

  private HighWireArchivalUnit makeAUFromProps(Properties props)
      throws ArchivalUnit.ConfigurationException {
    Configuration config = ConfigurationUtil.fromProps(props);
    return (HighWireArchivalUnit)plugin.configureAU(config);
  }

  public void testGetAUHandlesBadUrl() 
      throws ArchivalUnit.ConfigurationException, MalformedURLException {
    Properties props = new Properties();
    props.setProperty(HighWirePlugin.VOL_PROP, "322");
    props.setProperty(HighWirePlugin.BASE_URL_PROP, "blah");
    
    try {
      HighWireArchivalUnit au = makeAUFromProps(props);
      fail ("Didn't throw InstantiationException when given a bad url");
    } catch (ArchivalUnit.ConfigurationException auie) {
      MalformedURLException murle = 
	(MalformedURLException)auie.getNestedException();
      assertNotNull(auie.getNestedException());
    }
  }

  public void testGetAUConstructsProperAU() 
      throws ArchivalUnit.ConfigurationException, MalformedURLException {
    Properties props = new Properties();
    props.setProperty(VOL_PROP, "322");
    props.setProperty(BASE_URL_PROP, "http://www.example.com/");
    
    HighWireArchivalUnit au = makeAUFromProps(props);
    assertEquals(322, au.getVolumeNumber());
    assertEquals(new URL("http://www.example.com/"), au.getBaseUrl());
  }

  public void testFindAUReturnsExistingAU() 
      throws ArchivalUnit.ConfigurationException{
    Properties props = new Properties();
    props.setProperty(VOL_PROP, "322");
    props.setProperty(BASE_URL_PROP, "http://www.example.com/");
    
    HighWireArchivalUnit au1 = makeAUFromProps(props);
    
    props = new Properties();
    props.setProperty(VOL_PROP, "322");
    props.setProperty(BASE_URL_PROP, "http://www.example.com/");

    HighWireArchivalUnit au2 = makeAUFromProps(props);

    assertSame(au1, au2);
  }

  public void testGetPluginName() {
    assertEquals("org|lockss|plugin|highwire|HighWirePlugin",
		 plugin.getPluginId());
  }
}
