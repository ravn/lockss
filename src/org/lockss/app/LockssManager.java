/*
 * $Id: LockssManager.java,v 1.3 2003-02-06 05:16:06 claire Exp $
 */

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
package org.lockss.app;

/**
 * <p>Interface used to standardize a lockss manager </p>
 * @author Claire Griffin
 * @version 1.0
 */

public interface LockssManager {

  /**
   * init the manager - There is no guarentee that any other manager is
   * loaded into memory.
   * @param daemon the daemon that can be used to get additional services
   * @throws LockssDaemonException if this manager was already inited.
   */
  public void initService(LockssDaemon daemon)
      throws LockssDaemonException;

  /**
   * start the manager.  All managers are inited at this point
   */
  public void startService();

  /**
   * stop the manager
   */
  public void stopService();

}