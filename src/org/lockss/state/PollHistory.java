/*
 * $Id: PollHistory.java,v 1.5 2002-12-12 23:07:07 aalto Exp $
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


package org.lockss.state;

import java.util.*;
import org.lockss.protocol.LcapIdentity;

/**
 * PollHistory contains the information for a completed poll.
 */
public class PollHistory {
  int type;
  String regExp;
  int status;
  long startTime;
  long duration;
  ArrayList votes;

  /**
   * Empty constructor used for marshalling.  Needed to create the
   * PollHistoryBean.
   */
  public PollHistory() {
    type = -1;
    regExp = null;
    status = -1;
    startTime = 0;
    duration = 0;
    votes = null;
  }

  PollHistory(PollState state, long duration, ArrayList votes) {
    this.type = state.type;
    this.regExp = state.regexp;
    this.status = state.status;
    this.startTime = state.startTime;
    this.duration = duration;
    this.votes = (ArrayList)Collections.unmodifiableList(votes);
  }

  /**
   * Returns the type of the poll.
   * @return an int representing the type
   * @see org.lockss.protocol.LcapMessage
   */
  public int getType() {
    return type;
  }

  /**
   * Returns the regular expression for this poll.
   * @return the regexp or null
   */
  public String getRegExp() {
    return regExp;

  }
  /**
   * Returns the status of the poll.
   * @return an int representing the current status
   */
  public int getStatus() {
    return status;
  }

  /**
   * Returns the start time of the poll.
   * @return the start time in ms
   */
  public long getStartTime() {
    return startTime;
  }

  /**
   * Returns the duration the poll took.
   * @return the duration in ms
   */
  public long getDuration() {
    return duration;
  }

  /**
   * Returns a list of Votes.
   * @return an ArrayList of Poll.Vote objects.
   */
  public ArrayList getVotes() {
    return votes;
  }


}