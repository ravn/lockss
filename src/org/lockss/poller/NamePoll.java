/*
 * $Id: NamePoll.java,v 1.45 2003-04-16 01:18:14 claire Exp $
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

package org.lockss.poller;

import java.io.*;
import java.security.*;
import java.util.*;

import org.lockss.daemon.*;
import org.lockss.hasher.*;
import org.lockss.protocol.*;
import org.lockss.util.*;
import org.lockss.plugin.*;

/**
 * @author Claire Griffin
 * @version 1.0
 */

public class NamePoll
    extends Poll {

  ArrayList m_entries;

  public NamePoll(LcapMessage msg, PollSpec pollspec, PollManager pm) {
    super(msg, pollspec, pm);
    m_replyOpcode = LcapMessage.NAME_POLL_REP;
    m_tally.type = NAME_POLL;
  }

  /**
   * cast our vote for this poll
   */
  void castOurVote() {
    LcapMessage msg;
    LcapIdentity local_id = idMgr.getLocalIdentity();
    long remainingTime = m_deadline.getRemainingTime();
    try {
      msg = LcapMessage.makeReplyMsg(m_msg, m_hash, m_verifier,
                                     getEntries().toArray(), m_replyOpcode,
                                     remainingTime, local_id);
      log.debug("vote:" + msg.toString());
      m_pollmanager.sendMessage(msg, m_cus.getArchivalUnit());
    }
    catch (IOException ex) {
      log.info("unable to cast our vote.", ex);
    }
  }


  /**
   * handle a message which may be a incoming vote
   * @param msg the Message to handle
   */
  void receiveMessage(LcapMessage msg) {
    int opcode = msg.getOpcode();

    if (opcode == LcapMessage.NAME_POLL_REP) {
      startVoteCheck(msg);
    }
  }

  /**
   * schedule the hash for this poll.
   * @param hasher the MessageDigest used to hash the content
   * @param timer the Deadline by which we must complete
   * @param key the Object which will be returned from the hasher. Always the
   * message which triggered the hash
   * @param callback the hashing callback to use on return
   * @return true if hash successfully completed.
   */
  boolean scheduleHash(MessageDigest hasher, Deadline timer, Serializable key,
                       HashService.Callback callback) {

    HashService hs = m_pollmanager.getHashService();
    return hs.hashNames(m_cus, hasher, timer, callback, key);
  }

  /**
   * start the hash required for a vote cast in this poll
   * @param msg the LcapMessage containing the vote we're going to check
   */
  void startVoteCheck(LcapMessage msg) {
    super.startVoteCheck();

    if (shouldCheckVote(msg)) {
      Vote vote = new NameVote(msg, false);
      long dur = msg.getDuration();
      MessageDigest hasher = getInitedHasher(msg.getChallenge(),
                                             msg.getVerifier());

      if (!scheduleHash(hasher, Deadline.in(dur), vote, new VoteHashCallback())) {
        log.info(m_key + " no time to hash vote " + dur + ":" + m_hashTime);
        stopVoteCheck();
      }
    }
  }


  ArrayList getEntries() {
    if (m_entries == null) {
      Iterator it = m_cus.flatSetIterator();
      ArrayList alist = new ArrayList();
      String baseUrl = m_cus.getSpec().getUrl();
      while(it.hasNext()) {
        String name = ((CachedUrlSetNode)it.next()).getUrl();
        if(name.startsWith(baseUrl)) {
          name = name.substring(baseUrl.length());
        }
        alist.add(name);
      }
      m_entries = alist;
    }
    return m_entries;
  }

  NameVote findWinningVote(Iterator voteIter) {
    ArrayList winners = new ArrayList();
    NameVoteCounter winningCounter = null;

    // build a list of unique disagree votes
    while (voteIter.hasNext()) {
      NamePoll.NameVote vote = (NamePoll.NameVote) voteIter.next();
      if (!vote.agree) {
        NameVoteCounter counter = new NameVoteCounter(vote);
        if (winners.contains(counter)) {
          counter = (NameVoteCounter) winners.get(winners.indexOf(counter));
          counter.addVote();
        }
        else {
          winners.add(counter);
        }
      }
    }

    // find the "winner" with the most votes
    Iterator it = winners.iterator();
    while (it.hasNext()) {
      NameVoteCounter counter = (NameVoteCounter) it.next();
      if (winningCounter != null) {
        if (winningCounter.getNumVotes() < counter.getNumVotes()) {
          winningCounter = counter;
        }
      }
      else {
        winningCounter = counter;
      }
    }

    return winningCounter;
  }

  void buildPollLists(Iterator voteIter) {
    NameVote winningVote = findWinningVote(voteIter);

    if (winningVote != null) {
      m_tally.votedEntries = winningVote.getKnownEntries();
      String lwrRem = winningVote.getLwrRemaining();
      String uprRem = winningVote.getUprRemaining();

      if (lwrRem != null) {
        // we call a new poll on the remaining entries and set the regexp
        try {
          PollSpec spec = new PollSpec(m_pollspec.getCachedUrlSet(),lwrRem, uprRem);
          m_pollmanager.sendPollRequest(LcapMessage.NAME_POLL_REQ, spec);
        }
        catch (IOException ex) {
          log.error("Unable to create new poll request", ex);
        }
        // we make our list from whatever is in our
        // master list that doesn't match the remainder;
        ArrayList localSet = new ArrayList();
        Iterator localIt = getEntries().iterator();
        while (localIt.hasNext()) {
          String url = (String) localIt.next();
          if((lwrRem != null) && url.compareTo(lwrRem) < 0) {
            localSet.add(url);
          }
          else if((uprRem != null) && url.compareTo(uprRem) > 0) {
            localSet.add(url);
          }
        }
        m_tally.localEntries = localSet.toArray();
      }
    }
  }

  NameVote makeVote(LcapMessage msg, boolean agree) {
    return new NameVote(msg, agree);
  }

  static class NameVote extends Vote {
    private Object[] knownEntries;
    private String lwrRemaining;
    private String uprRemaining;

    NameVote(Object[] entries, String lwr, String upr) {
      knownEntries = entries;
      lwrRemaining = lwr;
      uprRemaining = upr;
    }

    NameVote(LcapMessage msg, boolean agree) {
      super(msg, agree);
      knownEntries = msg.getEntries();

      lwrRemaining = msg.getLwrRemain();
      uprRemaining = msg.getUprRemain();
    }

    Object[] getKnownEntries() {
      return knownEntries;
    }

    String getLwrRemaining() {
      return lwrRemaining;
    }

    String getUprRemaining() {
      return uprRemaining;
    }

    public boolean equals(Object obj) {
      if (obj instanceof NameVote) {
        return (sameEntries( ( (NameVote) obj).knownEntries));
      }
      return false;
    }

    boolean sameEntries(Object[] entries) {
      return Arrays.equals(knownEntries, entries);
    }
  }

  static class NameVoteCounter extends NameVote {
    private int voteCount;

    NameVoteCounter(NameVote vote) {
      super(vote.getKnownEntries(),vote.getLwrRemaining(),vote.getUprRemaining());
      voteCount = 1;
    }

    void addVote() {
      voteCount++;
    }

    int getNumVotes() {
      return voteCount++;
    }
  }

}