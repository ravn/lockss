/*
 * $Id: NamePoll.java,v 1.39 2003-03-05 23:47:07 claire Exp $
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

  String[] m_entries;

  public NamePoll(LcapMessage msg, PollSpec pollspec, PollManager pm) {
    super(msg, pollspec, pm);
    m_replyOpcode = LcapMessage.NAME_POLL_REP;
    m_tally.type = NAME_POLL;
  }

  /**
   * cast our vote for this poll
   */
  void vote() {
    LcapMessage msg;
    LcapIdentity local_id = idMgr.getLocalIdentity();
    long remainingTime = m_deadline.getRemainingTime();
    try {
      msg = LcapMessage.makeReplyMsg(m_msg, m_hash, m_verifier,
                                     getEntries(), m_replyOpcode,
                                     remainingTime, local_id);
      log.debug("vote:" + msg.toString());
      m_pollmanager.sendMessage(msg, m_arcUnit);
    }
    catch (IOException ex) {
      log.info("unable to cast our vote.", ex);
    }
  }

  /**
   * prepare to run a poll.  This should check any conditions that might
   * make running a poll unneccessary.
   * @param msg the message which is triggering the poll
   * @return boolean true if the poll should run, false otherwise
   */
  boolean prepareVoteCheck(LcapMessage msg) {

    // make sure our vote will actually matter
    if (m_tally.isLeadEnough()) {
      log.info(m_key + " lead is enough.");
      return false;
    }

    // are we too busy
    if (tooManyPending()) {
      log.info(m_key + " too busy to count " + m_pendingVotes + " votes");
      return false;
    }

    return true;
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

    HashService hs = m_pollmanager.getDaemon().getHashService();
    return hs.hashNames(m_urlSet, hasher, timer, callback, key);
  }

  /**
   * start the hash required for a vote cast in this poll
   * @param msg the LcapMessage containing the vote we're going to check
   */
  void startVoteCheck(LcapMessage msg) {
    super.startVoteCheck();

    if (prepareVoteCheck(msg)) {
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

  void tally() {
    if(!m_tally.didWinPoll()) {
      buildPollLists(m_tally.pollVotes.iterator());
    }
    super.tally();
  }

  String[] getEntries() {
    if (m_entries == null) {
      Iterator it = m_urlSet.flatSetIterator();
      ArrayList alist = new ArrayList();
      String baseUrl = m_urlSet.getSpec().getUrl();
      while(it.hasNext()) {
        String name = ((CachedUrlSetNode)it.next()).getUrl();
        if(name.startsWith(baseUrl)) {
          name = name.substring(name.length());
        }
        alist.add(name);
      }
      m_entries = (String[]) alist.toArray(new String[alist.size()]);
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
          PollSpec spec = new PollSpec(m_pollspec.getCachedUrlSet(),lwrRem,uprRem);
          m_pollmanager.requestPoll(LcapMessage.NAME_POLL_REQ, spec);
        }
        catch (IOException ex) {
          log.error("Unable to create new poll request", ex);
        }
        // we make our list from whatever is in our
        // master list that doesn't match the remainder;
        HashSet localSet = new HashSet();
        Iterator localIt = new ArrayIterator(getEntries());
        while (localIt.hasNext()) {
          String url = (String) localIt.next();
          if (url.compareTo(lwrRem) < 0 || url.compareTo(uprRem) > 0) {
            localSet.add(url);
          }
        }
        m_tally.localEntries = (String[]) localSet.toArray();
      }
    }
  }

  NameVote makeVote(LcapMessage msg, boolean agree) {
    return new NameVote(msg, agree);
  }

  static class NameVote extends Vote {
    private String[] knownEntries;
    private String lwrRemaining;
    private String uprRemaining;

    NameVote(String[] entries, String lwr, String upr) {
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

    String[] getKnownEntries() {
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

    boolean sameEntries(String[] entries) {
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