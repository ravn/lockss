/*
 * $Id: QueryRepositoriesClient.java,v 1.1.2.3 2014-07-18 15:59:03 wkwilson Exp $
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
 * A client for the DaemonStatusService.queryRepositories() web service
 * operation.
 */
package org.lockss.ws.status.client;

import java.util.List;
import org.lockss.ws.entities.RepositoryWsResult;

public class QueryRepositoriesClient extends QueryBaseClient {
  /**
   * The main method.
   * 
   * @param args
   *          A String[] with the command line arguments.
   * @throws Exception
   */
  public static void main(String args[]) throws Exception {
    QueryRepositoriesClient thisClient = new QueryRepositoriesClient();

    // Get the query.
    String query = thisClient.getQueryFromCommandLine(args);
    System.out.println("query = " + query);

    // Call the service and get the results of the query.
    List<RepositoryWsResult> repositories =
	thisClient.getProxy().queryRepositories(query);

    if (repositories != null) {
      System.out.println("repositories.size() = " + repositories.size());

      for (RepositoryWsResult repositoryResult : repositories) {
	System.out.println("repositoryResult = " + repositoryResult);
      }
    } else {
      System.out.println("repositories = " + repositories);
    }
  }
}
