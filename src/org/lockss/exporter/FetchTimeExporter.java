/*
 * $Id: FetchTimeExporter.java,v 1.2 2013-10-24 21:08:45 fergaloy-sf Exp $
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
 * Periodically exports fetch times of recently added metadata items.
 * 
 * @version 1.0
 */
package org.lockss.exporter;

import static org.lockss.db.DbManager.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import org.lockss.app.LockssDaemon;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.config.CurrentConfig;
import org.lockss.daemon.Cron;
import org.lockss.db.DbException;
import org.lockss.db.DbManager;
import org.lockss.metadata.MetadataManager;
import org.lockss.util.FileUtil;
import org.lockss.util.IOUtil;
import org.lockss.util.Logger;
import org.lockss.util.NumberUtil;
import org.lockss.util.TimeBase;

public class FetchTimeExporter {
  /**
   * Prefix for the export configuration entries.
   */
  public static final String PREFIX = Configuration.PREFIX + "export.";

  /**
   * Frequency of fetch time export operations.
   */
  public static final String PARAM_FETCH_TIME_EXPORT_TASK_FREQUENCY =
      PREFIX + "fetchTimeExportFrequency";

  /**
   * Default value of the frequency of fetch time export operations.
   */
  public static final String DEFAULT_FETCH_TIME_EXPORT_TASK_FREQUENCY =
      "hourly";

  /**
   * Name of this server for the purpose of assigning to it the fetch time
   * export output.
   * <p />
   * Defaults to the networking host name. Changes require daemon restart.
   */
  public static final String PARAM_SERVER_NAME =
      PREFIX + "fetchTimeExportServerName";

  /**
   * Name of the directory used to store the fetch time export output files.
   * <p />
   * Defaults to <code>fetchTime</code>. Changes require daemon restart.
   */
  public static final String PARAM_FETCH_TIME_EXPORT_OUTPUTDIR = PREFIX
      + "fetchTimeExportDirectoryName";

  /**
   * Default value of the directory used to store the fetch time export output
   * files.
   */
  public static final String DEFAULT_FETCH_TIME_EXPORT_OUTPUTDIR = "fetchTime";

  /**
   * Name of the key used to store in the database the identifier of the last
   * metadata item for which the data has been exported.
   * <p />
   * Defaults to <code>export_fetch_time_md_item_seq</code>. Changes require
   * daemon restart.
   */
  public static final String PARAM_FETCH_TIME_EXPORT_LAST_ITEM_LABEL =
      PREFIX + "fetchTimeExportLastMdItemSeqLabel";

  /**
   * Default value of the key used to store in the database the identifier of
   * the last metadata item for which the data has been exported.
   */
  public static final String DEFAULT_FETCH_TIME_EXPORT_LAST_ITEM_LABEL =
      "export_fetch_time_md_item_seq";

  // TODO: DERBYLOCK - This is needed to lock the tables for multiple shorter
  // periods of time instead of a single longer period. Once this problem is
  // solved, the parameter can be increased or the code that depends on it can
  // be refactored out.
  //
  // The maximum number of metadata items to process in one transaction.
  public static final int MAX_NUMBER_OF_TRANSACTION_EXPORTED_ITEMS = 100;

  // The maximum number of metadata items to write to one file.
  public static final int MAX_NUMBER_OF_EXPORTED_ITEMS_PER_FILE = 100000;

  private static final Logger log = Logger.getLogger(FetchTimeExporter.class);

  // Query to get the identifier of the last metadata item for which the data
  // has been exported.
  private static final String GET_LAST_EXPORTED_MD_ITEM_QUERY = "select "
      + LAST_VALUE_COLUMN
      + " from " + LAST_RUN_TABLE
      + " where " + LABEL_COLUMN + " = ?";

  // Query to update the identifier of the last metadata item for which the data
  // has been exported.
  private static final String INSERT_LAST_EXPORTED_MD_ITEM_QUERY = "insert "
      + "into " + LAST_RUN_TABLE
      + " (" + LABEL_COLUMN
      + "," + LAST_VALUE_COLUMN
      + ") values (?,?)";

  // Query to get the data to be exported.
  private static final String GET_EXPORT_FETCH_TIME_QUERY = "select "
      + "pr." + PUBLISHER_NAME_COLUMN
      + ", pl." + PLUGIN_ID_COLUMN
      + ", pl." + IS_BULK_CONTENT_COLUMN
      + ", min1." + NAME_COLUMN + " as PUBLICATION_NAME"
      + ", mit." + TYPE_NAME_COLUMN
      + ", mi2." + MD_ITEM_SEQ_COLUMN
      + ", min2." + NAME_COLUMN + " as ITEM_TITLE"
      + ", mi2." + DATE_COLUMN
      + ", mi2." + FETCH_TIME_COLUMN
      + ", a." + AU_KEY_COLUMN
      + ", u." + URL_COLUMN
      + " from " + PUBLISHER_TABLE + " pr"
      + "," + PLUGIN_TABLE + " pl"
      + "," + PUBLICATION_TABLE + " pn"
      + "," + MD_ITEM_NAME_TABLE + " min1"
      + "," + MD_ITEM_TABLE + " mi2"
      + "," + MD_ITEM_NAME_TABLE + " min2"
      + "," + MD_ITEM_TYPE_TABLE + " mit"
      + "," + AU_MD_TABLE + " am"
      + "," + AU_TABLE + " a"
      + "," + URL_TABLE + " u"
      + " where pr." + PUBLISHER_SEQ_COLUMN + " = pn." + PUBLISHER_SEQ_COLUMN
      + " and pn." + MD_ITEM_SEQ_COLUMN + " = min1." + MD_ITEM_SEQ_COLUMN
      + " and pn." + MD_ITEM_SEQ_COLUMN + " = mi2." + PARENT_SEQ_COLUMN
      + " and mi2." + MD_ITEM_SEQ_COLUMN + " = min2." + MD_ITEM_SEQ_COLUMN
      + " and mi2." + MD_ITEM_TYPE_SEQ_COLUMN
      + " = mit." + MD_ITEM_TYPE_SEQ_COLUMN
      + " and mi2." + AU_MD_SEQ_COLUMN + " = am." + AU_MD_SEQ_COLUMN
      + " and am." + AU_SEQ_COLUMN + " = a." + AU_SEQ_COLUMN
      + " and a." + PLUGIN_SEQ_COLUMN + " = pl." + PLUGIN_SEQ_COLUMN
      + " and mi2." + MD_ITEM_SEQ_COLUMN + " = u." + MD_ITEM_SEQ_COLUMN
      + " and u." + FEATURE_COLUMN + " = '"
      + MetadataManager.ACCESS_URL_FEATURE + "'"
      + " and " + "mi2." + MD_ITEM_SEQ_COLUMN + " > ?"
      + " order by mi2." + MD_ITEM_SEQ_COLUMN;

  // Query to update the identifier of the last metadata item for which the data
  // has been exported.
  private static final String UPDATE_LAST_EXPORTED_MD_ITEM_SEQ_QUERY = "update "
      + LAST_RUN_TABLE
      + " set " + LAST_VALUE_COLUMN + " = ?"
      + " where " + LABEL_COLUMN + " = ?";

  // The format of a date as required by the export output file name.
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat(
      "yyyy-MM-dd-HH-mm-ss");

  private static final String SEPARATOR = "\t";

  private final DbManager dbManager;
  private final FetchTimeExportManager exportManager;

  // The name of the server for the purpose of assigning to it the fetch time
  // export output.
  private String serverName = null;

  // The directory where the fetch time export output files reside.
  private File outputDir = null;

  // The key used to store in the database the identifier of the last metadata
  // item for which the data has been exported.
  private String lastMdItemSeqLabel = null;

  /**
   * Constructor.
   * 
   * @param daemon
   *          A LockssDaemon with the application daemon.
   */
  public FetchTimeExporter(LockssDaemon daemon) {
    dbManager = daemon.getDbManager();
    exportManager = daemon.getFetchTimeExportManager();
  }

  /**
   * Provides the Cron task used to schedule the export.
   * 
   * @return a Cron.Task with the task used to schedule the export.
   */
  public Cron.Task getCronTask() {
    return new FetchTimeExporterCronTask();
  }

  /**
   * Performs the periodic task of exporting the fetch time data.
   * 
   * @return <code>true</code> if the task can be considered executed,
   *         <code>false</code> otherwise.
   */
  private boolean export() {
    final String DEBUG_HEADER = "export(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // Do nothing more if the configuration failed.
    if (!configure()) {
      return true;
    }

    // Determine the report file name.
    String fileName = getReportFileName();
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "fileName = '" + fileName + "'.");

    File exportFile = new File(outputDir, fileName + ".ignore");
    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "exportFile = '" + exportFile + "'.");

    // Get the writer for this report.
    PrintWriter writer = null;

    try {
      writer = new PrintWriter(new FileWriter(exportFile));
    } catch (IOException ioe) {
      log.error("Cannot get a PrintWriter for the export output file '"
	  + exportFile + "'", ioe);
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
      return true;
    }

    // Get a connection to the database.
    Connection conn = null;

    try {
      conn = dbManager.getConnection();
    } catch (DbException dbe) {
      log.error("Cannot get a connection to the database", dbe);
      IOUtil.safeClose(writer);
      boolean deleted = exportFile.delete();
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "deleted = " + deleted);
      if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
      return true;
    }

    // An indication of whether any new data has been written out.
    boolean newDataWritten = false;

    try {
      // Get the database version.
      int dbVersion = dbManager.getDatabaseVersion(conn);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "dbVersion = " + dbVersion);

      // Check whether the database version is appropriate.
      if (dbVersion >= 10) {
	// Yes: Perform the export.
	newDataWritten = processExport(conn, exportFile, writer);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "newDataWritten = " + newDataWritten);
      } else {
	log.info("Database version is " + dbVersion
	    + " (< 10). Export skipped.");
      }
    } catch (DbException dbe) {
      log.error("Cannot export fetch times", dbe);
    } finally {
      DbManager.safeRollbackAndClose(conn);
      IOUtil.safeClose(writer);
    }

    // Check whether any new data has been written out.
    if (newDataWritten) {
      // Yes: Rename the output file to mark it as available.
      boolean renamed = exportFile.renameTo(new File(outputDir, fileName));
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "renamed = " + renamed);
    } else {
      // No: Delete the empty file to avoid cluttering.
      boolean deleted = exportFile.delete();
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "deleted = " + deleted);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
    return true;
  }

  /**
   * Handles configuration parameters.
   * 
   * @return <code>true</code> if the configuration is successful,
   *         <code>false</code> otherwise.
   */
  private boolean configure() {
    final String DEBUG_HEADER = "configure(): ";
    // Get the current configuration.
    Configuration config = ConfigManager.getCurrentConfig();

    // Get the name of the server for the purpose of assigning to it the fetch
    // time export output.
    try {
      serverName = config.get(PARAM_SERVER_NAME,
	  InetAddress.getLocalHost().getHostName());
      if (log.isDebug3())
	log.debug3(DEBUG_HEADER + "serverName = '" + serverName + "'.");
    } catch (UnknownHostException uhe) {
      log.error("Export of fetch times is disabled: No server name.", uhe);
      return false;
    }

    // Get the configured base directory for the export files.
    File exportDir = exportManager.getExportDir();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "exportDir = '"
	+ exportDir.getAbsolutePath() + "'.");

    // Check whether it exists, creating it if necessary.
    if (FileUtil.ensureDirExists(exportDir)) {
      // Specify the configured directory where to put the fetch time export
      // output files.
      outputDir = new File(exportDir,
	  config.get(PARAM_FETCH_TIME_EXPORT_OUTPUTDIR,
	      DEFAULT_FETCH_TIME_EXPORT_OUTPUTDIR));

      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "outputDir = '"
	  + outputDir.getAbsolutePath() + "'.");

      // Check whether it exists, creating it if necessary.
      if (!FileUtil.ensureDirExists(outputDir)) {

	log.error("Error creating the fetch time export output directory '"
	    + outputDir.getAbsolutePath() + "'.");
	return false;
      }
    } else {
      log.error("Error creating the export directory '"
	  + exportDir.getAbsolutePath() + "'.");
      return false;
    }

    // Get the label used to key in the database the identifier of the last
    // metadata item for which the fetch time has been exported.
    lastMdItemSeqLabel =
	config.get(PARAM_FETCH_TIME_EXPORT_LAST_ITEM_LABEL,
	    DEFAULT_FETCH_TIME_EXPORT_LAST_ITEM_LABEL);
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "lastMdItemSeqLabel = '"
	+ lastMdItemSeqLabel + "'.");

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
    return true;
  }

  /**
   * Provides the name of an export file.
   * 
   * @return a String with the report file name. The format is
   *         'fetch_time-serverName-yyyy-MM-dd-HH-mm-ss.tsv'.
   */
  private String getReportFileName() {
    return String.format("%s-%s-%s.%s", "fetch_time", serverName,
			 dateFormat.format(TimeBase.nowDate()), "tsv");
  }

  /**
   * Exports the fetch time data.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param exportFile
   *          A File where to export the data.
   * @param writer
   *          A PrintWriter used to write the export file.
   * @return <code>true</code> if any new data has been written out,
   *         <code>false</code> otherwise.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private boolean processExport(Connection conn, File exportFile,
      PrintWriter writer) throws DbException {
    final String DEBUG_HEADER = "processExport(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    // An indication of whether any new data has been written out.
    boolean newDataWritten = false;

    // Get the identifier of the last metadata item for which the fetch time has
    // been exported.
    long lastMdItemSeq = getLastExportedMdItemSeq(conn);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "lastMdItemSeq = " + lastMdItemSeq);

    // Export the data for metadata items newer than the last metadata item for
    // which the fetch time has been exported and get the new value for the
    // identifier of the last metadata item for which the fetch time has been
    // exported.
    long newLastMdItemSeq =
	exportNewData(conn, lastMdItemSeq, exportFile, writer);
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "newLastMdItemSeq = " + newLastMdItemSeq);

    // Get the indication of whether any new data has been written out.
    newDataWritten = newLastMdItemSeq > lastMdItemSeq;
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "newDataWritten = " + newDataWritten);

    // Check whether any new data has been written out.
    if (newDataWritten) {
      // Yes: Update in the database the identifier of the last metadata item
      // for which the fetch time has been exported.
      int count = updateLastExportedMdItemSeq(conn, newLastMdItemSeq);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "count = " + count);

      DbManager.commitOrRollback(conn, log);
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "newDataWritten = " + newDataWritten);
    return newDataWritten;
  }

  /**
   * Provides the identifier of the last metadata item for which the fetch time
   * has been exported.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @return a long with the metadata item identifier.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private long getLastExportedMdItemSeq(Connection conn) throws DbException {
    final String DEBUG_HEADER = "getLastExportedMdItemSeq(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");

    if (conn == null) {
      throw new DbException("Null connection");
    }

    long lastMdItemSeq = -1;
    String lastMdItemSeqAsString = null;
    PreparedStatement insertStmt = null;
    PreparedStatement selectStmt = null;
    ResultSet resultSet = null;
    String sql = GET_LAST_EXPORTED_MD_ITEM_QUERY;
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "sql = " + sql);

    String message = "Cannot get the identifier of the last exported item";

    try {
      // Prepare the statement used to get from the database the identifier of
      // the last metadata item for which the fetch time has been exported.
      selectStmt = dbManager.prepareStatement(conn, sql);
      selectStmt.setString(1, lastMdItemSeqLabel);

      // Try to get the value from the database.
      resultSet = dbManager.executeQuery(selectStmt);

      // Check whether the value was found.
      if (resultSet.next()) {
	// Yes: Convert it from text to a numeric value.
	lastMdItemSeqAsString = resultSet.getString(LAST_VALUE_COLUMN);
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "lastMdItemSeqAsString = "
	    + lastMdItemSeqAsString);

	lastMdItemSeq = NumberUtil.parseLong(lastMdItemSeqAsString);
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "lastMdItemSeq = " + lastMdItemSeq);
      } else {
	// No: Initialize it in the database because this is the first run.
	sql = INSERT_LAST_EXPORTED_MD_ITEM_QUERY;
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "sql = " + sql);

	message = "Cannot initialize the identifier of the last exported item";

        // Prepare the statement used to initialize the identifier of the last
	// metadata item for which the fetch time has been exported.
	insertStmt = dbManager.prepareStatement(conn, sql);
	insertStmt.setString(1, lastMdItemSeqLabel);
	insertStmt.setString(2, String.valueOf(lastMdItemSeq));

        // Insert the record.
        int count = dbManager.executeUpdate(insertStmt);
        log.debug2(DEBUG_HEADER + "count = " + count);

        DbManager.commitOrRollback(conn, log);
      }
    } catch (NumberFormatException nfe) {
      log.error(message, nfe);
      log.error("SQL = '" + sql + "'.");
      log.error("lastMdItemSeqAsString = '" + lastMdItemSeqAsString + "'.");
    } catch (SQLException sqle) {
      log.error(message, sqle);
      log.error("SQL = '" + sql + "'.");
      throw new DbException(message, sqle);
    } catch (DbException dbe) {
      log.error(message, dbe);
      log.error("SQL = '" + sql + "'.");
      throw dbe;
    } finally {
      DbManager.safeCloseResultSet(resultSet);
      DbManager.safeCloseStatement(selectStmt);
      DbManager.safeCloseStatement(insertStmt);
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "lastMdItemSeq = " + lastMdItemSeq);
    return lastMdItemSeq;
  }

  /**
   * Exports the data for metadata items newer than the last metadata item for
   * which the fetch time has been exported.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param lastMdItemSeq
   *          A long with the identifier of the last metadata item for which the
   *          fetch time has been exported.
   * @param exportFile
   *          A File where to export the data.
   * @param writer
   *          A PrintWriter used to write the export file.
   * @return a long with the new value for the identifier of the last metadata
   *         item for which the fetch time has been exported.
   * @throws DbException
   *           if any problem occurred accessing the database.
   */
  private long exportNewData(Connection conn, long lastMdItemSeq,
      File exportFile, PrintWriter writer) throws DbException {
    final String DEBUG_HEADER = "exportNewData(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "lastMdItemSeq = " + lastMdItemSeq);
      log.debug2(DEBUG_HEADER + "exportFile = " + exportFile);
    }

    if (conn == null) {
      throw new DbException("Null connection");
    }

    String message = "Cannot get the data to be exported";

    String sql = GET_EXPORT_FETCH_TIME_QUERY;
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "SQL = '" + sql + "'.");

    int totalCount = 0;
    boolean done = false;

    // Loop while there are still more metadata item fetch times to be exported
    // by this task.
    while (!done) {
      PreparedStatement getExportData = null;
      ResultSet results = null;
      int count = 0;

      try {
	// Prepare the statement used to get the data to be exported.
	getExportData = dbManager.prepareStatement(conn, sql);
	getExportData.setMaxRows(MAX_NUMBER_OF_TRANSACTION_EXPORTED_ITEMS);
	getExportData.setLong(1, lastMdItemSeq);

	// Get the data to be exported.
	results = dbManager.executeQuery(getExportData);

	// Loop through all the data to be exported.
	while (results.next()) {
	  // Extract the fetch time.
	  Long fetchTime = results.getLong(FETCH_TIME_COLUMN);
	  if (log.isDebug3())
	    log.debug3(DEBUG_HEADER + "fetchTime = " + fetchTime);

	  // Check whether it has been initialized.
	  if (fetchTime >= 0) {
	    // Yes: Extract the other individual pieces of data to be exported.
	    String publisherName = results.getString(PUBLISHER_NAME_COLUMN);
	    if (log.isDebug3())
	      log.debug3(DEBUG_HEADER + "publisherName = " + publisherName);

	    String pluginId = results.getString(PLUGIN_ID_COLUMN);
	    if (log.isDebug3())
	      log.debug3(DEBUG_HEADER + "pluginId = " + pluginId);

	    boolean isBulkContent = results.getBoolean(IS_BULK_CONTENT_COLUMN);
	    if (log.isDebug3())
	      log.debug3(DEBUG_HEADER + "isBulkContent = " + isBulkContent);

	    String publicationName = results.getString("PUBLICATION_NAME");
	    if (log.isDebug3())
	      log.debug3(DEBUG_HEADER + "publicationName = " + publicationName);

	    String typeName = results.getString(TYPE_NAME_COLUMN);
	    if (log.isDebug3())
	      log.debug3(DEBUG_HEADER + "typeName = " + typeName);

	    String itemTitle = results.getString("ITEM_TITLE");
	    if (log.isDebug3())
	      log.debug3(DEBUG_HEADER + "itemTitle = " + itemTitle);

	    String date = results.getString(DATE_COLUMN);
	    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "date = " + date);

	    String auKey = results.getString(AU_KEY_COLUMN);
	    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "auKey = " + auKey);

	    String accessUrl = results.getString(URL_COLUMN);
	    if (log.isDebug3())
	      log.debug3(DEBUG_HEADER + "accessUrl = " + accessUrl);

	    // Create the line to be written to the output file.
	    StringBuilder sb = new StringBuilder();

	    sb.append(serverName).append(SEPARATOR)
	    .append(publisherName).append(SEPARATOR)
	    .append(pluginId).append(SEPARATOR)
	    .append(auKey).append(SEPARATOR)
	    .append(isBulkContent).append(SEPARATOR)
	    .append(publicationName).append(SEPARATOR)
	    .append(typeName).append(SEPARATOR)
	    .append(itemTitle).append(SEPARATOR)
	    .append(date).append(SEPARATOR)
	    .append(fetchTime).append(SEPARATOR)
	    .append(accessUrl);

	    // Write the line to the export output file.
	    writer.println(sb.toString());

	    // Check whether there were errors writing the line.
	    if (writer.checkError()) {
	      // Yes: Report the error.
	      writer.close();
	      message = "Encountered unrecoverable error writing " +
		  "export output file '" + exportFile + "'";
	      log.error(message);
	      throw new DbException(message);
	    }

	    writer.flush();

	    // Get the new value of the identifier of the last metadata item for
	    // which the fetch time has been exported.
	    lastMdItemSeq = results.getLong(MD_ITEM_SEQ_COLUMN);
	    if (log.isDebug3())
	      log.debug3(DEBUG_HEADER + "lastMdItemSeq = " + lastMdItemSeq);

	    // Count this exported metadata item fetch time.
	    count++;
	  }
	}

	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "count = " + count);

	// Increment the total count of exported metadata item fetch times.
	totalCount += count;
	if (log.isDebug3())
	  log.debug3(DEBUG_HEADER + "totalCount = " + totalCount);

	// Determine whether this task has exported all the metadata item fetch
	// times that needed to export.
	done = count < MAX_NUMBER_OF_TRANSACTION_EXPORTED_ITEMS
	    || totalCount >= MAX_NUMBER_OF_EXPORTED_ITEMS_PER_FILE;
	if (log.isDebug3()) log.debug3(DEBUG_HEADER + "done = " + done);
      } catch (SQLException sqle) {
	log.error(message, sqle);
	log.error("SQL = '" + sql + "'.");
	throw new DbException(message, sqle);
      } catch (DbException dbe) {
	log.error(message, dbe);
	log.error("SQL = '" + sql + "'.");
	throw dbe;
      } finally {
	DbManager.rollback(conn, log);
	DbManager.safeCloseResultSet(results);
	DbManager.safeCloseStatement(getExportData);
      }
    }

    if (log.isDebug2())
      log.debug2(DEBUG_HEADER + "lastMdItemSeq = " + lastMdItemSeq);
    return lastMdItemSeq;
  }

  /**
   * Updates in the database the value for the identifier of the last metadata
   * item for which the fetch time has been exported.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param lastMdItemSeq
   *          A long with the identifier of the last metadata item for which the
   *          fetch time has been exported.
   * @return an int with the count of updated rows.
   * @throws DbException
   *           if there are problems accessing the database.
   */
  private int updateLastExportedMdItemSeq(Connection conn, long lastMdItemSeq)
      throws DbException {
    final String DEBUG_HEADER = "updateLastExportedMdItemSeq(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "lastMdItemSeq = " + lastMdItemSeq);
    }

    String message =
	"Cannot update the last item with exported data identifier";

    String sql = UPDATE_LAST_EXPORTED_MD_ITEM_SEQ_QUERY;
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "SQL = '" + sql + "'.");

    PreparedStatement updateLastId = null;
    int count = -1;

    try {
      // Prepare the statement used to update the identifier of the last
      // metadata item for which the data has been exported.
      updateLastId = dbManager.prepareStatement(conn, sql);
      updateLastId.setString(1, String.valueOf(lastMdItemSeq));
      updateLastId.setString(2, lastMdItemSeqLabel);

      // Update the identifier of the last item with exported data.
      count = dbManager.executeUpdate(updateLastId);
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "count = " + count);
    } catch (SQLException sqle) {
      throw new DbException(message, sqle);
    } finally {
      DbManager.safeCloseStatement(updateLastId);
    }

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "count = " + count);
    return count;
  }

  /**
   * Provides the instant when this task needs to be executed based on the last
   * execution and the frequency.
   * 
   * @param lastTime
   *          A long with the instant of this task's last execution.
   * @param frequency
   *          A String that represents the frequency.
   * @return a long with the instant when this task needs to be executed.
   */
  private long nextTimeA(long lastTime, String frequency) {
    final String DEBUG_HEADER = "nextTime(): ";

    log.debug2(DEBUG_HEADER + "lastTime = " + lastTime);
    log.debug2(DEBUG_HEADER + "frequency = '" + frequency + "'.");

    if ("hourly".equalsIgnoreCase(frequency)) {
      return Cron.nextHour(lastTime);
    } else if ("daily".equalsIgnoreCase(frequency)) {
      return Cron.nextDay(lastTime);
    } else if ("weekly".equalsIgnoreCase(frequency)) {
      return Cron.nextWeek(lastTime);
    } else {
      return Cron.nextMonth(lastTime);
    }
  }

  /**
   * Implementation of the Cron task interface.
   */
  public class FetchTimeExporterCronTask implements Cron.Task {
    /**
     * Performs the periodic task of exporting the fetch time data.
     * 
     * @return <code>true</code> if the task can be considered executed,
     *         <code>false</code> otherwise.
     */
    @Override
    public boolean execute() {
      return export();
    }

    /**
     * Provides the identifier of the periodic task.
     * 
     * @return a String with the identifier of the periodic task.
     */
    @Override
    public String getId() {
      return "FetchTimeExporter";
    }

    /**
     * Provides the instant when this task needs to be executed based on the
     * last execution.
     * 
     * @param lastTime
     *          A long with the instant of this task's last execution.
     * @return a long with the instant when this task needs to be executed.
     */
    @Override
    public long nextTime(long lastTime) {
      return nextTimeA(lastTime,
	  CurrentConfig.getParam(PARAM_FETCH_TIME_EXPORT_TASK_FREQUENCY,
	      			 DEFAULT_FETCH_TIME_EXPORT_TASK_FREQUENCY));
    }
  }
}
