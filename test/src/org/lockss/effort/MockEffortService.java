/*
 * $Id: MockEffortService.java,v 1.1.2.2 2004-10-03 20:40:52 dshr Exp $
 */

/*

Copyright (c) 2004 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.effort;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;
import org.lockss.util.*;
import org.lockss.app.*;
import org.lockss.plugin.*;
import org.lockss.config.*;

/**
 * Mock effort proof service.
 */
public class MockEffortService extends BaseLockssDaemonManager
    implements EffortService {
  static final String PREFIX = Configuration.PREFIX + "effort.";

    static Logger log = Logger.getLogger("MockEffortService");

    private EffortService theEffortService;
    private boolean myGenerateResult;
    private boolean myVerifyResult;
    private List myProof;
    private long duration;

    public MockEffortService() {
	super();
	theEffortService = this;
	myGenerateResult = false;
	myVerifyResult = false;
	myProof = ListUtil.list();
	duration = 500;
    }

    public MockEffortService(boolean res, boolean ver, List l, long dur) {
	super();
	theEffortService = this;
	myGenerateResult = res;
	myVerifyResult = ver;
	myProof = l;
	duration = dur;
    }

    public void setGenerateResult(boolean res) {
	myGenerateResult = res;
    }
    public void setVerifyResult(boolean res) {
	myVerifyResult = res;
    }
    public void setProof(List l) {
	myProof = l;
    }
  /**
   * Ask for the effort proof specified by the <code>EffortService.Proof</code>
   * object to be generated so that it can later be retrieved from the
   * object.
   * @param ep     the <code>EffortService.Proof</code> to be generated.
   * @param timer  the <code>Deadline</code> by which generation must be
   *               complete.
   * @param cb     the object whose <code>generationFinished()</code>
   *               method will be called when generation is complete.
   * @param cookie used to disambiguate callback
   * @return       <code>true</code> if generation could be scheduled
   *               <code>false</code> otherwise.
   */
  public boolean proveEffort(Proof effortProof,
			     Deadline timer,
			     ProofCallback callback,
			     Serializable cookie) {
      final Proof ep = effortProof;
      final ProofCallback cb = callback;
      // XXX
      TimerQueue.Callback tqcb = new TimerQueue.Callback() {
	      public void timerExpired(Object tqCookie) {
		  log.debug("Effort callback for " + ((String) tqCookie));
		  cb.generationFinished(ep, (Serializable)tqCookie, null);
	      }
	  };
	  TimerQueue.schedule(Deadline.in(duration), tqcb, cookie);
	  log.debug("Callback in 500 scheduled for " + ((String) cookie));
      return true;
  }

  /** Test whether an effort proof could be successfully sceduled before a
   * given deadline.
   * @param proof the <code>EffortService.Proof</code> to be scheduled
   * @param when the deadline
   * @return true if such a request could be accepted into the scedule.
   */
    public boolean canProofBeScheduledBefore(Proof ep, Deadline when) {
	// XXX
	return true;
    }

  /**
   * Ask for the vote specified by the <code>EffortService.Vote</code>
   * object to be generated so that it can later be retrieved from the
   * object.
   * @param vote   the <code>EffortService.Vote</code> to be generated.
   * @param timer  the <code>Deadline</code> by which generation must be
   *               complete.
   * @param cb     the object whose <code>generationFinished()</code>
   *               method will be called when generation is complete.
   * @param cookie used to disambiguate callback
   * @return       <code>true</code> if generation could be scheduled
   *               <code>false</code> otherwise.
   */
  public boolean generateVote(Vote voteSpec,
			     Deadline timer,
			     VoteCallback callback,
			     Serializable cookie) {
      final Vote vote = voteSpec;
      final VoteCallback cb = callback;
      // XXX
      TimerQueue.Callback tqcb = new TimerQueue.Callback() {
	      public void timerExpired(Object tqCookie) {
		  log.debug("Vote callback for " + ((String) tqCookie));
		  cb.generationFinished(vote, (Serializable)tqCookie, null);
	      }
	  };
	  TimerQueue.schedule(Deadline.in(duration), tqcb, cookie);
	  log.debug("Callback in 500 scheduled for " + ((String) cookie));
      return true;
  }

  /** Test whether a vote could be successfully scheduled before a
   * given deadline.
   * @param vote the <code>EffortService.Vote</code> to be generated
   * @param when the deadline
   * @return true if such a request could be accepted into the scedule.
   */
    public boolean canVoteBeScheduledBefore(Vote vote, Deadline when) {
	// XXX
	return true;
    }

  /** Return true if the EffortService has nothing to do.  Useful in unit
   * tests. */
    public boolean isIdle() {
	//  XXX
	return true;
    }

  /** Cancel generation of the specified proof.
   * @param ep the <code>EffortService.Proof</code> to be cancelled.
   */
    public void cancelProofs(Proof ep) {
    }

    public Proof makeProof() {
	return new ProofImpl();
    }

    public Vote makeVote() {
	return new VoteImpl();
    }

  /**
   * <code>EffortService.Proof</code> is used to describe effort proofs
   * to be generated by the effort service.
   */
  public class ProofImpl implements Proof {
    // XXX
    /**
     * Return the <code>EffortService</code> instance in use
     */
      public EffortService getEffortService() {
	  return theEffortService;
      }

      protected boolean generate() {
	  return myGenerateResult;
      }

      protected boolean verify() {
	  return myVerifyResult;
      }

      public List getProof() {
	  return myProof;
      }
  }

  /**
   * <code>EffortService.Vote</code> is used to describe effort proofs
   * to be generated by the effort service.
   */
  public class VoteImpl implements Vote {
    // XXX
    /**
     * Return the <code>EffortService</code> instance in use
     */
      public EffortService getEffortService() {
	  return theEffortService;
      }

  }


}
