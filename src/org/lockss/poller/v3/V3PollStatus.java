/*
* $Id: V3PollStatus.java,v 1.51.8.1 2014-12-27 03:29:25 tlipkis Exp $
 */

/*

Copyright (c) 2000-2014 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.poller.v3;

import java.util.*;
import java.text.*;
import java.io.*;
import org.lockss.daemon.status.*;
import org.lockss.daemon.status.StatusService.*;
import org.lockss.daemon.status.StatusTable.SummaryInfo;
import org.lockss.config.*;
import org.lockss.util.*;
import org.lockss.app.*;
import org.lockss.plugin.*;
import org.lockss.poller.*;
import org.lockss.poller.PollManager.*;
import org.lockss.poller.v3.*;
import org.lockss.hasher.LocalHashResult;
import org.lockss.state.*;
import org.lockss.protocol.*;
import org.lockss.protocol.psm.*;
import org.lockss.protocol.V3LcapMessage.PollNak;

import static org.lockss.poller.v3.V3Poller.*;
import static org.lockss.poller.v3.V3Voter.*;

/**
 * Provides support for the PollManager and Polls to present
 * the current status information of V3 Polls.
 */
public class V3PollStatus {
  public static final String POLLER_STATUS_TABLE_NAME = "V3PollerTable";
  public static final String VOTER_STATUS_TABLE_NAME = "V3VoterTable";
  public static final String POLLER_DETAIL_TABLE_NAME = "V3PollerDetailTable";
  public static final String VOTER_DETAIL_TABLE_NAME = "V3VoterDetailTable";
  public static final String ACTIVE_REPAIRS_TABLE_NAME = "V3ActiveRepairsTable";
  public static final String COMPLETED_REPAIRS_TABLE_NAME = "V3CompletedRepairsTable";
  public static final String NO_QUORUM_TABLE_NAME = "V3NoQuorumURLsTable";
  public static final String TOO_CLOSE_TABLE_NAME = "V3TooCloseURLsTable";
  public static final String AGREE_TABLE_NAME = "V3AgreeURLsTable";
  public static final String DISAGREE_TABLE_NAME = "V3DisagreeURLsTable";
  public static final String ERROR_TABLE_NAME = "V3ErrorURLsTable";

  protected PollManager pollManager;
  private static Logger theLog = Logger.getLogger("V3PollerStatus");

  V3PollStatus(PollManager pollManager) {
    this.pollManager = pollManager;
  }

  private static final DecimalFormat agreementFormat =
    new DecimalFormat("0.00");
    
  private static StatusTable.Reference makeAuRef(ArchivalUnit au,
						 String table) {
    return new StatusTable.Reference(au.getName(),
				     ArchivalUnitStatus.AU_STATUS_TABLE_NAME,
				     au.getAuId());
  }

  private static StatusTable.Reference makePollRef(Object value,
						   PeerIdentity pid,
						   String pollKey) {
    return new StatusTable.Reference(value,
				     pid,
				     POLLER_DETAIL_TABLE_NAME,
				     pollKey);
  }

  private static StatusTable.Reference makeVoteRef(Object value,
						   PeerIdentity pid,
						   String pollKey) {
    return new StatusTable.Reference(value,
				     pid,
				     VOTER_DETAIL_TABLE_NAME,
				     pollKey);
  }

  // Sort keys, not visible
  private static final String SORT_KEY1 = "sort1";
  private static final String SORT_KEY2 = "sort2";

  private static int SORT_BASE_ACTIVE = 0;
  private static int SORT_BASE_PENDING = 1;
  private static int SORT_BASE_DONE = 2;


  /**
   * <p>Overview status table for all V3 polls in which we are acting as
   * the caller of the poll.</p>
   */
  public static class V3PollerStatus
      extends V3PollStatus implements StatusAccessor {

    static final String TABLE_TITLE = "Polls";

    // Sort by (status, suborder):
    // (active, descending start time)
    // (pending, queue order)
    // (done, descending end time)

    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule(SORT_KEY1, true),
		    new StatusTable.SortRule(SORT_KEY2, false));

    private final List colDescs =
      ListUtil.list(new ColumnDescriptor("auId", "Volume",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("participants", "Participants",
                                         ColumnDescriptor.TYPE_INT),
                    new ColumnDescriptor("status", "Status",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("talliedUrls", "URLs Tallied",
                                         ColumnDescriptor.TYPE_INT,
                                         "Total number of URLs examined so " +
                                         "far in this poll."),
                    new ColumnDescriptor("Errors", "Hash Errors",
                                         ColumnDescriptor.TYPE_INT,
                                         "Errors encountered while hashing content."),
                    new ColumnDescriptor("completedRepairs", "Repairs",
                                         ColumnDescriptor.TYPE_INT,
                                         "Completed repairs."),
                    new ColumnDescriptor("agreement", "Agreement",
                                         ColumnDescriptor.TYPE_AGREEMENT),
                    new ColumnDescriptor("start", "Start",
                                         ColumnDescriptor.TYPE_DATE),
                    new ColumnDescriptor("deadline", "Deadline",
                                         ColumnDescriptor.TYPE_DATE),
                    new ColumnDescriptor("end", "End",
                                         ColumnDescriptor.TYPE_DATE),
                    new ColumnDescriptor("pollId", "Poll ID",
                                         ColumnDescriptor.TYPE_STRING));

    private static final List<String> defaultCols =
      ListUtil.list(
		    "auId",
		    "participants",
		    "status",
		    "talliedUrls",
		    "Errors",
		    "completedRepairs",
		    "agreement",
		    "start",
		    "deadline",
		    "pollId"
		    );


    public V3PollerStatus(PollManager pollManager) {
      super(pollManager);
    }

    public String getDisplayName() {
      return TABLE_TITLE;
    }

    public void populateTable(StatusTable table)
        throws StatusService.NoSuchTableException {
      String key = table.getKey();
      table.setColumnDescriptors(colDescs, defaultCols);
      table.setSummaryInfo(getSummary(pollManager, table));
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows(key));
    }
    
    private List getSummary(PollManager pollManager, StatusTable table) {
      boolean isDebug = table.getOptions().get(StatusTable.OPTION_DEBUG_USER);
      List summary = new ArrayList();

      if (isDebug) {
	StringBuilder sb = new StringBuilder();
	sb.append(pollManager.getEventCount(EventCtr.Polls));
	sb.append(" started");
	addEndStatus(sb, V3Poller.POLLER_STATUS_COMPLETE);
	addEndStatus(sb, V3Poller.POLLER_STATUS_NO_QUORUM);
	addEndStatus(sb, V3Poller.POLLER_STATUS_ERROR);
	summary.add(new StatusTable.SummaryInfo("Polls",
						ColumnDescriptor.TYPE_STRING,
						sb.toString()));
      }
      V3PollStatusAccessor status = pollManager.getV3Status();
      if (!CurrentConfig.getBooleanParam(V3PollFactory.PARAM_ENABLE_V3_POLLER,
					 V3PollFactory.DEFAULT_ENABLE_V3_POLLER)) { 
	summary.add(new StatusTable.SummaryInfo("Polling is disabled",
						ColumnDescriptor.TYPE_STRING,
						null));
      }
      if (status.getNextPollStartTime() != null) {
        long remainingTime = status.getNextPollStartTime().getRemainingTime();
        String timeStr = remainingTime > 0 ?
            StringUtil.timeIntervalToString(remainingTime) : "running";
	Object val = new StatusTable.DisplayedValue(remainingTime, timeStr);
        summary.add(new SummaryInfo("Poll Starter",
                                    ColumnDescriptor.TYPE_TIME_INTERVAL,
				    val));
      }
//       List<ArchivalUnit> queue = pollManager.getPendingQueueAus();
//       if (!queue.isEmpty()) {
//         summary.add(new SummaryInfo("Queued",
//                                     ColumnDescriptor.TYPE_INT,
//                                     queue.size()));
// 	ArchivalUnit au = queue.get(0);
//         summary.add(new SummaryInfo("Next",
//                                     ColumnDescriptor.TYPE_STRING,
// 				    au.getName()));
//       }
      return summary;
    }

    void addEndStatus(StringBuilder sb, int status) {
      addEndStatus(sb, status, null);
    }

    void addEndStatus(StringBuilder sb, int status, String msg) {
      int cnt = pollManager.getPollEndEventCount(status);
      if (cnt != 0) {
	sb.append(", ");
	sb.append(cnt);
	sb.append(" ");
	sb.append(msg != null ? msg : V3Poller.POLLER_STATUS_STRINGS[status]);
      }
    }

    public boolean requiresKey() {
      return false;
    }

    private List getRows(String key) {
      List rows = new ArrayList();
      Collection v3Pollers = pollManager.getV3Pollers();
      for (Iterator it = v3Pollers.iterator(); it.hasNext(); ) {
        V3Poller poller = (V3Poller)it.next();
        if ((key != null && key.equals(poller.getAu().getAuId())) || 
            key == null) {
          rows.add(makeRow(poller));
        }
      }
      int rowNum = 0;
      for (ArchivalUnit au : pollManager.getPendingQueueAus()) {
	rows.add(makePendingRow(au, rowNum++));

      }

      return rows;
    }

    private Map makeRow(V3Poller poller) {
      Map row = new HashMap();
      ArchivalUnit au = poller.getAu();
      row.put("auId", makeAuRef(au, ArchivalUnitStatus.AU_STATUS_TABLE_NAME));
      row.put("participants", new Integer(poller.getPollSize()));
      row.put("status", poller.getStatusString());
      row.put("talliedUrls", new Integer(poller.getTalliedUrls().size()));
      if (poller.getErrorUrls() != null) {
        row.put("hashErrors", new Integer(poller.getErrorUrls().size()));
      } else {
        row.put("hashErrors", "--");
      }
      row.put("completedRepairs", new Integer(poller.getCompletedRepairs().size()));
      Object agmt = (poller.getStatus() == V3Poller.POLLER_STATUS_COMPLETE)
	? poller.getPercentAgreement()
	: new StatusTable.DisplayedValue(StatusTable.NO_VALUE, "--");
      row.put("agreement", agmt);
      row.put("start", new Long(poller.getCreateTime()));
      row.put("deadline", poller.getDeadline());
      if (poller.isPollActive()) {
	row.put(SORT_KEY1, SORT_BASE_ACTIVE);
	row.put(SORT_KEY2, row.get("start"));
      } else {
	row.put("end", poller.getEndTime());
	row.put(SORT_KEY1, SORT_BASE_DONE);
	row.put(SORT_KEY2, row.get("end"));
      }
      String skey = PollUtil.makeShortPollKey(poller.getKey());
      row.put("pollId", new StatusTable.Reference(skey,
						  "V3PollerDetailTable",
						  poller.getKey()));
      return row;
    }

    private Map makePendingRow(ArchivalUnit au, int rowNum) {
      Map row = new HashMap();
      row.put("auId", makeAuRef(au, ArchivalUnitStatus.AU_STATUS_TABLE_NAME));
      row.put("status", "Pending");
      row.put(SORT_KEY1, SORT_BASE_PENDING);
      row.put(SORT_KEY2, Integer.MAX_VALUE - rowNum);
      return row;
    }
  }

  /**
   * <p>Overview status table for all V3 polls in which we are acting as
   * a participant.</p>
   */
  public static class V3VoterStatus
        extends V3PollStatus implements StatusAccessor {

    static final String TABLE_TITLE = "Votes";

  // Sort by (status, suborder):
  // (active, descending start time)
  // (done, descending end time)

    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule(SORT_KEY1, true),
		    new StatusTable.SortRule(SORT_KEY2, false));

    private final List colDescs =
      ListUtil.list(new ColumnDescriptor("auId", "Volume",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("caller", "Caller",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("status", "Status",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("start", "Start",
                                         ColumnDescriptor.TYPE_DATE),
                    new ColumnDescriptor("deadline", "Deadline",
                                         ColumnDescriptor.TYPE_DATE),
                    new ColumnDescriptor("pollId", "Poll ID",
                                         ColumnDescriptor.TYPE_STRING));

    public V3VoterStatus(PollManager pollManager) {
      super(pollManager);
    }

    public String getDisplayName() {
      return TABLE_TITLE;
    }

    public void populateTable(StatusTable table)
        throws StatusService.NoSuchTableException {
      boolean isDebug = table.getOptions().get(StatusTable.OPTION_DEBUG_USER);
      table.setColumnDescriptors(colDescs);
      if (isDebug) {
	table.setSummaryInfo(getSummary(pollManager));
      }
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows());
    }

    public boolean requiresKey() {
      return false;
    }

    private List getRows() {
      List rows = new ArrayList();
      Collection v3Voters = pollManager.getV3Voters();
      for (Iterator it = v3Voters.iterator(); it.hasNext(); ) {
        rows.add(makeRow((V3Voter)it.next()));
      }
      return rows;
    }

    private Map makeRow(V3Voter voter) {
      Map row = new HashMap();
      ArchivalUnit au = voter.getAu();
      row.put("auId", makeAuRef(au, ArchivalUnitStatus.AU_STATUS_TABLE_NAME));
      row.put("caller", voter.getPollerId().getIdString());
      row.put("status", voter.getStatusString());
      row.put("start", voter.getCreateTime());
      row.put("deadline", voter.getDeadline());
      String skey = PollUtil.makeShortPollKey(voter.getKey());
      row.put("pollId", new StatusTable.Reference(skey,
						  "V3VoterDetailTable",
						  voter.getKey()));
      if (voter.isPollActive()) {
	row.put(SORT_KEY1, SORT_BASE_ACTIVE);
	row.put(SORT_KEY2, voter.getCreateTime());
      } else {
	row.put(SORT_KEY1, SORT_BASE_DONE);
	row.put(SORT_KEY2, voter.getDeadline());
      }
      return row;
    }

    private List getSummary(PollManager pollManager) {
      List summary = new ArrayList();
      if (!CurrentConfig.getBooleanParam(V3PollFactory.PARAM_ENABLE_V3_VOTER,
					 V3PollFactory.DEFAULT_ENABLE_V3_VOTER)) { 
	summary.add(new StatusTable.SummaryInfo("Voting is disabled",
						ColumnDescriptor.TYPE_STRING,
						null));
      }
      StringBuilder sb = new StringBuilder();
      sb.append(pollManager.getEventCount(EventCtr.Accepted));
      sb.append(" accepted, ");
      int declined = pollManager.getEventCount(EventCtr.Declined);
      sb.append(declined);
      sb.append(" declined");
      if (declined != 0) {
	sb.append(" (");
	addNak(sb, PollNak.NAK_NO_AU, true);
	addNak(sb, PollNak.NAK_NOT_CRAWLED);
	addNak(sb, PollNak.NAK_PLUGIN_VERSION_MISMATCH);
	addNak(sb, PollNak.NAK_NO_TIME);
	addNak(sb, PollNak.NAK_TOO_MANY_VOTERS);
	addNak(sb, PollNak.NAK_HAVE_SUFFICIENT_REPAIRERS);
	sb.append(")");
      }
      summary.add(new StatusTable.SummaryInfo("Invitations",
					      ColumnDescriptor.TYPE_STRING,
					      sb.toString()));

      sb = new StringBuilder();
      int votes = pollManager.getEventCount(EventCtr.Voted);
      sb.append(votes);
      int noReceipt =
	votes - pollManager.getEventCount(EventCtr.ReceivedVoteReceipt);
      if (noReceipt != 0) {
	sb.append(" (");
	sb.append(noReceipt);
	sb.append(" no receipt)");
      }
      summary.add(new StatusTable.SummaryInfo("Votes",
					      ColumnDescriptor.TYPE_STRING,
					      sb.toString()));

      return summary;
    }

    void addNak(StringBuilder sb, PollNak nak) {
      addNak(sb, nak, false);
    }

    void addNak(StringBuilder sb, PollNak nak, boolean first) {
      int cnt = pollManager.getVoterNakEventCount(nak);
      if (cnt != 0) {
	if (!first) sb.append(", ");
	sb.append(cnt);
	sb.append(" ");
	sb.append(nak);
      }
    }
  }



  public static class PollOverview
    extends V3PollerStatus implements OverviewAccessor {

    public PollOverview(PollManager pollManager) {
      super(pollManager);
    }

    public Object getOverview(String tableName, BitSet options) {
      if (!CurrentConfig.getBooleanParam(V3PollFactory.PARAM_ENABLE_V3_POLLER,
					 V3PollFactory.DEFAULT_ENABLE_V3_POLLER)) { 
	return "Polling Disabled";
      }
      StringBuilder sb = new StringBuilder();
      sb.append(StringUtil.numberOfUnits(pollManager.getNumActiveV3Polls(),
					 "active poll", "active polls"));
      addEndStatus(sb, V3Poller.POLLER_STATUS_COMPLETE);
      addEndStatus(sb, V3Poller.POLLER_STATUS_NO_QUORUM);
      addEndStatus(sb, V3Poller.POLLER_STATUS_NO_TIME, "too busy");
      return new StatusTable.Reference(sb.toString(), POLLER_STATUS_TABLE_NAME);
    }
  }

  public static class VoterOverview
    extends V3PollerStatus implements OverviewAccessor {

    public VoterOverview(PollManager pollManager) {
      super(pollManager);
    }

    public Object getOverview(String tableName, BitSet options) {
      if (!CurrentConfig.getBooleanParam(V3PollFactory.PARAM_ENABLE_V3_VOTER,
					 V3PollFactory.DEFAULT_ENABLE_V3_VOTER)) { 
	return "Voting disabled";
      }
      int nActive = 0;
      int nError = 0;
      int nComplete = 0;
      int nTooBusy = 0;
      for (V3Voter voter :
	     (Collection<V3Voter>)pollManager.getV3Voters()) {
	switch (voter.getStatus()) {
	case STATUS_INITIALIZED:
	case STATUS_ACCEPTED_POLL:
	case STATUS_HASHING:
	case STATUS_VOTED:
	  nActive++;
	  break;
	case STATUS_NO_TIME:
	  nTooBusy++;
	  break;
	case STATUS_COMPLETE:
	  nComplete++;
	  break;
	case STATUS_EXPIRED:
	case STATUS_ERROR:
	  nError++;
	  break;
	case STATUS_DECLINED_POLL:
	case STATUS_VOTE_ACCEPTED:
	case STATUS_ABORTED:
	  break;
	}
      }
      List lst = new ArrayList();

      lst.add(StringUtil.numberOfUnits(nActive,
				       "active vote", "active votes"));
      if (nComplete > 0) lst.add(nComplete + " complete");
      if (nTooBusy > 0) lst.add(nTooBusy + " too busy");
      if (nError > 0) lst.add(nError + " error");
      String summ = StringUtil.separatedString(lst, ", ");
      return new StatusTable.Reference(summ, VOTER_STATUS_TABLE_NAME);
    }
  }

  /**
   * <p>The full status of an individual V3 Poll in which we are acting as a
   * poller.  Requires the PollID as a key.</p>
   *
   */
  public static class V3PollerStatusDetail
      extends V3PollStatus implements StatusAccessor {

    static final String TABLE_TITLE = "V3 Poll Status";

    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule("sort", true),
		    new StatusTable.SortRule("identity",
                                             CatalogueOrderComparator.SINGLETON));
    private final List<ColumnDescriptor> colDescs =
      ListUtil.list(new ColumnDescriptor("identity", "Peer",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("peerStatus", "Status",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("agreement", "Agreement",
                                         ColumnDescriptor.TYPE_AGREEMENT),
                    new ColumnDescriptor("numagree", "Agreeing URLs",
                                         ColumnDescriptor.TYPE_INT),
                    new ColumnDescriptor("numdisagree", "Disagreeing URLs",
                                         ColumnDescriptor.TYPE_INT),
                    new ColumnDescriptor("numpolleronly", "Poller-only URLs",
                                         ColumnDescriptor.TYPE_INT),
                    new ColumnDescriptor("numvoteronly", "Voter-only URLs",
                                         ColumnDescriptor.TYPE_INT),
                    new ColumnDescriptor("byteshashed", "Bytes Hashed",
                                         ColumnDescriptor.TYPE_INT),
                    new ColumnDescriptor("bytesread", "Bytes Read",
                                         ColumnDescriptor.TYPE_INT),
                    new ColumnDescriptor("state", "PSM State",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("when", "When",
                                         ColumnDescriptor.TYPE_DATE));

    private static final List<String> defaultCols =
      ListUtil.list("identity",
		    "peerStatus",
		    "agreement",
		    "numagree",
		    "numdisagree",
		    "numpolleronly",
		    "numvoteronly");


    public V3PollerStatusDetail(PollManager pollManager) {
      super(pollManager);
    }

    public void populateTable(StatusTable table) throws NoSuchTableException {
      String key = table.getKey();
      V3Poller poll = null;
      try {
        poll = (V3Poller)pollManager.getPoll(key);
      } catch (ClassCastException ex) {
        theLog.error("Expected V3Poller, but got " +
		     pollManager.getPoll(key).getClass().getName());
        return;
      }
      if (poll == null) return;
      table.setSummaryInfo(getSummary(poll, table));
      table.setTitle("Status of Poll " + key);
      if (!poll.isLocalPoll()) {
	table.setColumnDescriptors(colDescs, getDefaultCols(table, poll));
	table.setDefaultSortRules(sortRules);
	table.setRows(getRows(table, poll));
      }
    }

    private List<String> getDefaultCols(StatusTable table, V3Poller poll) {
      List<String> res = new ArrayList<String>();
      res.addAll(defaultCols);
      if (poll.isEnableHashStats()) {
	res.add("byteshashed");
	res.add("bytesread");
      }
      if (table.getOptions().get(StatusTable.OPTION_DEBUG_USER)) {
	res.add("state");
	res.add("when");
      }
      return res;
    }

    private List getRows(StatusTable table, V3Poller poll) {
      boolean isDebug = table.getOptions().get(StatusTable.OPTION_DEBUG_USER);
      List rows = new ArrayList();
      for (ParticipantUserData voter : poll.getParticipants()) {
        rows.add(makeRow(poll, voter, "0", isDebug));
      }
      for (ParticipantUserData voter : poll.getExParticipants()) {
        rows.add(makeRow(poll, voter, "1", isDebug));
      }
      return rows;
    }

    private Map makeRow(V3Poller poll, ParticipantUserData voter,
			Object sort, boolean isDebug) {
      Map row = new HashMap();
      PeerIdentity peer = voter.getVoterId();
      row.put("identity",
	      isDebug ? makeVoteRef(peer.getIdString(), peer, poll.getKey())
	      : peer.getIdString());
      row.put("peerStatus", voter.getStatusString());
      row.put("sort", sort);
      if (voter.hasVoted()) {
	ParticipantUserData.VoteCounts voteCounts = voter.getVoteCounts();
	row.put("agreement", voteCounts.getPercentAgreement());
	row.put("numagree", voteCounts.agreedVotes);
	row.put("numdisagree", voteCounts.disagreedVotes);
	row.put("numpolleronly", voteCounts.pollerOnlyVotes);
	row.put("numvoteronly", voteCounts.voterOnlyVotes);
	row.put("byteshashed", voter.getBytesHashed());
	row.put("bytesread", voter.getBytesRead());
      }
      PsmInterp interp = voter.getPsmInterp();
      if (interp != null) {
	PsmState state = interp.getCurrentState();
	if (state != null) {
	  row.put("state", state.getName());
	  long when = interp.getLastStateChange();
	  if (when > 0) {
	    row.put("when", when);
	  }
	}
      }	
      return row;
    }

    private List getSummary(V3Poller poll, StatusTable table) {
      boolean isDebug = table.getOptions().get(StatusTable.OPTION_DEBUG_USER);
      PollerStateBean pollerState = poll.getPollerStateBean();
      List summary = new ArrayList();
      summary.add(new SummaryInfo("Volume",
                                  ColumnDescriptor.TYPE_STRING,
				  makeAuRef(poll.getAu(),
					    ArchivalUnitStatus.AU_STATUS_TABLE_NAME)));
      summary.add(new SummaryInfo("Type",
                                  ColumnDescriptor.TYPE_STRING,
                                  poll.getPollVariant()));
      summary.add(new SummaryInfo("Status",
                                  ColumnDescriptor.TYPE_STRING,
                                  poll.getStatusString()));
      if (pollerState.getErrorDetail() != null) {
        summary.add(new SummaryInfo("Error",
                                    ColumnDescriptor.TYPE_STRING,
                                    pollerState.getErrorDetail()));
      }
      if (poll.getStatus() == POLLER_STATUS_COMPLETE) {
	if (!poll.isLocalPoll()) {
	  summary.add(new SummaryInfo("Agreement",
				      ColumnDescriptor.TYPE_AGREEMENT,
				      poll.getPercentAgreement()));
	}
      }
      if (isDebug && pollerState.getAdditionalInfo() != null) {
        summary.add(new SummaryInfo("Info",
                                    ColumnDescriptor.TYPE_STRING,
                                    pollerState.getAdditionalInfo()));
      }
      summary.add(new SummaryInfo("Start Time",
                                  ColumnDescriptor.TYPE_DATE,
                                  new Long(poll.getCreateTime())));
      if (!poll.isLocalPoll()) {
	summary.add(new SummaryInfo("Vote Deadline",
				    ColumnDescriptor.TYPE_DATE,
				    new Long(poll.getVoteDeadline())));
      }
      summary.add(new SummaryInfo("Duration",
                                  ColumnDescriptor.TYPE_TIME_INTERVAL,
                                  new Long(poll.getDuration())));
      if (poll.isPollActive()) {
	long remain = TimeBase.msUntil(poll.getDeadline().getExpirationTime());
	if (remain >= 0) {
	  summary.add(new SummaryInfo("Remaining",
				      ColumnDescriptor.TYPE_TIME_INTERVAL,
				      new Long(remain)));
	}
      } else if (!poll.getDeadline().equals(poll.getEndTime())) {
	summary.add(new SummaryInfo("Actual End",
				    ColumnDescriptor.TYPE_DATE,
				    poll.getEndTime()));
      }
      if (poll.getErrorUrls() != null && poll.getErrorUrls().size() > 0) {
        summary.add(new SummaryInfo("URLs with Hash errors",
                                    ColumnDescriptor.TYPE_STRING,
                                    new StatusTable.Reference(new Integer(poll.getErrorUrls().size()),
                                                              "V3ErrorURLsTable",
                                                              poll.getKey())));
      }
      if (isDebug) {
	File stateDir = poll.getStateDir();
        if (stateDir != null) {
	  summary.add(new SummaryInfo("State Dir",
				      ColumnDescriptor.TYPE_STRING,
				      stateDir));
	}
      }
      if (!poll.isLocalPoll()) {
	int activeRepairs = poll.getActiveRepairs().size();
	int talliedUrls = poll.getTalliedUrls().size();
	int agreeUrls = poll.getAgreedUrls().size();
	int disagreeUrls = poll.getDisagreedUrls().size();
	int noQuorumUrls = poll.getNoQuorumUrls().size();
	int tooCloseUrls = poll.getTooCloseUrls().size();
	int completedRepairs = poll.getCompletedRepairs().size();
        
	summary.add(new SummaryInfo("Total URLs In Vote",
				    ColumnDescriptor.TYPE_INT,
				    new Integer(talliedUrls)));
	if (agreeUrls > 0) {
	  summary.add(new SummaryInfo("Agreeing URLs",
				      ColumnDescriptor.TYPE_INT,
				      new StatusTable.Reference(new Integer(agreeUrls),
								"V3AgreeURLsTable",
								poll.getKey())));
	}
	if (disagreeUrls > 0) {
	  summary.add(new SummaryInfo("Disagreeing URLs",
				      ColumnDescriptor.TYPE_INT,
				      new StatusTable.Reference(new Integer(disagreeUrls),
								"V3DisagreeURLsTable",
								poll.getKey())));
	}
	if (noQuorumUrls > 0) {
	  summary.add(new SummaryInfo("No Quorum URLs",
				      ColumnDescriptor.TYPE_INT,
				      new StatusTable.Reference(new Integer(noQuorumUrls),
								"V3NoQuorumURLsTable",
								poll.getKey())));
	}
	if (tooCloseUrls > 0) {
	  summary.add(new SummaryInfo("Too Close URLs",
				      ColumnDescriptor.TYPE_INT,
				      new StatusTable.Reference(new Integer(tooCloseUrls),
								"V3TooCloseURLsTable",
								poll.getKey())));
	}
	if (completedRepairs > 0) {
	  summary.add(new SummaryInfo("Completed Repairs",
				      ColumnDescriptor.TYPE_INT,
				      new StatusTable.Reference(new Integer(completedRepairs),
								"V3CompletedRepairsTable",
								poll.getKey())));
	}
	if (activeRepairs > 0) {
	  String message = poll.isPollActive() ? "Queued Repairs" : "Incomplete Repairs";
	  summary.add(new SummaryInfo("Queued Repairs",
				      ColumnDescriptor.TYPE_INT,
				      new StatusTable.Reference(new Integer(activeRepairs),
								"V3ActiveRepairsTable",
								poll.getKey())));
        
	}
      }
      LocalHashResult lhr = poll.getLocalHashResult();
      if (lhr != null) {
	int matchingUrls = lhr.getMatchingUrls();
	int newlySuspectUrls = lhr.getNewlySuspectUrls();
	int newlyHashedUrls = lhr.getNewlyHashedUrls();
	int skippedUrls = lhr.getSkippedUrls();
	int totalUrls = matchingUrls + newlySuspectUrls + newlyHashedUrls
	  + skippedUrls;
	summary.add(new SummaryInfo("Total checked URLs",
				    ColumnDescriptor.TYPE_INT,
				    new Integer(totalUrls)));
	if (matchingUrls > 0) {
	  summary.add(new SummaryInfo("Matching URLs",
				      ColumnDescriptor.TYPE_INT,
				      new Integer(matchingUrls)));
	}
	if (newlySuspectUrls > 0) {
	  summary.add(new SummaryInfo("Newly Suspect URLs",
				      ColumnDescriptor.TYPE_INT,
				      new Integer(newlySuspectUrls)));
	}
	if (newlyHashedUrls > 0) {
	  summary.add(new SummaryInfo("Newly Hashed URLs",
				      ColumnDescriptor.TYPE_INT,
				      new Integer(newlyHashedUrls)));
	}
	if (skippedUrls > 0) {
	  summary.add(new SummaryInfo("Already Suspect URLs",
				      ColumnDescriptor.TYPE_INT,
				      new Integer(skippedUrls)));
	}
      }
      if (poll.isEnableHashStats()) {
        summary.add(new SummaryInfo("Bytes Hashed",
                                    ColumnDescriptor.TYPE_INT,
                                    poll.getBytesHashed()));
        
        summary.add(new SummaryInfo("Bytes Read",
                                    ColumnDescriptor.TYPE_INT,
                                    poll.getBytesRead()));
        
      }
      summary.add(new SummaryInfo("Quorum",
                                  ColumnDescriptor.TYPE_INT,
                                  poll.getQuorum()));
      return summary;
    }

    public String getDisplayName() {
      return TABLE_TITLE;
    }

    public boolean requiresKey() {
      return true;
    }
  }

  public static class V3ActiveRepairs
      extends V3PollStatus implements StatusAccessor {
    static final String TABLE_TITLE = "V3 Repairs (Active)";
    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule("url",
                                             CatalogueOrderComparator.SINGLETON));
    private final List colDescs =
      ListUtil.list(new ColumnDescriptor("url", "URL",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("repairFrom", "Repair From",
                                         ColumnDescriptor.TYPE_STRING));
    public V3ActiveRepairs(PollManager manager) {
      super(manager);
    }
    public void populateTable(StatusTable table) throws NoSuchTableException {
      String key = table.getKey();
      V3Poller poller = null;
      try {
        poller = (V3Poller)pollManager.getPoll(key);
      } catch (ClassCastException ex) {
        theLog.error("Expected V3Voter, but got " +
		     pollManager.getPoll(key).getClass().getName());
        return;
      }
      if (poller == null) return;
      table.setTitle("Active Repairs for Poll " + poller.getKey());
      table.setColumnDescriptors(colDescs);
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows(poller));
    }
    private List getRows(V3Poller poller) {
      List rows = new ArrayList();
      for (PollerStateBean.Repair rp: poller.getActiveRepairs()) {
        Map row = new HashMap();
        row.put("url", rp.getUrl());
        if (rp.isPublisherRepair()) {
          row.put("repairFrom", "Publisher");
        } else {
          row.put("repairFrom", rp.getRepairFrom().getIdString());
        }
        rows.add(row);
      }
      return rows;
    }
    public String getDisplayName() {
      return TABLE_TITLE;
    }
    public boolean requiresKey() {
      return true;
    }
  }

  public static class V3CompletedRepairs
      extends V3PollStatus implements StatusAccessor {
    static final String TABLE_TITLE = "V3 Repairs (Completed)";
    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule("url",
                                             CatalogueOrderComparator.SINGLETON));
    private final List colDescs =
      ListUtil.list(new ColumnDescriptor("url", "URL",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("repairFrom", "Repaired From",
                                         ColumnDescriptor.TYPE_STRING));
    public V3CompletedRepairs(PollManager manager) {
      super(manager);
    }
    public void populateTable(StatusTable table) throws NoSuchTableException {
      String key = table.getKey();
      V3Poller poller = null;
      try {
        poller = (V3Poller)pollManager.getPoll(key);
      } catch (ClassCastException ex) {
        theLog.error("Expected V3Poller, but got " +
		     pollManager.getPoll(key).getClass().getName());
        return;
      }
      if (poller == null) return;
      table.setTitle("Completed Repairs for Poll " + poller.getKey());
      table.setColumnDescriptors(colDescs);
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows(poller));
    }
    private List getRows(V3Poller poller) {
      List rows = new ArrayList();
      for (PollerStateBean.Repair rp: poller.getCompletedRepairs()) {
        Map row = new HashMap();
        row.put("url", rp.getUrl());
        if (rp.isPublisherRepair()) {
          row.put("repairFrom", "Publisher");
        } else {
          row.put("repairFrom", rp.getRepairFrom().getIdString());
        }
        rows.add(row);
      }
      return rows;
    }
    public String getDisplayName() {
      return TABLE_TITLE;
    }
    public boolean requiresKey() {
      return true;
    }
  }
  
  public static class V3NoQuorumURLs extends V3PollStatus 
      implements StatusAccessor {
    static final String TABLE_TITLE = "V3 Poll Details - No Quorum URLs";
    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule("url",
                                             CatalogueOrderComparator.SINGLETON));
    private final List colDescs =
      ListUtil.list(new ColumnDescriptor("url", "URL",
                                         ColumnDescriptor.TYPE_STRING));
    public V3NoQuorumURLs(PollManager manager) {
      super(manager);
    }
    public void populateTable(StatusTable table) throws NoSuchTableException {
      String key = table.getKey();
      V3Poller poller = null;
      try {
        poller = (V3Poller)pollManager.getPoll(key);
      } catch (ClassCastException ex) {
        theLog.error("Expected V3Poller, but got " +
		     pollManager.getPoll(key).getClass().getName());
        return;
      }
      if (poller == null) return;
      table.setTitle("V3 Poll Details - No Quorum URLs in Poll " + poller.getKey());
      table.setColumnDescriptors(colDescs);
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows(poller));
    }
    private List getRows(V3Poller poller) {
      List rows = new ArrayList();
      for (Iterator it = poller.getNoQuorumUrls().iterator(); it.hasNext(); ) {
        Map row = new HashMap();
        row.put("url", (String)it.next());
        rows.add(row);
      }
      return rows;
    }
    public String getDisplayName() {
      return TABLE_TITLE;
    }
    public boolean requiresKey() {
      return true;
    }
  }

  public static class V3TooCloseURLs extends V3PollStatus 
      implements StatusAccessor {
    static final String TABLE_TITLE = "V3 Poll Details - Too Close URLs";
    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule("url",
                                             CatalogueOrderComparator.SINGLETON));
    private final List colDescs =
      ListUtil.list(new ColumnDescriptor("url", "URL",
                                         ColumnDescriptor.TYPE_STRING));
    public V3TooCloseURLs(PollManager manager) {
      super(manager);
    }
    public void populateTable(StatusTable table) throws NoSuchTableException {
      String key = table.getKey();
      V3Poller poller = null;
      try {
        poller = (V3Poller)pollManager.getPoll(key);
      } catch (ClassCastException ex) {
        theLog.error("Expected V3Poller, but got " +
		     pollManager.getPoll(key).getClass().getName());
        return;
      }
      if (poller == null) return;
      table.setTitle("V3 Poll Details - Too Close URLs in Poll " + poller.getKey());
      table.setColumnDescriptors(colDescs);
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows(poller));
    }
    private List getRows(V3Poller poller) {
      List rows = new ArrayList();
      for (Iterator it = poller.getTooCloseUrls().iterator(); it.hasNext(); ) {
        Map row = new HashMap();
        row.put("url", (String)it.next());
        rows.add(row);
      }
      return rows;
    }
    public String getDisplayName() {
      return TABLE_TITLE;
    }
    public boolean requiresKey() {
      return true;
    }
  }
  
  
  public static class V3AgreeURLs extends V3PollStatus 
      implements StatusAccessor {
    static final String TABLE_TITLE = "V3 Poll Details - Agreeing URLs";
    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule("url",
                                             CatalogueOrderComparator.SINGLETON));
    private final List colDescs =
      ListUtil.list(new ColumnDescriptor("url", "URL",
                                         ColumnDescriptor.TYPE_STRING));
    public V3AgreeURLs(PollManager manager) {
      super(manager);
    }
    public void populateTable(StatusTable table) throws NoSuchTableException {
      String key = table.getKey();
      V3Poller poller = null;
      try {
        poller = (V3Poller)pollManager.getPoll(key);
      } catch (ClassCastException ex) {
        theLog.error("Expected V3Poller, but got " +
		     pollManager.getPoll(key).getClass().getName());
        return;
      }
      if (poller == null) return;
      table.setTitle("V3 Poll Details - Agreeing URLs in Poll " + poller.getKey());
      table.setColumnDescriptors(colDescs);
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows(poller));
    }
    private List getRows(V3Poller poller) {
      List rows = new ArrayList();
      for (Iterator it = poller.getAgreedUrls().iterator(); it.hasNext(); ) {
        Map row = new HashMap();
        row.put("url", (String)it.next());
        rows.add(row);
      }
      return rows;
    }
    public String getDisplayName() {
      return TABLE_TITLE;
    }
    public boolean requiresKey() {
      return true;
    }
  }

  public static class V3DisagreeURLs extends V3PollStatus 
      implements StatusAccessor {
    static final String TABLE_TITLE = "V3 Poll Details - Disagreeing URLs";
    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule("url",
                                             CatalogueOrderComparator.SINGLETON));
    private final List colDescs =
      ListUtil.list(new ColumnDescriptor("url", "URL",
                                         ColumnDescriptor.TYPE_STRING));
    public V3DisagreeURLs(PollManager manager) {
      super(manager);
    }
    public void populateTable(StatusTable table) throws NoSuchTableException {
      String key = table.getKey();
      V3Poller poller = null;
      try {
        poller = (V3Poller)pollManager.getPoll(key);
      } catch (ClassCastException ex) {
        theLog.error("Expected V3Poller, but got " +
		     pollManager.getPoll(key).getClass().getName());
        return;
      }
      if (poller == null) return;
      table.setTitle("V3 Poll Details - Disagreeing URLs in Poll " + poller.getKey());
      table.setColumnDescriptors(colDescs);
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows(poller));
    }
    private List getRows(V3Poller poller) {
      List rows = new ArrayList();
      for (Iterator it = poller.getDisagreedUrls().iterator(); it.hasNext(); ) {
        Map row = new HashMap();
        row.put("url", (String)it.next());
        rows.add(row);
      }
      return rows;
    }
    public String getDisplayName() {
      return TABLE_TITLE;
    }
    public boolean requiresKey() {
      return true;
    }
  }
  
  public static class V3ErrorURLs extends V3PollStatus 
      implements StatusAccessor {
    static final String TABLE_TITLE = "V3 Poll Details - URLs with Hash Errors";
    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule("url",
                                             CatalogueOrderComparator.SINGLETON));
    private final List colDescs =
      ListUtil.list(new ColumnDescriptor("url", "URL",
                                         ColumnDescriptor.TYPE_STRING),
                    new ColumnDescriptor("erorr", "Error",
                                         ColumnDescriptor.TYPE_STRING));
    public V3ErrorURLs(PollManager manager) {
      super(manager);
    }
    public void populateTable(StatusTable table) throws NoSuchTableException {
      String key = table.getKey();
      V3Poller poller = null;
      try {
        poller = (V3Poller)pollManager.getPoll(key);
      } catch (ClassCastException ex) {
        theLog.error("Expected V3Poller, but got " +
                     pollManager.getPoll(key).getClass().getName());
        return;
      }
      if (poller == null) return;
      table.setTitle("V3 Poll Details - URLs with Hash Errors in Poll " + poller.getKey());
      table.setColumnDescriptors(colDescs);
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows(poller));
    }
    private List getRows(V3Poller poller) {
      List rows = new ArrayList();
      Map errorUrls = poller.getErrorUrls();
      synchronized(errorUrls) {
        for (Iterator it = errorUrls.keySet().iterator(); it.hasNext(); ) {
          String url = (String)it.next();
          String exceptionMessage = (String)errorUrls.get(url);
          Map row = new HashMap();
          row.put("url", url);
          row.put("error", exceptionMessage);
          rows.add(row);
        }
      }
      return rows;
    }
    public String getDisplayName() {
      return TABLE_TITLE;
    }
    public boolean requiresKey() {
      return true;
    }
  }

  /**
   * <p>The full status of an individual V3 Poll in which we are acting as a
   * participant.  Requires the PollID as a key.</p>
   *
   */
  public static class V3VoterStatusDetail
      extends V3PollStatus implements StatusAccessor {
    static final String TABLE_TITLE = "V3 Vote Status";

    public V3VoterStatusDetail(PollManager manager) {
      super(manager);
    }

    public void populateTable(StatusTable table) throws NoSuchTableException {
      String key = table.getKey();
      V3Voter voter = null;
      try {
        voter = (V3Voter)pollManager.getPoll(key);
      } catch (ClassCastException ex) {
        theLog.error("Expected V3Voter, but got " +
		     pollManager.getPoll(key).getClass().getName());
        return;
      }
      if (voter == null) return;
      table.setSummaryInfo(getSummary(voter, table));
      table.setTitle("Status of Vote in Poll " + key);
    }

    private List getSummary(V3Voter voter, StatusTable table) {
      boolean isDebug = table.getOptions().get(StatusTable.OPTION_DEBUG_USER);
      VoterUserData userData = voter.getVoterUserData();
      List summary = new ArrayList();
      summary.add(new SummaryInfo("Volume",
                                  ColumnDescriptor.TYPE_STRING,
				  makeAuRef(voter.getAu(),
					    ArchivalUnitStatus.AU_STATUS_TABLE_NAME)));
      summary.add(new SummaryInfo("Status",
                                  ColumnDescriptor.TYPE_STRING,
                                  voter.getStatusString()));

      if (isDebug) {
	File stateDir = voter.getStateDir();
        if (stateDir != null) {
	  summary.add(new SummaryInfo("State Dir",
				      ColumnDescriptor.TYPE_STRING,
				      stateDir));
        }
	PsmInterp interp = voter.getPsmInterp();
	if (interp != null) {
	  PsmState state = interp.getCurrentState();
	  if (state != null) {
	    summary.add(new SummaryInfo("PSM State",
					ColumnDescriptor.TYPE_STRING,
					state.getName()));
	  }
	}
      }
      if (userData.getErrorDetail() != null) {
        summary.add(new SummaryInfo("Error",
                                    ColumnDescriptor.TYPE_STRING,
                                    userData.getErrorDetail()));
      }
      PeerIdentity peer = voter.getPollerId();
      Object caller = isDebug
	? makePollRef(peer.getIdString(), peer, table.getKey())
	: peer.getIdString();
      summary.add(new SummaryInfo("Caller",
                                  ColumnDescriptor.TYPE_STRING,
				  caller));
      summary.add(new SummaryInfo("Start Time",
                                  ColumnDescriptor.TYPE_DATE,
                                  new Long(voter.getCreateTime())));
      summary.add(new SummaryInfo("Vote Deadline",
                                  ColumnDescriptor.TYPE_DATE,
                                  voter.getVoteDeadline()));
      summary.add(new SummaryInfo("Duration",
                                  ColumnDescriptor.TYPE_TIME_INTERVAL,
                                  new Long(voter.getDuration())));
      long remain = TimeBase.msUntil(voter.getDeadline().getExpirationTime());
      if (remain >= 0) {
        summary.add(new SummaryInfo("Remaining",
                                    ColumnDescriptor.TYPE_TIME_INTERVAL,
                                    new Long(remain)));
      }
      if (voter.getStatus() == STATUS_COMPLETE && userData.hasReceivedHint()) {
	summary.add(new SummaryInfo("Agreement",
				    ColumnDescriptor.TYPE_AGREEMENT,
				    userData.getAgreementHint()));
      }
      summary.add(new SummaryInfo("Poller Nonce",
                                  ColumnDescriptor.TYPE_STRING,
                                  ByteArray.toBase64(voter.getPollerNonce())));
      summary.add(new SummaryInfo("Voter Nonce",
                                  ColumnDescriptor.TYPE_STRING,
                                  ByteArray.toBase64(voter.getVoterNonce())));
      if (userData.isSymmetricPoll()) {
	// Its a symmetric poll
	byte[] nonce2 = userData.getVoterNonce2();
	summary.add(new SummaryInfo("Voter Nonce2",
				    ColumnDescriptor.TYPE_STRING,
				    ByteArray.toBase64(nonce2)));
	if (voter.getStatus() == STATUS_COMPLETE) {
	  summary.add(new SummaryInfo("Agreeing URLs",
				      ColumnDescriptor.TYPE_INT,
				      userData.getNumAgreeUrl()));
	  summary.add(new SummaryInfo("Disagreeing URLs",
				      ColumnDescriptor.TYPE_INT,
				      userData.getNumDisagreeUrl()));
	  summary.add(new SummaryInfo("Voter only URLs",
				      ColumnDescriptor.TYPE_INT,
				      userData.getNumVoterOnlyUrl()));
	  summary.add(new SummaryInfo("Poller only URLs",
				      ColumnDescriptor.TYPE_INT,
				      userData.getNumPollerOnlyUrl()));
	}
      }
	
	
      return summary;
    }

    public String getDisplayName() {
      return TABLE_TITLE;
    }

    public boolean requiresKey() {
      return true;
    }
  }
}
