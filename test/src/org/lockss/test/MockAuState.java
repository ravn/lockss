/*
 * $Id: MockAuState.java,v 1.8 2003-06-20 22:34:55 claire Exp $
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

import org.lockss.state.*;
import org.lockss.plugin.ArchivalUnit;

/**
 * This is a mock version of <code>ArchivalUnit</code> used for testing
 */

public class MockAuState extends AuState {

  public MockAuState(ArchivalUnit au) {
    this(au, -1, -1, -1, new MockHistoryRepository());
  }

  public MockAuState() {
    this(null, -1, -1, -1, new MockHistoryRepository());
  }

  public MockAuState(ArchivalUnit au, long lastCrawlTime, long lastPollTime,
                     long lastTreeWalk, HistoryRepository historyRepo) {
    super(au, lastCrawlTime, lastPollTime, lastTreeWalk, historyRepo);
  }

  public void setLastCrawlTime(long newCrawlTime) {
    lastCrawlTime = newCrawlTime;
  }

  public void setLastTopLevelPollTime(long newPollTime) {
    lastTopLevelPoll = newPollTime;
  }

  public void setLastTreeWalkTime(long newTreeWalkTime) {
    lastTreeWalk = newTreeWalkTime;
  }

  public void newCrawlFinished() {
    super.newCrawlFinished();
  }

}

