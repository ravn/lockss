/*
* $Id: MockPollManager.java,v 1.11 2004-08-02 02:59:35 tlipkis Exp $
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

package org.lockss.test;

import java.io.IOException;
import java.util.Hashtable;
import org.lockss.app.*;
import org.lockss.plugin.*;
import org.lockss.poller.*;
import org.lockss.protocol.LcapMessage;
import org.lockss.protocol.ProtocolException;

/**
 * Mock override of the PollManager
 */
public class MockPollManager extends PollManager {
  public static Hashtable thePolls = new Hashtable();
  public static final String NAME_REQUESTED = "name_requested";
  public static final String CONTENT_REQUESTED = "content_requested";
  public static final String SUSPENDED = "suspended";
  public static final String RESUMED = "resumed";

  public MockPollManager() {
    super();
  }
  public void initService(LockssDaemon daemon) throws LockssAppException {
    super.initService(daemon);
  }
  public void startService() {
    super.startService();
  }

  public void stopService() {
    super.stopService();
    thePolls = new Hashtable();
  }

  public void sendPollRequest(int opcode, PollSpec ps) throws IOException {
    // note: uses a different key than the other two, since we're not
    // creating an actual challenge and verifier to key off of.
    if (opcode == LcapMessage.CONTENT_POLL_REQ) {
      thePolls.put(ps.getUrl(), CONTENT_REQUESTED);
    }
    else if (opcode == LcapMessage.NAME_POLL_REQ) {
      thePolls.put(ps.getUrl(), NAME_REQUESTED);
    }
  }

  public boolean isPollRunning(int opcode, PollSpec ps) {
    return thePolls.get(ps.getUrl()) != null;
  }

  public void suspendPoll(String key) {
    thePolls.put(key, SUSPENDED);
  }

  public void resumePoll(boolean replayNeeded, Object key) {
    thePolls.put(key, RESUMED);
  }

  public String getPollStatus(String key) {
    return (String)thePolls.get(key);
  }

  public BasePoll createPoll(LcapMessage msg, PollSpec pollspec) throws ProtocolException {
    try {
      sendPollRequest(msg.getOpcode(), pollspec);
    }
    catch (IOException ex) {
    }
    return super.createPoll(msg, pollspec);

  }

}
