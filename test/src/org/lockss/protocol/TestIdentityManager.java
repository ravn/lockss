/*
 * $Id: TestIdentityManager.java,v 1.35 2005-07-25 01:19:16 tlipkis Exp $
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

import java.io.File;
import java.util.*;
import java.net.UnknownHostException;
import org.lockss.util.*;
import org.lockss.daemon.status.*;
import org.lockss.plugin.*;
import org.lockss.poller.*;
import org.lockss.repository.*;
import org.lockss.test.*;

/** Test cases for org.lockss.protocol.IdentityManager that assume the
 * IdentityManager has been initialized.  See TestIdentityManagerInit for
 * more IdentityManager tests. */
public class TestIdentityManager extends LockssTestCase {
  Object testIdKey;

  private MockLockssDaemon theDaemon;
  private TestableIdentityManager idmgr;
  String tempDirPath;

  PeerIdentity peer1;
  PeerIdentity peer2;
  PeerIdentity peer3;
  MockArchivalUnit mau;

  private static final String LOCAL_IP = "127.1.2.3";
  private static final String IP_2 = "127.6.5.4";
  private static final int LOCAL_PORT_NUM = 3141;
//   private static final String LOCAL_PORT = "3141";
  private static final String LOCAL_PORT = Integer.toString(LOCAL_PORT_NUM);

  public void setUp() throws Exception {
    super.setUp();

    mau = new MockArchivalUnit();
    tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    ConfigurationUtil.setCurrentConfigFromProps(commonConfig());

    theDaemon = getMockLockssDaemon();
    idmgr = new TestableIdentityManager();
    idmgr.initService(theDaemon);
    theDaemon.setIdentityManager(idmgr);
    theDaemon.setHistoryRepository(new MockHistoryRepository(), mau);
    idmgr.startService();
  }

  public void tearDown() throws Exception {
    if (idmgr != null) {
      idmgr.stopService();
      idmgr = null;
    }
    super.tearDown();
  }

  Properties commonConfig() {
    Properties p = new Properties();
    p.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    p.setProperty(IdentityManager.PARAM_IDDB_DIR, tempDirPath + "iddb");
    p.setProperty(IdentityManager.PARAM_LOCAL_IP, LOCAL_IP);
    return p;
  }

  void setupPeer123() throws IdentityManager.MalformedIdentityKeyException {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    peer2 = idmgr.stringToPeerIdentity("127.0.0.2");
    peer3 = idmgr.stringToPeerIdentity("127.0.0.3");
  }

  public void testSetupLocalIdentitiesV1Only()
      throws IdentityManager.MalformedIdentityKeyException {
    IdentityManager mgr = new IdentityManager();
    mgr.setupLocalIdentities();
    assertNull(mgr.localPeerIdentities[Poll.V3_POLL]);
    PeerIdentity pid1 = mgr.localPeerIdentities[Poll.V1_POLL];
    PeerIdentity pid2 = mgr.stringToPeerIdentity(LOCAL_IP);
    assertSame(pid1, pid2);
    PeerAddress pa = pid1.getPeerAddress();
    assertTrue(pa instanceof PeerAddress.Udp);
    assertEquals(LOCAL_IP, ((PeerAddress.Udp)pa).getIPAddr().getHostAddress());
  }

  public void testSetupLocalIdentitiesV3Normal()
      throws IdentityManager.MalformedIdentityKeyException {
    Properties p = commonConfig();
    p.setProperty(IdentityManager.PARAM_LOCAL_V3_PORT, LOCAL_PORT);
    ConfigurationUtil.setCurrentConfigFromProps(p);
    IdentityManager mgr = new IdentityManager();
    mgr.setupLocalIdentities();
    PeerIdentity pid1 = mgr.localPeerIdentities[Poll.V3_POLL];
    String key = IdentityManager.ipAddrToKey(LOCAL_IP, LOCAL_PORT_NUM);
    PeerIdentity pid2 = mgr.stringToPeerIdentity(key);
    assertSame(pid1, pid2);
    PeerAddress pa = pid1.getPeerAddress();
    assertTrue(pa instanceof PeerAddress.Tcp);
    assertEquals(LOCAL_IP, ((PeerAddress.Tcp)pa).getIPAddr().getHostAddress());
    assertEquals(LOCAL_PORT_NUM, ((PeerAddress.Tcp)pa).getPort());
  }

  public void testSetupLocalIdentitiesV3Override()
      throws IdentityManager.MalformedIdentityKeyException {
    Properties p = commonConfig();
    p.setProperty(IdentityManager.PARAM_LOCAL_V3_PORT, LOCAL_PORT);
    p.setProperty(IdentityManager.PARAM_LOCAL_V3_IDENTITY,
		  IP_2 + IdentityManager.V3_ID_SEPARATOR +
		  (LOCAL_PORT_NUM + 123));
    ConfigurationUtil.setCurrentConfigFromProps(p);
    IdentityManager mgr = new IdentityManager();
    mgr.setupLocalIdentities();
    PeerIdentity pid1 = mgr.localPeerIdentities[Poll.V3_POLL];
    PeerAddress pa = pid1.getPeerAddress();
    assertTrue(pa instanceof PeerAddress.Tcp);
    assertEquals(IP_2, ((PeerAddress.Tcp)pa).getIPAddr().getHostAddress());
    assertEquals(LOCAL_PORT_NUM + 123, ((PeerAddress.Tcp)pa).getPort());
  }

  /** test for method stringToPeerIdentity **/
  public void testStringToPeerIdentity() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    assertNotNull(peer1);
    assertSame(peer1, idmgr.stringToPeerIdentity("127.0.0.1"));
    peer2 = idmgr.stringToPeerIdentity("127.0.0.2");
    assertNotNull(peer2);
    assertNotEquals(peer1, peer2);
    peer3 = idmgr.stringToPeerIdentity("127.0.0.2" +
				       IdentityManager.V3_ID_SEPARATOR +
				       "300");
    assertNotNull(peer3);
    assertNotEquals(peer3, peer2);
    assertNotEquals(peer3, peer1);
  }

  // XXX this should go away
  public void testStringToPeerIdentityNull() throws Exception {
    peer1 = idmgr.stringToPeerIdentity(null);
    assertTrue(peer1.isLocalIdentity());
    assertSame(peer1, idmgr.getLocalPeerIdentity(Poll.V1_POLL));
  }

  /** test for method ipAddrToPeerIdentity **/
  public void testIPAddrToPeerIdentity() throws Exception {
    IPAddr ip1 = IPAddr.getByName("127.0.0.1");
    IPAddr ip2 = IPAddr.getByName("127.0.0.2");
    IPAddr ip3 = IPAddr.getByName("127.0.0.3");

    peer1 = idmgr.ipAddrToPeerIdentity(ip1);
    assertNotNull(peer1);
    assertSame(peer1, idmgr.ipAddrToPeerIdentity(ip1));
    assertSame(peer1, idmgr.stringToPeerIdentity("127.0.0.1"));
    peer2 = idmgr.ipAddrToPeerIdentity(ip2);
    assertNotNull(peer2);
    assertNotEquals(peer1, peer2);
    assertSame(peer2, idmgr.stringToPeerIdentity("127.0.0.2"));
    peer3 = idmgr.ipAddrToPeerIdentity(ip2, 300);
    assertNotNull(peer3);
    assertNotEquals(peer3, peer2);
    assertNotEquals(peer3, peer1);
    assertSame(peer3, idmgr.stringToPeerIdentity("127.0.0.2" +
				       IdentityManager.V3_ID_SEPARATOR +
				       "300"));
  }

  // XXX this should go away
  public void testIPAddrToPeerIdentityNull() throws Exception {
    peer1 = idmgr.ipAddrToPeerIdentity(null);
    assertTrue(peer1.isLocalIdentity());
    assertSame(peer1, idmgr.getLocalPeerIdentity(Poll.V1_POLL));
  }

  public void testIdentityToIPAddr() throws Exception {
    IPAddr ip1 = IPAddr.getByName("127.0.0.1");
    IPAddr ip2 = IPAddr.getByName("127.0.0.2");
    peer1 = idmgr.ipAddrToPeerIdentity(ip1);
    peer2 = idmgr.ipAddrToPeerIdentity(ip2);

    assertEquals(ip1, idmgr.identityToIPAddr(peer1));
    assertEquals(ip2, idmgr.identityToIPAddr(peer2));
  }

  public void testIdentityToIPAddrV3() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1" +
				       IdentityManager.V3_ID_SEPARATOR +
				       "23");
    try {
      idmgr.identityToIPAddr(peer1);
      fail("identityToIPAddr(V3PeerId) should throw");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testGetLocalIdentity() {
    peer1 = idmgr.getLocalPeerIdentity(Poll.V1_POLL);
    assertTrue(peer1.isLocalIdentity());
    assertTrue(idmgr.isLocalIdentity(peer1));
    String key = peer1.getIdString();
    assertTrue(idmgr.isLocalIdentity(key));
  }

  public void testGetLocalIdentityIll() {
    try {
      peer1 = idmgr.getLocalPeerIdentity(Poll.MAX_POLL_VERSION + 32);
      fail("getLocalPeerIdentity(" + (Poll.MAX_POLL_VERSION + 32) +
	   ") should throw");
    } catch (IllegalArgumentException e) {
    }
  }

  void checkReputation(int orgRep, int maxDelta, int curRep) {
    if(maxDelta > 0) {
      assertTrue(curRep <= orgRep + maxDelta);
    }
    else if(maxDelta < 0) {
      assertTrue(curRep >= orgRep + maxDelta);
    }
  }
  // -----------------------------------------------------

  /** test for method agreeWithVote(..) */
  public void testAgreeWithVote() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    int rep = idmgr.getReputation(peer1);
    idmgr.changeReputation(peer1, IdentityManager.AGREE_VOTE);
    checkReputation(rep, idmgr.reputationDeltas[IdentityManager.AGREE_VOTE],
                    idmgr.getReputation(peer1));
  }

  /** test for method disagreeWithVote(..) */
  public void testDisagreeWithVote() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    int rep = idmgr.getReputation(peer1);
    idmgr.changeReputation(peer1,IdentityManager.DISAGREE_VOTE);
    checkReputation(rep, idmgr.reputationDeltas[IdentityManager.DISAGREE_VOTE],
                    idmgr.getReputation(peer1));
  }

  /** test for method callInternalPoll(..) */
  public void testCallInternalPoll() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    int rep = idmgr.getReputation(peer1);
    idmgr.changeReputation(peer1,IdentityManager.CALL_INTERNAL);
    checkReputation(rep, idmgr.reputationDeltas[IdentityManager.CALL_INTERNAL],
                    idmgr.getReputation(peer1));
  }

  /** test for method spoofDetected(..) */
  public void testSpoofDetected() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    int rep = idmgr.getReputation(peer1);
    idmgr.changeReputation(peer1,IdentityManager.SPOOF_DETECTED);
    checkReputation(rep, idmgr.reputationDeltas[IdentityManager.SPOOF_DETECTED],
                    idmgr.getReputation(peer1));
  }

  /** test for method replayDected(..) */
  public void testReplayDected() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    int rep = idmgr.getReputation(peer1);
    idmgr.changeReputation(peer1,IdentityManager.REPLAY_DETECTED);
    checkReputation(rep, idmgr.reputationDeltas[IdentityManager.REPLAY_DETECTED],
                    idmgr.getReputation(peer1));
  }

  /** test for method acttackDetected(..) */
  public void testAttackDetected() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    int rep = idmgr.getReputation(peer1);
    idmgr.changeReputation(peer1,IdentityManager.ATTACK_DETECTED);
    checkReputation(rep, idmgr.reputationDeltas[IdentityManager.ATTACK_DETECTED],
                    idmgr.getReputation(peer1));
  }

  /** test for method voteNotVerify(..) */
  public void testVoteNotVerify() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    int rep = idmgr.getReputation(peer1);
    idmgr.changeReputation(peer1,IdentityManager.VOTE_NOTVERIFIED);
    checkReputation(rep, idmgr.reputationDeltas[IdentityManager.VOTE_NOTVERIFIED],
                    idmgr.getReputation(peer1));
  }

  /** test for method voteVerify(..) */
  public void testVoteVerify() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    int rep = idmgr.getReputation(peer1);
    idmgr.changeReputation(peer1,IdentityManager.VOTE_VERIFIED);
    checkReputation(rep, idmgr.reputationDeltas[IdentityManager.VOTE_VERIFIED],
                    idmgr.getReputation(peer1));
  }

  /** test for method voteDisown(..) */
  public void testVoteDisown() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    int rep = idmgr.getReputation(peer1);
    idmgr.changeReputation(peer1,IdentityManager.VOTE_DISOWNED);
    checkReputation(rep, idmgr.reputationDeltas[IdentityManager.VOTE_DISOWNED],
                    idmgr.getReputation(peer1));
  }

  public void testStoreIdentities() throws Exception {
    setupPeer123();

    idmgr.storeIdentities();
  }

  public void testSignalAgreedThrowsOnNullAu() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    try {
      idmgr.signalAgreed(peer1, null);
      fail("Should have thrown on a null au");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testSignalAgreedThrowsOnNullId() throws Exception {
    try {
      idmgr.signalAgreed(null, mau);
      fail("Should have thrown on a null au");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testSignalDisagreedThrowsOnNullAu() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    try {
      idmgr.signalDisagreed(peer1, null);
      fail("Should have thrown on a null au");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testSignalDisagreedThrowsOnNullId() throws Exception {
    try {
      idmgr.signalDisagreed(null, mau);
      fail("Should have thrown on a null au");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testGetAgreeThrowsOnNullAu() throws Exception {
    try {
      idmgr.getAgreed(null);
      fail("Should have thrown on a null au");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testGetAgreedNoneSet() throws Exception {
    assertEmpty(idmgr.getAgreed(mau));
  }

  public void testHasAgreed() throws Exception {
    TimeBase.setSimulated(10);

    setupPeer123();
    assertFalse(idmgr.hasAgreed(peer1, mau));
    assertFalse(idmgr.hasAgreed(peer2, mau));

    idmgr.signalAgreed(peer1, mau);
    assertTrue(idmgr.hasAgreed(peer1, mau));
    assertFalse(idmgr.hasAgreed(peer2, mau));
    TimeBase.step();
    idmgr.signalAgreed(peer2, mau);
    assertTrue(idmgr.hasAgreed(peer1, mau));
    assertTrue(idmgr.hasAgreed(peer2, mau));
  }

  public void testGetAgreed() throws Exception {
    TimeBase.setSimulated(10);
    setupPeer123();

    idmgr.signalAgreed(peer1, mau);
    TimeBase.step();
    idmgr.signalAgreed(peer2, mau);
    Map expected = new HashMap();
    expected.put(peer1, new Long(10));
    expected.put(peer2, new Long(11));
    assertEquals(expected, idmgr.getAgreed(mau));
  }

  public void testGetIdentityAgreements() throws Exception {
    TimeBase.setSimulated(10);
    setupPeer123();

    idmgr.signalAgreed(peer1, mau);
    TimeBase.step();
    idmgr.signalAgreed(peer2, mau);
    idmgr.signalDisagreed(peer2, mau);
    IdentityManager.IdentityAgreement ida1 =
      new IdentityManager.IdentityAgreement(peer1);
    ida1.setLastAgree(10);
    IdentityManager.IdentityAgreement ida2 =
      new IdentityManager.IdentityAgreement(peer2);
    ida2.setLastAgree(11);
    ida2.setLastDisagree(11);
    assertEquals(SetUtil.set(ida1, ida2),
		 SetUtil.theSet(idmgr.getIdentityAgreements(mau)));
  }

  public void testHasAgreeMap() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");

    assertFalse(idmgr.hasAgreeMap(mau));
    idmgr.signalAgreed(peer1, mau);
    assertTrue(idmgr.hasAgreeMap(mau));
  }

  // ensure that deisctivating and reactivating an AU gets it's old data
  // back (i.e., uses auid not au as key in maps
  public void testAgreedUsesAuid() throws Exception {
    TimeBase.setSimulated(10);
    setupPeer123();

    idmgr.signalAgreed(peer1, mau);
    TimeBase.step();
    idmgr.signalAgreed(peer2, mau);
    Map expected = new HashMap();
    expected.put(peer1, new Long(10));
    expected.put(peer2, new Long(11));
    assertEquals(expected, idmgr.getAgreed(mau));
    MockArchivalUnit mau2 = new MockArchivalUnit();
    // simulate desctivating and reactivating an AU, which creates a new AU
    // instance with the same auid
    mau2.setAuId(mau.getAuId());
    assertEquals(expected, idmgr.getAgreed(mau2));
  }

  public void testDisagreeDoesntRemove() throws Exception {
    TimeBase.setSimulated(10);
    setupPeer123();

    idmgr.signalAgreed(peer1, mau);
    idmgr.signalAgreed(peer2, mau);

    idmgr.signalDisagreed(peer1, mau);

    Map expected = new HashMap();
    expected.put(peer1, new Long(10));
    expected.put(peer2, new Long(10));

    assertEquals(expected, idmgr.getAgreed(mau));
  }

  public void testGetCachesToRepairFromThrowsOnNullAu()
      throws Exception {
    try {
      idmgr.getCachesToRepairFrom(null);
      fail("Should have thrown on a null au");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testGetCachesToRepairFromNoneSet() throws Exception {
    assertEmpty(idmgr.getCachesToRepairFrom(mau));
  }

  public void testCachesToRepairFrom() throws Exception {
    TimeBase.setSimulated(10);
    setupPeer123();

    idmgr.signalAgreed(peer1, mau);
    idmgr.signalDisagreed(peer2, mau);
    TimeBase.step();
    idmgr.signalDisagreed(peer1, mau);
    idmgr.signalAgreed(peer2, mau);
    idmgr.signalAgreed(peer3, mau);
    List toRepair = idmgr.getCachesToRepairFrom(mau);
    assertSameElements(ListUtil.list(peer1, peer2, peer3), toRepair);
    // peer2,3 can be in either order, but peer1 must be last because it
    // has a disagreement
    assertEquals(peer1, toRepair.get(2));
  }

  public void testAgreeUpdatesTime() throws Exception {
    TimeBase.setSimulated(10);
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");


    idmgr.signalAgreed(peer1, mau);
    TimeBase.step(15);
    idmgr.signalAgreed(peer1, mau);

    Map expected = new HashMap();
    expected.put(peer1, new Long(25));

    assertEquals(expected, idmgr.getAgreed(mau));
  }

  public void testMultipleAus() throws Exception {
    TimeBase.setSimulated(10);
    setupPeer123();

    MockArchivalUnit mau1 = new MockArchivalUnit();
    MockArchivalUnit mau2 = new MockArchivalUnit();
    log.info("auid1: " + mau1.getAuId());
    log.info("auid2: " + mau2.getAuId());
    theDaemon.setHistoryRepository(new MockHistoryRepository(), mau1);
    theDaemon.setHistoryRepository(new MockHistoryRepository(), mau2);

    idmgr.signalAgreed(peer1, mau1);
    idmgr.signalAgreed(peer2, mau2);

    idmgr.signalDisagreed(peer1, mau2);

    Map expected = new HashMap();
    expected.put(peer2, new Long(10));

    assertEquals(expected, idmgr.getAgreed(mau2));

    expected = new HashMap();
    expected.put(peer1, new Long(10));
    assertEquals(expected, idmgr.getAgreed(mau1));
  }

  public void testIdentityAgreementEquals() {
    IdentityManager.IdentityAgreement a1 = new MyIdentityAgreement("id1");
    IdentityManager.IdentityAgreement a2 = new MyIdentityAgreement("id1");
    IdentityManager.IdentityAgreement a3 = new MyIdentityAgreement("id3");
    assertEquals(a1, a2);
    assertNotEquals(a1, a3);
    a1.setLastAgree(8);
    assertNotEquals(a1, a2);
    a2.setLastAgree(8);
    assertEquals(a1, a2);
    a1.setLastDisagree(12);
    assertNotEquals(a1, a2);
    a2.setLastDisagree(12);
    assertEquals(a1, a2);
  }    

  public void testIdentityAgreementHash() {
    IdentityManager.IdentityAgreement a1 = new MyIdentityAgreement("id1");
    IdentityManager.IdentityAgreement a2 = new MyIdentityAgreement("id1");
    assertEquals(a1.hashCode(), a2.hashCode());
    a1.setLastAgree(8);
    a2.setLastAgree(8);
    a1.setLastDisagree(12);
    a2.setLastDisagree(12);
    assertEquals(a1.hashCode(), a2.hashCode());
  }    

  static class MyIdentityAgreement extends IdentityManager.IdentityAgreement {
    MyIdentityAgreement(String id) {
      super();
      setId(id);
    }
  }

  public void testStoreIdentityAgreement() throws Exception {
    MockHistoryRepository hRep = new MockHistoryRepository();
    theDaemon.setHistoryRepository(hRep, mau);
    setupPeer123();

    idmgr.signalAgreed(peer1, mau);
    idmgr.signalAgreed(peer2, mau);

    idmgr.signalDisagreed(peer1, mau);
    idmgr.signalDisagreed(peer3, mau);

    List expected = new ArrayList();

    IdentityManager.IdentityAgreement ida =
        new IdentityManager.IdentityAgreement(peer1);
    ida.setLastAgree(10);
    ida.setLastDisagree(10);
    expected.add(ida);
    assertContains(hRep.getStoredIdentityAgreement(), ida);

    ida = new IdentityManager.IdentityAgreement(peer2);
    ida.setLastAgree(10);
    expected.add(ida);
    assertContains(hRep.getStoredIdentityAgreement(), ida);

    ida = new IdentityManager.IdentityAgreement(peer3);
    ida.setLastDisagree(10);
    expected.add(ida);
    assertContains(hRep.getStoredIdentityAgreement(), ida);

    assertSameElements(expected, hRep.getStoredIdentityAgreement());

    assertEquals(3, hRep.getStoredIdentityAgreement().size());
  }

  public void testLoadIdentityAgreement() throws Exception {
    MockHistoryRepository hRep = new MockHistoryRepository();
    theDaemon.setHistoryRepository(hRep, mau);

    setupPeer123();

    List loadList = new ArrayList(3);

    IdentityManager.IdentityAgreement ida =
        new IdentityManager.IdentityAgreement(peer1);
    ida.setLastAgree(10);
    ida.setLastDisagree(10);
    loadList.add(ida);

    ida = new IdentityManager.IdentityAgreement(peer2);
    ida.setLastAgree(10);
    loadList.add(ida);

    ida = new IdentityManager.IdentityAgreement(peer3);
    ida.setLastDisagree(10);
    loadList.add(ida);

    hRep.setLoadedIdentityAgreement(loadList);
    idmgr.forceReloadMap(mau);

    Map agree = new HashMap();
    agree.put(peer1, new Long(10));
    agree.put(peer2, new Long(10));
    assertEquals(agree, idmgr.getAgreed(mau));

    Map disagree = new HashMap();
    disagree.put(peer1, new Long(10));
    disagree.put(peer3, new Long(10));
    assertEquals(disagree, idmgr.getDisagreed(mau));
  }

  public void testLoadIdentityAgreementMerge() throws Exception {
    ConfigurationUtil.setFromArgs(IdentityManager.PARAM_MERGE_RESTORED_AGREE_MAP, "true");

    MockHistoryRepository hRep = new MockHistoryRepository();
    theDaemon.setHistoryRepository(hRep, mau);

    setupPeer123();

    List loadList = new ArrayList(3);

    IdentityManager.IdentityAgreement ida =
        new IdentityManager.IdentityAgreement(peer1);
    ida.setLastAgree(10);
    ida.setLastDisagree(10);
    loadList.add(ida);

    ida = new IdentityManager.IdentityAgreement(peer2);
    ida.setLastAgree(10);
    loadList.add(ida);

    ida = new IdentityManager.IdentityAgreement(peer3);
    ida.setLastDisagree(10);
    loadList.add(ida);

    TimeBase.setSimulated(9);
    idmgr.signalAgreed(peer1, mau);
    idmgr.signalDisagreed(peer2, mau);

    TimeBase.step(2);
    idmgr.signalAgreed(peer2, mau);
    idmgr.signalDisagreed(peer2, mau);
    idmgr.signalAgreed(peer3, mau);

    hRep.setLoadedIdentityAgreement(loadList);
    idmgr.forceReloadMap(mau);

    Map agree = new HashMap();
    agree.put(peer1, new Long(10));
    agree.put(peer2, new Long(11));
    agree.put(peer3, new Long(11));
    assertEquals(agree, idmgr.getAgreed(mau));

    Map disagree = new HashMap();
    disagree.put(peer1, new Long(10));
    disagree.put(peer2, new Long(11));
    disagree.put(peer3, new Long(10));
    assertEquals(disagree, idmgr.getDisagreed(mau));
  }

  public void testLoadIdentityAgreementNoMerge() throws Exception {
    ConfigurationUtil.setFromArgs(IdentityManager.PARAM_MERGE_RESTORED_AGREE_MAP, "false");

    MockHistoryRepository hRep = new MockHistoryRepository();
    theDaemon.setHistoryRepository(hRep, mau);

    setupPeer123();

    List loadList = new ArrayList(3);

    IdentityManager.IdentityAgreement ida =
        new IdentityManager.IdentityAgreement(peer1);
    ida.setLastAgree(10);
    ida.setLastDisagree(10);
    loadList.add(ida);

    ida = new IdentityManager.IdentityAgreement(peer2);
    ida.setLastAgree(10);
    loadList.add(ida);

    ida = new IdentityManager.IdentityAgreement(peer3);
    ida.setLastDisagree(10);
    loadList.add(ida);

    TimeBase.setSimulated(9);
    idmgr.signalAgreed(peer1, mau);
    idmgr.signalDisagreed(peer2, mau);

    TimeBase.step(2);
    idmgr.signalAgreed(peer2, mau);
    idmgr.signalDisagreed(peer2, mau);
    idmgr.signalAgreed(peer3, mau);

    hRep.setLoadedIdentityAgreement(loadList);
    idmgr.forceReloadMap(mau);

    Map agree = new HashMap();
    agree.put(peer1, new Long(10));
    agree.put(peer2, new Long(10));
    assertEquals(agree, idmgr.getAgreed(mau));

    Map disagree = new HashMap();
    disagree.put(peer1, new Long(10));
    disagree.put(peer3, new Long(10));
    assertEquals(disagree, idmgr.getDisagreed(mau));
  }



  /**
   * Tests that the IP address info fed to the IdentityManagerStatus object
   * looks like an IP address (x.x.x.x)
   */

  public void testStatusInterface() throws Exception {
    peer1 = idmgr.stringToPeerIdentity("127.0.0.1");
    peer2 = idmgr.stringToPeerIdentity("127.0.0.2");

    idmgr.signalAgreed(peer1, mau);
    idmgr.signalAgreed(peer2, mau);
    
    Map idMap = idmgr.getIdentityMap();
    Set expectedAddresses = new HashSet();
    expectedAddresses.add("127.0.0.1");
    expectedAddresses.add("127.0.0.2");
    expectedAddresses.add(LOCAL_IP);

    for (Iterator iter = idMap.values().iterator();
	 iter.hasNext();) {
      LcapIdentity id = (LcapIdentity)iter.next();
      assertTrue(id.getPeerIdentity() + " not found in " + expectedAddresses,
		 expectedAddresses.contains(id.getPeerIdentity().getIdString()));
    }
    
    assertEquals(expectedAddresses.size(), idMap.size()); //2 above,plus me
  }

  private class TestableIdentityManager extends IdentityManager {
    Map identities = null;

    public Map getIdentityMap() {
      return identities;
    }

    protected IdentityManagerStatus makeStatusAccessor(Map identities) {
      this.identities = identities;
      return new MockIdentityManagerStatus();
    }

    void forceReloadMap(ArchivalUnit au) {
      Map map = findAuAgreeMap(au);
      synchronized (map) {
	map.put(AGREE_MAP_INIT_KEY, "true");	// ensure map is reread
      }
    }
  }

  private static class MockIdentityManagerStatus
    extends IdentityManagerStatus {
    public MockIdentityManagerStatus() {
      super(null);
    }
    public String getDisplayName() {
      throw new UnsupportedOperationException();
    }
    
    public void populateTable(StatusTable table) {
      throw new UnsupportedOperationException();
    }

    public boolean requiresKey() {
      throw new UnsupportedOperationException();
    }
  }

  public static void main(String[] argv) {
    String[] testCaseList = {TestIdentityManager.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }
}
