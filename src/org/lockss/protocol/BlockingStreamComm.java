/*
 * $Id: BlockingStreamComm.java,v 1.38.2.3 2009-02-07 01:17:56 tlipkis Exp $
 */

/*

Copyright (c) 2000-2008 Board of Trustees of Leland Stanford Jr. University,
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

import java.io.*;
import java.net.*;
import java.text.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;
import java.util.*;

import org.apache.commons.collections.*;
import org.apache.commons.collections.bag.TreeBag; // needed to disambiguate
import EDU.oswego.cs.dl.util.concurrent.*;

import org.lockss.util.*;
import org.lockss.util.Queue;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;
import org.lockss.app.*;
import org.lockss.poller.*;

/**
 * BlockingStreamComm implements the streaming mesaage protocol using
 * blocking sockets.
 */
public class BlockingStreamComm
  extends BaseLockssDaemonManager
  implements ConfigurableManager, LcapStreamComm, PeerMessage.Factory {

  static Logger log = Logger.getLogger("SComm");

  public static final String SERVER_NAME = "StreamComm";

  /** Use V3 over SSL **/
  public static final String PARAM_USE_V3_OVER_SSL = PREFIX + "v3OverSsl";
  public static final boolean DEFAULT_USE_V3_OVER_SSL = false;

  /** Use client authentication for SSL **/
  public static final String PARAM_USE_SSL_CLIENT_AUTH = PREFIX + "SslClientAuth";
  public static final boolean DEFAULT_USE_SSL_CLIENT_AUTH = true;

  /** Use temporary SSL keystore**/
  public static final String PARAM_SSL_TEMP_KEYSTORE = PREFIX + "SslTempKeystore";
  public static final boolean DEFAULT_SSL_TEMP_KEYSTORE = true;

  /** File name for SSL key store **/
  public static final String PARAM_SSL_KEYSTORE = PREFIX + "SslKeyStore";
  public static final String DEFAULT_SSL_KEYSTORE = ".keystore";

  /** File name for SSL key store password **/
  public static final String PARAM_SSL_PRIVATE_KEY_PASSWORD_FILE = PREFIX + "SslPrivateKeyPasswordFile";
  public static final String DEFAULT_SSL_PRIVATE_KEY_PASSWORD_FILE = ".password";

  /** SSL protocol to use **/
  public static final String PARAM_SSL_PROTOCOL = PREFIX + "SslProtocol";
  public static final String DEFAULT_SSL_PROTOCOL = "TLSv1";

  /** Max peer channels.  Only affects outgoing messages; incoming
   * connections are always accepted. */
  public static final String PARAM_MAX_CHANNELS =
    PREFIX + "maxChannels";
  public static final int DEFAULT_MAX_CHANNELS = 50;

  /** Min threads in channel thread pool */
  public static final String PARAM_CHANNEL_THREAD_POOL_MIN =
    PREFIX + "threadPool.min";
  public static final int DEFAULT_CHANNEL_THREAD_POOL_MIN = 3;

  /** Max threads in channel thread pool */
  public static final String PARAM_CHANNEL_THREAD_POOL_MAX =
    PREFIX + "threadPool.max";
  public static final int DEFAULT_CHANNEL_THREAD_POOL_MAX = 3 * DEFAULT_MAX_CHANNELS;

  /** Duration after which idle threads will be terminated..  -1 = never */
  public static final String PARAM_CHANNEL_THREAD_POOL_KEEPALIVE =
    PREFIX + "threadPool.keepAlive";
  public static final long DEFAULT_CHANNEL_THREAD_POOL_KEEPALIVE =
    10 * Constants.MINUTE;

  /** Connect timeout */
  public static final String PARAM_CONNECT_TIMEOUT =
    PREFIX + "timeout.connect";
  public static final long DEFAULT_CONNECT_TIMEOUT = 2 * Constants.MINUTE;

  /** Data timeout (SO_TIMEOUT), channel is aborted if read times out.
   * This should be disabled (zero) because the read side of a channel may
   * legitimately be idle for a long time (if the channel is sending), and
   * interrupted reads apparently cannot reliably be resumed.  If the
   * channel is truly idle, the send side should close it. */
  public static final String PARAM_DATA_TIMEOUT =
    PREFIX + "timeout.data";
  public static final long DEFAULT_DATA_TIMEOUT = 0;

  /** Time after which idle channel will be closed */
  public static final String PARAM_CHANNEL_IDLE_TIME =
    PREFIX + "channelIdleTime";
  public static final long DEFAULT_CHANNEL_IDLE_TIME = 2 * Constants.MINUTE;

  /** Time channel remains in DRAIN_INPUT state before closing */
  public static final String PARAM_DRAIN_INPUT_TIME =
    PREFIX + "drainInputTime";
  public static final long DEFAULT_DRAIN_INPUT_TIME = 10 * Constants.SECOND;

  /** Interval at which send thread checks idle timer */
  public static final String PARAM_SEND_WAKEUP_TIME =
    PREFIX + "sendWakeupTime";
  public static final long DEFAULT_SEND_WAKEUP_TIME = 1 * Constants.MINUTE;

  /** Interval before message expiration time at which to retry */
  public static final String PARAM_RETRY_BEFORE_EXPIRATION =
    PREFIX + "retryBeforeExpiration";
  public static final long DEFAULT_RETRY_BEFORE_EXPIRATION =
    1 * Constants.MINUTE;

  /** Max time to wait and retry connection to unresponsive peer.  May
   * happen sooner if queued messages will expire sooner */
  public static final String PARAM_MAX_PEER_RETRY_INTERVAL =
    PREFIX + "maxPeerRetryInterval";
  public static final long DEFAULT_MAX_PEER_RETRY_INTERVAL =
    30 * Constants.MINUTE;

  /** Min time to wait and retry connection to unresponsive peer.  */
  public static final String PARAM_MIN_PEER_RETRY_INTERVAL =
    PREFIX + "minPeerRetryInterval";
  public static final long DEFAULT_MIN_PEER_RETRY_INTERVAL =
    30 * Constants.SECOND;

  /** Min time to wait between retry attempts (channel start interval) */
  public static final String PARAM_RETRY_DELAY =
    PREFIX + "retryDelay";
  public static final long DEFAULT_RETRY_DELAY = 5 * Constants.SECOND;

  /** FilePeerMessage will be used for messages larger than this, else
   * MemoryPeerMessage */
  public static final String PARAM_MIN_FILE_MESSAGE_SIZE =
    PREFIX + "minFileMessageSize";
  public static final int DEFAULT_MIN_FILE_MESSAGE_SIZE = 1024;

  /** Maximum allowable received message size */
  public static final String PARAM_MAX_MESSAGE_SIZE =
    PREFIX + "maxMessageSize";
  public static final long DEFAULT_MAX_MESSAGE_SIZE = 1024 * 1024 * 1024;

  /** Dir for PeerMessage data storage */
  public static final String PARAM_DATA_DIR = PREFIX + "messageDataDir";
  /** Default is PlatformInfo.getSystemTempDir() */
  public static final String DEFAULT_DATA_DIR = "Platform tmp dir";

  /** Wrap Socket OutputStream in BufferedOutputStream? */
  public static final String PARAM_IS_BUFFERED_SEND = PREFIX + "bufferedSend";
  public static final boolean DEFAULT_IS_BUFFERED_SEND = true;

  /** TCP_NODELAY */
  public static final String PARAM_TCP_NODELAY = PREFIX + "tcpNodelay";
  public static final boolean DEFAULT_TCP_NODELAY = true;

  /** Amount of time BlockingStreamComm.stopService() should wait for
   * worker threads to exit.  Zero disables wait.  */
  public static final String PARAM_WAIT_EXIT = PREFIX + "waitExit";
  public static final long DEFAULT_WAIT_EXIT = 2 * Constants.SECOND;

  /** If true, associated channels that refuse messages will be immediately
   * dissociated */
  public static final String PARAM_DISSOCIATE_ON_NO_SEND =
    PREFIX + "dissociateOnNoSend";
  public static final boolean DEFAULT_DISSOCIATE_ON_NO_SEND = true;

  /** If true, stopChannel() will dissociate unconditionally, matching the
   * previous behavior.  If false it will dissociate only if it changes the
   * state to CLOSING. */
  public static final String PARAM_DISSOCIATE_ON_EVERY_STOP =
    PREFIX + "dissociateOnEveryStop";
  public static final boolean DEFAULT_DISSOCIATE_ON_EVERY_STOP = false;

  /** If true, unknown peer messages opcodes cause the channel to abort */
  public static final String PARAM_ABORT_ON_UNKNOWN_OP =
    PREFIX + "abortOnUnknownOp";
  public static final boolean DEFAULT_ABORT_ON_UNKNOWN_OP = true;

  static final String WDOG_PARAM_SCOMM = "SComm";
  static final long WDOG_DEFAULT_SCOMM = 1 * Constants.HOUR;

  static final String PRIORITY_PARAM_SCOMM = "SComm";
  static final int PRIORITY_DEFAULT_SCOMM = -1;

  static final String PRIORITY_PARAM_SLISTEN = "SListen";
  static final int PRIORITY_DEFAULT_SLISTEN = -1;

  static final String WDOG_PARAM_CHANNEL = "Channel";
  static final long WDOG_DEFAULT_CHANNEL = 30 * Constants.MINUTE;

  static final String PRIORITY_PARAM_CHANNEL = "Channel";
  static final int PRIORITY_DEFAULT_CHANNEL = -1;

  static final String WDOG_PARAM_RETRY = "SRetry";
  static final long WDOG_DEFAULT_RETRY = 1 * Constants.HOUR;

  static final String PRIORITY_PARAM_RETRY = "SRetry";
  static final int PRIORITY_DEFAULT_RETRY = -1;


  private boolean paramUseV3OverSsl = DEFAULT_USE_V3_OVER_SSL;
  private boolean paramSslClientAuth = DEFAULT_USE_SSL_CLIENT_AUTH;
  private boolean paramSslTempKeystore = DEFAULT_SSL_TEMP_KEYSTORE;
  private String paramSslKeyStore = DEFAULT_SSL_KEYSTORE;
  private String paramSslPrivateKeyPasswordFile =
    DEFAULT_SSL_PRIVATE_KEY_PASSWORD_FILE;
  private String paramSslProtocol = DEFAULT_SSL_PROTOCOL;
  private int paramMinFileMessageSize = DEFAULT_MIN_FILE_MESSAGE_SIZE;
  private long paramMaxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
  private File dataDir = null;
  private int paramBacklog = DEFAULT_LISTEN_BACKLOG;
  private int paramMaxChannels = DEFAULT_MAX_CHANNELS;
  private int paramMinPoolSize = DEFAULT_CHANNEL_THREAD_POOL_MIN;
  private int paramMaxPoolSize = DEFAULT_CHANNEL_THREAD_POOL_MAX;
  private long paramPoolKeepaliveTime = DEFAULT_CHANNEL_THREAD_POOL_KEEPALIVE;
  private long paramConnectTimeout = DEFAULT_CONNECT_TIMEOUT;
  private long paramSoTimeout = DEFAULT_DATA_TIMEOUT;
  private long paramSendWakeupTime = DEFAULT_SEND_WAKEUP_TIME;
  private long paramRetryBeforeExpiration = DEFAULT_RETRY_BEFORE_EXPIRATION;
  private long paramMaxPeerRetryInterval = DEFAULT_MAX_PEER_RETRY_INTERVAL;
  private long paramMinPeerRetryInterval = DEFAULT_MIN_PEER_RETRY_INTERVAL;
  private long paramRetryDelay = DEFAULT_RETRY_DELAY;
  protected long paramChannelIdleTime = DEFAULT_CHANNEL_IDLE_TIME;
  private long paramDrainInputTime = DEFAULT_DRAIN_INPUT_TIME;
  private boolean paramIsBufferedSend = DEFAULT_IS_BUFFERED_SEND;
  private boolean paramIsTcpNodelay = DEFAULT_TCP_NODELAY;
  private long paramWaitExit = DEFAULT_WAIT_EXIT;
  private boolean paramAbortOnUnknownOp = DEFAULT_ABORT_ON_UNKNOWN_OP;
  private long lastHungCheckTime = 0;
  private PooledExecutor pool;
  protected SSLSocketFactory sslSocketFactory = null;
  protected SSLServerSocketFactory sslServerSocketFactory = null;
  private String paramSslKeyStorePassword = null;
  private boolean paramDissociateOnNoSend = DEFAULT_DISSOCIATE_ON_NO_SEND;
  private boolean paramDissociateOnEveryStop =
    DEFAULT_DISSOCIATE_ON_EVERY_STOP;

  private boolean enabled = DEFAULT_ENABLED;
  private boolean running = false;

  private SocketFactory sockFact;
  private ServerSocket listenSock;
  private PeerIdentity myPeerId;
  private PeerAddress.Tcp myPeerAddr;

  private IdentityManager idMgr;
  private OneShot configShot = new OneShot();

  private FifoQueue rcvQueue;	     // PeerMessages received from channels
  private ReceiveThread rcvThread;
  private ListenThread listenThread;
  private RetryThread retryThread;
  // Synchronization lock for rcv thread, listen thread manipulations
  private Object threadLock = new Object();

  // Map holds channels and queue associated with each Peer
  Map<PeerIdentity,PeerData> peers = new HashMap<PeerIdentity,PeerData>();

  Comparator ROC = new RetryOrderComparator();
  TreeSet<PeerData> peersToRetry = new TreeSet<PeerData>(ROC);

  // Record of draining channels (no longer associated with peer) so stats
  // can find them
  Set<BlockingPeerChannel> drainingChannels = new HashSet();

  private Vector messageHandlers = new Vector(); // Vector is synchronized

  int nPrimary = 0;
  int nSecondary = 0;
  int maxPrimary = 0;
  int maxSecondary = 0;
  int maxDrainingChannels = 0;
  Object ctrLock = new Object();	// lock for above counters

  // Counts number of successful messages with N retries
  Bag retryHist = new TreeBag();
  // Counts number of discarded messages with N retries
  Bag retryErrHist = new TreeBag();

  ChannelStats globalStats = new ChannelStats();

  public BlockingStreamComm() {
    sockFact = null;
  }

  class PeerData {
    PeerIdentity pid;
    BlockingPeerChannel primary;
    BlockingPeerChannel secondary;
    Queue sendQueue = null;	// Non-null only when there are queued
				// messages for a peer that has no active
				// primary channel.
    PeerMessage earliestMsg;	// Needed until we have separate channel
				// for each poll
    long lastRetry = 0;
    long nextRetry = TimeBase.MAX;  // Time at which we should try again to
				    // connect and send held msgs.
    private int origCnt = 0;		// Number of connect attempts
    private int failCnt = 0;		// Number of connect failures
    private int acceptCnt = 0;		// Number of incoming connections
    
    int msgsSent = 0;
    int msgsDiscarded = 0;
    int msgsRcvd = 0;
    int lastSendRpt = 0;

    PeerData(PeerIdentity pid) {
      this.pid = pid;
    }
    
    PeerIdentity getPid() {
      return pid;
    }
    
    BlockingPeerChannel getPrimaryChannel() {
      return primary;
    }

    BlockingPeerChannel getSecondaryChannel() {
      return secondary;
    }

    int getSendQueueSize() {
      Queue q = sendQueue;
      return q == null ? 0 : q.size();
    }
    
    long getLastRetry() {
      return lastRetry;
    }
    
    long getNextRetry() {
      return nextRetry;
    }
    
    long getFirstExpiration() {
      if (sendQueue == null || sendQueue.isEmpty()) {
	return TimeBase.MAX;
      }
      PeerMessage msg = earliestMsg;
      if (msg == null) {
	return TimeBase.MAX;
      }
      return msg.getExpiration();
    }
    
    int getOrigCnt() {
      return origCnt;
    }

    int getFailCnt() {
      return failCnt;
    }

    int getAcceptCnt() {
      return acceptCnt;
    }
    
    int getMsgsSent() {
      return msgsSent;
    }
    
    int getMsgsDiscarded() {
      return msgsDiscarded;
    }
    
    int getMsgsRcvd() {
      return msgsRcvd;
    }
    
    void countPrimary(boolean incr) {
      synchronized (ctrLock) {
	if (incr) {
	  nPrimary++;
	  if (nPrimary > maxPrimary) maxPrimary = nPrimary;
	} else {
	  nPrimary--;
	}
      }
    }

    void countSecondary(boolean incr) {
      synchronized (ctrLock) {
	if (incr) {
	  nSecondary++;
	  if (nSecondary > maxSecondary) maxSecondary = nSecondary;
	} else {
	  nSecondary--;
	}
      }
    }

    synchronized void associateChannel(BlockingPeerChannel chan) {
      acceptCnt++;
      if (primary == null) {
	primary = chan;
	countPrimary(true);
	handOffQueuedMsgs(primary);
	if (log.isDebug2()) log.debug2("Associated " + chan);
      } else if (primary == chan) {
	log.warning("Redundant peer-channel association (" + chan + ")");
      } else {
	if (secondary == null) {
	  secondary = chan;		// normal secondary association
	  countSecondary(true);
	  if (log.isDebug2()) log.debug2("Associated secondary " + chan);
	} else if (secondary == chan) {
	  log.debug("Redundant secondary peer-channel association(" +
		    chan +")");
	} else {
	  // maybe should replace if new working and old not.  but old will
	  // eventually timeout and close anyway
	  log.warning("Conflicting peer-channel association(" + chan +
		      "), was " + primary);
	}
      }
    }

    // This may be called more than once by the same channel, from its
    // multiple worker threads.  Redundant calls must be harmless.
    synchronized void dissociateChannel(BlockingPeerChannel chan) {
      if (primary == chan) {
	globalStats.add(primary.getStats());
	primary = null;
	countPrimary(false);
	if (log.isDebug2()) log.debug2("Removed: " + chan);
      }
      if (secondary == chan) {
	globalStats.add(secondary.getStats());
	secondary = null;
	countSecondary(false);
	if (log.isDebug2()) log.debug2("Removed secondary: " + chan);
      }
      synchronized (drainingChannels) {
	if (chan.isState(BlockingPeerChannel.ChannelState.DRAIN_INPUT)
	    && chan.getPeer() != null) {
	  // If this channel is draining, remember it so can include in stats
	  if (log.isDebug2()) log.debug2("Add to draining: " + chan);
	  drainingChannels.add(chan);
	  maxDrainingChannels = Math.max(maxDrainingChannels,
					 drainingChannels.size());
	} else {
	  // else ensure it's gone
	  if (drainingChannels.remove(chan)) {
	    if (log.isDebug2()) log.debug2("Del from draining: " + chan);
	  }
	}
      }
    }

    synchronized void send(PeerMessage msg) throws IOException {
      if (sendQueue != null) {
	// If queue exists, we're already waiting for connection retry.
	if (primary != null) {
	  log.error("send: sendQueue and primary channel both exist: " +
		    primary);
	}	  
	enqueueHeld(msg, false );
	return;
      }
      // A closing channel might refuse the message (return false), in which
      // case it will have dissociated itself so try again with a new
      // channel.
      BlockingPeerChannel last = null;
      int rpt = 0;
      while (rpt++ <= 3) {
	lastSendRpt = rpt;
	BlockingPeerChannel chan = findOrMakeChannel();
	if (chan == null) {
	  break;
	}
	if (last == chan)
	  throw new IllegalStateException("Got same channel as last time: "
					  + chan);
	if (chan.send(msg)) {
	  return;
	}
	if (chan.isUnusedOriginatingChannel()) {
	  log.warning("Couldn't start channel " + chan);
	  break;
	}
	last = chan;
	if (paramDissociateOnNoSend) {
	  dissociateChannel(chan);
	}
      }
      log.error("Couldn't enqueue msg to channel after "
		+ rpt + " tries: " + msg);
      if (msg.isRequeueable()) {
	// This counts as a connect failure, as the queue was empty when we
	// entered
	failCnt++;
	// XXX Not counting this as a retry causes unpredictable test results
	enqueueHeld(msg, true);
      }
    }

    synchronized BlockingPeerChannel findOrMakeChannel() {
      if (primary != null) {
	return primary;
      }
      if (secondary != null) {
	// found secondary, no primary.  promote secondary to primary
	primary = secondary;
	secondary = null;
	countPrimary(true);
	countSecondary(false);
	log.debug2("Promoted " + primary);
	handOffQueuedMsgs(primary);
	return primary;
      }
      // new primary channel, if we have room
      if (nPrimary < paramMaxChannels) {
	try {
	  BlockingPeerChannel chan =
	    getSocketFactory().newPeerChannel(BlockingStreamComm.this, pid);
	  if (log.isDebug2()) log.debug2("Created " + chan);
	  try {
	    handOffQueuedMsgs(chan);
	    lastRetry = TimeBase.nowMs();
	    chan.startOriginate();
	    origCnt++;
	    primary = chan;
	    countPrimary(true);
	    return primary;
	  } catch (IOException e) {
	    log.warning("Can't start channel " + chan, e);
	    return null;
	  }
	} catch (IOException e) {
	  log.warning("Can't create channel " + pid, e);
	  return null;
	}
      }
      return null;
    }

    synchronized void enqueueHeld(PeerMessage msg, boolean isRetry) {
      if (sendQueue == null) {
	sendQueue = new FifoQueue();
      }
      if (log.isDebug3()) log.debug3("enqueuing held "+ msg);
      BlockingPeerChannel chan = primary;
      if (chan != null
	  && !chan.isState(BlockingPeerChannel.ChannelState.DISSOCIATING)) {
	log.error("retry: sendQueue and primary channel both exist: " +
		  primary);
      }	

      sendQueue.put(msg);
      if (isRetry) {
	msg.incrRetryCount();
      }
      long retry = calcNextRetry(msg, isRetry);
      if (retry < nextRetry) {
	synchronized (peersToRetry) {
	  peersToRetry.remove(this);
	  earliestMsg = msg;
	  nextRetry = retry;
	  if (log.isDebug3()) {
	    log.debug3("Retry " + pid + " at "
		       + Deadline.at(nextRetry).shortString());
	  }
	  peersToRetry.add(this);
	  if (this == peersToRetry.first()) {
	    retryThread.recalcNext();
	  }
	}
      }
    }

    synchronized void handOffQueuedMsgs(BlockingPeerChannel chan) {
      if (sendQueue != null) {
	if (log.isDebug2()) {
	  log.debug2("Handing off " + sendQueue.size() + " msgs to " + chan);
	}
	chan.enqueueMsgs(sendQueue);
	sendQueue = null;
	synchronized (peersToRetry) {
	  peersToRetry.remove(this);
	}
	nextRetry = TimeBase.MAX;
	earliestMsg = null;
      }
    }

    /**
     * If channel aborts with unsent messages, queue them to try again later.
     */
    synchronized void drainQueue(PeerData pdata,
				 Queue queue,
				 boolean shouldRetry) {
      // Don't cause trouble if shutting down (happens in unit tests).
      if (!running || queue == null || queue.isEmpty()) {
	return;
      }
      failCnt++;
      if (shouldRetry) {
	requeueUnsentMsgs(queue);
      } else {
	deleteMsgs(queue);
      }
    }

    void requeueUnsentMsgs(Queue queue) {
      PeerMessage msg;
      try {
	int requeued = 0;
	int deleted = 0;
	while ((msg = (PeerMessage)queue.get(Deadline.EXPIRED)) != null) {
	  if (msg.isRequeueable() && !msg.isExpired()
	      && msg.getRetryCount() < msg.getRetryMax()) {
	    enqueueHeld(msg, true);
	    requeued++;
	  } else {
	    countMessageErrRetries(msg);
	    msg.delete();
	    deleted++;
	  }
	}
	if (log.isDebug2()) {
	  log.debug2("Requeued " + requeued + ", deleted " + deleted);
	}
      } catch (InterruptedException e) {
	// can't happen (get doesn't wait)
      }
    }

    void deleteMsgs(Queue queue) {
      PeerMessage msg;
      try {
	while ((msg = (PeerMessage)queue.get(Deadline.EXPIRED)) != null) {
	  msg.delete();
	}
      } catch (InterruptedException e) {
	// can't happen (get doesn't wait)
      }
    }

    // Called by retry thread when we're the first item in peersToRetry
    synchronized boolean retryIfNeeded() {
      if (!isRetryNeeded()) {
	synchronized (peersToRetry) {
	  peersToRetry.remove(this);
	}
	return false;
      }
      if (primary != null) {
	return false;
      }
      if (TimeBase.nowMs() < getNextRetry()) {
	return false;
      }
      BlockingPeerChannel chan = findOrMakeChannel();
      if (chan != null) {
	return true;
      } else {
	log.error("retry: couldn't create channel " + pid);
	return false;
      }
    }

    boolean isRetryNeeded() {
      if (sendQueue == null) {
	return false;
      }
      if (sendQueue.isEmpty()) {
	log.error("Empty send queue " + pid);
	return false;
      }
      return true;
    }

    long calcNextRetry(PeerMessage msg, boolean isRetry) {
      long last = msg.getLastRetry();
      long target = TimeBase.MAX;
      if (msg.getExpiration() > 0) {
	target = msg.getExpiration() - paramRetryBeforeExpiration;
      }
      long intr = msg.getRetryInterval();
      if (intr > 0) {
	target = Math.min(target, last + intr);
      }
      log.debug3("last: " + last
		 + ", intr: " + intr
		 + ", target: " + target
		 + ", minPeer: " + paramMinPeerRetryInterval);

      long retry =
	Math.max(Math.min(target,
			  lastRetry + paramMaxPeerRetryInterval),
		 lastRetry + paramMinPeerRetryInterval);
      return retry;
    }

    synchronized void abortChannels() {
      if (primary != null) {
	primary.abortChannel();
      }
      if (secondary != null) {
	secondary.abortChannel();
      }
    }
    synchronized void waitChannelsDone(Deadline timeout) {
      if (primary != null) {
	primary.waitThreadsExited(timeout);
      }
      if (secondary != null) {
	secondary.waitThreadsExited(timeout);
      }
    }

    synchronized void checkHung() {
      if (primary != null) {
	primary.checkHung();
      }
      if (secondary != null) {
	secondary.checkHung();
      }
    }

    public String toString() {
      StringBuilder sb = new StringBuilder(50);
      sb.append("[CP: ");
      sb.append(pid);
      if (nextRetry != TimeBase.MAX) {
	sb.append(", ");
	sb.append(Deadline.restoreDeadlineAt(nextRetry).shortString());
      }
      sb.append("]");
      return sb.toString();
    }
  }

  /**
   * start the stream comm manager.
   */
  public void startService() {
    super.startService();
    LockssDaemon daemon = getDaemon();
    idMgr = daemon.getIdentityManager();
    resetConfig();
    try {
      myPeerId = getLocalPeerIdentity();
    } catch (Exception e) {
      log.critical("No V3 identity, not starting stream comm", e);
      enabled = false;
      return;
    }
    log.debug("Local V3 peer: " + myPeerId);
    PeerAddress pad = myPeerId.getPeerAddress();
    if (pad instanceof PeerAddress.Tcp) {
      myPeerAddr = (PeerAddress.Tcp)pad;
    } else {
      log.error("Disabling stream comm; no local TCP peer address: " + pad);
      enabled = false;
    }
    if (enabled) {
      start();
      StatusService statSvc = daemon.getStatusService();
      statSvc.registerStatusAccessor(getStatusAccessorName("SCommChans"),
				     new ChannelStatus());
      statSvc.registerStatusAccessor(getStatusAccessorName("SCommPeers"),
				     new PeerStatus());
    }
  }

  protected String getStatusAccessorName(String base) {
    return base;
  }

  /**
   * stop the stream comm manager
   * @see org.lockss.app.LockssManager#stopService()
   */
  public void stopService() {
    StatusService statSvc = getDaemon().getStatusService();
    statSvc.unregisterStatusAccessor(getStatusAccessorName("SCommChans"));
    statSvc.unregisterStatusAccessor(getStatusAccessorName("SCommPeers"));
    if (running) {
      stop();
    }
    super.stopService();
  }


  /**
   * Set communication parameters from configuration, once only.
   * This service currently cannot be reconfigured.
   * @param config the Configuration
   */
  public void setConfig(Configuration config,
			Configuration prevConfig,
			Configuration.Differences changedKeys) {
    if (getDaemon().isDaemonInited()) {
      // one-time only init
      if (configShot.once()) {
	configure(config, prevConfig, changedKeys);
      }
      // these params can be changed on the fly
      paramMinFileMessageSize = config.getInt(PARAM_MIN_FILE_MESSAGE_SIZE,
					      DEFAULT_MIN_FILE_MESSAGE_SIZE);
      paramMaxMessageSize = config.getLong(PARAM_MAX_MESSAGE_SIZE,
					   DEFAULT_MAX_MESSAGE_SIZE);
      paramIsBufferedSend = config.getBoolean(PARAM_IS_BUFFERED_SEND,
					      DEFAULT_IS_BUFFERED_SEND);
      paramIsTcpNodelay = config.getBoolean(PARAM_TCP_NODELAY,
					    DEFAULT_TCP_NODELAY);
      paramWaitExit = config.getTimeInterval(PARAM_WAIT_EXIT,
					     DEFAULT_WAIT_EXIT);

      if (changedKeys.contains(PREFIX)) {
	String paramDataDir = config.get(PARAM_DATA_DIR,
					 PlatformUtil.getSystemTempDir());
	File dir = new File(paramDataDir);
	if (FileUtil.ensureDirExists(dir)) {
	  if (!dir.equals(dataDir)) {
	    dataDir = dir;
	    log.debug2("Message data dir: " + dataDir);
	  }
	} else {
	  log.warning("No message data dir: " + dir);
	  dataDir = null;
	}

	paramMaxChannels = config.getInt(PARAM_MAX_CHANNELS,
					 DEFAULT_MAX_CHANNELS);
	paramConnectTimeout = config.getTimeInterval(PARAM_CONNECT_TIMEOUT,
						     DEFAULT_CONNECT_TIMEOUT);
	paramSoTimeout = config.getTimeInterval(PARAM_DATA_TIMEOUT,
						DEFAULT_DATA_TIMEOUT);
	paramSendWakeupTime = config.getTimeInterval(PARAM_SEND_WAKEUP_TIME,
						     DEFAULT_SEND_WAKEUP_TIME);

	paramChannelIdleTime =
	  config.getTimeInterval(PARAM_CHANNEL_IDLE_TIME,
				 DEFAULT_CHANNEL_IDLE_TIME);
	paramDrainInputTime = config.getTimeInterval(PARAM_DRAIN_INPUT_TIME,
						     DEFAULT_DRAIN_INPUT_TIME);
	paramDissociateOnNoSend =
	  config.getBoolean(PARAM_DISSOCIATE_ON_NO_SEND,
			    DEFAULT_DISSOCIATE_ON_NO_SEND);
	paramDissociateOnEveryStop =
	  config.getBoolean(PARAM_DISSOCIATE_ON_EVERY_STOP,
			    DEFAULT_DISSOCIATE_ON_EVERY_STOP);

	paramRetryBeforeExpiration = 
	  config.getTimeInterval(PARAM_RETRY_BEFORE_EXPIRATION,
				 DEFAULT_RETRY_BEFORE_EXPIRATION);

	paramMaxPeerRetryInterval =
	  config.getTimeInterval(PARAM_MAX_PEER_RETRY_INTERVAL,
				 DEFAULT_MAX_PEER_RETRY_INTERVAL);
	paramMinPeerRetryInterval =
	  config.getTimeInterval(PARAM_MIN_PEER_RETRY_INTERVAL,
				 DEFAULT_MIN_PEER_RETRY_INTERVAL);
	paramRetryDelay =
	  config.getTimeInterval(PARAM_RETRY_DELAY, DEFAULT_RETRY_DELAY);

	paramAbortOnUnknownOp = config.getBoolean(PARAM_ABORT_ON_UNKNOWN_OP,
						  DEFAULT_ABORT_ON_UNKNOWN_OP);
      }
    }
  }

  /** Internal config, so can invoke from test constructor  */
  void configure(Configuration config,
		 Configuration prevConfig,
		 Configuration.Differences changedKeys) {
    enabled = config.getBoolean(PARAM_ENABLED, DEFAULT_ENABLED);
    if (!enabled) {
      return;
    }
    paramMinPoolSize = config.getInt(PARAM_CHANNEL_THREAD_POOL_MIN,
				     DEFAULT_CHANNEL_THREAD_POOL_MIN);
    paramMaxPoolSize = config.getInt(PARAM_CHANNEL_THREAD_POOL_MAX,
				     DEFAULT_CHANNEL_THREAD_POOL_MAX);
    paramPoolKeepaliveTime =
      config.getTimeInterval(PARAM_CHANNEL_THREAD_POOL_KEEPALIVE,
			     DEFAULT_CHANNEL_THREAD_POOL_KEEPALIVE);
    if (changedKeys.contains(PARAM_USE_V3_OVER_SSL)) {
      paramUseV3OverSsl = config.getBoolean(PARAM_USE_V3_OVER_SSL,
					    DEFAULT_USE_V3_OVER_SSL);
      sockFact = null;
      // XXX shut down old listen socket, do exponential backoff
      // XXX on bind() to bring up new listen socket
      // XXX then move this to the "change on the fly" above
    }
    if (!paramUseV3OverSsl)
	return;
    // We're trying to use SSL
    if (changedKeys.contains(PARAM_USE_SSL_CLIENT_AUTH)) {
      paramSslClientAuth = config.getBoolean(PARAM_USE_SSL_CLIENT_AUTH,
					     DEFAULT_USE_SSL_CLIENT_AUTH);
      sockFact = null;
    }
    if (changedKeys.contains(PARAM_SSL_TEMP_KEYSTORE)) {
      paramSslTempKeystore = config.getBoolean(PARAM_SSL_TEMP_KEYSTORE,
					     DEFAULT_SSL_TEMP_KEYSTORE);
      sockFact = null;
    }
    if (sslServerSocketFactory != null && sslSocketFactory != null) {
      // already initialized
      return;
    }
    if (paramSslTempKeystore) {
      // We're using the temporary keystore
      paramSslKeyStore = System.getProperty("javax.net.ssl.keyStore", null);
      paramSslKeyStorePassword =
	System.getProperty("javax.net.ssl.keyStorePassword", null);
      log.debug("Using temporary keystore from " + paramSslKeyStore);
      // Now create the SSL socket factories from the context
      sslServerSocketFactory =
	(SSLServerSocketFactory)SSLServerSocketFactory.getDefault();
      sslSocketFactory =
	(SSLSocketFactory)SSLSocketFactory.getDefault();
      return;
    }
    // We're using the real keystore
    if (changedKeys.contains(PARAM_SSL_KEYSTORE)) {
	paramSslKeyStore = config.get(PARAM_SSL_KEYSTORE,
				      DEFAULT_SSL_KEYSTORE);
	// The password for the keystore is the machine's FQDN.
	paramSslKeyStorePassword = config.get("org.lockss.platform.fqdn", "");
	log.debug("Using permanent keystore from " + paramSslKeyStore);
	sockFact = null;
    }
    byte[] sslPrivateKeyPassword = null;
    if (changedKeys.contains(PARAM_SSL_PRIVATE_KEY_PASSWORD_FILE)) {
      paramSslPrivateKeyPasswordFile = config.get(PARAM_SSL_PRIVATE_KEY_PASSWORD_FILE,
						  DEFAULT_SSL_PRIVATE_KEY_PASSWORD_FILE);
      sockFact = null;
    }
    if (changedKeys.contains(PARAM_SSL_PROTOCOL)) {
      paramSslProtocol = config.get(PARAM_SSL_PROTOCOL,
				    DEFAULT_SSL_PROTOCOL);
      sockFact = null;
    }
    try {
      File keyStorePasswordFile = new File(paramSslPrivateKeyPasswordFile);
      if (keyStorePasswordFile.exists()) {
	FileInputStream fis = new FileInputStream(keyStorePasswordFile);
	sslPrivateKeyPassword = new byte[(int)keyStorePasswordFile.length()];
	if (fis.read(sslPrivateKeyPassword) != sslPrivateKeyPassword.length) {
	  throw new IOException("short read");
	}
	fis.close();
	FileOutputStream fos = new FileOutputStream(keyStorePasswordFile);
	byte[] junk = new byte[(int)keyStorePasswordFile.length()];
	for (int i = 0; i < junk.length; i++)
	  junk[i] = 0;
	fos.write(junk);
	fos.close();
	keyStorePasswordFile.delete();
      } else {
        log.debug("SSL password file " + paramSslPrivateKeyPasswordFile + " missing");
	return;
      }
    } catch (IOException ex) {
      log.error(paramSslPrivateKeyPasswordFile + " threw " + ex);
      return;
    }
    // We now have a password to decrypt the private key in the
    // SSL keystore.  Next create the keystore from the file.
    KeyStore keyStore = null;
    InputStream fis = null;
    try {
      keyStore = KeyStore.getInstance("JCEKS");
      fis = new FileInputStream(paramSslKeyStore);
      keyStore.load(fis, paramSslKeyStorePassword.toCharArray());
    } catch (KeyStoreException ex) {
      log.error("loading SSL key store threw " + ex);
      return;
    } catch (IOException ex) {
      log.error("loading SSL key store threw " + ex);
      return;
    } catch (NoSuchAlgorithmException ex) {
      log.error("loading SSL key store threw " + ex);
      return;
    } catch (CertificateException ex) {
      log.error("loading SSL key store threw " + ex);
      return;
    } finally {
      IOUtil.safeClose(fis);
    }
    {
      String temp = new String(sslPrivateKeyPassword);
      logKeyStore(keyStore, temp.toCharArray());
    }
    // Now create a KeyManager from the keystore using the password.
    KeyManager[] kma = null;
    try {
      KeyManagerFactory kmf =
	KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, new String(sslPrivateKeyPassword).toCharArray());
      kma = kmf.getKeyManagers();
    } catch (NoSuchAlgorithmException ex) {
      log.error("creating SSL key manager threw " + ex);
      return;
    } catch (KeyStoreException ex) {
      log.error("creating SSL key manager threw " + ex);
      return;
    } catch (UnrecoverableKeyException ex) {
      log.error("creating SSL key manager threw " + ex);
      return;
    }
    // Now create a TrustManager from the keystore using the password
    TrustManager[] tma = null;
    try {
      TrustManagerFactory tmf = 
	TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(keyStore);
      tma = tmf.getTrustManagers();
    } catch (NoSuchAlgorithmException ex) {
      log.error("creating SSL trust manager threw " + ex);
      return;
    } catch (KeyStoreException ex) {
      log.error("creating SSL trust manager threw " + ex);
      return;
    } finally {
      // Now forget the password
      for (int i = 0; i < sslPrivateKeyPassword.length; i++) {
	sslPrivateKeyPassword[i] = 0;
      }
      sslPrivateKeyPassword = null;
    }
    // Now create an SSLContext from the KeyManager
    SSLContext sslContext = null;
    try {
      sslContext = SSLContext.getInstance(paramSslProtocol);
      sslContext.init(kma, tma, null);
      // Now create the SSL socket factories from the context
      sslServerSocketFactory = sslContext.getServerSocketFactory();
      sslSocketFactory = sslContext.getSocketFactory();
    } catch (NoSuchAlgorithmException ex) {
      log.error("Creating SSL context threw " + ex);
      sslContext = null;
    } catch (KeyManagementException ex) {
      log.error("Creating SSL context threw " + ex);
      sslContext = null;
    }
  }

  // private debug output of keystore
  private void logKeyStore(KeyStore ks, char[] privateKeyPassWord) {
    log.debug3("start of key store");
    try {
      for (Enumeration en = ks.aliases(); en.hasMoreElements(); ) {
        String alias = (String) en.nextElement();
	log.debug3("Next alias " + alias);
        if (ks.isCertificateEntry(alias)) {
	  log.debug3("About to Certificate");
          java.security.cert.Certificate cert = ks.getCertificate(alias);
          if (cert == null) {
            log.debug3(alias + " null cert chain");
          } else {
            log.debug3("Cert for " + alias + " is " + cert.toString());
          }
        } else if (ks.isKeyEntry(alias)) {
	  log.debug3("About to getKey");
  	  Key privateKey = ks.getKey(alias, privateKeyPassWord);
  	  log.debug3(alias + " key " + privateKey.getAlgorithm() + "/" + privateKey.getFormat());
        } else {
  	  log.debug3(alias + " neither key nor cert");
        }
      }
      log.debug3("end of key store");
    } catch (Exception ex) {
      log.error("logKeyStore() threw " + ex);
    }
  }

  // overridable for testing
  protected PeerIdentity getLocalPeerIdentity() {
    return idMgr.getLocalPeerIdentity(Poll.V3_PROTOCOL);
  }

  PeerIdentity findPeerIdentity(String idkey)
      throws IdentityManager.MalformedIdentityKeyException {
    return idMgr.findPeerIdentity(idkey);
  }

  PeerIdentity getMyPeerId() {
    return myPeerId;
  }

  Queue getReceiveQueue() {
    return rcvQueue;
  }

  SocketFactory getSocketFactory() {
    if (sockFact == null)
      if (paramUseV3OverSsl) {
	sockFact = new SslSocketFactory();
      } else {
        sockFact = new NormalSocketFactory();
      }
    return sockFact;
  }

  long getConnectTimeout() {
    return paramConnectTimeout;
  }

  long getSoTimeout() {
    return paramSoTimeout;
  }

  long getSendWakeupTime() {
    return paramSendWakeupTime;
  }

  long getChannelIdleTime() {
    return paramChannelIdleTime;
  }

  long getDrainInputTime() {
    return paramDrainInputTime;
  }

  long getChannelHungTime() {
    return paramChannelIdleTime + 1000;
  }

  long getMaxMessageSize() {
    return paramMaxMessageSize;
  }

  boolean isBufferedSend() {
    return paramIsBufferedSend;
  }

  boolean isTcpNodelay() {
    return paramIsTcpNodelay;
  }

  boolean getAbortOnUnknownOp() {
    return paramAbortOnUnknownOp;
  }

  boolean getDissociateOnEveryStop() {
    return paramDissociateOnEveryStop;
  }

  /**
   * Called by channel when it learns its peer's identity
   */
  void associateChannelWithPeer(BlockingPeerChannel chan, PeerIdentity peer) {
    PeerData pdata = findPeerData(peer);
    pdata.associateChannel(chan);
  }

  /**
   * Called by channel when closing
   */
  void dissociateChannelFromPeer(BlockingPeerChannel chan, PeerIdentity peer,
				 Queue sendQueue) {
    // Do nothing if channel has no peer.  E.g., incoming connection, on
    // which no PeerId msg received.
    if (peer != null) {
      PeerData pdata = getPeerData(peer);
      // No action if no PeerData
      if (pdata != null) {
	pdata.dissociateChannel(chan);
	if (sendQueue != null) {
	  pdata.drainQueue(pdata, sendQueue, chan.shouldRetry());
	}
      }
    }
  }

  /**
   * Return an existing channel for the peer or create and start one
   */
  PeerData findPeerData(PeerIdentity pid) {
    if (pid == null) {
      log.error("findPeerData: null pid", new Throwable());
    }
    synchronized (peers) {
      PeerData pdata = peers.get(pid);
      if (pdata == null) {
	log.debug2("new PeerData("+pid+")");
	pdata = new PeerData(pid);
	peers.put(pid, pdata);
      }
      return pdata;
    }
  }

  /** Send a message to a peer.
   * @param msg the message to send
   * @param id the identity of the peer to which to send the message
   * @throws IOException if message couldn't be queued
   */
  public void sendTo(PeerMessage msg, PeerIdentity id, RateLimiter limiter)
      throws IOException {
    if (!running) throw new IllegalStateException("SComm not running");
    if (msg == null) throw new NullPointerException("Null message");
    if (id == null) throw new NullPointerException("Null peer");
    if (log.isDebug3()) log.debug3("sending "+ msg +" to "+ id);
    if (limiter == null || limiter.isEventOk()) {
      sendToChannel(msg, id);
      if (limiter != null) limiter.event();
    } else {
      log.debug2("Pkt rate limited");
    }
  }

  private void sendToChannel(PeerMessage msg, PeerIdentity id)
      throws IOException {
    PeerData pdata = findPeerData(id);
    pdata.send(msg);
  }

  BlockingPeerChannel findOrMakeChannel(PeerIdentity id) throws IOException {
    PeerData pdata = findPeerData(id);
    return pdata.findOrMakeChannel();
  }

  void countMessageRetries(PeerMessage msg) {
    synchronized (retryHist) {
      retryHist.add(msg.getRetryCount());
    }
  }

  void countMessageErrRetries(PeerMessage msg) {
    synchronized (retryErrHist) {
      retryErrHist.add(msg.getRetryCount());
    }
  }

  void start() {
    pool = new PooledExecutor(paramMaxPoolSize);
    pool.setMinimumPoolSize(paramMinPoolSize);
    pool.setKeepAliveTime(paramPoolKeepaliveTime);
    log.debug2("Channel thread pool min, max: " +
	      pool.getMinimumPoolSize() + ", " + pool.getMaximumPoolSize());
    pool.abortWhenBlocked();

    rcvQueue = new FifoQueue();
    try {
      int port = myPeerAddr.getPort();
      if (!getDaemon().getResourceManager().reserveTcpPort(port,
							   SERVER_NAME)) {
	throw new IOException("TCP port " + port + " unavailable");
      }
      log.debug("Listening on port " + port);
      listenSock =
	getSocketFactory().newServerSocket(port, paramBacklog);
    } catch (IOException e) {
      log.critical("Can't create listen socket", e);
      return;
    }
    ensureQRunner();
    ensureRetryThread();
    ensureListener();
    running = true;
  }

  // stop all threads and channels
  void stop() {
    running = false;
    Deadline timeout = null;
    synchronized (threadLock) {
      if (paramWaitExit > 0) {
	timeout = Deadline.in(paramWaitExit);
      }
      stopThread(retryThread, timeout);
      retryThread = null;
      stopThread(listenThread, timeout);
      listenThread = null;
      stopThread(rcvThread, timeout);
      rcvThread = null;
    }
    log.debug2("Shutting down pool");
    if (pool != null) {
      pool.shutdownNow();
    }
    log.debug2("pool shut down ");
  }

  List<PeerData> getAllPeerData() {
    synchronized (peers) {
      return new ArrayList<PeerData>(peers.values());
    }
  }

  PeerData getPeerData(PeerIdentity pid) {
    synchronized (peers) {
      return peers.get(pid);
    }
  }

  // stop all channels in channel map
  void stopChannels(Map map, Deadline timeout) {
    log.debug2("Stopping channels");
    List<PeerData> lst = getAllPeerData();
    for (PeerData pdata : lst) {
      pdata.abortChannels();
    }
    // Wait until the threads have exited before proceeding.  Useful in
    // testing to keep debug output straight.

    // Any channels that had already dissociated themselves are not waited
    // for.  It would take extra bookkeeping to handle those and they don't
    // seem to cause nearly as much trouble.

    if (timeout != null) {
      for (PeerData pdata : lst) {
	pdata.waitChannelsDone(timeout);
      }
    }
  }

  // poke channels that might have hung sender
  void checkHungChannels() {
    log.debug3("Doing hung check");
    for (PeerData pdata : getAllPeerData()) {
      pdata.checkHung();
    }
  }

  /**
   * Execute the runnable in a pool thread
   * @param run the Runnable to be run
   * @throws RuntimeException if no pool thread is available
   */
  void execute(Runnable run) throws InterruptedException {
    if (run == null)
      log.warning("Executing null", new Throwable());
    pool.execute(run);
  }

  // process a socket returned by accept()
  // overridable for testing
  void processIncomingConnection(Socket sock) throws IOException {
    if (sock.isClosed()) {
      throw new SocketException("socket closed during handshake");
    }
    log.debug2("Accepted connection from " +
	       new IPAddr(sock.getInetAddress()));
    BlockingPeerChannel chan = getSocketFactory().newPeerChannel(this, sock);
    chan.startIncoming();
  }

  private void processReceivedPacket(PeerMessage msg) {
    log.debug2("Received " + msg);
    try {
      runHandlers(msg);
    } catch (ProtocolException e) {
      log.warning("Cannot process incoming packet", e);
    }
  }

  protected void runHandler(MessageHandler handler,
			    PeerMessage msg) {
    try {
      handler.handleMessage(msg);
    } catch (Exception e) {
      log.error("callback threw", e);
    }
  }

  private void runHandlers(PeerMessage msg)
      throws ProtocolException {
    try {
      int proto = msg.getProtocol();
      MessageHandler handler;
      if (proto >= 0 && proto < messageHandlers.size() &&
	  (handler = (MessageHandler)messageHandlers.get(proto)) != null) {
	runHandler(handler, msg);
      } else {
	log.warning("Received message with unregistered protocol: " + proto);
      }
    } catch (RuntimeException e) {
      log.warning("Unexpected error in runHandlers", e);
      throw new ProtocolException(e.toString());
    }
  }

  /**
   * Register a {@link LcapStreamComm.MessageHandler}, which will be called
   * whenever a message is received.
   * @param protocol an int representing the protocol
   * @param handler MessageHandler to add
   */
  public void registerMessageHandler(int protocol, MessageHandler handler) {
    synchronized (messageHandlers) {
      if (protocol >= messageHandlers.size()) {
	messageHandlers.setSize(protocol + 1);
      }
      if (messageHandlers.get(protocol) != null) {
	throw
	  new RuntimeException("Protocol " + protocol + " already registered");
      }
      messageHandlers.set(protocol, handler);
    }
  }

  /**
   * Unregister a {@link LcapStreamComm.MessageHandler}.
   * @param protocol an int representing the protocol
   */
  public void unregisterMessageHandler(int protocol) {
    if (protocol < messageHandlers.size()) {
      messageHandlers.set(protocol, null);
    }
  }

  // PeerMessage.Factory implementation

  public PeerMessage newPeerMessage() {
    return new MemoryPeerMessage();
  }

  public PeerMessage newPeerMessage(long estSize) {
    if (estSize < 0) {
      return newPeerMessage();
    } else if (estSize > 0 &&
	       dataDir != null &&
	       estSize >= paramMinFileMessageSize) {
      return new FilePeerMessage(dataDir);
    } else {
      return new MemoryPeerMessage();
    }
  }

  protected void handShake(SSLSocket s) {
    SSLSession session = s.getSession();
    try {
      java.security.cert.Certificate[] certs = session.getPeerCertificates();
      log.debug(session.getPeerHost() + " via " + session.getProtocol() + " verified");
    } catch (SSLPeerUnverifiedException ex) {
      log.error(s.getInetAddress() + " not verified");
      try {
        s.close();
      } catch (IOException ex2) {
	log.error("Socket close threw " + ex2);
      }
    }
  }

  /** Sort retry list by time of next retry.  Ensure never equal */
  static class RetryOrderComparator implements Comparator {
    public int compare(Object o1, Object o2) {
      if (o1 == o2) {
	return 0;
      }
      PeerData pd1 = (PeerData)o1;
      PeerData pd2 = (PeerData)o2;
      long r1 = pd1.getNextRetry();
      long r2 = pd2.getNextRetry();
      int res = (r2 > r1 ? -1
		 : (r2 < r1 ? 1
		    : (System.identityHashCode(pd1)
		       - System.identityHashCode(pd2))));
      return res;
    }
  }

  void ensureQRunner() {
    synchronized (threadLock) {
      if (rcvThread == null) {
	log.info("Starting receive thread");
	rcvThread = new ReceiveThread("SCommRcv: " +
				      myPeerId.getIdString());
	rcvThread.start();
	rcvThread.waitRunning();
      }
    }
  }

  void ensureListener() {
    synchronized (threadLock) {
      if (listenThread == null) {
	log.info("Starting listen thread");
	listenThread = new ListenThread("SCommListen: " +
					myPeerId.getIdString());
	listenThread.start();
	listenThread.waitRunning();
      }
    }
  }

  void ensureRetryThread() {
    synchronized (threadLock) {
      if (retryThread == null) {
	log.info("Starting retry thread");
	retryThread = new RetryThread("SCommRetry: " +
				      myPeerId.getIdString());
	retryThread.start();
	retryThread.waitRunning();
      }
    }
  }

  void stopThread(CommThread th, Deadline timeout) {
    if (th != null) {
      log.info("Stopping " + th.getName());
      th.stopCommThread();
      if (timeout != null) {
	th.waitExited(timeout);
      }
    }
  }

  abstract class CommThread extends LockssThread {
    abstract void stopCommThread();

    CommThread(String name) {
      super(name);
    }
  }

  // Receive thread
  private class ReceiveThread extends CommThread {
    private volatile boolean goOn = true;
    private Deadline timeout = Deadline.in(getChannelHungTime());

    ReceiveThread(String name) {
      super(name);
    }

    public void lockssRun() {
      setPriority(PRIORITY_PARAM_SCOMM, PRIORITY_DEFAULT_SCOMM);
      triggerWDogOnExit(true);
      startWDog(WDOG_PARAM_SCOMM, WDOG_DEFAULT_SCOMM);
      nowRunning();

      while (goOn) {
	pokeWDog();
	try {
	  synchronized (timeout) {
	    if (goOn) {
	      timeout.expireIn(getChannelHungTime());
	    }
	  }
	  if (log.isDebug3()) log.debug3("rcvQueue.get(" + timeout + ")");
	  Object qObj = rcvQueue.get(timeout);
	  if (qObj != null) {
	    if (qObj instanceof PeerMessage) {
	      if (log.isDebug3()) log.debug3("Rcvd " + qObj);
	      processReceivedPacket((PeerMessage)qObj);
	    } else {
	      log.warning("Non-PeerMessage on rcv queue" + qObj);
	    }
	  }
	  if (TimeBase.msSince(lastHungCheckTime) >
	      getChannelHungTime()) {
	    checkHungChannels();
	    lastHungCheckTime = TimeBase.nowMs();
	  }
	} catch (InterruptedException e) {
	  // just wake up and check for exit
	} finally {
	}
      }
      rcvThread = null;
    }

    void stopCommThread() {
      synchronized (timeout) {
	stopWDog();
	triggerWDogOnExit(false);
	goOn = false;
	timeout.expire();
      }
    }
  }

  // Listen thread
  private class ListenThread extends CommThread {
    private volatile boolean goOn = true;

    private ListenThread(String name) {
      super(name);
    }

    public void lockssRun() {
      setPriority(PRIORITY_PARAM_SLISTEN, PRIORITY_DEFAULT_SLISTEN);
      triggerWDogOnExit(true);
//       startWDog(WDOG_PARAM_SLISTEN, WDOG_DEFAULT_SLISTEN);
      nowRunning();

      while (goOn) {
// 	pokeWDog();
	log.debug3("accept()");
	try {
	  Socket sock = listenSock.accept();
	  if (!goOn) {
	    break;
	  }
	  if (sock instanceof SSLSocket && paramSslClientAuth) {
	    // Ensure handshake is complete before doing anything else
	    handShake((SSLSocket)sock);
	  }
	  processIncomingConnection(sock);
	} catch (SocketException e) {
	  if (goOn) {
	    log.warning("Listener", e);
	  }
	} catch (Exception e) {
	  if (listenSock instanceof SSLServerSocket) {
	    log.debug("SSL Listener ", e);
	  } else {
	    log.warning("Listener", e);
          }
	}
      }
      listenThread = null;
    }

    void stopCommThread() {
      stopWDog();
      triggerWDogOnExit(false);
      goOn = false;
      IOUtil.safeClose(listenSock);
      this.interrupt();
    }
  }

  // Outside thread so stat table can find it
  // Must initialize (to mutable Deadline) in case recalcNext called before
  // thread runs.
  private volatile Deadline retryThreadNextRetry = Deadline.at(TimeBase.MAX);

  // Retry thread
  private class RetryThread extends CommThread {
    private volatile boolean goOn = true;
    private long soonest = 0;

    RetryThread(String name) {
      super(name);
    }

    public void lockssRun() {
      setPriority(PRIORITY_PARAM_RETRY, PRIORITY_DEFAULT_RETRY);
      triggerWDogOnExit(true);
      startWDog(WDOG_PARAM_RETRY, WDOG_DEFAULT_RETRY);
      nowRunning();

      outer:
      while (goOn) {
	pokeWDog();

	do {
	  retryThreadNextRetry = getNextRetry();
	  log.debug2("nextRetry: " + retryThreadNextRetry.shortString());
	  try {
	    retryThreadNextRetry.sleep();
	  } catch (InterruptedException e) {
	    // just wakeup and check for work
	  }
	  if (!goOn) {
	    break outer;
	  }
	} while (TimeBase.nowMs() < soonest);
	PeerData pdata = firstPeerToRetry();
	if (pdata != null && pdata.retryIfNeeded()) {
	  soonest = TimeBase.nowMs() + paramRetryDelay;
	} else {
	  soonest = 0;
	}
      }
      retryThread = null;
    }

    Deadline getNextRetry() {
      synchronized (peersToRetry) {
	PeerData pdata = firstPeerToRetry();
	if (log.isDebug3()) log.debug3("firstPeerToRetry: " + pdata);
	if (pdata != null) {
	  log.debug3("pdata.getNextRetry(): " + pdata.getNextRetry());
	  return Deadline.at(Math.max(soonest, pdata.getNextRetry()));
	} else {
	  return Deadline.at(TimeBase.MAX);
	}
      }
    }

    PeerData firstPeerToRetry() {
      synchronized (peersToRetry) {
	if (peersToRetry.isEmpty()) {
	  return null;
	}
	PeerData pdata = peersToRetry.first();
	if (log.isDebug2()) {
	  log.debug2("First peer to retry: " + pdata.getPid());
	}
	return pdata;
      }
    }

    void recalcNext() {
      retryThreadNextRetry.expire();
    }

    void stopCommThread() {
      synchronized (retryThreadNextRetry) {
	stopWDog();
	triggerWDogOnExit(false);
	goOn = false;
	retryThreadNextRetry.expire();
      }
    }

    @Override
    protected void threadHung() {
      Deadline next = getNextRetry();
      if (next.expired()) {
	super.threadHung();
      } else {
	pokeWDog();
      }
    }
  }

  /** SocketFactory interface allows test code to use instrumented or mock
      sockets and peer channels */
  interface SocketFactory {
    ServerSocket newServerSocket(int port, int backlog) throws IOException;

    Socket newSocket(IPAddr addr, int port) throws IOException;

    BlockingPeerChannel newPeerChannel(BlockingStreamComm comm,
				       Socket sock)
	throws IOException;

    BlockingPeerChannel newPeerChannel(BlockingStreamComm comm,
				       PeerIdentity peer)
	throws IOException;
  }

  /** Normal socket factory creates real TCP Sockets */
  class NormalSocketFactory implements SocketFactory {
    public ServerSocket newServerSocket(int port, int backlog)
	throws IOException {
      return new ServerSocket(port, backlog);
    }

    public Socket newSocket(IPAddr addr, int port) throws IOException {
      return new Socket(addr.getInetAddr(), port);
    }

    public BlockingPeerChannel newPeerChannel(BlockingStreamComm comm,
					      Socket sock)
	throws IOException {
      return new BlockingPeerChannel(comm, sock);
    }

    public BlockingPeerChannel newPeerChannel(BlockingStreamComm comm,
					      PeerIdentity peer)
	throws IOException {
      return new BlockingPeerChannel(comm, peer);
    }
  }

  /** SSL socket factory */
  class SslSocketFactory implements SocketFactory {
    public ServerSocket newServerSocket(int port, int backlog)
      throws IOException {
      if (sslServerSocketFactory == null) {
	throw new IOException("no SSL server socket factory");
      }
      SSLServerSocket s = (SSLServerSocket)
	sslServerSocketFactory.createServerSocket(port, backlog);
      s.setNeedClientAuth(paramSslClientAuth);
      log.debug("New SSL server socket: " + port + " backlog " + backlog +
		" clientAuth " + paramSslClientAuth);
      String cs[] = s.getEnabledCipherSuites();
      for (int i = 0; i < cs.length; i++) {
	log.debug2(cs[i] + " enabled cipher suite");
      }
      cs = s.getSupportedCipherSuites();
      for (int i = 0; i < cs.length; i++) {
	log.debug2(cs[i] + " supported cipher suite");
      }
      cs = s.getEnabledProtocols();
      for (int i = 0; i < cs.length; i++) {
	log.debug2(cs[i] + " enabled protocol");
      }
      cs = s.getSupportedProtocols();
      for (int i = 0; i < cs.length; i++) {
	log.debug2(cs[i] + " supported protocol");
      }
      log.debug2("enable session creation " + s.getEnableSessionCreation());
      return s;
    }

    public Socket newSocket(IPAddr addr, int port) throws IOException {
      if (sslSocketFactory == null) {
	throw new IOException("no SSL client socket factory");
      }
      SSLSocket s = (SSLSocket)
	  sslSocketFactory.createSocket(addr.getInetAddr(), port);
      log.debug2("New SSL client socket: " + port + "@" + addr.toString());
      if (paramSslClientAuth) {
	handShake(s);
      }
      return s;
    }

    public BlockingPeerChannel newPeerChannel(BlockingStreamComm comm,
					      Socket sock)
	throws IOException {
      return new BlockingPeerChannel(comm, sock);
    }

    public BlockingPeerChannel newPeerChannel(BlockingStreamComm comm,
					      PeerIdentity peer)
	throws IOException {
      return new BlockingPeerChannel(comm, peer);
    }
  }

  private static final List chanStatusColDescs =
    ListUtil.list(
		  new ColumnDescriptor("Peer", "Peer",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("State", "State",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("Flags", "Flags",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("SendQ", "SendQ",
				       ColumnDescriptor.TYPE_INT),
		  new ColumnDescriptor("Sent", "Msgs Sent",
				       ColumnDescriptor.TYPE_INT),
		  new ColumnDescriptor("Rcvd", "Msgs Rcvd",
				       ColumnDescriptor.TYPE_INT),
		  new ColumnDescriptor("SentBytes", "Bytes Sent",
				       ColumnDescriptor.TYPE_INT),
		  new ColumnDescriptor("RcvdBytes", "Bytes Rcvd",
				       ColumnDescriptor.TYPE_INT),
		  new ColumnDescriptor("LastSend", "LastSend",
				       ColumnDescriptor.TYPE_TIME_INTERVAL),
		  new ColumnDescriptor("LastRcv", "LastRcv",
				       ColumnDescriptor.TYPE_TIME_INTERVAL),
		  new ColumnDescriptor("PrevState", "PrevState",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("PrevStateChange", "Change",
				       ColumnDescriptor.TYPE_TIME_INTERVAL)
		  );

  private class ChannelStatus implements StatusAccessor {
    long start;

    public String getDisplayName() {
      return "Comm Channels";
    }

    public boolean requiresKey() {
      return false;
    }

    public void populateTable(StatusTable table) {
      String key = table.getKey();
      ChannelStats cumulative = new ChannelStats();
      table.setColumnDescriptors(chanStatusColDescs);
      table.setRows(getRows(key, cumulative));
      cumulative.add(globalStats);
      table.setSummaryInfo(getSummaryInfo(key, cumulative));
    }

    private List getSummaryInfo(String key, ChannelStats stats) {
      List res = new ArrayList();

      StringBuilder sb = new StringBuilder();
      if (paramUseV3OverSsl) {
	sb.append(paramSslProtocol);
	if (paramSslClientAuth) {
	  sb.append(", Client Auth");
	}
      } else {
	sb.append("No");
      }
      res.add(new StatusTable.SummaryInfo("SSL",
					  ColumnDescriptor.TYPE_STRING,
					  sb.toString()));
      res.add(new StatusTable.SummaryInfo("Channels",
					  ColumnDescriptor.TYPE_STRING,
					  nPrimary + "/"
					  + paramMaxChannels + ", "
					  + maxPrimary + " max"));
      res.add(new StatusTable.SummaryInfo("RcvChannels",
					  ColumnDescriptor.TYPE_STRING,
					  nSecondary + ", "
					  + maxSecondary +" max"));
      res.add(new StatusTable.SummaryInfo("Draining",
					  ColumnDescriptor.TYPE_STRING,
					  drainingChannels.size() + ", "
					  + maxDrainingChannels + " max"));
      ChannelStats.Count count = stats.getInCount();
      res.add(new StatusTable.SummaryInfo("Msgs Sent",
					  ColumnDescriptor.TYPE_INT,
					  count.getMsgs()));
      res.add(new StatusTable.SummaryInfo("Bytes Sent",
					  ColumnDescriptor.TYPE_INT,
					  count.getBytes()));
      count = stats.getOutCount();
      res.add(new StatusTable.SummaryInfo("Msgs Rcvd",
					  ColumnDescriptor.TYPE_INT,
					  count.getMsgs()));
      res.add(new StatusTable.SummaryInfo("Bytes Rcvd",
					  ColumnDescriptor.TYPE_INT,
					  count.getBytes()));
      return res;
    }

    private List getRows(String key, ChannelStats cumulative) {
      List table = new ArrayList();
      for (PeerData pdata : getAllPeerData()) {
	BlockingPeerChannel primary = pdata.getPrimaryChannel();
	if (primary != null) {
	  table.add(makeRow(primary, "", cumulative));
	}
	BlockingPeerChannel secondary = pdata.getSecondaryChannel();
	if (secondary != null) {
	  table.add(makeRow(secondary, "", cumulative));
	}
      }
      synchronized (drainingChannels) {
	for (BlockingPeerChannel chan : drainingChannels) {
	  table.add(makeRow(chan, "D", cumulative));
	}
      }
      return table;
    }

    private Map makeRow(BlockingPeerChannel chan,
			String flags, ChannelStats cumulative) {
      PeerIdentity pid = chan.getPeer();
      Map row = new HashMap();
      // Draining channels can sometimes have null peer
      row.put("Peer", (pid == null) ? "???" : pid.getIdString());
      row.put("State", chan.getState());
      row.put("SendQ", chan.getSendQueueSize());
      ChannelStats stats = chan.getStats();
      cumulative.add(stats);
      ChannelStats.Count count = stats.getInCount();
      row.put("Sent", count.getMsgs());
      row.put("SentBytes", count.getBytes());
      count = stats.getOutCount();
      row.put("Rcvd", count.getMsgs());
      row.put("RcvdBytes", count.getBytes());
      StringBuilder sb = new StringBuilder(flags);
      if (chan.isOriginate()) sb.append("O");
      if (chan.hasConnecter()) sb.append("C");
      if (chan.hasReader()) sb.append("R");
      if (chan.hasWriter()) sb.append("W");
      row.put("Flags", sb.toString());
      row.put("LastSend", lastTime(chan.getLastSendTime()));
      row.put("LastRcv", lastTime(chan.getLastRcvTime()));
      if (chan.getPrevState() != BlockingPeerChannel.ChannelState.NONE) {
	row.put("PrevState", chan.getPrevState());
	row.put("PrevStateChange", lastTime(chan.getLastStateChange()));
      }
      return row;
    }

    Long lastTime(long time) {
      if (time <= 0) return null;
      return TimeBase.msSince(time);
    }
  }

  private static final List peerStatusColDescs =
    ListUtil.list(
		  new ColumnDescriptor("Peer", "Peer",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("Orig", "Orig",
				       ColumnDescriptor.TYPE_INT),
		  new ColumnDescriptor("Fail", "Fail",
				       ColumnDescriptor.TYPE_INT),
		  new ColumnDescriptor("Accept", "Accept",
				       ColumnDescriptor.TYPE_INT),
		  new ColumnDescriptor("Chan", "Chan",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("SendQ", "Send Q",
				       ColumnDescriptor.TYPE_INT),
		  new ColumnDescriptor("LastRetry", "Last Attempt",
				       ColumnDescriptor.TYPE_DATE),
		  new ColumnDescriptor("NextRetry", "Next Retry",
				       ColumnDescriptor.TYPE_DATE)
		  );

  private static final List peerStatusSortRules =
    ListUtil.list(new StatusTable.SortRule("Peer", true));


  private class PeerStatus implements StatusAccessor {
    long start;

    public String getDisplayName() {
      return "Comm Peer Data";
    }

    public boolean requiresKey() {
      return false;
    }

    public void populateTable(StatusTable table) {
      String key = table.getKey();
      table.setColumnDescriptors(peerStatusColDescs);
      table.setDefaultSortRules(peerStatusSortRules);
      table.setRows(getRows(key));
      table.setSummaryInfo(getSummaryInfo(key));
    }

    private List getRows(String key) {
      List table = new ArrayList();
      for (PeerData pdata : getAllPeerData()) {
	table.add(makeRow(pdata));
      }
      return table;
    }

    private Map makeRow(PeerData pdata) {
      PeerIdentity pid = pdata.getPid();
      Map row = new HashMap();
      row.put("Peer", (pid == null) ? "???" : pid.getIdString());
      row.put("Orig", pdata.getOrigCnt());
      row.put("Fail", pdata.getFailCnt());
      row.put("Accept", pdata.getAcceptCnt());
      StringBuilder sb = new StringBuilder(2);
      if (pdata.getPrimaryChannel() != null) {
	sb.append("P");
      }
      if (pdata.getSecondaryChannel() != null) {
	sb.append("S");
      }
      if (sb.length() != 0) {
	row.put("Chan", sb.toString());
      }
      int pq = pdata.getSendQueueSize();
      if (pq != 0) {
	row.put("SendQ", pq);
      } else {
	BlockingPeerChannel chan = pdata.getPrimaryChannel();
	if (chan != null) {
	  row.put("SendQ", chan.getSendQueueSize());
	}
      }
      row.put("LastRetry", pdata.getLastRetry());
      if (pdata.getNextRetry() != TimeBase.MAX) {
	row.put("NextRetry", pdata.getNextRetry());
      }
      return row;
    }

    private String histString(Bag hist) {
      synchronized (hist) {
	List lst = new ArrayList();
	for (Integer cnt : ((Set<Integer>)hist.uniqueSet())) {
	  StringBuilder sb = new StringBuilder();
	  sb.append("[");
	  sb.append(cnt);
	  sb.append(",");
	  sb.append(hist.getCount(cnt));
	  sb.append("]");
	  lst.add(sb.toString());
	}
	return StringUtil.separatedString(lst, ",");
      }
    }

    private List getSummaryInfo(String key) {
      List res = new ArrayList();
      res.add(new StatusTable.SummaryInfo("Msg Ok Retries",
					  ColumnDescriptor.TYPE_STRING,
					  histString(retryHist)));
      res.add(new StatusTable.SummaryInfo("Msg Err Retries",
					  ColumnDescriptor.TYPE_STRING,
					  histString(retryErrHist)));
      res.add(new StatusTable.SummaryInfo("Waiting Retry",
					  ColumnDescriptor.TYPE_INT,
					  peersToRetry.size()));
      if (peersToRetry.size() != 0) {
	res.add(new StatusTable.SummaryInfo("Next Retry",
					    ColumnDescriptor.TYPE_DATE,
					    retryThreadNextRetry));
      }
      return res;
    }
  }

}
