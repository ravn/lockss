/*
 * $Id: AuthorizationInterceptor.java,v 1.1.2.2 2014-06-06 04:09:52 fergaloy-sf Exp $
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

import org.apache.cxf.binding.soap.interceptor.SoapHeaderInterceptor;
import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.lockss.account.UserAccount;
import org.lockss.app.LockssDaemon;
import org.lockss.servlet.LockssServlet;
import org.lockss.util.Logger;
import org.lockss.ws.entities.LockssWebServicesFault;
import org.lockss.ws.entities.LockssWebServicesFaultInfo;

/**
 * The common code of the authorization interceptors for the various web
 * services.
 */
public abstract class AuthorizationInterceptor extends SoapHeaderInterceptor {
  public static String NO_REQUIRED_ROLE =
      "User does not have the required role.";

  private static Logger log = Logger.getLogger(AuthorizationInterceptor.class);

  /**
   * Provides the name of the role required for the user to be able to execute
   * operations of this web service. Implemented in each subclass.
   * 
   * @return a String with the required role.
   */
  protected abstract String getRequiredRole();

  /**
   * Message handler.
   * 
   * @param message A Message with the message in the inbound chain.
   * @throws Fault
   */
  @Override
  public void handleMessage(Message message) throws Fault {
    final String DEBUG_HEADER = "handleMessage(): ";
    if (log.isDebug2()) log.debug2(DEBUG_HEADER + "message = " + message);

    // Get the authorization policy provided by CXF.
    AuthorizationPolicy policy = message.get(AuthorizationPolicy.class);

    if (policy == null) {
      // This should not happen. If the policy is not set, in theory the user
      // did not specify credentials, but this should have been caught upstream
      // and it should never have reached this point.
      // Nevertheless, send back to the client a 401 error indicating that
      // authentication is required.
      String errorMessage = "No credentials were received.";
      log.error(errorMessage);

      throw new Fault(new LockssWebServicesFault(errorMessage,
	  new LockssWebServicesFaultInfo("401")));
    }

    // Get the name of the authenticated user.
    String userName = policy.getUserName();
    if (log.isDebug3()) log.debug3(DEBUG_HEADER + "userName = " + userName);

    // Get the user account.
    UserAccount userAccount =
	LockssDaemon.getLockssDaemon().getAccountManager().getUser(userName);
    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "userAccount = " + userAccount);
      log.debug3(DEBUG_HEADER + "userAccount.getRoles() = "
	  + userAccount.getRoles());
      log.debug3(DEBUG_HEADER + "userAccount.getRoleSet() = "
	  + userAccount.getRoleSet());
    }

    // Get the role required by this web service.
    String requiredRole = getRequiredRole();
    if (log.isDebug3())
      log.debug3(DEBUG_HEADER + "requiredRole = " + requiredRole);

    // Check whether the user has the role required to execute operations of
    // this web service.
    if (isAuthorized(userAccount, requiredRole)) {
      // Yes: Continue normally.
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "Authorized.");
    } else {
      // No: Report back the problem.
      log.info(NO_REQUIRED_ROLE);
      log.info("userName = " + userName);

      throw new Fault(new LockssWebServicesFault(NO_REQUIRED_ROLE,
	  new LockssWebServicesFaultInfo("401")));
    }
  }

  /**
   * Provides an indication of whether the user has the role required to execute
   * operations of this web service.
   * 
   * @param userAccount
   *          A UserAccount with the user account data.
   * @param requiredRole
   *          A String with the role required by this web service.
   * @return a boolean with <code>TRUE</code> if the user has the role required
   *         to execute operations of this web service, <code>FALSE</code>
   *         otherwise.
   */
  protected boolean isAuthorized(UserAccount userAccount, String requiredRole) {
    return (userAccount != null
	&& (userAccount.isUserInRole(requiredRole)
	    || userAccount.isUserInRole(LockssServlet.ROLE_USER_ADMIN)));
  }
}
