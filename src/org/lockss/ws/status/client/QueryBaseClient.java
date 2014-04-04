/*
 * $Id: QueryBaseClient.java,v 1.1 2014-04-04 22:00:46 fergaloy-sf Exp $
 */

/*

 Copyright (c) 2014 Board of Trustees of Leland Stanford Jr. University,
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
 * A base client for the DaemonStatusService.queryXyz() web service operations.
 */
package org.lockss.ws.status.client;

public abstract class QueryBaseClient extends DaemonStatusServiceBaseClient {
  /**
   * Provides the query specified in the command line.
   * 
   * @param args a String[] with the command line arguments.
   * @return a String with the query.
   */
  protected String getQueryFromCommandLine(String args[]) {
    // The default query.
    String query = "select *";

    // Check whether a query was specified in the command line.
    if (args.length > 0 && args[0].trim().length() > 0) {
      // Yes: Use it.
      query = args[0].trim();
    }

    return query;
  }
}
