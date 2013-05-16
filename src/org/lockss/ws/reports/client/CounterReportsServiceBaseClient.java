/*
 * $Id: CounterReportsServiceBaseClient.java,v 1.1 2013-03-22 04:47:30 fergaloy-sf Exp $
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
 * A base client for the COUNTER Reports web service.
 */
package org.lockss.ws.reports.client;

import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import org.lockss.ws.AllServicesBaseClient;
import org.lockss.ws.reports.CounterReportsService;

public abstract class CounterReportsServiceBaseClient extends
    AllServicesBaseClient {
  private static final String ADDRESS_LOCATION =
      "http://localhost:8081/ws/CounterReportsService?wsdl";
  private static final String TARGET_NAMESPACE =
      "http://reports.ws.lockss.org/";
  private static final String SERVICE_NAME = "CounterReportsServiceImplService";

  /**
   * Executes the client code common to all the operations.
   * 
   * @param args
   *          A String[] with the command line arguments.
   * @throws Exception
   */
  protected CounterReportsService getProxy() throws Exception {
    super.authenticate();
    Service service = Service.create(new URL(ADDRESS_LOCATION), new QName(
	TARGET_NAMESPACE, SERVICE_NAME));

    return service.getPort(CounterReportsService.class);
  }
}
