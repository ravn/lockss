/*
 * $Id: IdentityManagerStatus.java,v 1.8 2007-10-11 01:13:26 smorabito Exp $
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
package org.lockss.protocol;
import java.util.*;
import org.lockss.daemon.status.*;
import org.lockss.util.*;

public class IdentityManagerStatus
  implements StatusAccessor,  StatusAccessor.DebugOnly {

  private Map<PeerIdentity,PeerIdentityStatus> theIdentities;

  public IdentityManagerStatus(Map<PeerIdentity,PeerIdentityStatus> theIdentities) {
    this.theIdentities = theIdentities;
  }

  private static final List statusSortRules =
    ListUtil.list(new StatusTable.SortRule("ip", true));

  private static final List statusColDescs =
    ListUtil.list(
		  new ColumnDescriptor("ip", "Peer",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("lastMessage", "Last Message",
				       ColumnDescriptor.TYPE_DATE,
				       "Last time a message was received " +
				       "from this peer."),
		  new ColumnDescriptor("lastOp", "Message Type",
				       ColumnDescriptor.TYPE_STRING,
				       "Last message type that " +
				       "was sent from this peer."),
		  new ColumnDescriptor("origTot", "Messages",
				       ColumnDescriptor.TYPE_INT,
				       "Total number of messages received " +
				       "from this peer."),
		  new ColumnDescriptor("origLastPoller", "Last Poll",
		                       ColumnDescriptor.TYPE_DATE,
		                       "Last time that the local peer " +
		                       "participated in a poll with this peer."),
                  new ColumnDescriptor("origLastVoter", "Last Vote",
                                       ColumnDescriptor.TYPE_DATE,
                                       "Last time that this peer " +
                                       "participate as a voter in a poll " +
                                       "called by the local peer."),
                  new ColumnDescriptor("origLastInvitation", "Last Invitation",
                                       ColumnDescriptor.TYPE_DATE,
                                       "Last time this peer was invited into " +
                                       "a poll called by the local peer."),
                  new ColumnDescriptor("origTotalInvitations", "Invitations",
                                       ColumnDescriptor.TYPE_INT,
                                       "Total number of invitations sent to " +
                                       "this peer by the local peer."),
                  new ColumnDescriptor("origPoller", "Polls Called",
                                       ColumnDescriptor.TYPE_INT,
                                       "Total number of polls called by " +
                                       "this peer in which the local peer " +
                                       "voted."),
                  new ColumnDescriptor("origVoter", "Votes Cast",
                                       ColumnDescriptor.TYPE_INT,
                                       "Total number of polls called by " +
                                       "the local peer in which this peer " +
                                       "voted."),
                  new ColumnDescriptor("pollsRejected", "Polls Rejected",
                                       ColumnDescriptor.TYPE_INT,
                                       "Total number of poll requests "
                                       + "rejected by this peer"),
                  new ColumnDescriptor("pollNak", "NAK Reason",
                                       ColumnDescriptor.TYPE_INT,
                                       "Reason for most recent poll request " +
                                       "rejection, if any.")
		  );

  public String getDisplayName() {
    return "Peer Identities";
  }

  public void populateTable(StatusTable table) {
    String key = table.getKey();
    table.setColumnDescriptors(statusColDescs);
    table.setDefaultSortRules(statusSortRules);
    table.setRows(getRows(key));
    table.setSummaryInfo(getSummaryInfo(key));
  }

  public boolean requiresKey() {
    return false;
  }

  private List getRows(String key) {
    List table = new ArrayList();
    for (PeerIdentity pid : theIdentities.keySet()) {
      if (!pid.isLocalIdentity()) {
        table.add(makeRow(pid, theIdentities.get(pid)));
      }
    }
    return table;
  }
  
  private List getSummaryInfo(String key) {
    List res = new ArrayList();
    List<String> localIds = new ArrayList();
    for (PeerIdentity pid : theIdentities.keySet()) {
      if (pid.isLocalIdentity()) {
        localIds.add(pid.getIdString());
      }
    }
    if (!localIds.isEmpty()) {
      String idList =  StringUtil.separatedString(localIds, ", ");
      res.add(new StatusTable.SummaryInfo("Local Identities",
                                          ColumnDescriptor.TYPE_STRING,
                                          idList));
    }
    return res;
  }

  private Map makeRow(PeerIdentity pid, PeerIdentityStatus status) {
    Map row = new HashMap();
    row.put("ip", pid.getIdString());
    row.put("lastMessage", new Long(status.getLastMessageTime()));
    row.put("lastOp", getMessageType(status.getLastMessageOpCode()));
    row.put("origTot", new Long(status.getTotalMessages()));
    row.put("origPoller",
            new Long(status.getTotalPollerPolls()));
    row.put("origLastPoller",
            new Long(status.getLastPollerTime()));
    row.put("origVoter",
            new Long(status.getTotalVoterPolls()));
    row.put("origLastVoter",
            new Long(status.getLastVoterTime()));
    row.put("origLastInvitation",
            new Long(status.getLastPollInvitationTime()));
    row.put("origTotalInvitations",
            new Long(status.getTotalPollInvitatioins()));
    row.put("pollsRejected",
            new Long(status.getTotalRejectedPolls()));
    row.put("pollNak",
            status.getLastPollNak());
    return row;
  }

  
  private String getMessageType(int opcode) {
    if (opcode >= V3LcapMessage.POLL_MESSAGES_BASE && 
        opcode < (V3LcapMessage.POLL_MESSAGES.length +
            V3LcapMessage.POLL_MESSAGES_BASE)) {
      return V3LcapMessage.POLL_MESSAGES[opcode - 
                                         V3LcapMessage.POLL_MESSAGES_BASE]
                                         + " (" + opcode + ")";
    } else {
      return "n/a";
    }
  }

}
