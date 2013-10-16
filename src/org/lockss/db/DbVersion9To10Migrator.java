/*
 * $Id: DbVersion9To10Migrator.java,v 1.1 2013-10-16 23:10:44 fergaloy-sf Exp $
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
package org.lockss.db;

import org.lockss.app.LockssDaemon;
import org.lockss.daemon.LockssRunnable;
import org.lockss.util.Logger;

/**
 * Migrates in a separate thread the database from version 9 to version 10.
 * 
 * @author Fernando Garcia-Loygorri
 */
public class DbVersion9To10Migrator extends LockssRunnable {
  private static Logger log = Logger.getLogger(DbVersion9To10Migrator.class);

  /**
   * Constructor.
   */
  public DbVersion9To10Migrator() {
    super("DbVersion9To10Migrator");
  }

  /**
   * Entry point to start the process of migrating the database from version 9
   * to version 10.
   */
  public void lockssRun() {
    final String DEBUG_HEADER = "lockssRun(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Starting...");
    LockssDaemon daemon = LockssDaemon.getLockssDaemon();

    // Wait until the AUs have been started.
    if (!daemon.areAusStarted()) {
      log.debug(DEBUG_HEADER + "Waiting for aus to start");

      while (!daemon.areAusStarted()) {
	try {
	  daemon.waitUntilAusStarted();
	} catch (InterruptedException ex) {
	}
      }
    }

    // Perform the actual work.
    daemon.getDbManager().migrateDatabaseFrom9To10();

    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "Done.");
  }
}
