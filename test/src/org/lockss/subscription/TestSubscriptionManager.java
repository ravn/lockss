/*
 * $Id: TestSubscriptionManager.java,v 1.1 2013-05-22 23:52:05 fergaloy-sf Exp $
 */

/*

 Copyright (c) 2013 Board of Trustees of Leland Stanford Jr. University,
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

/**
 * Test class for org.lockss.subscription.SubscriptionManager.
 * 
 * @author Fernando Garcia-Loygorri
 * @version 1.0
 */
package org.lockss.subscription;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.config.Tdb;
import org.lockss.config.TdbAu;
import org.lockss.config.Tdb.TdbException;
import org.lockss.db.DbManager;
import org.lockss.metadata.MetadataManager;
import org.lockss.plugin.PluginManager;
import org.lockss.plugin.PluginTestUtil;
import org.lockss.plugin.simulated.SimulatedContentGenerator;
import org.lockss.plugin.simulated.SimulatedPlugin;
import org.lockss.protocol.MockIdentityManager;
import org.lockss.remote.RemoteApi;
import org.lockss.test.ConfigurationUtil;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockLockssDaemon;
import org.lockss.util.StringUtil;

public class TestSubscriptionManager extends LockssTestCase {
  private String tempDirPath;
  private MockLockssDaemon theDaemon;
  private PluginManager pluginManager;
  private RemoteApi remoteApi;
  private SubscriptionManager subManager;
  private DbManager dbManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    tempDirPath = getTempDir().getAbsolutePath();

    // set derby database log 
    System.setProperty("derby.stream.error.file",
                       new File(tempDirPath,"derby.log").getAbsolutePath());

    Properties props = new Properties();
    props.setProperty(MetadataManager.PARAM_INDEXING_ENABLED, "true");
    props.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST,
		      tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    theDaemon = getMockLockssDaemon();
    theDaemon.setDaemonInited(true);

    pluginManager = theDaemon.getPluginManager();
    pluginManager.setLoadablePluginsReady(true);
    theDaemon.setDaemonInited(true);
    pluginManager.startService();

    PluginTestUtil.createAndStartSimAu(SimulatedPlugin.class,
	simAuConfig(tempDirPath + "/0"));

    MockIdentityManager idm = new MockIdentityManager();
    theDaemon.setIdentityManager(idm);

    remoteApi = theDaemon.getRemoteApi();
    remoteApi.startService();

    dbManager = new DbManager();
    theDaemon.setDbManager(dbManager);
    dbManager.initService(theDaemon);
    dbManager.startService();

    subManager = new SubscriptionManager();
    theDaemon.setSubscriptionManager(subManager);
    subManager.initService(theDaemon);
    subManager.startService();
  }

  private Configuration simAuConfig(String rootPath) {
    Configuration conf = ConfigManager.newConfiguration();
    conf.put("root", rootPath);
    conf.put("depth", "2");
    conf.put("branch", "1");
    conf.put("numFiles", "3");
    conf.put("fileTypes", "" + (SimulatedContentGenerator.FILE_TYPE_PDF +
                                SimulatedContentGenerator.FILE_TYPE_HTML));
    conf.put("binFileSize", "7");
    return conf;
  }

  /**
   * Check the behavior of areAllRangesValid().
   */
  public final void testAreAllRangesValid() {
    assertTrue(areAllRangesValid(""));
    assertTrue(areAllRangesValid("1234567890123456"
	+ "1234567890123456" + "1234567890123456" + "1234567890123456"));
    assertFalse(areAllRangesValid("1234567890123456"
	+ "1234567890123456" + "1234567890123456" + "1234567890123456" + "1"));
    assertTrue(areAllRangesValid("-"));
    assertTrue(areAllRangesValid("1900-"));
    assertTrue(areAllRangesValid("-2000"));
    assertTrue(areAllRangesValid("2000-2001"));
    assertFalse(areAllRangesValid("2010-2001"));
    assertTrue(areAllRangesValid("0-9999"));
    assertFalse(areAllRangesValid("2000(10)-1999"));
    assertTrue(areAllRangesValid("2000(10)-2000"));
    assertTrue(areAllRangesValid("2000(10)-2001"));
    assertTrue(areAllRangesValid("2000(10)-2001(0)"));
    assertFalse(areAllRangesValid("2000-1999(10)"));
    assertTrue(areAllRangesValid("2000-2000(10)"));
    assertTrue(areAllRangesValid("2000-2001(10)"));
    assertTrue(areAllRangesValid("2000(0)-2001(10)"));
    assertTrue(areAllRangesValid("2000(10)-2000(11)"));
    assertFalse(areAllRangesValid("2002(10)-2000(11)"));
    assertTrue(areAllRangesValid("2000(12)-2001(11)"));
    assertFalse(areAllRangesValid("2000(12)-2000(11)"));
    assertTrue(areAllRangesValid("2000(0)-2000(9999)"));
    assertTrue(areAllRangesValid("0(0)-9999(9999)"));
    assertTrue(areAllRangesValid("2000(10)-"));
    assertTrue(areAllRangesValid("2000(10)-9999(9999)"));
    assertFalse(areAllRangesValid("2000(10)-0(0)"));
    assertTrue(areAllRangesValid("-2000(10)"));
    assertTrue(areAllRangesValid("0(0)-2000(10)"));
    assertFalse(areAllRangesValid("9999(9999)-2000(10)"));
    assertTrue(areAllRangesValid("2000()()-2000"));
    assertTrue(areAllRangesValid("2000()()-2001"));
    assertTrue(areAllRangesValid("2000-2000()()"));
    assertTrue(areAllRangesValid("2000-2001()()"));
    assertTrue(areAllRangesValid("2000()()-2000(0)"));
    assertTrue(areAllRangesValid("2000()()-2000(9999)"));
    assertTrue(areAllRangesValid("2000(1)-2000()()"));
    assertTrue(areAllRangesValid("2000(1)-2001()()"));
    assertTrue(areAllRangesValid("2000()()-2000()()"));
    assertTrue(areAllRangesValid("2000()()-2001()()"));
    assertTrue(areAllRangesValid("2000(10)(2)-2000(11)(7)"));
    assertFalse(areAllRangesValid("2003(10)(2)-2000(11)(7)"));
    assertFalse(areAllRangesValid("2000(13)(2)-2000(11)(7)"));
    assertTrue(areAllRangesValid("2000(13)(2)-2001(11)(7)"));
    assertFalse(areAllRangesValid("2000(10)(9)-2000(10)(7)"));
    assertTrue(areAllRangesValid("2000(10)(9)-2001(10)(7)"));
    assertTrue(areAllRangesValid("2000(0)(0)-2000(9999)(9999)"));
    assertTrue(areAllRangesValid("0(0)(0)-9999(9999)(9999)"));
    assertTrue(areAllRangesValid("2000()(1)-2000(0)(1)"));
    assertTrue(areAllRangesValid("2000()(1)-2000(9999)(1)"));
    assertTrue(areAllRangesValid("2000()(1)-2000()()"));
    assertTrue(areAllRangesValid("2000()(1)-2001()()"));
    assertFalse(areAllRangesValid("2000()(1)-2000()(0)"));
    assertTrue(areAllRangesValid("2000()(1)-2000()(1)"));
    assertTrue(areAllRangesValid("2000()(1)-2000()(3)"));
    assertTrue(areAllRangesValid("2000()(1)-2001()(0)"));
    assertTrue(areAllRangesValid("(10)-(11)"));
    assertFalse(areAllRangesValid("(12)-(11)"));
    assertTrue(areAllRangesValid("(0)-(9999)"));
    assertTrue(areAllRangesValid("(10)(2)-(11)(7)"));
    assertFalse(areAllRangesValid("(12)(2)-(11)(7)"));
    assertTrue(areAllRangesValid("(10)(9)-(11)(7)"));
    assertTrue(areAllRangesValid("(0)(0)-(9999)(9999)"));
    assertTrue(areAllRangesValid("()(2)-()(7)"));
    assertFalse(areAllRangesValid("()(8)-()(7)"));
    assertTrue(areAllRangesValid("()(0)-()(9999)"));
    assertFalse(areAllRangesValid("a-2000"));
    assertTrue(areAllRangesValid("2000(10)-2001"));
    assertTrue(areAllRangesValid("2000(10)-2001()"));
    assertTrue(areAllRangesValid("2000()(3)-2001"));
    assertTrue(areAllRangesValid("2000()(3)-2001()()"));
    assertTrue(areAllRangesValid("2000(4)(3)-2001()()"));
    assertTrue(areAllRangesValid("2000(4)-2000()()"));
    assertTrue(areAllRangesValid("2000(4)-2001()()"));
    assertTrue(areAllRangesValid("0(0)(0)-9999()()"));
    assertTrue(areAllRangesValid("0()()-9999()()"));
    assertTrue(areAllRangesValid("()()-9999()()"));
    assertTrue(areAllRangesValid("0()()-9999(x)()"));
    assertTrue(areAllRangesValid("0()()-9999(x)(c)"));
    assertTrue(areAllRangesValid(","));
    assertTrue(areAllRangesValid("1999,"));
    assertTrue(areAllRangesValid(",2000"));
    assertTrue(areAllRangesValid("1900,1950"));
    assertTrue(areAllRangesValid("1920,,2020"));
  }

  /**
   * Provides an indication of whether the passed subscription ranges are valid.
   * 
   * @param subscriptionRanges
   *          A String with the subscription ranges to be validated.
   * @return a boolean with <code>true</code> if all the passed subscription
   *         ranges are valid, <code>false</code> otherwise.
   */
  boolean areAllRangesValid(String subscriptionRanges) {
    return subManager.areAllRangesValid(BibliographicPeriod
	.createCollection(subscriptionRanges));
  }

  /**
   * Check the behavior of displayableAuPeriod().
   */
  public final void testDisplayableAuPeriod() throws TdbException {
    Properties properties = new Properties();
    properties.setProperty("title", "MyTitle");
    properties.setProperty("journalTitle", "MyJournalTitle");
    properties.setProperty("plugin", "MyPlugin");
    properties.setProperty("attributes.publisher", "MyPublisher");

    TdbAu tdbAu = createTdbAu(properties);
    assertTrue(StringUtil.isNullString(subManager.displayableAuPeriod(tdbAu)));

    properties.setProperty("attributes.year", "1954");

    tdbAu = createTdbAu(properties);
    assertEquals("1954", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.year", "");
    properties.setProperty("attributes.volume", "4");

    tdbAu = createTdbAu(properties);
    assertEquals("(4)", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.volume", "");
    properties.setProperty("attributes.issue", "2");

    tdbAu = createTdbAu(properties);
    assertEquals("()(2)", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.year", "1954");
    properties.setProperty("attributes.volume", "4");
    properties.setProperty("attributes.issue", "");

    tdbAu = createTdbAu(properties);
    assertEquals("1954(4)", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.volume", "");
    properties.setProperty("attributes.issue", "2");

    tdbAu = createTdbAu(properties);
    assertEquals("1954()(2)", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.year", "");
    properties.setProperty("attributes.volume", "4");

    tdbAu = createTdbAu(properties);
    assertEquals("(4)(2)", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.year", "1954-1988");
    properties.setProperty("attributes.volume", "");
    properties.setProperty("attributes.issue", "");

    tdbAu = createTdbAu(properties);
    assertEquals("1954-1988", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.volume", "4");

    tdbAu = createTdbAu(properties);
    assertEquals("1954(4)-1988(4)", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.volume", "4-12");

    tdbAu = createTdbAu(properties);
    assertEquals("1954(4)-1988(12)", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.volume", "");
    properties.setProperty("attributes.issue", "2");

    tdbAu = createTdbAu(properties);
    assertEquals("1954()(2)-1988()(2)", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.issue", "2-28");

    tdbAu = createTdbAu(properties);
    assertEquals("1954()(2)-1988()(28)", subManager.displayableAuPeriod(tdbAu));

    properties.setProperty("attributes.year", "");
    properties.setProperty("attributes.volume", "4-12");

    tdbAu = createTdbAu(properties);
    assertEquals("(4)(2)-(12)(28)", subManager.displayableAuPeriod(tdbAu));
  }

  /**
   * Creates a TdbAu from passed properties.
   * 
   * @param properties
   *          A Properties with the properties of the TdbAu to be created.
   */
  private TdbAu createTdbAu(Properties properties) throws TdbException {
    Tdb tdb = new Tdb();
    tdb.addTdbAuFromProperties(properties);

    for (TdbAu tdbAu : tdb.getTdbAusByName(properties.getProperty("title"))) {
      return tdbAu;
    }

    return null;
  }

  /**
   * Check the behavior of configureAus().
   */
  public final void testConfigureAus()
      throws TdbException, IOException, SQLException, SubscriptionException {
    // Get the default repository.
    String defaultRepo =
	remoteApi.findLeastFullRepository(remoteApi.getRepositoryMap());

    // Specify the relevant properties of the archival unit.
    Properties properties = new Properties();
    properties.setProperty("title", "MyTitle");
    properties.setProperty("journalTitle", "MyJournalTitle");
    properties.setProperty("plugin",
	"org.lockss.plugin.simulated.SimulatedPlugin");
    properties.setProperty("attributes.publisher", "MyPublisher");
    properties.setProperty("attributes.year", "1954");
    properties.setProperty("param.1.key", "root");
    properties.setProperty("param.1.value", tempDirPath + "/0");
    properties.setProperty("param.2.key", "base_url");
    properties.setProperty("param.2.value", "http://www.title3.org/");

    // Create the archival unit.
    TdbAu tdbAu = createTdbAu(properties);

    // Create the publication corresponding to the archival unit.
    SerialPublication publication = new SerialPublication();
    publication.setPublicationName(tdbAu.getName());
    publication.setTdbTitle(tdbAu.getTdbTitle());

    // Create the subscription to the publication.
    Subscription subscription = new Subscription();
    subscription.setPublication(publication);
    subscription.setSubscribedRanges(Collections
	.singleton(new BibliographicPeriod("1900-1999")));

    SubscriptionOperationStatus status = new SubscriptionOperationStatus();
    Connection conn = dbManager.getConnection();

    // The AU is not added because it is already configured.
    subManager.configureAus(conn, subscription, defaultRepo, status);
    assertEquals(0, status.getFailureAuAddCount());
    assertEquals(0, status.getSuccessAuAddCount());

    // Delete the AU so that it can be added.
    pluginManager
    .deleteAu(pluginManager.getAuFromId(tdbAu.getAuId(pluginManager)));

    status = new SubscriptionOperationStatus();

    // The AU is added.
    subManager.configureAus(conn, subscription, defaultRepo, status);
    assertEquals(0, status.getFailureAuAddCount());
    assertEquals(1, status.getSuccessAuAddCount());

    // Delete the AU so that it can be added again.
    pluginManager
    .deleteAu(pluginManager.getAuFromId(tdbAu.getAuId(pluginManager)));

    // Place the AU outside the subscribed range.
    subscription.setSubscribedRanges(Collections
	.singleton(new BibliographicPeriod("1988-1999")));

    status = new SubscriptionOperationStatus();

    // The AU is not added because it is not inside the subscribed range.
    subManager.configureAus(conn, subscription, defaultRepo, status);
    assertEquals(0, status.getFailureAuAddCount());
    assertEquals(0, status.getSuccessAuAddCount());

    // Place the AU inside the subscribed range and the unsubscribed range.
    subscription.setSubscribedRanges(Collections
	.singleton(new BibliographicPeriod("1900-1999")));
    subscription.setUnsubscribedRanges(Collections
	.singleton(new BibliographicPeriod("1950-1960")));

    status = new SubscriptionOperationStatus();

    // The AU is not added because it is inside the unsubscribed range.
    subManager.configureAus(conn, subscription, defaultRepo, status);
    assertEquals(0, status.getFailureAuAddCount());
    assertEquals(0, status.getSuccessAuAddCount());
  }
}
