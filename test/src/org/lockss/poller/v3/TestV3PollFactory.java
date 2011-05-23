package org.lockss.poller.v3;

import java.util.*;
import java.io.*;

import org.lockss.config.ConfigManager;
import org.lockss.daemon.*;
import org.lockss.app.*;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.repository.RepositoryManager;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.poller.*;
import org.lockss.protocol.*;
import org.lockss.protocol.IdentityManager.IdentityAgreement;
import org.lockss.protocol.V3LcapMessage.PollNak;
import org.lockss.plugin.*;
import org.lockss.protocol.*;
import org.lockss.state.*;
import org.lockss.hasher.*;

public class TestV3PollFactory extends LockssTestCase {
  
  private MockLockssDaemon theDaemon;
  private ArchivalUnit testAu;
  private PollManager pollManager;
  private HashService hashService;
  private MyIdentityManager idmgr;
  private MockAuState aus;
  private File tempDir;
  private String tempDirPath;
  private String pluginVer;
  private MockPollSpec ps;
  private V3LcapMessage testMsg;
  private MyV3PollFactory thePollFactory;
  private PeerIdentity testId;
  private static Logger logger = Logger.getLogger("TestV3PollFactory");

  public void setUp() throws Exception {
    super.setUp();

    TimeBase.setSimulated();
    pluginVer = "2";
    
    theDaemon = getMockLockssDaemon();
    idmgr = new MyIdentityManager();
    theDaemon.setIdentityManager(idmgr);
    pollManager = theDaemon.getPollManager();
    hashService = theDaemon.getHashService();

    theDaemon.getPluginManager();
    tempDir = getTempDir();
    tempDirPath = tempDir.getAbsolutePath();
    String tempDirURI = RepositoryManager.LOCAL_REPO_PROTOCOL + tempDirPath;

    Properties p = new Properties();
    p.setProperty(IdentityManager.PARAM_IDDB_DIR, tempDirPath + "iddb");
    p.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirURI);
    p.setProperty(IdentityManager.PARAM_LOCAL_IP, "127.0.0.1");
    p.setProperty(ConfigManager.PARAM_NEW_SCHEDULER, "true");
    p.setProperty(V3Poller.PARAM_QUORUM, "3");
    p.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    p.setProperty(V3Poller.PARAM_STATE_PATH, tempDirPath);
    p.setProperty(IdentityManager.PARAM_LOCAL_V3_IDENTITY, "TCP:[127.0.0.1]:9729");
    ConfigurationUtil.setCurrentConfigFromProps(p);

    idmgr.initService(theDaemon);
    idmgr.startService();
    theDaemon.getSchedService().startService();
    hashService.startService();

    testAu = setupAu();
    aus = new MockAuState(testAu);
    aus.setLastCrawlTime(100);
    MockNodeManager nm = new MockNodeManager();
    nm.setAuState(aus);
    theDaemon.setNodeManager(nm, testAu);
    MockLockssRepository lr =
      new MockLockssRepository(tempDirPath, testAu);
    theDaemon.setLockssRepository(lr, testAu);
    MockHistoryRepository hr =
      new MockHistoryRepository();
    hr.setIdentityManager(idmgr);
    hr.setFileName(tempDirPath + File.separator + "dpis");
    hr.setRepositoryNode(new MockRepositoryNode(urls[0],
						tempDirPath));
    theDaemon.setHistoryRepository(hr, testAu);
    theDaemon.getActivityRegulator(testAu).startService();
    pollManager.startService();

    testId = idmgr.findPeerIdentity("TCP:[127.0.0.1]:9000");
    ps = new MockPollSpec(testAu.getAuCachedUrlSet(), null, null,
                          Poll.V3_POLL);
    testMsg = makePollMsg();
    thePollFactory = new MyV3PollFactory(pollManager);
  }
  
  private String[] urls =
  {
   "http://www.example.com/",
   "http://www.example.com/index.html"
  };
  
  public V3LcapMessage makePollMsg() {
    return makePollMsg(V3LcapMessage.MSG_POLL);
  }

  public V3LcapMessage makePollMsg(int opcode) {
    long msgDeadline = TimeBase.nowMs() + 500000;
    long voteDuration = 1000;
    V3LcapMessage msg =
      new V3LcapMessage(testAu.getAuId(), "key", "3",
                        ByteArray.makeRandomBytes(20),
                        ByteArray.makeRandomBytes(20),
                        opcode,
                        10000, testId, tempDir, theDaemon);
    msg.setVoteDuration(voteDuration);
    msg.setEffortProof(ByteArray.makeRandomBytes(20));
    return msg;
  }

  private MockArchivalUnit setupAu() {
    MockArchivalUnit mau = new MyMockArchivalUnit();
    mau.setAuId("mock");
    MockPlugin plug = new MockPlugin(theDaemon);
    mau.setPlugin(plug);
    PluginTestUtil.registerArchivalUnit(mau);
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    cus.setEstimatedHashDuration(1000);
    List files = new ArrayList();
    for (int ix = 0; ix < urls.length; ix++) {
      MockCachedUrl cu = (MockCachedUrl)mau.addUrl(urls[ix], false, true);
      // Add mock file content.
      cu.setContent("This is content for CUS file " + ix);
      files.add(cu);
    }
    cus.setHashItSource(files);
    cus.setFlatItSource(files);
   
    return mau;
  }
  
  public void dontTestCreatePollPoller() throws Exception {
    Poll p = thePollFactory.createPoll(ps, theDaemon, testId, 1000,
                                       "SHA1", null);
    assertNotNull(p);
    assertTrue(p instanceof V3Poller);
  }
   
  public void dontTestCreatePollVoter() throws Exception {
    Poll p = thePollFactory.createPoll(ps, theDaemon, testId, 1000,
                                       "SHA1", testMsg);
    assertNotNull(p);
    assertTrue(p instanceof V3Voter);
  }
   
  public void testInvitationClearsNoAu() throws Exception {
    PeerIdentity id2 = idmgr.findPeerIdentity("TCP:[127.0.0.2]:9000");
    PeerIdentity id3 = idmgr.findPeerIdentity("TCP:[127.0.0.3]:9000");
    Collection ids = ListUtil.list(testId, id2, id3);

    DatedPeerIdSet noAuSet = pollManager.getNoAuPeerSet(testAu);
    synchronized (noAuSet) {
      noAuSet.addAll(ids);
    }
    if (logger.isDebug3()) {
      noAuSet.load();
      logger.debug3("noAuSet.size(): " + noAuSet.size());
      Iterator it = noAuSet.iterator();
      while (it.hasNext()) {
	PeerIdentity pi = (PeerIdentity)it.next();
	logger.debug3("Peer: " + pi.toString());
      }
      noAuSet.store();
    }
    assertTrue(noAuSet.containsAll(ids));
    assertTrue(noAuSet.contains(testId));

    Poll p = thePollFactory.createPoll(ps, theDaemon, testId, 1000,
                                       "SHA1", testMsg);
    assertNotNull(p);
    assertTrue(p instanceof V3Voter);
    assertFalse(noAuSet.contains(testId));
  }
   


  public void dontTestNoVoteIfNotPollMsg() throws Exception {
    testMsg = makePollMsg(V3LcapMessage.MSG_POLL_ACK);
    Poll p = thePollFactory.createPoll(ps, theDaemon, testId, 1000,
                                       "SHA1", testMsg);
    assertNull(p);
  }
   
  public void dontTestNoVoteIfNoCrawl() throws Exception {
    aus.setLastCrawlTime(-1);

    Poll p = thePollFactory.createPoll(ps, theDaemon, testId, 1000,
                                       "SHA1", testMsg);
    assertNull(p);
    assertEquals(ListUtil.list(ListUtil.list(PollNak.NAK_NOT_CRAWLED,
					     testAu.getAuId())),
		 thePollFactory.naks);
  }
   
  public void dontTestNoVoteIfNoCrawlAndDown() throws Exception {
    aus.setLastCrawlTime(-1);
    testAu.setConfiguration(ConfigurationUtil.fromArgs(ConfigParamDescr.PUB_DOWN.getKey(), "true"));
    assertTrue(AuUtil.isPubDown(testAu));

    Poll p = thePollFactory.createPoll(ps, theDaemon, testId, 1000,
                                       "SHA1", testMsg);
    assertNull(p);
    assertEquals(ListUtil.list(ListUtil.list(PollNak.NAK_NOT_CRAWLED,
					     testAu.getAuId())),
		 thePollFactory.naks);
  }
   
  public void dontTestNoVoteIfNoAu() throws Exception {
    aus.setLastCrawlTime(-1);
    theDaemon.setAusStarted(true);
    ps.setNullCUS(true);
    Poll p = thePollFactory.createPoll(ps, theDaemon, testId, 1000,
                                       "SHA1", testMsg);
    assertNull(p);
    assertEquals(ListUtil.list(ListUtil.list(PollNak.NAK_NO_AU,
					     testAu.getAuId())),
		 thePollFactory.naks);
  }
   
  public void dontTestNoVoteIfNoAuAndNotStarted() throws Exception {
    aus.setLastCrawlTime(-1);
    theDaemon.setAusStarted(false);
    ps.setNullCUS(true);
    Poll p = thePollFactory.createPoll(ps, theDaemon, testId, 1000,
                                       "SHA1", testMsg);
    assertNull(p);
    assertEquals(ListUtil.list(ListUtil.list(PollNak.NAK_NOT_READY,
					     testAu.getAuId())),
		 thePollFactory.naks);
  }
   
  private String[] peerNames = {
    "TCP:[10.1.0.0]:3141", "TCP:[10.1.0.1]:3141",
    "TCP:[10.1.0.2]:3141", "TCP:[10.1.0.3]:3141",
    "TCP:[10.1.0.4]:3141", "TCP:[10.1.0.5]:3141",
    "TCP:[10.1.0.6]:3141", "TCP:[10.1.0.7]:3141",
    "TCP:[10.1.0.8]:3141", "TCP:[10.1.0.9]:3141",
  };


  private IdentityAgreement getIda(PeerIdentity pid) {
    return idmgr.findTestIdentityAgreement(pid, testAu);
  }

  private PeerIdentityStatus getStatus(PeerIdentity pid) {
    return idmgr.getPeerIdentityStatus(pid);
  }

  private PeerIdentity[] makePeers(String[] keys) throws Exception {
    PeerIdentity[] peerIds = new PeerIdentity[keys.length];
    int idIndex = 0;
    float hint = 0.0f;
    for (String key : keys) {
      PeerIdentity pid = idmgr.findPeerIdentity(key);
      peerIds[idIndex++] = pid;
      idmgr.findLcapIdentity(pid, key);
      PeerIdentityStatus status = getStatus(pid);
      status.setLastMessageTime(900);
      IdentityAgreement ida = getIda(pid);
      ida.setPercentAgreementHint(hint);
      assertEquals(hint, ida.getHighestPercentAgreementHint());
      hint += 0.1f;
    }
    return peerIds;
  }

  PeerIdentity[] peerIds;

  public void dontTestCountWillingRepairers() throws Exception {
    TimeBase.setSimulated(1000);
    IdentityAgreement ida;
    PeerIdentityStatus status;
    ConfigurationUtil.addFromArgs(V3Voter.PARAM_MIN_PERCENT_AGREEMENT_FOR_REPAIRS,
				  "25",
				  V3PollFactory.PARAM_WILLING_REPAIRER_LIVENESS,
				  "200");
    peerIds = makePeers(peerNames);
    assertEquals(7, thePollFactory.countWillingRepairers(testAu));
    ida = getIda(peerIds[9]);
    ida.setPercentAgreementHint(0.1f);
    // lower last hint shouldn't change count
    assertEquals(0.1f, ida.getPercentAgreementHint());
    assertEquals(0.9f, ida.getHighestPercentAgreementHint(), 0.01f);
    assertEquals(7, thePollFactory.countWillingRepairers(testAu));

    ida = getIda(peerIds[1]);
    ida.setPercentAgreementHint(0.4f);
    assertEquals(8, thePollFactory.countWillingRepairers(testAu));

    status = getStatus(peerIds[8]);
    status.setLastMessageTime(600);
    assertEquals(7, thePollFactory.countWillingRepairers(testAu));

    ConfigurationUtil.addFromArgs(V3Poller.PARAM_NO_INVITATION_SUBNETS,
				  "10.1.0.7/32");
    assertEquals(6, thePollFactory.countWillingRepairers(testAu));

    Poll p = thePollFactory.createPoll(ps, theDaemon, testId, 1000,
                                       "SHA1", testMsg);
    assertNotNull(p);
    assertTrue(p instanceof V3Voter);
  }

  double acceptProb(PeerIdentity pid) {
    return thePollFactory.acceptProb(pid, testAu);
  }

  double acceptProb() {
    return thePollFactory.acceptProb(testId, testAu);
  }

  public void dontTestAcceptProb() throws Exception {
    peerIds = makePeers(peerNames);

    thePollFactory.setWillingRepairers(0);
    assertEquals(1.0, acceptProb());
    thePollFactory.setWillingRepairers(10);
    assertEquals(1.0, acceptProb());
    thePollFactory.setWillingRepairers(1000);
    assertEquals(1.0, acceptProb());

    ConfigurationUtil.addFromArgs(V3PollFactory.PARAM_ACCEPT_PROBABILITY_SAFETY_CURVE,
				  "[10,1.0],[10,.5],[20,.5],[20,.1]");

    thePollFactory.setWillingRepairers(0);
    assertEquals(1.0, acceptProb());
    thePollFactory.setWillingRepairers(10);
    assertEquals(1.0, acceptProb());
    thePollFactory.setWillingRepairers(11);
    assertEquals(0.5, acceptProb());
    thePollFactory.setWillingRepairers(20);
    assertEquals(0.5, acceptProb());
    thePollFactory.setWillingRepairers(21);
    assertEquals(0.1, acceptProb(), .01);
    thePollFactory.setWillingRepairers(21);
    assertEquals(0.1, acceptProb(), .01);
    ConfigurationUtil.addFromArgs(V3PollFactory.PARAM_ACCEPT_PROBABILITY_SAFETY_CURVE,
				  "[5,1.0],[5,.5],[20,.1]");
    thePollFactory.setWillingRepairers(0);
    assertEquals(1.0, acceptProb());
    thePollFactory.setWillingRepairers(5);
    assertEquals(1.0, acceptProb());
    thePollFactory.setWillingRepairers(6);
    assertEquals(0.47, acceptProb(), .01);
    thePollFactory.setWillingRepairers(10);
    assertEquals(0.37, acceptProb(), .01);
    thePollFactory.setWillingRepairers(20);
    assertEquals(0.1, acceptProb(), .01);
    thePollFactory.setWillingRepairers(21);
    assertEquals(0.1, acceptProb(), .01);

    thePollFactory.setWillingRepairers(0);
    log.info("getAcceptRepairersPollPercent: " +
	     pollManager.getAcceptRepairersPollPercent());

    assertEquals(0.9, acceptProb(peerIds[8]), .01);
    assertEquals(1.0, acceptProb(peerIds[0]), .01);
    ConfigurationUtil.addFromArgs(V3PollFactory.PARAM_ACCEPT_REPAIRERS_POLL_PERCENT,
				  "75");
    assertEquals(0.75, acceptProb(peerIds[8]), .01);
    assertEquals(1.0, acceptProb(peerIds[0]), .01);
  }


  class MyV3PollFactory extends V3PollFactory {
    List naks = new ArrayList();
    int numWillingRepairers = -1;

    public MyV3PollFactory(PollManager pollMgr) {
      super(pollMgr);
      this.idMgr = idmgr;
    }

    @Override
    protected void sendNak(LockssDaemon daemon, PollNak nak,
			   String auid, V3LcapMessage msg) {
      naks.add(ListUtil.list(nak, auid));
    }

    @Override
    int countWillingRepairers(ArchivalUnit au) {
      if (numWillingRepairers < 0) {
	return super.countWillingRepairers(au);
      }
      return numWillingRepairers;
    }

    void setWillingRepairers(int n) {
      numWillingRepairers = n;
    }
  }

  class MyMockArchivalUnit extends MockArchivalUnit {
    public TitleConfig getTitleConfig() {
      return null;
    }
  }

  static class MyIdentityManager extends IdentityManagerImpl {
    IdentityAgreement findTestIdentityAgreement(PeerIdentity pid,
						ArchivalUnit au) {
      Map map = findAuAgreeMap(au);
      synchronized (map) {
	return findPeerIdentityAgreement(map, pid);
      }
    }

    public void storeIdentities() throws ProtocolException {
    }
  }
}
