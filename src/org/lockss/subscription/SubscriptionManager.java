/*
 * $Id: SubscriptionManager.java,v 1.21 2014-09-16 19:55:42 fergaloy-sf Exp $
 */

/*

 Copyright (c) 2013-2014 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.subscription;

import static org.lockss.db.SqlConstants.*;
import static org.lockss.metadata.MetadataManager.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.collections.map.MultiValueMap;
import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.config.TdbAu;
import org.lockss.config.TdbProvider;
import org.lockss.config.TdbPublisher;
import org.lockss.config.TdbTitle;
import org.lockss.config.TdbUtil;
import org.lockss.db.DbException;
import org.lockss.db.DbManager;
import org.lockss.extractor.MetadataField;
import org.lockss.metadata.MetadataManager;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.AuEvent;
import org.lockss.plugin.AuEventHandler;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.PluginManager;
import org.lockss.remote.RemoteApi;
import org.lockss.remote.RemoteApi.BatchAuStatus;
import org.lockss.util.Logger;
import org.lockss.util.MetadataUtil;
import org.lockss.util.PlatformUtil;
import org.lockss.util.StringUtil;

/**
 * Manager of serial publication subscriptions.
 * 
 * @author Fernando Garcia-Loygorri
 */
public class SubscriptionManager extends BaseLockssDaemonManager implements
    ConfigurableManager {

  private static final Logger log = Logger.getLogger(SubscriptionManager.class);

  // Prefix for the subscription manager configuration entries.
  private static final String PREFIX = Configuration.PREFIX + "subscription.";

  /**
   * Indication of whether the subscription subsystem should be enabled.
   * <p />
   * Defaults to false. Changes require daemon restart.
   */
  public static final String PARAM_SUBSCRIPTION_ENABLED = PREFIX + "enabled";

  /**
   * Default value of subscription subsystem operation configuration parameter.
   * <p />
   * <code>false</code> to disable, <code>true</code> to enable.
   */
  public static final boolean DEFAULT_SUBSCRIPTION_ENABLED = false;

  /**
   * Maximum number of retries for transient SQL exceptions.
   */
  public static final String PARAM_REPOSITORY_AVAIL_SPACE_THRESHOLD =
      PREFIX + "repositoryAvailSpaceThreshold";
  public static final int DEFAULT_REPOSITORY_AVAIL_SPACE_THRESHOLD = 0;

  // Query to find a subscription by its publication and provider.
  private static final String FIND_SUBSCRIPTION_QUERY = "select "
      + SUBSCRIPTION_SEQ_COLUMN
      + " from " + SUBSCRIPTION_TABLE
      + " where " + PUBLICATION_SEQ_COLUMN + " = ?"
      + " and " + PROVIDER_SEQ_COLUMN + " = ?";

  // Query to add a subscription.
  private static final String INSERT_SUBSCRIPTION_QUERY = "insert into "
      + SUBSCRIPTION_TABLE
      + "(" + SUBSCRIPTION_SEQ_COLUMN
      + "," + PUBLICATION_SEQ_COLUMN
      + "," + PROVIDER_SEQ_COLUMN
      + ") values (default,?,?)";

  // Query to find the subscription ranges of a publication.
  private static final String FIND_SUBSCRIPTION_RANGES_QUERY = "select "
      + SUBSCRIPTION_RANGE_COLUMN
      + "," + RANGE_IDX_COLUMN
      + " from " + SUBSCRIPTION_RANGE_TABLE
      + " where " + SUBSCRIPTION_SEQ_COLUMN + " = ?"
      + " and " + SUBSCRIBED_COLUMN + " = ?"
      + " order by " + RANGE_IDX_COLUMN;

  // Query to add a subscription range.
  private static final String INSERT_SUBSCRIPTION_RANGE_QUERY = "insert into "
      + SUBSCRIPTION_RANGE_TABLE
      + "(" + SUBSCRIPTION_SEQ_COLUMN
      + "," + SUBSCRIPTION_RANGE_COLUMN
      + "," + SUBSCRIBED_COLUMN
      + "," + RANGE_IDX_COLUMN
      + ") values (?,?,?,"
      + "(select coalesce(max(" + RANGE_IDX_COLUMN + "), 0) + 1"
      + " from " + SUBSCRIPTION_RANGE_TABLE
      + " where " + SUBSCRIPTION_SEQ_COLUMN + " = ?"
      + " and " + SUBSCRIBED_COLUMN + " = ?))";

  // Query to add a subscription range using MySQL.
  private static final String INSERT_SUBSCRIPTION_RANGE_MYSQL_QUERY = "insert "
      + "into " + SUBSCRIPTION_RANGE_TABLE
      + "(" + SUBSCRIPTION_SEQ_COLUMN
      + "," + SUBSCRIPTION_RANGE_COLUMN
      + "," + SUBSCRIBED_COLUMN
      + "," + RANGE_IDX_COLUMN
      + ") values (?,?,?,"
      + "(select next_idx from "
      + "(select coalesce(max(" + RANGE_IDX_COLUMN + "), 0) + 1 as next_idx"
      + " from " + SUBSCRIPTION_RANGE_TABLE
      + " where " + SUBSCRIPTION_SEQ_COLUMN + " = ?"
      + " and " + SUBSCRIBED_COLUMN + " = ?) as temp_sub_range_table))";

  // Query to delete all of the ranges of one type of a subscription.
  private static final String DELETE_ALL_SUBSCRIPTION_RANGES_TYPE_QUERY =
      "delete"
      + " from " + SUBSCRIPTION_RANGE_TABLE
      + " where " + SUBSCRIPTION_SEQ_COLUMN + " = ?"
      + " and " + SUBSCRIBED_COLUMN + " = ?";

  // Query to delete all of the ranges of a subscription.
  private static final String DELETE_ALL_SUBSCRIPTION_RANGES_QUERY = "delete"
      + " from " + SUBSCRIPTION_RANGE_TABLE
      + " where " + SUBSCRIPTION_SEQ_COLUMN + " = ?";

  // Query to find all the subscriptions and their publisher.
  private static final String FIND_ALL_SUBSCRIPTIONS_AND_PUBLISHERS_QUERY =
      "select distinct"
      + " pu." + PUBLISHER_NAME_COLUMN
      + ",n." + NAME_COLUMN
      + ",pr." + PROVIDER_LID_COLUMN
      + ",pr." + PROVIDER_NAME_COLUMN
      + " from " + SUBSCRIPTION_RANGE_TABLE + " sr"
      + "," + SUBSCRIPTION_TABLE + " s"
      + "," + PUBLISHER_TABLE + " pu"
      + "," + PUBLICATION_TABLE + " p"
      + "," + MD_ITEM_NAME_TABLE + " n"
      + "," + PROVIDER_TABLE + " pr"
      + " where sr." + SUBSCRIPTION_SEQ_COLUMN + " = s."
      + SUBSCRIPTION_SEQ_COLUMN
      + " and s." + PUBLICATION_SEQ_COLUMN + " = p." + PUBLICATION_SEQ_COLUMN
      + " and p." + PUBLISHER_SEQ_COLUMN + " = pu." + PUBLISHER_SEQ_COLUMN
      + " and p." + MD_ITEM_SEQ_COLUMN + " = n." + MD_ITEM_SEQ_COLUMN
      + " and s." + PROVIDER_SEQ_COLUMN + " = pr." + PROVIDER_SEQ_COLUMN
      + " order by pu." + PUBLISHER_NAME_COLUMN
      + ",n." + NAME_COLUMN
      + ",pr." + PROVIDER_NAME_COLUMN;

  // Query to find all the subscriptions and their ranges.
  private static final String FIND_ALL_SUBSCRIPTIONS_AND_RANGES_QUERY = "select"
      + " s." + SUBSCRIPTION_SEQ_COLUMN
      + ",n." + NAME_COLUMN
      + ",p." + PUBLICATION_ID_COLUMN
      + ",pu." + PUBLISHER_NAME_COLUMN
      + ",pr." + PROVIDER_LID_COLUMN
      + ",pr." + PROVIDER_NAME_COLUMN
      + ",sr." + SUBSCRIPTION_RANGE_COLUMN
      + ",sr." + SUBSCRIBED_COLUMN
      + ",sr." + RANGE_IDX_COLUMN
      + ",i1." + ISSN_COLUMN + " as " + P_ISSN_TYPE
      + ",i2." + ISSN_COLUMN + " as " + E_ISSN_TYPE
      + " from " + SUBSCRIPTION_TABLE + " s"
      + "," + PUBLICATION_TABLE + " p"
      + "," + PUBLISHER_TABLE + " pu"
      + "," + MD_ITEM_NAME_TABLE + " n"
      + "," + PROVIDER_TABLE + " pr"
      + "," + SUBSCRIPTION_RANGE_TABLE + " sr"
      + "," + MD_ITEM_TABLE + " mi"
      + " left outer join " + ISSN_TABLE + " i1"
      + " on mi." + MD_ITEM_SEQ_COLUMN + " = i1." + MD_ITEM_SEQ_COLUMN
      + " and i1." + ISSN_TYPE_COLUMN + " = '" + P_ISSN_TYPE + "'"
      + " left outer join " + ISSN_TABLE + " i2"
      + " on mi." + MD_ITEM_SEQ_COLUMN + " = i2." + MD_ITEM_SEQ_COLUMN
      + " and i2." + ISSN_TYPE_COLUMN + " = '" + E_ISSN_TYPE + "'"
      + " where s." + PUBLICATION_SEQ_COLUMN + " = p." + PUBLICATION_SEQ_COLUMN
      + " and p." + PUBLISHER_SEQ_COLUMN + " = pu." + PUBLISHER_SEQ_COLUMN
      + " and p." + MD_ITEM_SEQ_COLUMN + " = mi." + MD_ITEM_SEQ_COLUMN
      + " and p." + MD_ITEM_SEQ_COLUMN + " = n." + MD_ITEM_SEQ_COLUMN
      + " and n." + NAME_TYPE_COLUMN + " = 'primary'"
      + " and s." + PROVIDER_SEQ_COLUMN + " = pr." + PROVIDER_SEQ_COLUMN
      + " and s." + SUBSCRIPTION_SEQ_COLUMN + " = sr." + SUBSCRIPTION_SEQ_COLUMN
      + " order by pu." + PUBLISHER_NAME_COLUMN
      + ",n." + NAME_COLUMN
      + ",pr." + PROVIDER_NAME_COLUMN
      + ",sr." + SUBSCRIPTION_RANGE_COLUMN
      + ",sr." + SUBSCRIBED_COLUMN
      + ",sr." + RANGE_IDX_COLUMN;

  // Query to get the count of subscriptions.
  private static final String COUNT_SUBSCRIBED_PUBLICATIONS_QUERY = "select"
      + " count(*)"
      + " from " + SUBSCRIPTION_TABLE
      + " where " + SUBSCRIPTION_SEQ_COLUMN + " in ("
      + "select distinct s." + SUBSCRIPTION_SEQ_COLUMN
      + " from " + SUBSCRIPTION_TABLE + " s"
      + ", " + SUBSCRIPTION_RANGE_TABLE + " sr"
      + " where s." + SUBSCRIPTION_SEQ_COLUMN + " = sr."
      + SUBSCRIPTION_SEQ_COLUMN + ")";

  private static final String CANNOT_CONNECT_TO_DB_ERROR_MESSAGE =
      "Cannot connect to the database";

  private static final String CANNOT_CHECK_FIRST_RUN_ERROR_MESSAGE = "Cannot "
      + "determine whether this is the first run of the subscription manager";

  private static final String CANNOT_ROLL_BACK_DB_CONNECTION_ERROR_MESSAGE =
      "Cannot rollback the connection";

  // The name of the file used for back-up purposes.
  private static final String BACKUP_FILENAME = "subscriptions.bak";

  // Query to find all the subscription data for backup purposes.
  private static final String FIND_SUBSCRIPTION_BACKUP_DATA_QUERY = "select"
      + " pu." + PUBLISHER_NAME_COLUMN
      + ",p." + PUBLICATION_ID_COLUMN
      + ",n." + NAME_COLUMN
      + ",pr." + PROVIDER_LID_COLUMN
      + ",pr." + PROVIDER_NAME_COLUMN
      + ",i1." + ISSN_COLUMN + " as " + P_ISSN_TYPE
      + ",i2." + ISSN_COLUMN + " as " + E_ISSN_TYPE
      + ",sr." + SUBSCRIPTION_RANGE_COLUMN
      + ",sr." + SUBSCRIBED_COLUMN
      + ",sr." + RANGE_IDX_COLUMN
      + " from " + SUBSCRIPTION_RANGE_TABLE + " sr"
      + "," + SUBSCRIPTION_TABLE + " s"
      + "," + PUBLISHER_TABLE + " pu"
      + "," + PUBLICATION_TABLE + " p"
      + "," + MD_ITEM_NAME_TABLE + " n"
      + "," + PROVIDER_TABLE + " pr"
      + "," + MD_ITEM_TABLE + " mi"
      + " left outer join " + ISSN_TABLE + " i1"
      + " on mi." + MD_ITEM_SEQ_COLUMN + " = i1." + MD_ITEM_SEQ_COLUMN
      + " and i1." + ISSN_TYPE_COLUMN + " = '" + P_ISSN_TYPE + "'"
      + " left outer join " + ISSN_TABLE + " i2"
      + " on mi." + MD_ITEM_SEQ_COLUMN + " = i2." + MD_ITEM_SEQ_COLUMN
      + " and i2." + ISSN_TYPE_COLUMN + " = '" + E_ISSN_TYPE + "'"
      + " where sr." + SUBSCRIPTION_SEQ_COLUMN + " = s."
      + SUBSCRIPTION_SEQ_COLUMN
      + " and s." + PUBLICATION_SEQ_COLUMN + " = p." + PUBLICATION_SEQ_COLUMN
      + " and p." + PUBLISHER_SEQ_COLUMN + " = pu." + PUBLISHER_SEQ_COLUMN
      + " and p." + MD_ITEM_SEQ_COLUMN + " = mi." + MD_ITEM_SEQ_COLUMN
      + " and mi." + MD_ITEM_SEQ_COLUMN + " = n." + MD_ITEM_SEQ_COLUMN
      + " and n." + NAME_TYPE_COLUMN + " = '" + PRIMARY_NAME_TYPE + "'"
      + " and s." + PROVIDER_SEQ_COLUMN + " = pr." + PROVIDER_SEQ_COLUMN
      + " order by pu." + PUBLISHER_NAME_COLUMN
      + ",n." + NAME_COLUMN
      + ",pr." + PROVIDER_NAME_COLUMN;

  // Query to update the type of a subscription range.
  private static final String UPDATE_SUBSCRIPTION_RANGE_TYPE_QUERY = "update "
      + SUBSCRIPTION_RANGE_TABLE
      + " set " + SUBSCRIBED_COLUMN + " = ?"
      + " where " + SUBSCRIPTION_SEQ_COLUMN + " = ?"
      + " and " + SUBSCRIPTION_RANGE_COLUMN + " = ?";

  // The field separator in the subscription backup file.
  private static final String BACKUP_FIELD_SEPARATOR = "\t";

  // The database manager.
  private DbManager dbManager = null;

  // The metadata manager.
  private MetadataManager mdManager = null;

  // The plugin manager.
  private PluginManager pluginManager = null;

  // The remote API.
  private RemoteApi remoteApi;

  // An indication of whether this object is ready to be used.
  private boolean ready = false;

  // The current TdbTitle when processing multiple TdbAus.
  private TdbTitle currentTdbTitle;

  // The current list of subscribed ranges when processing multiple TdbAus.
  private List<BibliographicPeriod> currentSubscribedRanges;

  // The current list of unsubscribed ranges when processing multiple TdbAus.
  private List<BibliographicPeriod> currentUnsubscribedRanges;

  // The current set of TdbAus matched by the subscribed ranges and not matched
  // by the unsubscribed ranges when processing multiple TdbAus.
  private Set<TdbAu> currentCoveredTdbAus;

  // The list of repositories to use when configuring AUs.
  private List<String> repositories = null;

  // The index of the next repository to  be used when configuring AUs.
  private int repositoryIndex = 0;

  // The handler for Archival Unit events.
  private AuEventHandler auEventHandler;

  // Sorter of publications.
  private static Comparator<SerialPublication> PUBLICATION_COMPARATOR =
      new Comparator<SerialPublication>() {
    public int compare(SerialPublication o1, SerialPublication o2) {
      // Sort by publication name first.
      int nameComparison = o1.getPublicationName().compareTo(
	  o2.getPublicationName());

      if (nameComparison != 0) {
	return nameComparison;
      }

      // Sort by provider name if the publication name is the same.
      return o1.getProviderName().compareTo(o2.getProviderName());
    }
  };

  // Sorter of subscriptions by their publications.
  private static Comparator<Subscription>
  SUBSCRIPTION_BY_PUBLICATION_COMPARATOR = new Comparator<Subscription>() {
    public int compare(Subscription o1, Subscription o2) {
      // Sort by publication name first.
      SerialPublication p1 = o1.getPublication();
      SerialPublication p2 = o2.getPublication();
      int nameComparison = p1.getPublicationName().compareTo(
	  p2.getPublicationName());

      if (nameComparison != 0) {
	return nameComparison;
      }

      // Sort by provider name if the publication name is the same.
      return p1.getProviderName().compareTo(p2.getProviderName());
    }
  };

  /**
   * Starts the SubscriptionManager service.
   */
  @Override
  public void startService() {
    final String DEBUG_HEADER = "startService(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // Do nothing more if subscriptions are disabled.
    if (!ConfigManager.getCurrentConfig()
	.getBoolean(PARAM_SUBSCRIPTION_ENABLED, DEFAULT_SUBSCRIPTION_ENABLED)) {
      if (log.isDebug2())
	log.debug2(DEBUG_HEADER + "Subscriptions are disabled.");
      ready = false;
      return;
    }

    // Do nothing more if it is already initialized.
    if (ready) {
      return;
    }

    dbManager = getDaemon().getDbManager();
    pluginManager = getDaemon().getPluginManager();
    mdManager = getDaemon().getMetadataManager();
    remoteApi = getDaemon().getRemoteApi();

    // Register the event handler to receive Archival Unit removal notifications
    // and to be able to unsubscribe such Archival Units, if necessary.
    auEventHandler = new AuEventHandler.Base() {
      @Override
      public void auDeleted(AuEvent event, ArchivalUnit au) {
	Connection conn = null;

	try {
	  // Get a connection to the database.
	  conn = dbManager.getConnection();
	} catch (DbException dbe) {
	  log.error(CANNOT_CONNECT_TO_DB_ERROR_MESSAGE, dbe);
	  return;
	}

	try {
	  // Unsubscribe the Archival Unit, if necessary and possible.
	  try {
	    unsubscribeAu(conn, au);
	    DbManager.commitOrRollback(conn, log);
	  } catch (DbException dbe) {
	    log.error("Error unsubscribing deleted AU " + au, dbe);
	  } finally {
	    try {
	      DbManager.rollback(conn, log);
	    } catch (DbException dbe2) {
	      log.error("Error rolling back unsubscribing deleted AU " + au
		  + " transaction", dbe2);
	    }
	  }
	} finally {
	  DbManager.safeRollbackAndClose(conn);
	}
      }
    };

    pluginManager.registerAuEventHandler(auEventHandler);
    ready = true;

    if (log.isDebug()) log.debug(DEBUG_HEADER
	+ "SubscriptionManager service successfully started");
  }

  /**
   * Handler of configuration changes.
   * 
   * @param newConfig
   *          A Configuration with the new configuration.
   * @param prevConfig
   *          A Configuration with the previous configuration.
   * @param changedKeys
   *          A Configuration.Differences with the keys of the configuration
   *          elements that have changed.
   */
  public void setConfig(Configuration newConfig, Configuration prevConfig,
      Configuration.Differences changedKeys) {
    final String DEBUG_HEADER = "setConfig(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // Do nothing more if subscriptions are disabled.
    if (!newConfig.getBoolean(PARAM_SUBSCRIPTION_ENABLED,
			      DEFAULT_SUBSCRIPTION_ENABLED)) {
      if (log.isDebug2())
	log.debug2(DEBUG_HEADER + "Subscriptions are disabled.");
      ready = false;
      return;
    }

    // Force a re-calculation of the relative weights of the repositories.
    repositories = null;

    // On daemon startup, perform the handling of configuration changes in its
    // own thread.
    if (!isReady()) {
      SubscriptionStarter starter =
	  new SubscriptionStarter(this, newConfig, prevConfig, changedKeys);
      new Thread(starter).start();
    } else {
      handleConfigurationChange(newConfig, prevConfig, changedKeys);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Provides an indication of whether this object is ready to be used.
   * 
   * @return <code>true</code> if this object is ready to be used,
   *         <code>false</code> otherwise.
   */
  public boolean isReady() {
    return ready;
  }

  /**
   * Performs the necessary work on configuration changes.
   * 
   * @param newConfig
   *          A Configuration with the new configuration.
   * @param prevConfig
   *          A Configuration with the previous configuration.
   * @param changedKeys
   *          A Configuration.Differences with the keys of the configuration
   *          elements that have changed.
   */
  void handleConfigurationChange(Configuration newConfig,
      Configuration prevConfig, Configuration.Differences changedKeys) {
    final String DEBUG_HEADER = "handleConfigurationChange(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // Sanity check.
    if (dbManager == null || !dbManager.isReady()) {
      if (log.isDebug()) log.debug(DEBUG_HEADER + "DbManager is not ready.");
      return;
    }

    Connection conn = null;
    boolean isFirstRun = false;
    String message = CANNOT_CONNECT_TO_DB_ERROR_MESSAGE;

    try {
      // Get a connection to the database.
      conn = dbManager.getConnection();

      message = CANNOT_CHECK_FIRST_RUN_ERROR_MESSAGE;

      // Determine whether this is the first run of the subscription manager.
      isFirstRun = mdManager.countUnconfiguredAus(conn) == 0;
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "isFirstRun = " + isFirstRun);
    } catch (DbException dbe) {
      log.error(message, dbe);
      if (log.isDebug2()) log.debug(DEBUG_HEADER + "Done.");
      return;
    }

    TdbAu tdbAu = null;
    currentTdbTitle = null;
    currentSubscribedRanges = null;
    currentUnsubscribedRanges = null;

    // Initialize the configuration used to configure the archival units.
    Configuration config = ConfigManager.newConfiguration();

    try {
      // Get access to the changed archival units.
      Iterator<TdbAu> tdbAuIterator = changedKeys.getTdbDifferences()
  	.newTdbAuIterator();

      // Loop through all the changed archival units.
      while (tdbAuIterator.hasNext()) {
	try {
	  // Get the archival unit.
	  tdbAu = tdbAuIterator.next();
	  if (log.isDebug3()) log.debug3(DEBUG_HEADER + "tdbAu = " + tdbAu);

	  // Process the archival unit.
	  processNewTdbAu(tdbAu, conn, isFirstRun, config);
	  DbManager.commitOrRollback(conn, log);
	} catch (DbException dbe) {
	  log.error("Error handling archival unit " + tdbAu, dbe);
	  conn.rollback();
	} catch (RuntimeException re) {
	  log.error("Error handling archival unit " + tdbAu, re);
	  conn.rollback();
	}
      }
    } catch (SQLException sqle) {
      log.error(CANNOT_ROLL_BACK_DB_CONNECTION_ERROR_MESSAGE, sqle);
    } finally {
      DbManager.safeRollbackAndClose(conn);
    }

    // Configure the archival units.
    try {
      configureAuBatch(config);
    } catch (IOException ioe) {
      log.error("Exception caught configuring a batch of archival units. "
	  + "Config = " + config, ioe);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Performs the necessary processing for an archival unit that appears in the
   * configuration changeset.
   * 
   * @param tdbAu
   *          A TdbAu for the archival unit to be processed.
   * @param conn
   *          A Connection with the database connection to be used.
   * @param isFirstRun
   *          A boolean with <code>true</code> if this is the first run of the
   *          subscription manager, <code>false</code> otherwise.
   * @param config
   *          A Configuration to which to add the archival unit configuration.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private void processNewTdbAu(TdbAu tdbAu, Connection conn, boolean isFirstRun,
      Configuration config) throws DbException {
    final String DEBUG_HEADER = "processNewTdbAu(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "tdbAu = " + tdbAu);
      log.debug2(DEBUG_HEADER + "isFirstRun = " + isFirstRun);
      log.debug2(DEBUG_HEADER + "config = " + config);
    }

    // Get the archival unit identifier.
    String auId;

    try {
      auId = tdbAu.getAuId(pluginManager);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "auId = " + auId);
    } catch (IllegalStateException ise) {
      log.debug2("Ignored " + tdbAu
	  + " because of problems getting its identifier: " + ise.getMessage());
      return;
    } catch (RuntimeException re) {
      log.error("Ignored " + tdbAu
	  + " because of problems getting its identifier: " + re.getMessage());
      return;
    }

    // Check whether the archival unit is already configured.
    if (pluginManager.getAuFromId(auId) != null) {
      // Yes: Nothing more to do.
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "TdbAu '" + tdbAu
	  + "' is already configured.");
      return;
    }

    // Check whether this is the first run of the subscription manager.
    if (isFirstRun) {
      // Yes: Add the archival unit to the table of unconfigured archival units.
      mdManager.persistUnconfiguredAu(conn, auId);
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
      return;
    }

    // Nothing to do if the archival unit is in the table of unconfigured
    // archival units already.
    if (mdManager.isAuInUnconfiguredAuTable(conn, auId)) {
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
      return;
    }

    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "currentTdbTitle = " + currentTdbTitle);
      log.debug3(DEBUG_HEADER + "tdbAu.getTdbTitle() = " + tdbAu.getTdbTitle());
    }

    // Check whether this archival unit belongs to a different title than the
    // previous archival unit processed.
    if (!tdbAu.getTdbTitle().equals(currentTdbTitle)) {
      // Yes: Update the title data for this archival unit.
      currentTdbTitle = tdbAu.getTdbTitle();

      // Get the subscription ranges for the archival unit title.
      currentSubscribedRanges = new ArrayList<BibliographicPeriod>();
      currentUnsubscribedRanges = new ArrayList<BibliographicPeriod>();

      populateTitleSubscriptionRanges(conn, currentTdbTitle,
	  currentSubscribedRanges, currentUnsubscribedRanges);

      // Get the archival units covered by the subscription.
      currentCoveredTdbAus = getCoveredTdbAus(currentTdbTitle,
	  currentSubscribedRanges, currentUnsubscribedRanges);
    } else {
      // No: Reuse the title data from the previous archival unit.
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "Reusing data from title = "
	  + currentTdbTitle);
    }

    // Check whether the archival unit covers a subscribed range and it does not
    // cover any unsubscribed range.
    if (currentCoveredTdbAus.contains(tdbAu)) {
      // Yes: Add the archival unit configuration to those to be configured.
      config = addAuConfiguration(tdbAu, auId, config);
    } else {
      // No: Add it to the table of unconfigured archival units.
      mdManager.persistUnconfiguredAu(conn, auId);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
    return;
  }

  /**
   * Populates the sets of subscribed and unsubscribed ranges for a title.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param title
   *          A TdbTitle with the title.
   * @param subscribedRanges
   *          A List<BibliographicPeriod> to be populated with the title
   *          subscribed ranges, if any.
   * @param unsubscribedRanges
   *          A List<BibliographicPeriod> to be populated with the title
   *          unsubscribed ranges, if any.
   * @return a Set<Long> with the database subscription identifiers to which the
   *         populated ranges belong.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private Set<Long> populateTitleSubscriptionRanges(Connection conn,
      TdbTitle title, List<BibliographicPeriod> subscribedRanges,
      List<BibliographicPeriod> unsubscribedRanges) throws DbException {
    final String DEBUG_HEADER = "populateTitleSubscriptionRanges(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "title = " + title);
      log.debug2(DEBUG_HEADER + "subscribedRanges = " + subscribedRanges);
      log.debug2(DEBUG_HEADER + "unsubscribedRanges = " + unsubscribedRanges);
    }

    // Get the title identifier.
    String titleId = title.getId();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "titleId = " + titleId);

    // Get the title publisher.
    String publisher = title.getTdbPublisher().getName();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "publisher = " + publisher);

    // Locate the publisher database identifier.
    Long publisherSeq = mdManager.findPublisher(conn, publisher);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "publisherSeq = " + publisherSeq);

    // Check whether the publisher does not exist in the database.
    if (publisherSeq == null) {
      // Yes: There are no title subscription definitions.
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
      return null;
    }

    // Get the title name.
    String name = title.getName();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "name = " + name);

    // Get the unpunctuated title print ISSN.
    String pIssn = MetadataUtil.toUnpunctuatedIssn(title.getPrintIssn());
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "pIssn = " + pIssn);

    // Get the unpunctuated title electronic ISSN.
    String eIssn = MetadataUtil.toUnpunctuatedIssn(title.getEissn());
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "eIssn = " + eIssn);

    // Get the publication type.
    String pubType = title.getPublicationType();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "pubType = " + pubType);

    // Check whether it's not a serial publication type.
    if (!isSerialPublicationType(pubType)) {
      // Yes: Report the problem.
      log.error("Unexpected publication type '" + pubType 
	  + "' for subscription to title '" + name + "'");
      return null;
    }

    // Locate the title publication in the database (bookSeries or other)
    Long publicationSeq = null;
    if (MetadataField.PUBLICATION_TYPE_BOOKSERIES.equals(pubType)) {
      // Find the book series, where name is series title
      publicationSeq =
	  mdManager.findBookSeries(conn, publisherSeq, pIssn, eIssn, name);
    } else if (MetadataField.PUBLICATION_TYPE_JOURNAL.equals(pubType)) {
      // name is journal title
      publicationSeq =
	  mdManager.findJournal(conn, publisherSeq, pIssn, eIssn, name);
    }
    
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);

    // Check whether the publication does not exist in the database.
    if (publicationSeq == null) {
      // Yes: There are no title subscription definitions.
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
      return null;
    }

    Set<Long> result = new HashSet<Long>();
    Long providerSeq;
    Long subscriptionSeq = null;

    // Loop through all the title providers.
    for (TdbProvider provider : title.getTdbProviders()) {
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "provider = " + provider);

      // Find the provider in the database.
      // TODO: Replace the second argument with provider.getLid() when
      // available.
      providerSeq = dbManager.findProvider(conn, null, provider.getName());
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "providerSeq = " + providerSeq);

      // Check whether the provider exists in the database.
      if (providerSeq != null) {
	// Yes: Find in the database the publication subscription for the
	// provider.
	subscriptionSeq = findSubscription(conn, publicationSeq, providerSeq);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);

	// Check whether the subscription exists in the database.
	if (subscriptionSeq != null) {
	  // Yes: Find in the database the subscribed ranges and add them to the
	  // results.
	  subscribedRanges
	  	.addAll(findSubscriptionRanges(conn, subscriptionSeq, true));

	  // Find in the database the unsubscribed ranges and add them to the
	  // results.
	  unsubscribedRanges
	  	.addAll(findSubscriptionRanges(conn, subscriptionSeq, false));

	  // Add this subscription identifier to the results.
	  result.add(subscriptionSeq);
	}
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * Provides the archival units of a title that are covered by a passed list of
   * subscribed ranges and they are not covered by a passed list of unsubscribed
   * ranges.
   * 
   * @param title
   *          A TdbTitle with the title involved.
   * @param subscribedRanges
   *          A List<BibliographicPeriod> with the list of subscribed ranges.
   * @param unsubscribedRanges
   *          A List<BibliographicPeriod> with the list of un subscribed ranges.
   * @return a Set<TdbAu> with the archival units covered by the subscribed
   *         ranges and not covered by the unsubscribed ranges.
   */
  Set<TdbAu> getCoveredTdbAus(TdbTitle title,
      List<BibliographicPeriod> subscribedRanges,
      List<BibliographicPeriod> unsubscribedRanges) {
    final String DEBUG_HEADER = "getCoveredTdbAus(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "title = " + title);
      log.debug2(DEBUG_HEADER + "subscribedRanges = " + subscribedRanges);
      log.debug2(DEBUG_HEADER + "unsubscribedRanges = " + unsubscribedRanges);
    }

    // Initialize the result.
    Set<TdbAu> result = new HashSet<TdbAu>();

    // Get the publication archival units.
    List<TdbAu> tdbAus = title.getSortedTdbAus();

    // Do nothing more if the publication has no archival units.
    if (tdbAus == null || tdbAus.size() < 1) {
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
      return result;
    }

    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "tdbAus.size() = " + tdbAus.size());

    // Loop through all the subscribed ranges.
    for (BibliographicPeriod range : subscribedRanges) {
      // Add to the result all the archival units covered by the subscribed
      // range.
      result.addAll(getRangeCoveredTdbAus(range, tdbAus));
    }

    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "result = " + result);

    // Do nothing more if there are no unsubscribed ranges.
    if (unsubscribedRanges != null && unsubscribedRanges.size() > 0) {
      // Loop through all the unsubscribed ranges.
      for (BibliographicPeriod range : unsubscribedRanges) {
	// remove from the result all the archival units covered by the
	// unsubscribed range.
	result.removeAll(getRangeCoveredTdbAus(range, tdbAus));
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * Provides the subset of archival units from a passed superset of archival
   * units that are covered by a passed publication range.
   * 
   * @param range
   *          A BibliographicPeriod with the publication range.
   * @param tdbAus
   *          A List<TdbAu> with the TdbAu objects to check for coverage.
   * @return a List<TdbAu> with the archival units covered by the range.
   */
  private List<TdbAu> getRangeCoveredTdbAus(BibliographicPeriod range,
      List<TdbAu> tdbAus) {
    final String DEBUG_HEADER = "getRangeCoveredTdbAus(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "range = " + range.toDisplayableString());

    List<TdbAu> result = new ArrayList<TdbAu>();

    // Check whether the range covers nothing.
    if (range == null || range.isEmpty()) {
      // Yes: Return an empty result.
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
      return result;
    }

    // Check whether the range covers everything.
    if (range.isAllTime()) {
      // Yes: Return all the passed archival units.
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + tdbAus);
      return tdbAus;
    }

    int firstIndex = -1;
    int lastIndex = -1;

    // Check whether the range start is in the far past.
    if (range.getStartEdge().isInfinity()) {
      // Yes: The first archival unit to be returned is the first one passed.
      firstIndex = 0;
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "firstIndex = " + firstIndex);
      // No: Check whether the range end is in the far future.
    } else if (range.getEndEdge().isInfinity()) {
      // Yes: The last archival unit to be returned is the last one passed.
      lastIndex = tdbAus.size() - 1;
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "lastIndex = " + lastIndex);
    }

    int currentIndex = 0;

    // Loop through all the passed archival units.
    for (TdbAu tdbAu : tdbAus) {
      if (log.isDebug3()) {
	log.debug3(DEBUG_HEADER + "tdbAu = " + tdbAu);
	log.debug3(DEBUG_HEADER + "tdbAu.getPublicationRanges() = "
	    + tdbAu.getPublicationRanges());
      }

      // Check whether the range matches any of the publication ranges of the
      // archival unit.
      if (range.matches(tdbAu.getPublicationRanges())) {
	// Yes: Check whether this is the first AU covered by the range.
	if (firstIndex == -1) {
	  // Yes: Remember it.
	  firstIndex = currentIndex;
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "firstIndex = " + firstIndex);
	}

	// Check whether the last AU covered by the range is known.
	if (lastIndex == tdbAus.size() - 1) {
	  // Yes: The subsset is now known.
	  break;
	} else {
	  // No: This AU is a candidate to be the last one.
	  lastIndex = currentIndex;
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "lastIndex = " + lastIndex);
	}
      }

      // Point to the next archival unit.
      currentIndex++;
    }

    // Check whether any archival units are covered by the range.
    if (firstIndex != -1) {
      // Yes: Add to the result the archival units covered by the range.
      for (int index = firstIndex; index <= lastIndex; index++) {
	result.add(tdbAus.get(index));
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * Configures a batch of archival units into the system.
   * 
   * @param config
   *          A Configuration with the configuration of the archival units to be
   *          configured into the system.
   * @return a BatchAuStatus with the status of the operation.
   * @throws IOException
   *           if there are problems configuring the batch of archival units.
   */
  private BatchAuStatus configureAuBatch(Configuration config)
      throws IOException {
    final String DEBUG_HEADER = "configureAuBatch(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "config = " + config);
    BatchAuStatus status = null;

    // Check whether there are archival units to configure.
    if (!config.isEmpty()) {
      // Yes: Perform the actual configuration into the system of the archival
      // units in the configuration.
      status = remoteApi.batchAddAus(RemoteApi.BATCH_ADD_ADD, config, null);
      log.info("Successful configuration of " + status.getOkCnt() + " AUs.");

      // Check whether there are any errors.
      if (status.hasNotOk()) {
	// Yes: Report them.
	for (BatchAuStatus.Entry stat : status.getStatusList()) {
	  if (!stat.isOk() && stat.getExplanation() != null) {
	    log.error("Error configuring AU '" + stat.getName() + "': "
		+ stat.getExplanation());
	  }
	}
      }
    } else {
      // No.
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "No AUs to configure.");
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "status = " + status);
    return status;
  }

  /**
   * Adds the current configuration of an archival unit to a passed
   * configuration.
   * 
   * @param tdbAu
   *          A TdbAu with the archival unit.
   * @param auId
   *          A String with the archival unit identifier.
   * @param config
   *          A Configuration to which to add the archival unit configuration.
   * @return a Configuration with the archival unit configuration added to the
   *         passed configuration.
   */
  private Configuration addAuConfiguration(TdbAu tdbAu, String auId,
      Configuration config) {
    final String DEBUG_HEADER = "addAuConfiguration(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "tdbAu = " + tdbAu);
      log.debug2(DEBUG_HEADER + "auId = " + auId);
      log.debug2(DEBUG_HEADER + "config = " + config);
    }

    Plugin plugin = tdbAu.getPlugin(pluginManager);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "pluginId = " + plugin.getPluginId());

    Map<String, String> params = tdbAu.getParams();
    Properties props = PluginManager.defPropsFromProps(plugin, params);
    Configuration auConfig = ConfigManager.fromPropertiesUnsealed(props);

    // Specify the repository.
    auConfig.put(PluginManager.AU_PARAM_REPOSITORY, getRepository());
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "auConfig = " + auConfig);

    // Get the sub-tree prefix.
    String prefix = PluginManager.auConfigPrefix(auId);
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "prefix = " + prefix);

    // Add the archival unit configuration to the passed configuration.
    config.addAsSubTree(auConfig, prefix);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "config = " + config);
    return config;
  }

  /**
   * Provides an indication of whether a publication type corresponds to a
   * serial publication.
   * 
   * @param pubType
   *          A String with the publication type.
   * @return a boolean with <code>true</code> if this publication type
   *         corresponds to a serial publication, <code>false</code> otherwise.
   */
  private boolean isSerialPublicationType(String pubType) {
    return (MetadataField.PUBLICATION_TYPE_BOOKSERIES.equals(pubType) ||
	MetadataField.PUBLICATION_TYPE_JOURNAL.equals(pubType));
  }

  /**
   * Provides the identifier of a subscription.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param publicationSeq
   *          A Long with the identifier of the publication.
   * @param providerSeq
   *          A Long with the identifier of the provider.
   * @return a Long with the identifier of the subscription.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private Long findSubscription(Connection conn, Long publicationSeq,
      Long providerSeq) throws DbException {
    final String DEBUG_HEADER = "findSubscription(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "publicationSeq = " + publicationSeq);
      log.debug2(DEBUG_HEADER + "providerSeq = " + providerSeq);
    }

    PreparedStatement findSubscription =
	dbManager.prepareStatement(conn, FIND_SUBSCRIPTION_QUERY);
    ResultSet resultSet = null;
    Long subscriptionSeq = null;

    try {
      findSubscription.setLong(1, publicationSeq);
      findSubscription.setLong(2, providerSeq);
      resultSet = dbManager.executeQuery(findSubscription);
      if (resultSet.next()) {
	subscriptionSeq = resultSet.getLong(SUBSCRIPTION_SEQ_COLUMN);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "Found subscriptionSeq = "
	      + subscriptionSeq);
      }
    } catch (SQLException sqle) {
      log.error("Cannot find subscription", sqle);
      log.error("SQL = '" + FIND_SUBSCRIPTION_QUERY + "'.");
      log.error("publicationSeq = " + publicationSeq);
      log.error("providerSeq = " + providerSeq);
      throw new DbException("Cannot find subscription", sqle);
    } finally {
      DbManager.safeCloseResultSet(resultSet);
      DbManager.safeCloseStatement(findSubscription);
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);
    return subscriptionSeq;
  }

  /**
   * Provides the subscription ranges for a subscription.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscriptionSeq
   *          A Long with the identifier of the subscription.
   * @param subscribed
   *          A boolean with the subscribed attribute of the ranges to be
   *          provided.
   * @return a List<BibliographicPeriod> with the ranges for the subscription.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  List<BibliographicPeriod> findSubscriptionRanges(Connection conn,
      Long subscriptionSeq, boolean subscribed) throws DbException {
    final String DEBUG_HEADER = "findSubscriptionsRanges(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);
      log.debug2(DEBUG_HEADER + "subscribed = " + subscribed);
    }

    String range;
    List<BibliographicPeriod> ranges = new ArrayList<BibliographicPeriod>();
    String query = FIND_SUBSCRIPTION_RANGES_QUERY;
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "SQL = " + query);

    PreparedStatement getSubscriptionRanges =
	dbManager.prepareStatement(conn, query);
    ResultSet resultSet = null;

    try {
      getSubscriptionRanges.setLong(1, subscriptionSeq);
      getSubscriptionRanges.setBoolean(2, subscribed);
      resultSet = dbManager.executeQuery(getSubscriptionRanges);

      while (resultSet.next()) {
	range = resultSet.getString(SUBSCRIPTION_RANGE_COLUMN);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "range = " + range);

	ranges.add(new BibliographicPeriod(range));
      }
    } catch (SQLException sqle) {
      log.error("Cannot get ranges", sqle);
      log.error("SQL = '" + query + "'.");
      log.error("subscriptionSeq = " + subscriptionSeq);
      log.error("subscribed = " + subscribed);
      throw new DbException("Cannot get ranges", sqle);
    } finally {
      DbManager.safeCloseResultSet(resultSet);
      DbManager.safeCloseStatement(getSubscriptionRanges);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "ranges = " + ranges);
    return ranges;
  }

  /**
   * Creates subscriptions for all the archival units configured in the system.
   * 
   * @return a SubscriptionOperationStatus with a summary of the status of the
   *         operation.
   */
  public SubscriptionOperationStatus subscribeAllConfiguredAus() {
    final String DEBUG_HEADER = "subscribeAllConfiguredAus(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");
    SubscriptionOperationStatus status = new SubscriptionOperationStatus();

    // Get a connection to the database.
    Connection conn = null;

    try {
      conn = dbManager.getConnection();
    } catch (DbException dbe) {
      log.error(CANNOT_CONNECT_TO_DB_ERROR_MESSAGE, dbe);
      status.addStatusEntry(null, false, dbe.getMessage(), null);
      return status;
    }

    // Get the configured Archival Units.
    List<TdbAu> configuredAus = TdbUtil.getConfiguredTdbAus();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "configuredAus.size() = "
	  + configuredAus.size());

    // Get the titles with configured Archival Units.
    Collection<TdbTitle> configuredTitles = TdbUtil.getConfiguredTdbTitles();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "configuredTitles.size() = "
	  + configuredTitles.size());

    try {
      // Loop through all the titles with configured archival units.
      for (TdbTitle title : configuredTitles) {
	// Skip any titles that are not subscribable.
	if (!isSubscribable(title)) {
	  continue;
	}

	// Create subscriptions for all the configured archival units of this
	// title.
	subscribePublicationConfiguredAus(title, conn, configuredAus, status);
      }
    } finally {
      DbManager.safeRollbackAndClose(conn);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "status = " + status);
    return status;
  }

  /**
   * Creates subscriptions for the archival units of a title configured in the
   * system.
   * 
   * @param title
   *          A TdbTitle with the title.
   * @param conn
   *          A Connection with the database connection to be used.
   * @param configuredAus
   *          A List<TdbAu> with the archival units already configured in the
   *          system.
   * @param status
   *          A SubscriptionOperationStatus through which to provide a summary
   *          of the status of the operation.
   */
  private void subscribePublicationConfiguredAus(TdbTitle title,
      Connection conn, List<TdbAu> configuredAus,
      SubscriptionOperationStatus status) {
    final String DEBUG_HEADER = "subscribePublicationConfiguredAus(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "title = " + title);

    // Get the title publisher.
    String publisher = title.getTdbPublisher().getName();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "publisher = " + publisher);

    // Get the title name.
    String publicationName = title.getName();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "name = " + publicationName);

    // Get the unpunctuated title print ISSN.
    String pIssn = MetadataUtil.toUnpunctuatedIssn(title.getPrintIssn());
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "pIssn = " + pIssn);

    // Get the unpunctuated title electronic ISSN.
    String eIssn = MetadataUtil.toUnpunctuatedIssn(title.getEissn());
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "eIssn = " + eIssn);
    
    // Get the title proprietary identifier.
    String proprietaryId = title.getProprietaryId();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "proprietaryId = " + proprietaryId);

    // Get the periods covered by the title currently configured archival units,
    // indexed by provider.
    Map<TdbProvider, List<BibliographicPeriod>> periodsByProvider =
	getTitleConfiguredCoveragePeriodsByProvider(title.getSortedTdbAus(),
	    configuredAus);

    try {
      // Find the publisher in the database or create it.
      Long publisherSeq = mdManager.findOrCreatePublisher(conn, publisher);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "publisherSeq = " + publisherSeq);

      String pubType = title.getPublicationType();
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "pubType = " + pubType);

      // Check whether it's not a serial publication type.
      if (!isSerialPublicationType(pubType)) {
	// Yes: Report the problem.
        String msg = "Cannot subscribe to publication type '" + pubType + "'"; 
        log.error(msg);
        status.addStatusEntry(publicationName, false, msg, null);
      }

      Long publicationSeq = null;
      if (MetadataField.PUBLICATION_TYPE_BOOKSERIES.equals(pubType)) {
        publicationSeq = 
            mdManager.findOrCreateBookSeries(conn, publisherSeq, 
                                             pIssn, eIssn, 
                                             publicationName, proprietaryId);
      } else if (MetadataField.PUBLICATION_TYPE_JOURNAL.equals(pubType)) {
        publicationSeq = 
            mdManager.findOrCreateJournal(conn, publisherSeq,  
                                          pIssn, eIssn, 
                                          publicationName, proprietaryId);
      }

      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);

      // Loop through all the providers for which the title has archival units
      // currently configured.
      for (TdbProvider/*Map<String, String>*/ provider : periodsByProvider.keySet()) {
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "provider = " + provider);

	List<BibliographicPeriod> periods = periodsByProvider.get(provider);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "periods = " + periods);

	// Create the subscriptions for the configured archival units for the
	// publication and provider.
	subscribePublicationProviderConfiguredAus(publicationSeq, provider,
	    periods, conn);
      }

      // Finalize all the subscription changes for this title.
      DbManager.commitOrRollback(conn, log);

      // Report the success back to the caller.
      status.addStatusEntry(publicationName, null);
    } catch (DbException dbe) {
      // Report the failure back to the caller.
      log.error("Cannot add/update subscription to title with Id = "
	  + title.getId(), dbe);
      status.addStatusEntry(publicationName, false, dbe.getMessage(), null);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Provides the periods covered by the currently configured archival units of
   * a title, indexed by provider.
   * 
   * @param titleAus
   *          A list of TdbAus with the title
   * @param configuredAus
   *          A List<TdbAu> with the archival units already configured in the
   *          system.
   * @return a Map<TdbProvider, List<BibliographicPeriod>> with the periods
   *         covered by the currently configured archival units of the title,
   *         indexed by provider.
   */
  private Map<TdbProvider, List<BibliographicPeriod>>
  getTitleConfiguredCoveragePeriodsByProvider(List<TdbAu> titleAus,
      List<TdbAu> configuredAus) {
    final String DEBUG_HEADER =
	"getTitleConfiguredCoveragePeriodsByProvider(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "titleAus = " + titleAus);

    Map<TdbProvider, List<BibliographicPeriod>> periodsByProvider =
	new HashMap<TdbProvider, List<BibliographicPeriod>>();

    TdbProvider provider = null;
    TdbProvider lastProvider = null;
    List<BibliographicPeriod> periods = null;
    BibliographicPeriod period = null;

    // Loop through all the title archival units.
    for (TdbAu au : titleAus) {
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "au = " + au);

      try {
	// Check whether the archival unit is not down and it is configured.
	if (!au.isDown() && configuredAus.contains(au)) {
	  // Yes: Get the provider.
	  provider = au.getTdbProvider();
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "provider = " + provider);

	  // Check whether there is a provider change.
	  if (lastProvider != null && !lastProvider.equals(provider)) {
	    // Check whether there is a period defined by the previous archival
	    // unit that needs to be saved.
	    if (period != null) {
	      // Yes: Add it to the list of periods.
	      periods.add(period);
	      if (log.isDebug3())
		log.debug3(DEBUG_HEADER + "periods.size() = " + periods.size());

	      period = null;

	      // Add the list of periods to the result map.
	      periodsByProvider.put(lastProvider, periods);

	      lastProvider = provider;
	    }
	  }

	  // Check whether this provider already exists in the result map.
	  if (periodsByProvider.containsKey(provider)) {
	    // Yes: Get the list of periods for this provider already in the
	    // result map.
	    periods = periodsByProvider.get(provider);
	  } else {
	    // No: Initialize the list of periods.
	    periods = new ArrayList<BibliographicPeriod>();
	  }

	  List<BibliographicPeriod> auRanges = au.getPublicationRanges();
	  int auRangesLastIndex = auRanges.size() - 1;
	  if (log.isDebug3()) log.debug3(DEBUG_HEADER + "auRangesLastIndex = "
	      + auRangesLastIndex);

	  // Check whether this configured archival unit follows another
	  // configured archival unit for the same provider.
	  if (period != null) {
	    // Yes: Check whether the end edge of the last publication range of
	    // the archival unit exists.
	    if (auRanges.get(auRangesLastIndex).getEndEdge() != null) {
	      // Yes: Extend the period to the end edge of the last publication
	      // range of the archival unit.
	      period = new BibliographicPeriod(period.getStartEdge(),
		  auRanges.get(auRangesLastIndex).getEndEdge());
	    } else {
	      // No: report the problem.
	      log.warning("Skipped invalid last range "
		  + auRanges.get(auRangesLastIndex) + " for AU = " + au
		  + ", provider = " + provider);
	    }
	  } else {
	    // No: Check whether both the start edge of the first publication
	    // range of the archival unit and the end edge of the last
	    // publication range of the archival unit exist.
	    if (auRanges.get(0).getStartEdge() != null
		&& auRanges.get(auRangesLastIndex).getEndEdge() != null) {
	      // Yes: Initialize the subscription period with the start edge of
	      // the first publication range of the archival unit and the end
	      // edge of the last publication range of the archival unit.
	      period = new BibliographicPeriod(auRanges.get(0).getStartEdge(),
		  auRanges.get(auRangesLastIndex).getEndEdge());
	    } else {
	      // No: report the problem.
	      log.warning("Skipped invalid first range "
		  + auRanges.get(0) + " and/or last range "
		  + auRanges.get(auRangesLastIndex) + " for AU = " + au
		  + ", provider = " + provider);
	    }
	  }

	  if (log.isDebug3()) log.debug3(DEBUG_HEADER + "period = " + period);
	} else {
	  // No: Nothing more to do with this archival unit.
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "Unconfigured au = " + au);

	  // Check whether there is a period defined by the previous archival
	  // unit that needs to be saved.
	  if (period != null) {
	    // Yes: Add it to the list of periods.
	    periods.add(period);
	    if (log.isDebug3())
	      log.debug3(DEBUG_HEADER + "periods.size() = " + periods.size());

	    period = null;

	    // Add the list of periods to the result map.
	    periodsByProvider.put(provider, periods);
	  }
	}
      } catch (RuntimeException re) {
	log.error("Cannot find the periods for AU " + au, re);
      }
    }

    // Check whether there is a period defined by the last archival unit that
    // needs to be saved.
    if (period != null) {
      // Yes: Add it to the list of periods.
      periods.add(period);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "periods.size() = " + periods.size());

      // Add the list of periods to the result map.
      periodsByProvider.put(provider, periods);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
    return periodsByProvider;
  }

  /**
   * Creates subscriptions for the archival units of a title for a provider
   * that are configured in the system.
   * 
   * @param publicationSeq
   *          A Long with the publication identifier.
   * @param provider
   *          A Map<String, String> with the provider information.
   * @param periods
   *          A List<BibliographicPeriod> with the periods of the archival
   *          units.
   * @param conn
   *          A Connection with the database connection to be used.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private void subscribePublicationProviderConfiguredAus(Long publicationSeq,
      TdbProvider/*Map<String, String>*/ provider, List<BibliographicPeriod> periods,
      Connection conn) throws DbException {
    final String DEBUG_HEADER = "subscribePublicationProviderConfiguredAus(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "publicationSeq = " + publicationSeq);
      log.debug2(DEBUG_HEADER + "provider = " + provider);
      log.debug2(DEBUG_HEADER + "periods = " + periods);
    }

    // Find the provider in the database or create it.
    // TODO: Replace the second argument with provider().getLid() when
    // available.
    Long providerSeq =
	dbManager.findOrCreateProvider(conn, null, provider.getName());
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "providerSeq = " + providerSeq);

    // Find the subscription in the database or create it.
    Long subscriptionSeq =
	findOrCreateSubscription(conn, publicationSeq, providerSeq);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);

    // Delete all the subscribed ranges.
    int deletedRangesCount =
	deleteSubscriptionTypeRanges(conn, subscriptionSeq, true);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "deletedRangesCount = " + deletedRangesCount);

    // Get the count of periods to persist.
    int periodCount = periods.size();

    // Loop through all the coalesced subscribed ranges covered by the title
    // currently configured archival units for this provider.
    for (BibliographicPeriod period : periods) {
      if (log.isDebug3()) {
	log.debug3(DEBUG_HEADER + "period = " + period);
	log.debug3(DEBUG_HEADER + "periodCount = " + periodCount);
      }

      // Check whether this is the last period.
      if (periodCount-- == 1) {
	// Yes: Extend it to the far future and persist it.
	BibliographicPeriod lastPeriod =
	    new BibliographicPeriod(period.getStartEdge(),
		BibliographicPeriodEdge.INFINITY_EDGE);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "lastPeriod = " + lastPeriod);

	persistSubscriptionRange(conn, subscriptionSeq, lastPeriod, true);
      } else {
	// No: Just persist it.
	persistSubscriptionRange(conn, subscriptionSeq, period, true);
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Provides the identifier of a subscription if existing or after creating it
   * otherwise.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param publicationSeq
   *          A Long with the identifier of the publication.
   * @param providerSeq
   *          A Long with the identifier of the provider.
   * @return a Long with the identifier of the subscription.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private Long findOrCreateSubscription(Connection conn, Long publicationSeq,
      Long providerSeq) throws DbException {
    final String DEBUG_HEADER = "findOrCreateSubscription(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "publicationSeq = " + publicationSeq);
      log.debug2(DEBUG_HEADER + "providerSeq = " + providerSeq);
    }

    // Locate the subscription in the database.
    Long subscriptionSeq = findSubscription(conn, publicationSeq, providerSeq);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "old subscriptionSeq = " + subscriptionSeq);

    // Check whether it is a new subscription.
    if (subscriptionSeq == null) {
      // Yes: Add to the database the new subscription.
      subscriptionSeq = persistSubscription(conn, publicationSeq, providerSeq);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "new subscriptionSeq = " + subscriptionSeq);
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);
    return subscriptionSeq;
  }

  /**
   * Adds a subscription range to the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscriptionSeq
   *          A Long with the identifier of the subscription.
   * @param range
   *          A BibliographicPeriod with the subscription range.
   * @param subscribed
   *          A boolean with the indication of whether the LOCKSS installation
   *          is subscribed to the publication range or not.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private int persistSubscriptionRange(Connection conn, Long subscriptionSeq,
      BibliographicPeriod range, boolean subscribed) throws DbException {
    final String DEBUG_HEADER = "persistSubscriptionRange(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);
      log.debug2(DEBUG_HEADER + "range = " + range);
      log.debug2(DEBUG_HEADER + "subscribed = " + subscribed);
    }

    int count = 0;

    // Skip an empty range that does not accomplish anything.
    if (range.isEmpty()) {
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "count = " + count);
      return count;
    }

    String sql = getInsertSubscriptionRangeSql();
    PreparedStatement insertSubscriptionRange =
	dbManager.prepareStatement(conn, sql);

    try {
      insertSubscriptionRange.setLong(1, subscriptionSeq);
      insertSubscriptionRange.setString(2, range.toDisplayableString());
      insertSubscriptionRange.setBoolean(3, subscribed);
      insertSubscriptionRange.setLong(4, subscriptionSeq);
      insertSubscriptionRange.setBoolean(5, subscribed);

      count = dbManager.executeUpdate(insertSubscriptionRange);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "count = " + count);
    } catch (SQLException sqle) {
      log.error("Cannot insert subscription range", sqle);
      log.error("SQL = '" + sql + "'.");
      log.error("subscriptionSeq = " + subscriptionSeq);
      log.error("range = " + range);
      log.error("subscribed = " + subscribed);
      throw new DbException("Cannot insert subscription range", sqle);
    } finally {
      DbManager.safeCloseStatement(insertSubscriptionRange);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "count = " + count);
    return count;
  }

  /**
   * Deletes all ranges of a type belonging to a subscription from the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscriptionSeq
   *          A Long with the identifier of the subscription.
   * @param subscribed
   *          A boolean with the indication of whether the LOCKSS installation
   *          is subscribed to the publication range or not.
   * @return an int with the number of deleted rows.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  int deleteSubscriptionTypeRanges(Connection conn, Long subscriptionSeq,
      boolean subscribed) throws DbException {
    final String DEBUG_HEADER = "deleteSubscriptionTypeRanges(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);
      log.debug2(DEBUG_HEADER + "subscribed = " + subscribed);
    }

    int count = 0;
    PreparedStatement deleteSubscriptionRange = dbManager.prepareStatement(conn,
	DELETE_ALL_SUBSCRIPTION_RANGES_TYPE_QUERY);

    try {
      deleteSubscriptionRange.setLong(1, subscriptionSeq);
      deleteSubscriptionRange.setBoolean(2, subscribed);

      count = dbManager.executeUpdate(deleteSubscriptionRange);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "count = " + count);
    } catch (SQLException sqle) {
      log.error("Cannot delete subscription range", sqle);
      log.error("SQL = '" + DELETE_ALL_SUBSCRIPTION_RANGES_TYPE_QUERY + "'.");
      log.error("subscriptionSeq = " + subscriptionSeq);
      log.error("subscribed = " + subscribed);
      throw new DbException("Cannot delete subscription range", sqle);
    } finally {
      DbManager.safeCloseStatement(deleteSubscriptionRange);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "count = " + count);
    return count;
  }

  /**
   * Adds a subscription to the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param publicationSeq
   *          A Long with the identifier of the publication.
   * @param providerSeq
   *          A Long with the identifier of the provider.
   * @return a Long with the identifier of the subscription just added.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  Long persistSubscription(Connection conn, Long publicationSeq,
      Long providerSeq) throws DbException {
    final String DEBUG_HEADER = "persistSubscription(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "publicationSeq = " + publicationSeq);
      log.debug2(DEBUG_HEADER + "providerSeq = " + providerSeq);
    }

    PreparedStatement insertSubscription = dbManager.prepareStatement(conn,
	INSERT_SUBSCRIPTION_QUERY, Statement.RETURN_GENERATED_KEYS);

    ResultSet resultSet = null;
    Long subscriptionSeq = null;

    try {
      // Skip auto-increment key field #0
      insertSubscription.setLong(1, publicationSeq);
      insertSubscription.setLong(2, providerSeq);
      dbManager.executeUpdate(insertSubscription);
      resultSet = insertSubscription.getGeneratedKeys();

      if (!resultSet.next()) {
	log.error("Unable to create SUBSCRIPTION table row: publicationSeq = "
	    + publicationSeq + ", providerSeq = " + providerSeq
	    + " - No keys were generated.");
	if (log.isDebug2()) log.debug2(DEBUG_HEADER + "subscriptionSeq = null");
	return null;
      }

      subscriptionSeq = resultSet.getLong(1);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "Added subscriptionSeq = " + subscriptionSeq);
    } catch (SQLException sqle) {
      log.error("Cannot insert subscription", sqle);
      log.error("SQL = '" + INSERT_SUBSCRIPTION_QUERY + "'.");
      log.error("publicationSeq = " + publicationSeq);
      log.error("providerSeq = " + providerSeq);
      throw new DbException("Cannot insert subscription", sqle);
    } finally {
      DbManager.safeCloseResultSet(resultSet);
      DbManager.safeCloseStatement(insertSubscription);
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);
    return subscriptionSeq;
  }

  /**
   * Provides all the subscriptions in the system and their ranges.
   * 
   * @return a List<Subscription> with all the subscriptions and their ranges in
   *         the system.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  public List<Subscription> findAllSubscriptionsAndRanges()
      throws DbException {
    final String DEBUG_HEADER = "findAllSubscriptionsAndRanges(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    Long subscriptionSeq;
    String publicationName;
    String publicationId;
    String pIssn;
    String eIssn;
    String publisherName;
    String providerLid;
    String providerName;
    String ranges;
    boolean subscribed;
    SerialPublication publication;
    Subscription subscription = new Subscription();
    List<Subscription> subscriptions = new ArrayList<Subscription>();

    // Get a connection to the database.
    Connection conn = dbManager.getConnection();

    String query = FIND_ALL_SUBSCRIPTIONS_AND_RANGES_QUERY;
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "SQL = " + query);

    PreparedStatement getAllSubscriptionRanges =
	dbManager.prepareStatement(conn, query);
    ResultSet resultSet = null;

    try {
      // Get all the subscriptions and ranges from the database.
      resultSet = dbManager.executeQuery(getAllSubscriptionRanges);

      // Loop through all the results.
      while (resultSet.next()) {
	subscriptionSeq = resultSet.getLong(SUBSCRIPTION_SEQ_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);

	publicationName = resultSet.getString(NAME_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "publicationName = " + publicationName);

	publicationId = resultSet.getString(PUBLICATION_ID_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "publicationId = " + publicationId);

        pIssn = resultSet.getString(P_ISSN_TYPE);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "pIssn = " + pIssn);

        eIssn = resultSet.getString(E_ISSN_TYPE);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "eIssn = " + eIssn);

	publisherName = resultSet.getString(PUBLISHER_NAME_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "publisherName = " + publisherName);

	providerLid = resultSet.getString(PROVIDER_LID_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "providerLid = " + providerLid);

	providerName = resultSet.getString(PROVIDER_NAME_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "providerName = " + providerName);

	ranges = resultSet.getString(SUBSCRIPTION_RANGE_COLUMN);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "ranges = " + ranges);

	subscribed = resultSet.getBoolean(SUBSCRIBED_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "subscribed = " + subscribed);

	// Convert the text ranges into a list of bibliographic periods.
	List<BibliographicPeriod> periods =
	    BibliographicPeriod.createList(ranges);

	// Check whether this is another range for the same subscription as the
	// last one.
	if (subscriptionSeq.equals(subscription.getSubscriptionSeq())) {
	  // Yes: Check whether it is a subscribed range.
	  if (subscribed) {
	    // Yes: Add it to the subscription subscribed ranges.
	    subscription.addSubscribedRanges(periods);
	  } else {
	    // No: Add it to the subscription unsubscribed ranges.
	    subscription.addUnsubscribedRanges(periods);
	  }
	} else {
	  // No: Add the previous subscription to the results.
	  if (subscription.getSubscriptionSeq() != null) {
	    subscriptions.add(subscription);
	  }

	  // Initialize the new subscription publication.
	  publication = new SerialPublication();
	  publication.setPublicationName(publicationName);
	  publication.setProprietaryId(publicationId);
	  publication.setPissn(pIssn);
	  publication.setEissn(eIssn);
	  publication.setPublisherName(publisherName);
	  publication.setProviderLid(providerLid);
	  publication.setProviderName(providerName);

	  // Initialize the new subscription.
	  subscription = new Subscription();
	  subscription.setPublication(publication);
	  subscription.setSubscriptionSeq(subscriptionSeq);

	  // Check whether it is a subscribed range.
	  if (subscribed) {
	    // Yes: Add it to the subscription subscribed ranges.
	    subscription.setSubscribedRanges(periods);
	  } else {
	    // No: Add it to the subscription unsubscribed ranges.
	    subscription.setUnsubscribedRanges(periods);
	  }
	}
      }

      // Add the last subscription to the results.
      if (subscription.getSubscriptionSeq() != null) {
	subscriptions.add(subscription);
      }
    } catch (SQLException sqle) {
      log.error("Cannot get existing subscriptions", sqle);
      log.error("SQL = '" + query + "'.");
      throw new DbException("Cannot get existing subscriptions", sqle);
    } finally {
      DbManager.safeCloseResultSet(resultSet);
      DbManager.safeCloseStatement(getAllSubscriptionRanges);
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscriptions = " + subscriptions);
    return subscriptions;
  }

  /**
   * Provides a count of the publications with subscriptions.
   *
   * @return a long with the count of the publications with subscriptions.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  public long countSubscribedPublications() throws DbException {
    final String DEBUG_HEADER = "countSubscribedPublications(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    long result = 0;

    // Get a connection to the database.
    Connection conn = dbManager.getConnection();

    String query = COUNT_SUBSCRIBED_PUBLICATIONS_QUERY;
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "SQL = " + query);

    PreparedStatement countSubscriptions =
	dbManager.prepareStatement(conn, query);
    ResultSet resultSet = null;

    try {
      resultSet = dbManager.executeQuery(countSubscriptions);
      resultSet.next();
      result = resultSet.getLong(1);
    } catch (SQLException sqle) {
      log.error("Cannot count subscribed publications", sqle);
      log.error("SQL = '" + query + "'.");
      throw new DbException("Cannot count subscribed publications", sqle);
    } finally {
      DbManager.safeCloseResultSet(resultSet);
      DbManager.safeCloseStatement(countSubscriptions);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * Provides the publications for which subscription decisions have not been
   * made yet.
   * 
   * @return A List<SerialPublication> with the publications for which
   *         subscription decisions have not been made yet.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  public List<SerialPublication> getUndecidedPublications()
      throws DbException {
    final String DEBUG_HEADER = "getUndecidedPublications(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    List<SerialPublication> unsubscribedPublications =
	new ArrayList<SerialPublication>();

    // Get the existing subscriptions with publisher names.
    MultiValueMap subscriptionMap =
	mapSubscriptionsByPublisher(findAllSubscriptionsAndPublishers());

    Collection<Subscription> publisherSubscriptions = null;
    String publisherName;
    String titleName;
    SerialPublication publication;
    int publicationNumber = 1;

    // Loop through all the publishers.
    for (TdbPublisher publisher :
      TdbUtil.getTdb().getAllTdbPublishers().values()) {
      publisherName = publisher.getName();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "publisherName = " + publisherName);

      // Get the subscribed publications that belong to the publisher.
      publisherSubscriptions =
	  (Collection<Subscription>)subscriptionMap.get(publisherName);
      if (log.isDebug3()) {
	if (publisherSubscriptions != null) {
	  log.debug3(DEBUG_HEADER + "publisherSubscriptions.size() = "
	      + publisherSubscriptions.size());
	} else {
	  log.debug3(DEBUG_HEADER + "publisherSubscriptions is null.");
	}
      }

      // Loop through all the titles (subscribed or not) of the publisher.
      for (TdbTitle title : publisher.getTdbTitles()) {
	// Skip any titles that are not subscribable.
	if (!isSubscribable(title)) {
	  continue;
	}

	titleName = title.getName();
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "titleName = " + titleName);

	// Loop through all the title providers. 
	for (TdbProvider provider : title.getTdbProviders()) {
	  String providerName = provider.getName();
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "providerName = " + providerName);

	  // TODO: Replace with provider.getLid() when available.
	  String providerLid = null;
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "providerLid = " + providerLid);

	  // Check whether there is no subscription defined for this title and
	  // this provider.
	  if (publisherSubscriptions == null
	      || !matchSubscriptionTitleAndProvider(publisherSubscriptions,
		  titleName, providerLid, providerName)) {
	    // Yes: Add the publication to the list of publications with no
	    // subscriptions.
	    publication = new SerialPublication();
	    publication.setPublicationNumber(publicationNumber++);
	    publication.setPublicationName(titleName);
	    publication.setProviderLid(providerLid);
	    publication.setProviderName(providerName);
	    publication.setPublisherName(publisherName);
	    publication.setPissn(title.getPrintIssn());
	    publication.setEissn(title.getEissn());
	    publication.setProprietaryId(title.getProprietaryId());
	    publication.setTdbTitle(title);

	    if (log.isDebug3())
	      log.debug3(DEBUG_HEADER + "publication = " + publication);

	    unsubscribedPublications.add(normalizePublication(publication));
	  }
	}
      }
    }

    if (log.isDebug3()) log.debug3(DEBUG_HEADER
	+ "unsubscribedPublications.size() = "
	+ unsubscribedPublications.size());

    // Sort the publications for displaying purposes.
    Collections.sort(unsubscribedPublications, PUBLICATION_COMPARATOR);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER
	+ "unsubscribedPublications.size() = "
	+ unsubscribedPublications.size());
    return unsubscribedPublications;
  }

  /**
   * Provides all the subscriptions and their publishers.
   * 
   * @return a List<Subscription> with the subscriptions and their publishers.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private List<Subscription> findAllSubscriptionsAndPublishers()
      throws DbException {
    final String DEBUG_HEADER = "findAllSubscriptionsAndPublishers(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    String publicationName;
    String providerLid;
    String providerName;
    String publisherName;
    SerialPublication publication;
    Subscription subscription;
    List<Subscription> subscriptions = new ArrayList<Subscription>();

    // Get a connection to the database.
    Connection conn = dbManager.getConnection();

    String query = FIND_ALL_SUBSCRIPTIONS_AND_PUBLISHERS_QUERY;
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "SQL = " + query);

    PreparedStatement getAllSubscriptionsAndPublishers =
	dbManager.prepareStatement(conn, query);
    ResultSet resultSet = null;

    try {
      resultSet = dbManager.executeQuery(getAllSubscriptionsAndPublishers);

      while (resultSet.next()) {
	publisherName = resultSet.getString(PUBLISHER_NAME_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "publisherName = " + publisherName);

	publicationName = resultSet.getString(NAME_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "publicationName = " + publicationName);

	providerLid = resultSet.getString(PROVIDER_LID_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "providerLid = " + providerLid);

	providerName = resultSet.getString(PROVIDER_NAME_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "providerName = " + providerName);

	publication = new SerialPublication();
	publication.setPublisherName(publisherName);
	publication.setPublicationName(publicationName);
	publication.setProviderLid(providerLid);
	publication.setProviderName(providerName);

	subscription = new Subscription();
	subscription.setPublication(publication);

	subscriptions.add(subscription);
      }
    } catch (SQLException sqle) {
      log.error("Cannot get subscriptions and publishers", sqle);
      log.error("SQL = '" + query + "'.");
      throw new DbException("Cannot get subscriptions and publishers", sqle);
    } finally {
      DbManager.safeCloseResultSet(resultSet);
      DbManager.safeCloseStatement(getAllSubscriptionsAndPublishers);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "subscriptions.size() = "
	+ subscriptions.size());
    return subscriptions;
  }

  /**
   * Provides the subscriptions in the system keyed by their publisher.
   * 
   * @param subscriptions
   *          A List<Subscription> with the subscriptions in the system.
   * @return a MultivalueMap with the subscriptions in the system keyed by their
   *         publisher.
   */
  private MultiValueMap mapSubscriptionsByPublisher(
      List<Subscription> subscriptions) {
    final String DEBUG_HEADER = "mapSubscriptionsByPublisher(): ";
    if (log.isDebug2()) {
      if (subscriptions != null) {
	log.debug2(DEBUG_HEADER + "subscriptions.size() = "
	    + subscriptions.size());
      } else {
	log.debug2(DEBUG_HEADER + "subscriptions is null");
      }
    }

    MultiValueMap mapByPublisher = new MultiValueMap();

    // Loop through all the subscriptions.
    for (Subscription subscription : subscriptions) {
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "subscription = " + subscription);

      // Get the subscription publication.
      SerialPublication publication = subscription.getPublication();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "publication = " + publication);

      // Get the publication publisher name.
      String publisherName = publication.getPublisherName();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "publisherName = " + publisherName);

      // Save the publisher subscription.
      mapByPublisher.put(publisherName, subscription);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "Added subscription "
	  + subscription + " for publisher " + publisherName);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "mapByPublisher.size() = "
	+ mapByPublisher.size());
    return mapByPublisher;
  }

  /**
   * Provides an indication of whether a TdbTitle can be the subject of a
   * subscription.
   * 
   * @return <code>true</code> if the TdbTitle can be the subject of a
   *         subscription, <code>false</code> otherwise.
   */
  public boolean isSubscribable(TdbTitle tdbTitle) {
    final String DEBUG_HEADER = "isSubscribable(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "tdbTitle = " + tdbTitle);
    boolean result = false;

    // Check whether the TdbTitle exists.
    if (tdbTitle != null) {
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "tdbTitle = " + tdbTitle);

      // Yes: Check whether the TdbTitle is marked as a serial title.
      if (tdbTitle.isSerial()) {
	// Yes: Loop through the title archival units.
	for (TdbAu tdbAu : tdbTitle.getTdbAus()) {
	  // Check whether this archival unit is not down.
	  if (!tdbAu.isDown()) {
	    // Yes: The TdbTitle is subscribable; no need to do anything more.
	    result = true;
	    break;
	  }
	}

	if (log.isDebug3() && !result) {
	  log.debug3(DEBUG_HEADER + "tdbTitle '" + tdbTitle
	      + "' is not subscribable because all of its AUs are down.");
	}
      } else {
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "title '" + tdbTitle
	    + "' is not subscribable because it's not a serial publication.");
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * Provides an indication of whether there is a subscription defined for this
   * title and this provider.
   * 
   * @param subscriptions
   *          A Collection<Subscription> with all the subscriptions.
   * @param titleName
   *          A String with the name of the title.
   * @param providerLid
   *          A String with the LOCKSS identifier of the provider.
   * @param providerName
   *          A String with the name of the provider.
   * @return a boolean with <code>true</code> if the subscription for the title
   *         and provider exists, <code>false</code> otherwise.
   */
  private boolean matchSubscriptionTitleAndProvider(
      Collection<Subscription> subscriptions, String titleName,
      String providerLid, String providerName) {
    final String DEBUG_HEADER = "matchSubscriptionTitleAndProvider(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "subscriptions = " + subscriptions);
      log.debug2(DEBUG_HEADER + "titleName = " + titleName);
      log.debug2(DEBUG_HEADER + "providerLid = " + providerLid);
      log.debug2(DEBUG_HEADER + "providerName = " + providerName);
    }

    // Handle the case when there are no subscriptions.
    if (subscriptions == null || subscriptions.size() < 1) {
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Did not find match.");
      return false;
    }

    SerialPublication publication;

    // Loop through all the subscriptions.
    for (Subscription subscription : subscriptions) {
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "subscription = " + subscription);

      // Get the subscription publication.
      publication = subscription.getPublication();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "publication = " + publication);

      // Check whether there is a match.
      if (publication.getPublicationName().equals(titleName)
	  && (publication.getProviderLid() == null
	      || publication.getProviderLid().equals(providerLid))
	  && publication.getProviderName().equals(providerName)) {
	// Yes: No need for further checking.
	if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Found match.");
	return true;
      }
    }

    // No match was found.
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Did not find match.");
    return false;
  }

  /**
   * Normalizes publication data.
   * 
   * @param publication
   *          A SerialPublication with the publication data.
   * @return a SerialPublication with the normalized publication data.
   */
  private SerialPublication normalizePublication(
      SerialPublication publication) {
    final String DEBUG_HEADER = "normalizePublication(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "publication = " + publication);

    // Normalize the provider LOCKSS identifier, if necessary.
    if (!StringUtil.isNullString(publication.getProviderLid())) {
      if (publication.getProviderLid().length() > MAX_LID_COLUMN) {
	log.warning("provider LOCKSS ID too long '"
	    + publication.getProviderLid() + "' for title: '"
	    + publication.getPublicationName() + "' publisher: "
	    + publication.getPublisherName() + "'");
	publication.setProviderLid(DbManager.truncateVarchar(
	    publication.getProviderLid(), MAX_LID_COLUMN));
      }
    }

    // Normalize the provider name, if necessary.
    if (!StringUtil.isNullString(publication.getProviderName())) {
      if (publication.getProviderName().length() > MAX_NAME_COLUMN) {
	log.warning("provider name too long '" + publication.getProviderName()
	    + "' for title: '" + publication.getPublicationName()
	    + "' publisher: " + publication.getPublisherName() + "'");
	publication.setProviderName(DbManager.truncateVarchar(
	    publication.getProviderName(), MAX_NAME_COLUMN));
      }
    }

    // Normalize the print ISSN, if necessary.
    if (!StringUtil.isNullString(publication.getPissn())) {
      String issn = publication.getPissn().replaceAll("-", "");
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "issn = '" + issn + "'.");

      if (issn.length() > MAX_ISSN_COLUMN) {
	log.warning("issn too long '" + publication.getPissn()
	    + "' for title: '" + publication.getPublicationName()
	    + "' publisher: " + publication.getPublisherName() + "'");
	publication.setPissn(DbManager.truncateVarchar(issn, MAX_ISSN_COLUMN));
      } else {
	publication.setPissn(issn);
      }
    }

    // Normalize the electronic ISSN, if necessary.
    if (!StringUtil.isNullString(publication.getEissn())) {
      String issn = publication.getEissn().replaceAll("-", "");
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "issn = '" + issn + "'.");

      if (issn.length() > MAX_ISSN_COLUMN) {
	log.warning("issn too long '" + publication.getEissn()
	    + "' for title: '" + publication.getPublicationName()
	    + "' publisher: " + publication.getPublisherName() + "'");
	publication.setEissn(DbManager.truncateVarchar(issn, MAX_ISSN_COLUMN));
      } else {
	publication.setEissn(issn);
      }
    }

    // Normalize the proprietary identifier, if necessary.
    if (!StringUtil.isNullString(publication.getProprietaryId())) {
      if (publication.getProprietaryId().length() > MAX_PUBLICATION_ID_COLUMN) {
	log.warning("proprietaryId too long '" + publication.getProprietaryId()
	    + "' for title: '" + publication.getPublicationName()
	    + "' publisher: " + publication.getPublisherName() + "'");
	publication.setProprietaryId(DbManager.truncateVarchar(
	    publication.getProprietaryId(), MAX_PUBLICATION_ID_COLUMN));
      }
    }

    // Normalize the publisher name, if necessary.
    if (!StringUtil.isNullString(publication.getPublisherName())) {
      if (publication.getPublisherName().length() > MAX_NAME_COLUMN) {
	log.warning("publisher too long '" + publication.getPublisherName()
	    + "' for title: '" + publication.getPublicationName() + "'");
	publication.setPublisherName(DbManager.truncateVarchar(
	    publication.getPublisherName(), MAX_NAME_COLUMN));
      }
    }

    // Normalize the publication name, if necessary.
    if (!StringUtil.isNullString(publication.getPublicationName())) {
      if (publication.getPublicationName().length() > MAX_NAME_COLUMN) {
	log.warning("title too long '" + publication.getPublicationName()
	    + "' for publisher: " + publication.getPublisherName() + "'");
	publication.setPublicationName(DbManager.truncateVarchar(
	    publication.getPublicationName(), MAX_NAME_COLUMN));
      }
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "publication = " + publication);
    return publication;
  }

  /**
   * Validates subscription ranges.
   * 
   * @param subscriptionRanges
   *          A List<BibliographicPeriod> with the subscription ranges to be
   *          validated.
   * @param publication
   *          A SerialPublication with the publication data.
   * @return a List<BibliographicPeriod> with the subscription ranges that are
   *         not valid.
   */
  public List<BibliographicPeriod> validateRanges(
      List<BibliographicPeriod> subscriptionRanges,
      SerialPublication publication) {
    final String DEBUG_HEADER = "validateRanges(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "subscriptionRanges = " + subscriptionRanges);
      log.debug2(DEBUG_HEADER + "publication = " + publication);
    }

    List<BibliographicPeriod> invalidRanges =
	new ArrayList<BibliographicPeriod>();

    // Loop through all  the subscription ranges.
    for (BibliographicPeriod range : subscriptionRanges) {
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "range = " + range);

      // Check whether this range is not valid.
      if (range.isEmpty() || !isRangeValid(range, publication)) {
	// Yes: Add it to the result.
	invalidRanges.add(range);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "Range '" + range + "' is not valid.");
      }
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "invalidRanges = " + invalidRanges);
    return invalidRanges;
  }

  /**
   * Provides an indication of whether the passed subscription range is valid.
   * 
   * @param range
   *          A BibliographicPeriod with the subscription range to be validated.
   * @param publication
   *          A SerialPublication with the publication data.
   * @return a boolean with <code>true</code> if the passed subscription range
   *         is valid, <code>false</code> otherwise.
   */
  boolean isRangeValid(BibliographicPeriod range,
      SerialPublication publication) {
    final String DEBUG_HEADER = "isRangeValid(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "range = " + range);
      log.debug2(DEBUG_HEADER + "publication = " + publication);
    }

    // Get the range textual definition.
    String text = range.toDisplayableString();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "text = " + text);

    // Check whether the range textual definition is too long for the database.
    if (text.length() > MAX_RANGE_COLUMN) {
      // Yes: Report the problem.
      log.error("Invalid length (" + text.length() + ") for range '" + text
	  + "'.");
      return false;
    }

    // Check whether the range does not involve volumes or issues.
    if (range.includesFullYears()) {
      // Yes: Year-only ranges are always valid.
      return true;
    }

    // No: Determine whether the range matches any TdbAu of the publication.
    boolean result = matchesTitleTdbAu(range, publication);
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result = " + result);
    return result;
  }

  /**
   * Provides an indication of whether the passed range matches any Archival
   * Unit of a publication.
   * 
   * @param range
   *          A BibliographicPeriod with the publication range to be matched.
   * @param publication
   *          A SerialPublication with the publication data.
   * @return a boolean with <code>true</code> if the passed range matches any
   *         Archival Unit of this publication, <code>false</code> otherwise.
   */
  boolean matchesTitleTdbAu(BibliographicPeriod range,
      SerialPublication publication) {
    final String DEBUG_HEADER = "matchesTitleTdbAu(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "range = " + range);
      log.debug2(DEBUG_HEADER + "publication = " + publication);
    }

    // Loop through the publication archival units.
    for (TdbAu tdbAu : publication.getTdbTitle().getTdbAus()) {
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "tdbAu = " + tdbAu);

      // Check whether the range matches any of the publication ranges of the
      // current archival unit.
      if (range.matches(tdbAu.getPublicationRanges())) {
	// Yes: No need to check any further.
	if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result is true");
	return true;
      }
    }

    // No: The range does not match any Archival Unit of the publication.
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "result is false");
    return false;
  }

  /**
   * Adds subscriptions to the system.
   * 
   * @param subscriptions
   *          A Collection<Subscription> with the subscriptions to be added.
   * @param status
   *          A SubscriptionOperationStatus where to return the status of the
   *          operation.
   */
  public void addSubscriptions(Collection<Subscription> subscriptions,
      SubscriptionOperationStatus status) {
    final String DEBUG_HEADER = "addSubscriptions(): ";
    if (log.isDebug2()) {
      if (subscriptions != null) {
	log.debug2(DEBUG_HEADER + "subscriptions.size() = "
	    + subscriptions.size());
      } else {
	log.debug2(DEBUG_HEADER + "subscriptions is null");
      }
    }

    // Force a re-calculation of the relative weights of the repositories.
    repositories = null;

    Connection conn = null;

    try {
      // Get a connection to the database.
      conn = dbManager.getConnection();
    } catch (DbException dbe) {
      log.error(CANNOT_CONNECT_TO_DB_ERROR_MESSAGE, dbe);

      for (Subscription subscription : subscriptions) {
	status.addStatusEntry(subscription.getPublication()
	    .getPublicationName(), false, dbe.getMessage(), null);
      }

      if (log.isDebug2()) log.debug(DEBUG_HEADER + "Done.");
      return;
    }

    BatchAuStatus bas;

    try {
      // Loop through all the subscriptions.
      for (Subscription subscription : subscriptions) {
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "subscription = " + subscription);

	try {
	  // Persist the subscription in the database.
	  persistSubscription(conn, subscription);

	  List<BibliographicPeriod> subscribedRanges =
	      subscription.getSubscribedRanges();
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "subscribedRanges = " + subscribedRanges);

	  // Check whether the added subscription may imply the configuration of
	  // some archival unit.
	  if (subscribedRanges != null
	      && subscribedRanges.size() > 0
	      && (subscribedRanges.size() > 1
	          || !subscribedRanges.iterator().next().isEmpty())) {
	    // Yes: Configure the archival units that correspond to this
	    // subscription.
	    bas = configureAus(conn, subscription);
	  } else {
	    bas = null;
	  }

	  DbManager.commitOrRollback(conn, log);
	  status.addStatusEntry(subscription.getPublication()
	      .getPublicationName(), bas);
	} catch (IllegalStateException ise) {
	  try {
	    if ((conn != null) && !conn.isClosed()) {
	      conn.rollback();
	    }
	  } catch (SQLException sqle) {
	    log.error(CANNOT_ROLL_BACK_DB_CONNECTION_ERROR_MESSAGE, sqle);
	  }
	  log.error("Cannot add subscription " + subscription, ise);
	  status.addStatusEntry(subscription.getPublication()
	      .getPublicationName(), false, ise.getMessage(), null);
	} catch (IOException ioe) {
	  try {
	    if ((conn != null) && !conn.isClosed()) {
	      conn.rollback();
	    }
	  } catch (SQLException sqle) {
	    log.error(CANNOT_ROLL_BACK_DB_CONNECTION_ERROR_MESSAGE, sqle);
	  }
	  log.error("Cannot add subscription " + subscription, ioe);
	  status.addStatusEntry(subscription.getPublication()
	      .getPublicationName(), false, ioe.getMessage(), null);
	} catch (DbException dbe) {
	  try {
	    if ((conn != null) && !conn.isClosed()) {
	      conn.rollback();
	    }
	  } catch (SQLException sqle) {
	    log.error(CANNOT_ROLL_BACK_DB_CONNECTION_ERROR_MESSAGE, sqle);
	  }
	  log.error("Cannot add subscription " + subscription, dbe);
	  status.addStatusEntry(subscription.getPublication()
	      .getPublicationName(), false, dbe.getMessage(), null);
	} catch (SubscriptionException se) {
	  try {
	    if ((conn != null) && !conn.isClosed()) {
	      conn.rollback();
	    }
	  } catch (SQLException sqle) {
	    log.error(CANNOT_ROLL_BACK_DB_CONNECTION_ERROR_MESSAGE, sqle);
	  }
	  log.error("Cannot add subscription " + subscription, se);
	  status.addStatusEntry(subscription.getPublication()
	      .getPublicationName(), false, se.getMessage(), null);
	}
      }
    } finally {
      DbManager.safeRollbackAndClose(conn);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Persists a subscription in the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscription
   *          A Subscription with the subscription to be persisted.
   * @throws DbException
   *           if any problem occurred accessing the database.
   * @throws SubscriptionException
   *           if there are problems with the subscription publication.
   */
  private void persistSubscription(Connection conn, Subscription subscription)
      throws DbException, SubscriptionException {
    final String DEBUG_HEADER = "persistSubscription(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscription = " + subscription);

    // Get the subscription ranges.
    List<BibliographicPeriod> subscribedRanges =
	subscription.getSubscribedRanges();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "subscribedRanges = " + subscribedRanges);

    List<BibliographicPeriod> unsubscribedRanges =
	subscription.getUnsubscribedRanges();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "unsubscribedRanges = " + unsubscribedRanges);

    // Do nothing more if there are no subscription ranges.
    if ((subscribedRanges == null || subscribedRanges.size() < 1)
	&& (unsubscribedRanges == null || unsubscribedRanges.size() < 1)) {
      return;
    }

    // Get the subscription publication.
    SerialPublication publication = subscription.getPublication();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "publication = " + publication);

    // Find the publisher in the database or create it.
    Long publisherSeq =
	mdManager.findOrCreatePublisher(conn,  publication.getPublisherName());
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "publisherSeq = " + publisherSeq);

    String publicationName = publication.getPublicationName();

    // Get the publication TDB title.
    TdbTitle title = publication.getTdbTitle();

    // Check whether this publication has no TDB title.
    if (title == null) {
      // Yes: Report the problem.
      String message = "No TdbTitle for publication '" + publicationName + "'";
      log.error(message);
      throw new SubscriptionException(message);
    }

    String pubType = title.getPublicationType();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "pubType = " + pubType);

    // Check whether it's not a serial publication type.
    if (!isSerialPublicationType(pubType)) {
      // Yes: Report the problem.
      String message = "It's not possible to subscribe to publication '"
	  + publicationName + "' because it is of type'" + pubType 
          + "', not a book series or a journal";
      log.error(message);
      throw new SubscriptionException(message);
    }

    String pIssn = publication.getPissn();
    String eIssn = publication.getEissn();
    String proprietaryId = publication.getProprietaryId();

    // Find the publication in the database or create it.
    Long publicationSeq = null;

    // Check whether it is a book series.
    if (MetadataField.PUBLICATION_TYPE_BOOKSERIES.equals(pubType)) {
      // Yes: Find it or create it.
      publicationSeq = mdManager.findOrCreateBookSeries(conn, publisherSeq,
	  pIssn, eIssn, publicationName, proprietaryId);
      // No: Check whether it is a journal.
    } else if (MetadataField.PUBLICATION_TYPE_JOURNAL.equals(pubType)) {
      // Yes: Find it or create it.
      publicationSeq = mdManager.findOrCreateJournal(conn,publisherSeq, pIssn,
	  eIssn, publicationName, proprietaryId);
    }
      
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);

    // Find the provider in the database or create it.
    Long providerSeq = dbManager.findOrCreateProvider(conn,
	publication.getProviderLid(), publication.getProviderName());
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "providerSeq = " + providerSeq);

    // Find the subscription in the database or create it.
    Long subscriptionSeq =
	findOrCreateSubscription(conn, publicationSeq, providerSeq);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);

    // Persist the subscribed ranges.
    int count =
	persistSubscribedRanges(conn, subscriptionSeq, subscribedRanges);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "Added " + count + " subscribed ranges.");

    // Persist the unsubscribed ranges.
    count =
	persistUnsubscribedRanges(conn, subscriptionSeq, unsubscribedRanges);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "Added " + count + " unsubscribed ranges.");

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Configures the archival units covered by a subscription.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscription
   *          A Subscription with the subscription involved.
   * @return a BatchAuStatus with the status of the operation.
   * @throws IOException
   *           if there are problems configuring the archival units.
   * @throws DbException
   *           if any problem occurred accessing the database.
   * @throws SubscriptionException
   *           if there are problems with the subscription publication.
   */
  BatchAuStatus configureAus(Connection conn, Subscription subscription)
      throws IOException, DbException, SubscriptionException {
    final String DEBUG_HEADER = "configureAus(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscription = " + subscription);

    // Get the subscribed ranges.
    List<BibliographicPeriod> subscribedRanges =
	subscription.getSubscribedRanges();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "subscribedRanges = " + subscribedRanges);

    // Do nothing more if there are no subscribed ranges.
    if (subscribedRanges == null || subscribedRanges.size() < 1) {
      return null;
    }

    // Get the subscription publication.
    SerialPublication publication = subscription.getPublication();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "publication = " + publication);

    // Check whether the publication has no TdbTitle.
    if (publication.getTdbTitle() == null) {
      // Yes: Report the problem.
      String message = "Cannot find tdbTitle with name '"
	  + publication.getPublicationName() + "'.";
      log.error(message);
      throw new SubscriptionException(message);
    }

    // Configure the archival units.
    return configureAus(conn, publication, subscribedRanges,
	subscription.getUnsubscribedRanges());
  }

  /**
   * Persists the subscribed ranges of a subscription in the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscriptionSeq
   *          A Long with the subscription identifier.
   * @param subscribedRanges
   *          A List<BibliographicPeriod> with the subscription subscribed
   *          ranges to be persisted.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  int persistSubscribedRanges(Connection conn, Long subscriptionSeq,
      List<BibliographicPeriod> subscribedRanges) throws DbException {
    int count = 0;

    if (subscribedRanges != null) {
      // Find the existing subscribed ranges for the subscription.
      List<BibliographicPeriod> existingRanges =
	  findSubscriptionRanges(conn, subscriptionSeq, true);

      // Loop through all the subscribed ranges to be persisted.
      for (BibliographicPeriod range : subscribedRanges) {
	// Check whether the range to be persisted does not exist already.
	if (!existingRanges.contains(range)) {
	  // Yes: Persist the subscribed ranges.
	  count += persistSubscriptionRange(conn, subscriptionSeq, range, true);
	}
      }
    }

    return count;
  }

  /**
   * Persists the unsubscribed ranges of a subscription in the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscriptionSeq
   *          A Long with the subscription identifier.
   * @param unsubscribedRanges
   *          A List<BibliographicPeriod> with the subscription unsubscribed
   *          ranges to be persisted.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  int persistUnsubscribedRanges(Connection conn, Long subscriptionSeq,
      List<BibliographicPeriod> unsubscribedRanges) throws DbException {
    int count = 0;

    if (unsubscribedRanges != null) {
      // Find the existing unsubscribed ranges for the subscription.
      List<BibliographicPeriod> existingRanges =
	  findSubscriptionRanges(conn, subscriptionSeq, false);

      // Loop through all the unsubscribed ranges to be persisted.
      for (BibliographicPeriod range : unsubscribedRanges) {
	// Check whether the range to be persisted does not exist already.
	if (!existingRanges.contains(range)) {
	  // Persist the unsubscribed range.
	  count +=
	      persistSubscriptionRange(conn, subscriptionSeq, range, false);
	}
      }
    }

    return count;
  }

  /**
   * Configures the archival units covered by the subscribed ranges of a
   * publication and not covered by the unsubscribed ranges.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param publication
   *          A SerialPublication with the publication involved.
   * @param subscribedRanges
   *          A List<BibliographicPeriod> with the subscribed ranges of the
   *          publication.
   * @param unsubscribedRanges
   *          A List<BibliographicPeriod> with the unsubscribed ranges of the
   *          publication.
   * @return a BatchAuStatus with the status of the operation.
   * @throws IOException
   *           if there are problems configuring the archival units.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private BatchAuStatus configureAus(Connection conn,
      SerialPublication publication,
      List<BibliographicPeriod> subscribedRanges,
      List<BibliographicPeriod> unsubscribedRanges)
	  throws IOException, DbException {
    final String DEBUG_HEADER = "configureAus(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "publication = " + publication);
      log.debug2(DEBUG_HEADER + "subscribedRanges = " + subscribedRanges);
      log.debug2(DEBUG_HEADER + "unsubscribedRanges = " + unsubscribedRanges);
    }

    // Get the publication archival units covered by the subscription.
    Set<TdbAu> coveredTdbAus = getCoveredTdbAus(publication.getTdbTitle(),
	subscribedRanges, unsubscribedRanges);

    // Initialize the configuration used to configure the archival units.
    Configuration config = ConfigManager.newConfiguration();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "config = " + config);

    // Loop through all the covered publication archival units.
    for (TdbAu tdbAu : coveredTdbAus) {
      // Skip those archival units that are down.
      if (tdbAu.isDown()) {
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "TdbAu '" + tdbAu
	    + "' is marked down.");
	continue;
      }

      // Get the archival unit identifier.
      String auId = tdbAu.getAuId(pluginManager);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "auId = " + auId);

      // Get the archival unit.
      ArchivalUnit au = pluginManager.getAuFromId(auId);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "au = " + au);

      // Check whether the archival unit is not active.
      if (au == null || (!pluginManager.isActiveAu(au))) {
	// Yes: Add the archival unit to the configuration of those to be
	// configured.
	config = addAuConfiguration(tdbAu, auId, config);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "config = " + config);
      } else {
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "TdbAu '" + tdbAu
	    + "' is already configured.");
      }
    }

    // Configure the unconfigured archival units that are covered by the
    // subscription.
    return configureAuBatch(config);
  }

  /**
   * Updates existing subscriptions.
   * 
   * @param subscriptions
   *          A Collection<Subscription> with the subscriptions to be updated.
   * @param status
   *          A SubscriptionOperationStatus where to return the status of the
   *          operation.
   */
  public void updateSubscriptions(Collection<Subscription> subscriptions,
      SubscriptionOperationStatus status) {
    final String DEBUG_HEADER = "updateSubscriptions(): ";
    if (log.isDebug2()) {
      if (subscriptions != null) {
	log.debug2(DEBUG_HEADER + "subscriptions.size() = "
	    + subscriptions.size());
      } else {
	log.debug2(DEBUG_HEADER + "subscriptions is null");
      }
    }

    // Force a re-calculation of the relative weights of the repositories.
    repositories = null;

    Connection conn = null;

    try {
      // Get a connection to the database.
      conn = dbManager.getConnection();
    } catch (DbException dbe) {
      log.error(CANNOT_CONNECT_TO_DB_ERROR_MESSAGE, dbe);

      for (Subscription subscription : subscriptions) {
	status.addStatusEntry(subscription.getPublication()
	    .getPublicationName(), false, dbe.getMessage(), null);
      }

      if (log.isDebug2()) log.debug(DEBUG_HEADER + "Done.");
      return;
    }

    BatchAuStatus bas;

    try {
      // Loop through all the subscriptions.
      for (Subscription subscription : subscriptions) {
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "subscription = " + subscription);

	try {
	  // Update the subscription in the database.
	  updateSubscription(conn, subscription);

	  List<BibliographicPeriod> subscribedRanges =
	      subscription.getSubscribedRanges();
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "subscribedRanges = " + subscribedRanges);

	  // Check whether the updated subscription may imply the configuration
	  // of some archival unit.
	  if (subscribedRanges != null
	      && subscribedRanges.size() > 0
	      && (subscribedRanges.size() > 1
	          || !subscribedRanges.iterator().next().isEmpty())) {
	    // Yes: Configure the archival units that correspond to this
	    // subscription.
	    bas = configureAus(conn, subscription);
	  } else {
	    bas = null;
	  }

	  DbManager.commitOrRollback(conn, log);
	  status.addStatusEntry(subscription.getPublication()
	      .getPublicationName(), bas);
	} catch (IllegalStateException ise) {
	  try {
	    if ((conn != null) && !conn.isClosed()) {
	      conn.rollback();
	    }
	  } catch (SQLException sqle) {
	    log.error(CANNOT_ROLL_BACK_DB_CONNECTION_ERROR_MESSAGE, sqle);
	  }
	  log.error("Cannot update subscription " + subscription, ise);
	  status.addStatusEntry(subscription.getPublication()
	      .getPublicationName(), false, ise.getMessage(), null);
	} catch (IOException ioe) {
	  try {
	    if ((conn != null) && !conn.isClosed()) {
	      conn.rollback();
	    }
	  } catch (SQLException sqle) {
	    log.error(CANNOT_ROLL_BACK_DB_CONNECTION_ERROR_MESSAGE, sqle);
	  }
	  log.error("Cannot update subscription " + subscription, ioe);
	  status.addStatusEntry(subscription.getPublication()
	      .getPublicationName(), false, ioe.getMessage(), null);
	} catch (DbException dbe) {
	  try {
	    if ((conn != null) && !conn.isClosed()) {
	      conn.rollback();
	    }
	  } catch (SQLException sqle) {
	    log.error(CANNOT_ROLL_BACK_DB_CONNECTION_ERROR_MESSAGE, sqle);
	  }
	  log.error("Cannot update subscription " + subscription, dbe);
	  status.addStatusEntry(subscription.getPublication()
	      .getPublicationName(), false, dbe.getMessage(), null);
	} catch (SubscriptionException se) {
	  try {
	    if ((conn != null) && !conn.isClosed()) {
	      conn.rollback();
	    }
	  } catch (SQLException sqle) {
	    log.error(CANNOT_ROLL_BACK_DB_CONNECTION_ERROR_MESSAGE, sqle);
	  }
	  log.error("Cannot update subscription " + subscription, se);
	  status.addStatusEntry(subscription.getPublication()
	      .getPublicationName(), false, se.getMessage(), null);
	}
      }
    } finally {
      DbManager.safeRollbackAndClose(conn);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Updates a subscriptions in the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscription
   *          A Subscription with the subscription to be persisted.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private void updateSubscription(Connection conn, Subscription subscription)
      throws DbException {
    final String DEBUG_HEADER = "updateSubscription(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscription = " + subscription);

    // Get the subscription identifier.
    Long subscriptionSeq = subscription.getSubscriptionSeq();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);

    // Delete all the subscription ranges.
    int deletedRangesCount = deleteAllSubscriptionRanges(conn, subscriptionSeq);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "deletedRangesCount = " + deletedRangesCount);

    // Get the subscribed ranges.
    List<BibliographicPeriod> ranges = subscription.getSubscribedRanges();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "subscribedRanges = " + ranges);

    // Persist in the database the updated subscribed ranges.
    persistSubscriptionRanges(conn, subscriptionSeq, ranges, true);

    // Get the unsubscribed ranges.
    ranges = subscription.getUnsubscribedRanges();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "unsubscribedRanges = " + ranges);

    // Persist in the database the updated unsubscribed ranges.
    persistSubscriptionRanges(conn, subscriptionSeq, ranges, false);

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Deletes all the ranges of a given subscription.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscriptionSeq
   *          A Long with the identifier of the subscription.
   * @return an int with the number of deleted rows.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private int deleteAllSubscriptionRanges(Connection conn, Long subscriptionSeq)
      throws DbException {
    final String DEBUG_HEADER = "deleteAllSubscriptionRanges(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);

    int count = 0;
    PreparedStatement deleteAllSubscriptionRanges =
	dbManager.prepareStatement(conn, DELETE_ALL_SUBSCRIPTION_RANGES_QUERY);

    try {
      deleteAllSubscriptionRanges.setLong(1, subscriptionSeq);

      count = dbManager.executeUpdate(deleteAllSubscriptionRanges);
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "Deleted " + count + " subscription ranges.");
    } catch (SQLException sqle) {
      log.error("Cannot delete subscription ranges", sqle);
      log.error("SQL = '" + DELETE_ALL_SUBSCRIPTION_RANGES_QUERY + "'.");
      log.error("subscriptionSeq = " + subscriptionSeq);
      throw new DbException("Cannot delete subscription ranges", sqle);
    } finally {
      DbManager.safeCloseStatement(deleteAllSubscriptionRanges);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "count = " + count);
    return count;
  }

  /**
   * Persists ranges of a given subscription.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscriptionSeq
   *          A Long with the identifier of the subscription.
   * @param ranges
   *          A List<BibliographicPeriod> with the subscription ranges.
   * @param subscribed
   *          A boolean with the indication of whether the LOCKSS installation
   *          is subscribed to the publication range or not.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private void persistSubscriptionRanges(Connection conn, Long subscriptionSeq,
      List<BibliographicPeriod> ranges, boolean subscribed)
      throws DbException {
    final String DEBUG_HEADER = "persistSubscriptionRanges(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);
      log.debug2(DEBUG_HEADER + "ranges = " + ranges);
      log.debug2(DEBUG_HEADER + "subscribed = " + subscribed);
    }

    // Loop through the ranges to be persisted.
    if (ranges != null) {
      for (BibliographicPeriod range : ranges) {
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "range = " + range);
	persistSubscriptionRange(conn, subscriptionSeq, range, subscribed);
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Provides the sorter of publications.
   * 
   * @return a Comparator<SerialPublication> with the sorter of publications.
   */
  public Comparator<SerialPublication> getPublicationComparator() {
    return PUBLICATION_COMPARATOR;
  }

  /**
   * Provides the sorter of subscriptions by their publications.
   * 
   * @return a Comparator<SerialPublication> with the sorter of subscriptions by
   *         their publications.
   */
  public Comparator<Subscription> getSubscriptionByPublicationComparator() {
    return SUBSCRIPTION_BY_PUBLICATION_COMPARATOR;
  }

  /**
   * Provides the repository to be used when configuring an AU.
   * 
   * @return a String identifying the repository to be used.
   */
  synchronized String getRepository() {
    final String DEBUG_HEADER = "getRepository(): ";

    // Check whether there is no list of weighted repositories.
    if (repositories == null) {
      // Yes: Populate the list of weighted repositories.
      repositories = populateRepositories(remoteApi.getRepositoryMap());

      // Use the first repository in the new list.
      repositoryIndex = 0;
    }

    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "repositoryIndex = " + repositoryIndex);

    // Get the repository to be used.
    String repository = repositories.get(repositoryIndex);

    // Point to the next repository.
    repositoryIndex = ++repositoryIndex % repositories.size();

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "repository = " + repository);
    return repository;
  }

  /**
   * Populates the list of weighted repositories, to be used in a round-robin
   * fashion for the subsequent AU configurations.
   * 
   * @param repositoryMap
   *          A Map<String, PlatformUtil.DF> with the map of all distinct
   *          repositories available.
   * @return a List<String> with the list of weighted repositories.
   */
  List<String> populateRepositories(Map<String, PlatformUtil.DF> repositoryMap)
  {
    final String DEBUG_HEADER = "populateRepositories(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "repositoryMap.size() = "
	+ repositoryMap.size());

    // Initialize the list of available repositories.
    List<String> repos = new ArrayList<String>();

    // Handle an empty repository map.
    if (repositoryMap.size() < 1) {
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "repos = " + repos);
      return repos;
    }

    // Get the available repositories sorted by their available space.
    TreeSet<Entry<String, PlatformUtil.DF>> sortedRepos =
	new TreeSet<Entry<String, PlatformUtil.DF>>(DF_BY_AVAIL_COMPARATOR);
    sortedRepos.addAll(repositoryMap.entrySet());
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "sortedRepos.size() = " + sortedRepos.size());

    // Handle the case of a single repository.
    if (sortedRepos.size() == 1) {
      repos.add(sortedRepos.first().getKey());
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "Added " + sortedRepos.first().getKey());

      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "repos = " + repos);
      return repos;
    }

    // Get the repository available space threshold from the configuration.
    int repoThreshold =	ConfigManager.getCurrentConfig()
	.getInt(PARAM_REPOSITORY_AVAIL_SPACE_THRESHOLD,
	        DEFAULT_REPOSITORY_AVAIL_SPACE_THRESHOLD);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "repoThreshold = " + repoThreshold);

    // Get the available space of the repository with the least amount of
    // available space.
    long minAvail = sortedRepos.first().getValue().getAvail();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "minAvail = " + minAvail);

    // Remove repositories that don't have a minimum of space, except the last
    // one.
    while (minAvail < repoThreshold) {
      sortedRepos.remove(sortedRepos.first());
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "sortedRepos.size() = " + sortedRepos.size());

      // If there is only one repository left, use it.
      if (sortedRepos.size() == 1) {
        repos.add(sortedRepos.first().getKey());
        if (log.isDebug3())
  	log.debug3(DEBUG_HEADER + "Added " + sortedRepos.first().getKey());

        if (log.isDebug2()) log.debug2(DEBUG_HEADER + "repos = " + repos);
        return repos;
      }

      // Get the available space of the repository with the least amount of
      // available space.
      minAvail = sortedRepos.first().getValue().getAvail();
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "minAvail = " + minAvail);
    }

    // Count the remaining repositories.
    int repoCount = sortedRepos.size();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "repoCount = " + repoCount);

    // Initialize the array of repositories and the total available space.
    long totalAvailable = 0l;
    int i = 0;
    Entry<String, PlatformUtil.DF>[] repoArray = new Entry[repoCount];

    for (Entry<String, PlatformUtil.DF> df : sortedRepos) {
      totalAvailable += df.getValue().getAvail();
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "totalAvailable = " + totalAvailable);

      repoArray[i++] = df;
    }

    // For each repository, compute the target fraction and initialize the count
    // of appearances in the final list.
    i = 0;
    double[] repoTargetFraction = new double[repoCount];
    int[] repoAppearances = new int[repoCount];

    for (Entry<String, PlatformUtil.DF> df : repoArray) {
      repoTargetFraction[i] =
	  df.getValue().getAvail() / (double)totalAvailable;
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "i = " + i
	  + ", repoTargetFraction[i] = " + repoTargetFraction[i]);

      repoAppearances[i++] = 0;
    }

    // The first repository in the list is the one with the largest amount of
    // available space.
    repos.add(repoArray[repoCount - 1].getKey());
    repoAppearances[repoCount - 1]++;

    // An indication of whether the created list matches the target fractions of
    // all the repositories.
    boolean done = false;
    
    while (!done) {
      // If no differences between the target fractions and the fractions of
      // appearances are found in the process below, the list is complete.
      done = true;

      double difference = 0;
      double maxDifference = 0;
      int nextRepo = -1;

      // Loop through all the repositories.
      for (int j = 0; j < repoCount; j++) {
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "j = " + j
	    + ", repoAppearances[j]/(double)repos.size() = "
	    + repoAppearances[j]/(double)repos.size()
	    + ", repoTargetFraction[j] = " + repoTargetFraction[j]);

	// Find the difference between the target fraction and the fraction of
	// appearances.
	difference =
	    repoTargetFraction[j] - repoAppearances[j]/(double)repos.size();
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "difference = " + difference);

	// Update the largest difference, if necessary.
	if (maxDifference < difference) {
	  maxDifference = difference;
	  nextRepo = j;
	}
      }

      // Check whether a repository with the largest difference was found.
      if (nextRepo != -1) {
	// Yes: Add it to the list.
	repos.add(repoArray[nextRepo].getKey());
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "Added " + repoArray[nextRepo].getKey());

	// Increment its appearance count.
	repoAppearances[nextRepo]++;

	// Check whether not all the target fractions have been achieved.
	for (int k = 0; k < repoCount; k++) {
	  difference =
	      repoAppearances[k]/(double)repos.size() - repoTargetFraction[k];
	  if (log.isDebug3()) log.debug3(DEBUG_HEADER + "k = " + k
	      + ", difference = " + difference);

	  // Within one per cent is a match.
	  if (Math.abs(difference) > 0.01) {
	    done = false;
	    break;
	  }
	}
      }
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "repos = " + repos);
    return repos;
  }

  // Sorter of repository disk information by available space.
  private static Comparator<Entry<String, PlatformUtil.DF>>
  DF_BY_AVAIL_COMPARATOR = new Comparator<Entry<String, PlatformUtil.DF>>() {
    public int compare(Entry<String, PlatformUtil.DF> o1,
	Entry<String, PlatformUtil.DF> o2) {
      // Sort by available space.
      return ((new Long(o1.getValue().getAvail()))
	  .compareTo(new Long(o2.getValue().getAvail())));
    }
  };

  /**
   * Provides the SQL statement used to insert a subscription range.
   * 
   * @return a String with the SQL statement used to insert a subscription
   *         range.
   */
  private String getInsertSubscriptionRangeSql() {
    if (dbManager.isTypeMysql()) {
      return INSERT_SUBSCRIPTION_RANGE_MYSQL_QUERY;
    }

    return INSERT_SUBSCRIPTION_RANGE_QUERY;
  }

  /**
   * Writes to a zip file the subscription definitions.
   * 
   * @param zipStream
   *          A ZipOutputStream to the zip file.
   * 
   * @throws DbException, IOException
   */
  public void writeSubscriptionsBackupToZip(ZipOutputStream zipStream)
      throws DbException, IOException {
    final String DEBUG_HEADER = "writeSubscriptionsBackupToZip(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // Get the subscriptions.
    List<Subscription> subscriptions = findSubscriptionDataForBackup();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "subscriptions = " + subscriptions);

    // Do nothing if there are no subscriptions.
    if (subscriptions == null || subscriptions.size() == 0) {
      if (log.isDebug2())
	log.debug2(DEBUG_HEADER + "No subscriptions to write.");
      return;
    }

    // Create the subscription backup file entry.
    zipStream.putNextEntry(new ZipEntry(BACKUP_FILENAME));

    // Loop through all the subscription definitions to be backed up.
    for (Subscription subscription : subscriptions) {
      // Write this subscription definition to the zip file.
      writeSubscriptionBackupToStream(subscription, zipStream);
    }

    // Close the subscription backup file entry.
    zipStream.closeEntry();

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Provides the subscription data for backup purposes.
   * 
   * @return a List<Subscription> with the subscription data.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private List<Subscription> findSubscriptionDataForBackup()
      throws DbException {
    final String DEBUG_HEADER = "findSubscriptionDataForBackup(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    String publicationName;
    String providerLid;
    String providerName;
    String publisherName;
    String publicationId;
    String pIssn;
    String eIssn;
    String range;
    Boolean subscribed;
    String previousPublicationName = null;
    String previousProviderLid = null;
    String previousProviderName = null;
    String previousPublisherName = null;
    String previousPublicationId = null;
    String previousPissn = null;
    String previousEissn = null;
    SerialPublication publication;
    Subscription subscription = null;
    List<Subscription> subscriptions = new ArrayList<Subscription>();

    // Get a connection to the database.
    Connection conn = dbManager.getConnection();

    String query = FIND_SUBSCRIPTION_BACKUP_DATA_QUERY;
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "SQL = " + query);

    PreparedStatement getSubscriptionDataForBackup =
	dbManager.prepareStatement(conn, query);
    ResultSet resultSet = null;

    try {
      // Get the subscriptions from the database.
      resultSet = dbManager.executeQuery(getSubscriptionDataForBackup);

      // Loop through all the results.
      while (resultSet.next()) {
	// Get the publication data.
	publisherName = resultSet.getString(PUBLISHER_NAME_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "publisherName = " + publisherName);

	publicationName = resultSet.getString(NAME_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "publicationName = " + publicationName);

	providerLid = resultSet.getString(PROVIDER_LID_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "providerLid = " + providerLid);

	providerName = resultSet.getString(PROVIDER_NAME_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "providerName = " + providerName);

	publicationId = resultSet.getString(PUBLICATION_ID_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "publicationId = " + publicationId);

	pIssn = resultSet.getString(P_ISSN_TYPE);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "pIssn = " + pIssn);

	eIssn = resultSet.getString(E_ISSN_TYPE);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "eIssn = " + eIssn);

	// Check whether the publication of this result does not correspond to
	// the publication of the previous result.
	if (((publicationName == null && previousPublicationName != null)
	     || (publicationName != null
	         && !publicationName.equals(previousPublicationName)))
	    || ((providerLid == null && previousProviderLid != null)
		|| (providerLid != null
		    && !providerLid.equals(previousProviderLid)))
	    || ((providerName == null && previousProviderName != null)
		|| (providerName != null
		    && !providerName.equals(previousProviderName)))
	    || ((publicationId == null && previousPublicationId != null)
		|| (publicationId != null
		    && !publicationId.equals(previousPublicationId)))
	    || ((publisherName == null && previousPublisherName != null)
		|| (publisherName != null
		    && !publisherName.equals(previousPublisherName)))
	    || ((pIssn == null && previousPissn != null)
		|| (pIssn != null && !pIssn.equals(previousPissn)))
	    || ((eIssn == null && previousEissn != null)
		|| (eIssn != null && !eIssn.equals(previousEissn)))) {

	  // Yes: Start a new subscription.
	  publication = new SerialPublication();
	  publication.setPublisherName(publisherName);
	  publication.setPublicationName(publicationName);
	  publication.setProviderLid(providerLid);
	  publication.setProviderName(providerName);
	  publication.setProprietaryId(publicationId);
	  publication.setPissn(pIssn);
	  publication.setEissn(eIssn);

	  // Validate the publication name.
	  publication.getTdbTitle();

	  subscription = new Subscription();
	  subscription.setPublication(publication);
	  subscription.
	  setSubscribedRanges(new ArrayList<BibliographicPeriod>());
	  subscription.
	  setUnsubscribedRanges(new ArrayList<BibliographicPeriod>());

	  subscriptions.add(subscription);

	  // Remember the publication for this subscription.
	  previousPublisherName = publisherName;
	  previousPublicationName = publicationName;
	  previousProviderLid = providerLid;
	  previousProviderName = providerName;
	  previousPublicationId = publicationId;
	  previousPissn = pIssn;
	  previousEissn = eIssn;
	}

	// Get the subscription data.
	range = resultSet.getString(SUBSCRIPTION_RANGE_COLUMN);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "range = " + range);

	subscribed = resultSet.getBoolean(SUBSCRIBED_COLUMN);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "subscribed = " + subscribed);

	if (subscribed.booleanValue()) {
	  subscription.getSubscribedRanges().
	  add(new BibliographicPeriod(range));
	} else {
	  subscription.getUnsubscribedRanges().
	  add(new BibliographicPeriod(range));
	}
      }
    } catch (SQLException sqle) {
      String message = "Cannot get subscriptions for backup";
      log.error(message, sqle);
      log.error("SQL = '" + query + "'.");
      throw new DbException(message, sqle);
    } finally {
      DbManager.safeCloseResultSet(resultSet);
      DbManager.safeCloseStatement(getSubscriptionDataForBackup);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "subscriptions.size() = "
	+ subscriptions.size());
    return subscriptions;
  }

  /**
   * Writes to a stream the definition of a subscription for backup purposes.
   * 
   * @param subscription
   *          A Subscription with the subscription definition.
   * @param outputStream
   *          An OutputStream where to write the subscription data.
   * 
   * @throws IOException
   */
  private void writeSubscriptionBackupToStream(Subscription subscription,
      OutputStream outputStream) throws IOException {
    final String DEBUG_HEADER = "writeSubscriptionBackupToStream(): ";
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscription = " + subscription);

    // Ignore empty subscriptions.
    if (subscription == null) {
      log.warning("Null subscription not added to backup file.");
      return;
    }

    // Get the subscription publication.
    SerialPublication publication = subscription.getPublication();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "publication = " + publication);

    // Ignore subscriptions with no publication.
    if (publication == null) {
      log.warning("Subscription with null publication not added to backup " +
	  "file.");
      return;
    }

    // Write the publication data.
    StringBuilder entry = new StringBuilder(StringUtil
	.blankOutNlsAndTabs(publication.getPublicationName()));

    entry.append(BACKUP_FIELD_SEPARATOR)
    .append(StringUtil.blankOutNlsAndTabs(publication.getProviderLid()))
    .append(BACKUP_FIELD_SEPARATOR)
    .append(StringUtil.blankOutNlsAndTabs(publication.getProviderName()))
    .append(BACKUP_FIELD_SEPARATOR)
    .append(StringUtil.blankOutNlsAndTabs(publication.getPublisherName()))
    .append(BACKUP_FIELD_SEPARATOR)
    .append(StringUtil.blankOutNlsAndTabs(publication.getPissn()))
    .append(BACKUP_FIELD_SEPARATOR)
    .append(StringUtil.blankOutNlsAndTabs(publication.getEissn()))
    .append(BACKUP_FIELD_SEPARATOR)
    .append(StringUtil.blankOutNlsAndTabs(publication.getProprietaryId()));

    // Loop through all the subscribed ranges.
    for (BibliographicPeriod range : subscription.getSubscribedRanges()) {
      // Write the subscribed range.
      entry.append(BACKUP_FIELD_SEPARATOR).append("true")
      .append(BACKUP_FIELD_SEPARATOR)
      .append(StringUtil.blankOutNlsAndTabs(range.toDisplayableString()));
    }

    // Loop through all the unsubscribed ranges.
    for (BibliographicPeriod range : subscription.getUnsubscribedRanges()) {
      // Write the unsubscribed range.
      entry.append(BACKUP_FIELD_SEPARATOR).append("false")
      .append(BACKUP_FIELD_SEPARATOR)
      .append(StringUtil.blankOutNlsAndTabs(range.toDisplayableString()));
    }

    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "entry = " + entry);

    // Write the entry to the output stream.
    outputStream.write((entry.toString() + "\n")
	.getBytes(Charset.forName("UTF-8")));

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Loads into the system the subscriptions defined in a backup file.
   * 
   * @param backupDir
   *          A File with the directory where the backup files are stored.
   */
  public void loadSubscriptionsFromBackup(File backupDir) {
    final String DEBUG_HEADER = "loadSubscriptionsFromBackup(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "backupDir = " + backupDir);

    // Get the backup file.
    File backupFile = new File(backupDir, BACKUP_FILENAME);

    // Do nothing more if the backup file does not exist.
    if (!backupFile.exists()) {
      if (log.isDebug()) log.debug("No subscription backup file named '" +
	  BACKUP_FILENAME + "' found in directory '" + backupDir + "'.");

      return;
    }

    // Get the subscriptions defined in the backup file.
    List<Subscription> subscriptions =
	getSubscriptionsFromBackupFile(backupFile);
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "subscriptions.size() = "
	+ subscriptions.size());

    // Add to the system any subscriptions defined in the backup file.
    if (subscriptions.size() > 0) {
      addSubscriptions(subscriptions, new SubscriptionOperationStatus());
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Provides the subscriptions defined in a backup file.
   * 
   * @param backupFile
   *          A File with the backup file.
   * @return a List<Subscription> with the subscriptions defined in the backup
   *         file.
   */
  private List<Subscription> getSubscriptionsFromBackupFile(File backupFile) {
    final String DEBUG_HEADER = "getSubscriptionsFromBackupFile(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "backupFile = " + backupFile);

    List<Subscription> subscriptions = new ArrayList<Subscription>();

    InputStream is = null;
    Reader r = null;
    BufferedReader br = null;
    Subscription subscription = null;

    try {
      String line;
      is = new FileInputStream(backupFile);
      r = new InputStreamReader(is, "UTF-8");
      br = new BufferedReader(r);

      // Loop through all the lines in the backup file.
      while ((line = br.readLine()) != null) {
	// Get the subscription defined in the line, if any.
	subscription = getSubscriptionFromBackupFileLine(line);

	// Add the defined subscription to the results.
	if (subscription != null) {
	  subscriptions.add(subscription);
	}
      }
    } catch (Exception e) {
      log.error("Exception caught processing subscription backup file = "
	  + backupFile, e);
    } finally {
      if (br != null) {
	try {
	  br.close();
	} catch(Throwable t) {
	  if (log.isDebug()) log.debug(DEBUG_HEADER
	      + "Cound not close BufferedReader for file = " + backupFile);
	}
      }

      if (r != null) {
	try {
	  r.close();
	} catch(Throwable t) {
	  if (log.isDebug()) log.debug(DEBUG_HEADER
	      + "Cound not close Reader for file = " + backupFile);
	}
      }

      if (is != null) {
	try {
	  is.close();
	} catch(Throwable t) {
	  if (log.isDebug()) log.debug(DEBUG_HEADER
	      + "Cound not close InputStream for file = " + backupFile);
	}
      }
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscriptions = " + subscriptions);
    return subscriptions;
  }

  /**
   * Provides the subscription defined in one line of a backup file.
   * 
   * @param line
   *          A String with the subscription definition.
   * @return a Subscription with the subscriptions defined in the line.
   */
  private Subscription getSubscriptionFromBackupFileLine(String line) {
    final String DEBUG_HEADER = "getSubscriptionFromBackupFileLine(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "line = " + line);

    // Ignore empty lines.
    if (line == null || line.length() == 0) {
      if (log.isDebug())
	log.debug("No subscription definition in line '" + line + "'.");

      return null;
    }

    // Parse the line.
    Collection<String> items = StringUtil.breakAt(line, BACKUP_FIELD_SEPARATOR);

    // Ignore lines that do not contain a complete subscription definition.
    if (items == null || items.size() < 8) {
      if (log.isDebug())
	log.debug("No subscription definition in line '" + line + "'.");

      return null;
    }

    Iterator<String> iterator = items.iterator();

    // Get the publication of the subscription.
    SerialPublication publication = new SerialPublication();
    publication.setPublicationName(iterator.next());
    publication.setProviderLid(iterator.next());
    publication.setProviderName(iterator.next());
    publication.setPublisherName(iterator.next());

    String pIssn = iterator.next();
    
    if (!pIssn.isEmpty()) {
      publication.setPissn(pIssn);
    }

    String eIssn = iterator.next();
    
    if (!eIssn.isEmpty()) {
      publication.setEissn(eIssn);
    }

    String proprietaryId = iterator.next();
    
    if (!proprietaryId.isEmpty()) {
      publication.setProprietaryId(proprietaryId);
    }

    // Get the subscription ranges.
    List<BibliographicPeriod> subscribedRanges =
	new ArrayList<BibliographicPeriod>();
    List<BibliographicPeriod> unSubscribedRanges =
	new ArrayList<BibliographicPeriod>();

    while (iterator.hasNext()) {
      if ("true".equals(iterator.next())) {
	subscribedRanges.add(new BibliographicPeriod(iterator.next()));
      } else {
	unSubscribedRanges.add(new BibliographicPeriod(iterator.next()));
      }
    }

    // Create the subscription to be returned.
    Subscription subscription = new Subscription();

    subscription.setPublication(publication);
    subscription.setSubscribedRanges(subscribedRanges);
    subscription.setUnsubscribedRanges(unSubscribedRanges);

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "subscription = " + subscription);
    return subscription;
  }

  /**
   * Unsubscribes an archival unit.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param au
   *          An ArchivalUnit with the archival unit to be unsubscribed.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  void unsubscribeAu(Connection conn, ArchivalUnit au) throws DbException {
    final String DEBUG_HEADER = "unsubscribeAu(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "au = " + au);

    // Get the archival unit title.
    TdbAu tdbAu = au.getTdbAu();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "tdbAu = " + tdbAu);
    if (tdbAu == null) {
      return;
    }

    TdbTitle title = tdbAu.getTdbTitle();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "title = " + title);
    if (title == null) {
      return;
    }

    // Get the provider identifiers and the subscription identifiers and ranges
    // for the archival unit title.
    List<BibliographicPeriod> subscribedRanges =
	new ArrayList<BibliographicPeriod>();
    List<BibliographicPeriod> unsubscribedRanges =
	new ArrayList<BibliographicPeriod>();

    //try {
      Set<Long> subscriptionSeqs = populateTitleSubscriptionRanges(conn, title,
	  subscribedRanges, unsubscribedRanges);
      if (log.isDebug3()) {
	log.debug3(DEBUG_HEADER + "subscriptionSeqs = " + subscriptionSeqs);
	log.debug3(DEBUG_HEADER + "subscribedRanges = " + subscribedRanges);
	log.debug3(DEBUG_HEADER + "unsubscribedRanges = " + unsubscribedRanges);
      }

      // Nothing to do if the publication is not covered by any subscription at
      // all.
      if (subscriptionSeqs == null || subscriptionSeqs.size() == 0
	  || subscribedRanges == null || subscribedRanges.size() == 0) {
	return;
      }

      // Determine whether the archival unit is currently covered by a
      // subscribed range.
      List<TdbAu> tdbAus = new ArrayList<TdbAu>(1);
      tdbAus.add(tdbAu);

      boolean isSubscribed = false;

      // Loop through all the subscribed ranges.
      for (BibliographicPeriod range : subscribedRanges) {
	if (getRangeCoveredTdbAus(range, tdbAus).size() > 0) {
	  isSubscribed = true;
	  break;
	}
      }

      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "isSubscribed = " + isSubscribed);

      // Nothing to do if the archival unit is not currently covered by a
      // subscribed range.
      if (!isSubscribed) {
	return;
      }
    
      // Determine whether the archival unit is currently covered by an
      // unsubscribed range, even though it is also covered by a subscribed
      // range.
      boolean isUnsubscribed = false;
    
      // Check whether there are unsubscribed ranges.
      if (unsubscribedRanges != null && unsubscribedRanges.size() > 0) {
	// Yes: Loop through all the unsubscribed ranges.
	for (BibliographicPeriod range : unsubscribedRanges) {
	  if (getRangeCoveredTdbAus(range, tdbAus).size() > 0) {
	    isUnsubscribed = true;
	    break;
	  }
	}
      }

      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "isUnsubscribed = " + isUnsubscribed);

      // Nothing to do if the archival unit is currently covered by an
      // unsubscribed range.
      if (isUnsubscribed) {
	return;
      }

      // Get the archival unit publication ranges.
      List<BibliographicPeriod> ranges = tdbAu.getPublicationRanges();
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "ranges = " + ranges);

      // Loop through all the archival unit publication ranges to be
      // unsubscribed.
      for (BibliographicPeriod range : ranges) {
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "range = " + range);

	// Loop through all the publication subscriptions in the database.
	for (Long subscriptionSeq : subscriptionSeqs) {
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);

	  // Find the existing subscribed ranges for the subscription.
	  List<BibliographicPeriod> existingRanges =
	      findSubscriptionRanges(conn, subscriptionSeq, true);

	  // Check whether the range to be unsubscribed is subscribed.
	  if (existingRanges.contains(range)) {
	    // Yes: Switch the range from subscribed to unsubscribed.
	    int count = updateSubscriptionRangeType(conn, subscriptionSeq,
		range, false);
	    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "count = " + count);

	    if (count > 0) continue;
	  }

	  // Find the existing unsubscribed ranges for the subscription.
	  existingRanges = findSubscriptionRanges(conn, subscriptionSeq, false);

	  // Check whether the range to be persisted does not exist already.
	  if (!existingRanges.contains(range)) {
	    // Yes: Persist the unsubscribed range.
	    int count =
		persistSubscriptionRange(conn, subscriptionSeq, range, false);
	    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "count = " + count);
	  }
	}
      }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Updates the type of a subscription range.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param subscriptionSeq
   *          A Long with the identifier of the subscription.
   * @param range
   *          A BibliographicPeriod with the subscription range.
   * @param subscribed
   *          A boolean with the indication of whether the LOCKSS installation
   *          is subscribed to the publication range or not.
   * @return an int with the count of rows updated in the database.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private int updateSubscriptionRangeType(Connection conn, Long subscriptionSeq,
      BibliographicPeriod range, boolean subscribed) throws DbException {
    final String DEBUG_HEADER = "updateSubscriptionRangeType(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "subscriptionSeq = " + subscriptionSeq);
      log.debug2(DEBUG_HEADER + "range = " + range);
      log.debug2(DEBUG_HEADER + "subscribed = " + subscribed);
    }

    int count = 0;

    // Skip an empty range that does not accomplish anything.
    if (range.isEmpty()) {
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "count = " + count);
      return count;
    }

    PreparedStatement updateSubscriptionRange =
	dbManager.prepareStatement(conn, UPDATE_SUBSCRIPTION_RANGE_TYPE_QUERY);

    try {
      updateSubscriptionRange.setBoolean(1, subscribed);
      updateSubscriptionRange.setLong(2, subscriptionSeq);
      updateSubscriptionRange.setString(3, range.toDisplayableString());

      count = dbManager.executeUpdate(updateSubscriptionRange);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "count = " + count);
    } catch (SQLException sqle) {
      log.error("Cannot update subscription range", sqle);
      log.error("SQL = '" + UPDATE_SUBSCRIPTION_RANGE_TYPE_QUERY + "'.");
      log.error("subscriptionSeq = " + subscriptionSeq);
      log.error("range = " + range);
      log.error("subscribed = " + subscribed);
      throw new DbException("Cannot update subscription range", sqle);
    } finally {
      DbManager.safeCloseStatement(updateSubscriptionRange);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "count = " + count);
    return count;
  }
}
