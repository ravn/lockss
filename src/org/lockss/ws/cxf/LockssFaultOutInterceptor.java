/*
 * $Id: LockssFaultOutInterceptor.java,v 1.1 2014-02-12 19:41:00 fergaloy-sf Exp $
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

package org.lockss.ws.cxf;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.Phase;
import org.lockss.util.Logger;
import org.lockss.ws.entities.LockssWebServicesFault;
import org.lockss.ws.entities.LockssWebServicesFaultInfo;

/**
 * An interceptor for the CXF outbound fault chain.
 */
public class LockssFaultOutInterceptor extends AbstractSoapInterceptor {
  private static Logger log = Logger.getLogger(LockssFaultOutInterceptor.class);

  /**
   * Constructor.
   */
  public LockssFaultOutInterceptor() {
    super(Phase.MARSHAL);
    if (log.isDebug()) log.debug("LockssFaultOutInterceptor constructed.");
  }

  /**
   * Message handler.
   * 
   * @param message A SoapMessage with the message in the outbound fault chain.
   * @throws Fault
   */
  public void handleMessage(SoapMessage message) throws Fault {
    final String DEBUG_HEADER = "handleMessage(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "message = " + message);

    Fault fault = (Fault) message.getContent(Exception.class);
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "fault = " + fault);

    if (fault != null) {
      Throwable cause = fault.getCause();
      log.error("Exception caught", cause);

      if (cause != null && (cause instanceof LockssWebServicesFault)) {
	LockssWebServicesFaultInfo lwsfi =
	    ((LockssWebServicesFault)cause).getFaultInfo();

	if (lwsfi != null) {
	  log.error("LockssWebServicesFaultInfo message: "
	      + lwsfi.getMessage());
	}
      }
    }
  }
}
