/*
* $Id: PsmMsgAction.java,v 1.1 2005-02-23 02:19:05 tlipkis Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.protocol.psm;

import java.util.*;

/**
 * Base class for actions run in response to message events.
 */
public abstract class PsmMsgAction extends PsmAction {
  /** Calls {@link #runMsg} */
  public final PsmEvent run(PsmEvent event, PsmInterp interp) {
    return runMsg((PsmMsgEvent)event, interp);
  }

  /** Concrete subclasses should implement this method to process the
   * message event.  The message is event.getMessage()
   * @param event the message event to which this action is running in
   * response.
   * @param interp the state interpreter, from which the action can get the
   * user object.
   * @return the next event
   */
  protected abstract PsmEvent runMsg(PsmMsgEvent event, PsmInterp interp);
}
