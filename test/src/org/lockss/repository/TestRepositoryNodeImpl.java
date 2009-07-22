/*
 * $Id: TestRepositoryNodeImpl.java,v 1.60.4.3 2009-07-22 00:25:07 edwardsb1 Exp $
 */

/*

Copyright (c) 2000-2007 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.repository;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.jcr.Node;

import org.lockss.test.*;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.protocol.*;
import org.lockss.repository.v2.*;
import org.lockss.repository.RepositoryNodeImpl.RepositoryNodeVersionImpl;
import org.lockss.repository.jcr.*;

/**
 * This is the test class for org.lockss.repository.RepositoryNodeImpl
 */
public class TestRepositoryNodeImpl extends LockssTestCase {
  static final String TREE_SIZE_PROPERTY =
    RepositoryNodeImpl.TREE_SIZE_PROPERTY;
  static final String CHILD_COUNT_PROPERTY =
    RepositoryNodeImpl.CHILD_COUNT_PROPERTY;
  
  // Used by the "getPreferredVersion" and "createNewVersion" tests. 
  private static int k_numTestVersions = 10;

  private MockLockssDaemon theDaemon;
  private MyLockssRepositoryImpl repo;
  private String tempDirPath;
  MockArchivalUnit mau;

  private MockIdentityManager idmgr;
  
  Properties props;

  public void setUp() throws Exception {
    super.setUp();
    tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    mau = new MockArchivalUnit();

    theDaemon = getMockLockssDaemon();
    
    // Create the identity manager...
    idmgr = new MockIdentityManager();
    theDaemon.setIdentityManager(idmgr);
    idmgr.initService(theDaemon);
    
    repo = (MyLockssRepositoryImpl)MyLockssRepositoryImpl.createNewLockssRepository(mau);
    theDaemon.setAuManager(LockssDaemon.LOCKSS_REPOSITORY, mau, repo);
    repo.initService(theDaemon);
    repo.startService();
  }

  public void tearDown() throws Exception {
    TimeBase.setReal();
    repo.stopService();
    theDaemon.stopDaemon();
    super.tearDown();
  }

  // RepositoryNodeImpl relies on nonexistent dir.listFiles() returning
  // null, not empty list.
  public void testFileAssumptions() throws Exception {
    // empty dir returns empty list
    File dir1 = getTempDir();
    assertNotNull(null, dir1.listFiles());
    assertEquals(new File[0], dir1.listFiles());
    // nonexistent dir returns null
    File dir2 = new File(dir1, "bacds");
    assertNull(null, dir2.listFiles());
    // dir list of non-dir returns null
    File file1 = File.createTempFile("xxx", ".tmp", dir1);
    assertTrue(file1.exists());
    assertNull(null, file1.listFiles());
  }

  public void testGetNodeUrl() {
    RepositoryNode node = new RepositoryNodeImpl("testUrl", "testDir", null, mau);
    assertEquals("testUrl", node.getNodeUrl());
    node = new RepositoryNodeImpl("testUrl/test.txt", "testUrl/test.txt", null, mau);
    assertEquals("testUrl/test.txt", node.getNodeUrl());
  }

  public void testFileLocation() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/branch1/leaf1",
                   "test stream", null);
    tempDirPath = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath, mau);
    tempDirPath = LockssRepositoryImpl.mapUrlToFileLocation(tempDirPath,
        "http://www.example.com/testDir/branch1/leaf1");
    File testFile = new File(tempDirPath);
    assertTrue(testFile.exists());
    testFile = new File(tempDirPath + "/#content/current");
    assertTrue(testFile.exists());
    testFile = new File(tempDirPath + "/#content/current.props");
    assertTrue(testFile.exists());
    testFile = new File(tempDirPath + "/#node_props");
    assertFalse(testFile.exists());
    testFile = new File(tempDirPath + "/#agreement");
    assertFalse(testFile.exists());
  }
  
  public void testUpdateAgreementCreatesFile() throws Exception {
    RepositoryNode leaf =
      createLeaf("http://www.example.com/testDir/branch1/leaf1",
                 "test stream", null);
    tempDirPath = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath, mau);
    tempDirPath = LockssRepositoryImpl.mapUrlToFileLocation(tempDirPath,
        "http://www.example.com/testDir/branch1/leaf1");
    File testFile = new File(tempDirPath, "#agreement");
    assertFalse(testFile.exists());
    
    // Agreeing IDs.
    PeerIdentity[] agreeingPeers =
      { new MockPeerIdentity("TCP:[192.168.0.1]:9723"),
        new MockPeerIdentity("TCP:[192.168.0.2]:9723")
      };
    
    leaf.signalAgreement(ListUtil.fromArray(agreeingPeers));
    assertTrue(testFile.exists());
  }

  public void testUpdateAndLoadAgreement() throws Exception {
    RepositoryNode leaf =
      createLeaf("http://www.example.com/testDir/branch1/leaf1",
                 "test stream", null);
    tempDirPath = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath, mau);
    tempDirPath = LockssRepositoryImpl.mapUrlToFileLocation(tempDirPath,
        "http://www.example.com/testDir/branch1/leaf1");
    PeerIdentity testid_1 = new MockPeerIdentity("TCP:[192.168.0.1]:9723");
    PeerIdentity testid_2 = new MockPeerIdentity("TCP:[192.168.0.2]:9723");
    PeerIdentity testid_3 = new MockPeerIdentity("TCP:[192.168.0.3]:9723");
    PeerIdentity testid_4 = new MockPeerIdentity("TCP:[192.168.0.4]:9723");
    
    idmgr.addPeerIdentity(testid_1.getIdString(), testid_1);
    idmgr.addPeerIdentity(testid_2.getIdString(), testid_2);
    idmgr.addPeerIdentity(testid_3.getIdString(), testid_3);
    idmgr.addPeerIdentity(testid_4.getIdString(), testid_4);
    
    leaf.signalAgreement(ListUtil.list(testid_1, testid_3));

    assertEquals(2, ((RepositoryNodeImpl)leaf).loadAgreementHistory().size());

    assertTrue(leaf.hasAgreement(testid_1));
    assertFalse(leaf.hasAgreement(testid_2));
    assertTrue(leaf.hasAgreement(testid_3));
    assertFalse(leaf.hasAgreement(testid_4));

    leaf.signalAgreement(ListUtil.list(testid_1, testid_2, testid_3, testid_4));
    
    assertEquals(4, ((RepositoryNodeImpl)leaf).loadAgreementHistory().size());

    assertTrue(leaf.hasAgreement(testid_1));
    assertTrue(leaf.hasAgreement(testid_2));
    assertTrue(leaf.hasAgreement(testid_3));
    assertTrue(leaf.hasAgreement(testid_4));
  }
  
  public void testVersionFileLocation() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/branch1/leaf1",
        "test stream", null);
    tempDirPath = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath, mau);
    tempDirPath = LockssRepositoryImpl.mapUrlToFileLocation(tempDirPath,
        "http://www.example.com/testDir/branch1/leaf1");
    File testFile = new File(tempDirPath + "/#content/1");
    assertFalse(testFile.exists());
    testFile = new File(tempDirPath + "/#content/1.props");
    assertFalse(testFile.exists());

    leaf.makeNewVersion();
    OutputStream os = leaf.getNewOutputStream();
    InputStream is = new StringInputStream("test stream 2");
    StreamUtil.copy(is, os);
    is.close();
    os.close();
    leaf.setNewProperties(new Properties());
    leaf.sealNewVersion();
    testFile = new File(tempDirPath + "/#content/1");
    assertTrue(testFile.exists());
    testFile = new File(tempDirPath + "/#content/1.props");
    assertTrue(testFile.exists());
  }

  public void testInactiveFileLocation() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/branch1/leaf1",
                   "test stream", null);
    tempDirPath = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath, mau);
    tempDirPath = LockssRepositoryImpl.mapUrlToFileLocation(tempDirPath,
        "http://www.example.com/testDir/branch1/leaf1");
    File curFile = new File(tempDirPath + "/#content/current");
    File curPropsFile = new File(tempDirPath + "/#content/current.props");
    File inactFile = new File(tempDirPath + "/#content/inactive");
    File inactPropsFile = new File(tempDirPath + "/#content/inactive.props");
    assertTrue(curFile.exists());
    assertTrue(curPropsFile.exists());
    assertFalse(inactFile.exists());
    assertFalse(inactPropsFile.exists());

    leaf.deactivateContent();
    assertFalse(curFile.exists());
    assertFalse(curPropsFile.exists());
    assertTrue(inactFile.exists());
    assertTrue(inactPropsFile.exists());

    //reactivate
    leaf.restoreLastVersion();
    assertTrue(curFile.exists());
    assertTrue(curPropsFile.exists());
    assertFalse(inactFile.exists());
    assertFalse(inactPropsFile.exists());

    leaf.deactivateContent();
    assertFalse(curFile.exists());
    assertFalse(curPropsFile.exists());
    assertTrue(inactFile.exists());
    assertTrue(inactPropsFile.exists());

    // make new version
    leaf.makeNewVersion();
    OutputStream os = leaf.getNewOutputStream();
    InputStream is = new StringInputStream("test stream 2");
    StreamUtil.copy(is, os);
    is.close();
    os.close();
    leaf.setNewProperties(new Properties());
    leaf.sealNewVersion();
    assertTrue(curFile.exists());
    assertTrue(curPropsFile.exists());
    assertFalse(inactFile.exists());
    assertFalse(inactPropsFile.exists());
  }

  public void testDeleteFileLocation() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/branch1/leaf1",
                   "test stream", null);
    tempDirPath = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath, mau);
    tempDirPath = LockssRepositoryImpl.mapUrlToFileLocation(tempDirPath,
        "http://www.example.com/testDir/branch1/leaf1");
    File curFile = new File(tempDirPath + "/#content/current");
    File curPropsFile = new File(tempDirPath + "/#content/current.props");
    File inactFile = new File(tempDirPath + "/#content/inactive");
    File inactPropsFile = new File(tempDirPath + "/#content/inactive.props");
    assertTrue(curFile.exists());
    assertTrue(curPropsFile.exists());
    assertFalse(inactFile.exists());
    assertFalse(inactPropsFile.exists());

    leaf.markAsDeleted();
    assertFalse(curFile.exists());
    assertFalse(curPropsFile.exists());
    assertTrue(inactFile.exists());
    assertTrue(inactPropsFile.exists());

    //reactivate
    leaf.restoreLastVersion();
    assertTrue(curFile.exists());
    assertTrue(curPropsFile.exists());
    assertFalse(inactFile.exists());
    assertFalse(inactPropsFile.exists());

    leaf.markAsDeleted();
    assertFalse(curFile.exists());
    assertFalse(curPropsFile.exists());
    assertTrue(inactFile.exists());
    assertTrue(inactPropsFile.exists());

    // make new version
    leaf.makeNewVersion();
    OutputStream os = leaf.getNewOutputStream();
    InputStream is = new StringInputStream("test stream 2");
    StreamUtil.copy(is, os);
    is.close();
    os.close();
    leaf.setNewProperties(new Properties());
    leaf.sealNewVersion();
    assertTrue(curFile.exists());
    assertTrue(curPropsFile.exists());
    assertFalse(inactFile.exists());
    assertFalse(inactPropsFile.exists());
  }

  public void testListEntriesNonexistentDir() throws Exception {
    RepositoryNode node = new RepositoryNodeImpl("foo-no-url", "foo-no-dir",
						 null, mau);
    try {
      node.listChildren(null, false);
      fail("listChildren() is nonexistent dir should throw");
    } catch (LockssRepository.RepositoryStateException e) {
    }
  }

  public void testListEntries() throws Exception {
    createLeaf("http://www.example.com/testDir/branch1/leaf1",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch1/leaf2",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch2/leaf3",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch2", "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf4", "test stream", null);

    // root branch
    RepositoryNode dirEntry =
        repo.getNode("http://www.example.com/testDir");
    Iterator childIt = dirEntry.listChildren(null, false);
    ArrayList childL = new ArrayList(3);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    String[] expectedA = new String[] {
      "http://www.example.com/testDir/branch1",
      "http://www.example.com/testDir/branch2",
      "http://www.example.com/testDir/leaf4"
      };
    assertIsomorphic(expectedA, childL);

    // sub-branch
    dirEntry = repo.getNode("http://www.example.com/testDir/branch1");
    childL.clear();
    childIt = dirEntry.listChildren(null, false);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    expectedA = new String[] {
      "http://www.example.com/testDir/branch1/leaf1",
      "http://www.example.com/testDir/branch1/leaf2",
      };
    assertIsomorphic(expectedA, childL);

    // sub-branch with content
    dirEntry = repo.getNode("http://www.example.com/testDir/branch2");
    childL.clear();
    childIt = dirEntry.listChildren(null, false);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    expectedA = new String[] {
      "http://www.example.com/testDir/branch2/leaf3",
      };
    assertIsomorphic(expectedA, childL);

    // leaf node
    dirEntry = repo.getNode("http://www.example.com/testDir/branch1/leaf1");
    childL.clear();
    childIt = dirEntry.listChildren(null, false);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    expectedA = new String[] { };
    assertIsomorphic(expectedA, childL);
  }

  String normalizeName(RepositoryNodeImpl node, String name) {
    return node.normalize(new File(name)).getPath();
  }

  public void testNormalizeUrlEncodingCase() throws Exception {
    RepositoryNodeImpl node = new RepositoryNodeImpl("foo", "bar", null, mau);
    // nothing to normalize
    File file = new File("foo/bar/baz");
    assertSame(file, node.normalize(file));
    file = new File("foo/bar/ba%ABz");
    assertSame(file, node.normalize(file));
    // unnormalized in parent dir name is left alone
    file = new File("ba%abz/bar");
    assertSame(file, node.normalize(file));
    file = new File("foo/ba%abz/bar");
    assertSame(file, node.normalize(file));
    // should be normalized
    assertEquals("ba%ABz", normalizeName(node, "ba%aBz"));
    assertEquals("/ba%ABz", normalizeName(node, "/ba%aBz"));
    assertEquals("foo/bar/ba%ABz", normalizeName(node, "foo/bar/ba%aBz"));
    assertEquals("foo/bar/ba%ABz", normalizeName(node, "foo/bar/ba%Abz"));
    assertEquals("foo/bar/ba%ABz", normalizeName(node, "foo/bar/ba%abz"));
    assertEquals("foo/bar/ba%abz/ba%ABz", normalizeName(node, "foo/bar/ba%abz/ba%abz"));
  }

  public void testNormalizeTrailingQuestion() throws Exception {
    RepositoryNodeImpl node = new RepositoryNodeImpl("foo", "bar", null, mau);
    // nothing to normalize
    File file = new File("foo/bar/baz");
    assertSame(file, node.normalize(file));
    file = new File("foo/bar/ba?z");
    assertSame(file, node.normalize(file));
    // unnormalized in parent dir name is left alone
    file = new File("ba?/bar");
    assertSame(file, node.normalize(file));
    // should be normalized
    assertEquals("baz", normalizeName(node, "baz?"));
    assertEquals("/ba", normalizeName(node, "/ba?"));
    assertEquals("foo/bar/bar", normalizeName(node, "foo/bar/bar?"));
    assertEquals("foo/ba?r/bar", normalizeName(node, "foo/ba?r/bar?"));
    assertEquals("foo/bar?/bar", normalizeName(node, "foo/bar?/bar?"));

    // disable trailing ? normalization
    ConfigurationUtil.addFromArgs(UrlUtil.PARAM_NORMALIZE_EMPTY_QUERY,
				  "false");
    assertEquals("baz?", normalizeName(node, "baz?"));
  }

  List getChildNames(String nodeName) throws MalformedURLException {
    RepositoryNode dirEntry = repo.getNode(nodeName);
    ArrayList res = new ArrayList();
    for (Iterator childIt = dirEntry.listChildren(null, false);
	 childIt.hasNext(); ) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      res.add(node.getNodeUrl());
    }
    return res;
  }

  public void testFixUnnormalized_Rename() throws Exception {
    repo.setDontNormalize(true);
    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "false");
    createLeaf("http://www.example.com/testDir/branch%3c1/leaf%2C1",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch%3c1/leaf%2c2",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch2/leaf3",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch2", "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf4", "test stream", null);

    String[] expectedA = new String[] {
      "http://www.example.com/testDir/branch%3c1",
      "http://www.example.com/testDir/branch2",
      "http://www.example.com/testDir/leaf4"
      };
    assertIsomorphic(expectedA,
		     getChildNames(("http://www.example.com/testDir")));

    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "true");
    String[] expectedB = new String[] {
      "http://www.example.com/testDir/branch%3C1",
      "http://www.example.com/testDir/branch2",
      "http://www.example.com/testDir/leaf4"
      };
    assertIsomorphic(expectedB,
		     getChildNames(("http://www.example.com/testDir")));

    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "false");
    assertIsomorphic(expectedB,
		     getChildNames(("http://www.example.com/testDir")));

    String[] expectedC = new String[] {
      "http://www.example.com/testDir/branch%3C1/leaf%2C1",
      "http://www.example.com/testDir/branch%3C1/leaf%2c2",
      };
    assertIsomorphic(expectedC,
		     getChildNames(("http://www.example.com/testDir/branch%3C1")));

    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "true");
    assertIsomorphic(expectedB,
		     getChildNames(("http://www.example.com/testDir")));

    String[] expectedD = new String[] {
      "http://www.example.com/testDir/branch%3C1/leaf%2C1",
      "http://www.example.com/testDir/branch%3C1/leaf%2C2",
      };
    assertIsomorphic(expectedD,
		     getChildNames(("http://www.example.com/testDir/branch%3C1")));
  }

  public void testFixUnnormalizedMultiple_Delete() throws Exception {
    repo.setDontNormalize(true);
    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "false");
    createLeaf("http://www.example.com/testDir/leaf%2C1",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf%2c1",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf3",
               "test stream", null);

    String[] expectedA = new String[] {
      "http://www.example.com/testDir/leaf%2C1",
      "http://www.example.com/testDir/leaf%2c1",
      "http://www.example.com/testDir/leaf3",
      };
    assertIsomorphic(expectedA,
		     getChildNames(("http://www.example.com/testDir")));

    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "true");
    String[] expectedB = new String[] {
      "http://www.example.com/testDir/leaf%2C1",
      "http://www.example.com/testDir/leaf3",
      };
    assertIsomorphic(expectedB,
		     getChildNames(("http://www.example.com/testDir")));

    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "false");
    assertIsomorphic(expectedB,
		     getChildNames(("http://www.example.com/testDir")));
  }

  public void testFixUnnormalizedMultiple_DeleteMultiple() throws Exception {
    repo.setDontNormalize(true);
    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "false");
    createLeaf("http://www.example.com/testDir/leaf%CA%3E",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf%cA%3E",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf%ca%3E",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf%ca%3e",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf3",
               "test stream", null);

    String[] expectedA = new String[] {
      "http://www.example.com/testDir/leaf%CA%3E",
      "http://www.example.com/testDir/leaf%cA%3E",
      "http://www.example.com/testDir/leaf%ca%3E",
      "http://www.example.com/testDir/leaf%ca%3e",
      "http://www.example.com/testDir/leaf3",
      };
    assertIsomorphic(expectedA,
		     getChildNames(("http://www.example.com/testDir")));

    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "true");
    String[] expectedB = new String[] {
      "http://www.example.com/testDir/leaf%CA%3E",
      "http://www.example.com/testDir/leaf3",
      };
    assertIsomorphic(expectedB,
		     getChildNames(("http://www.example.com/testDir")));

    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "false");
    assertIsomorphic(expectedB,
		     getChildNames(("http://www.example.com/testDir")));
  }

  public void testFixUnnormalized_DontFixParent() throws Exception {
    repo.setDontNormalize(true);
    createLeaf("http://www.example.com/testDir/branch%3c1/leaf%2C1",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch%3c1/leaf%2c2",
               "test stream", null);

    ConfigurationUtil.addFromArgs(RepositoryNodeImpl.PARAM_FIX_UNNORMALIZED,
				  "true");
    String[] expectedA = new String[] {
      "http://www.example.com/testDir/branch%3c1/leaf%2C1",
      "http://www.example.com/testDir/branch%3c1/leaf%2C2",
      };
    assertIsomorphic(expectedA,
		     getChildNames(("http://www.example.com/testDir/branch%3c1")));
  }

  public void testEntrySort() throws Exception {
    createLeaf("http://www.example.com/testDir/branch2/leaf1", null, null);
    createLeaf("http://www.example.com/testDir/leaf4", null, null);
    createLeaf("http://www.example.com/testDir/branch1/leaf1", null, null);
    createLeaf("http://www.example.com/testDir/leaf3", null, null);

    RepositoryNode dirEntry =
        repo.getNode("http://www.example.com/testDir");
    Iterator childIt = dirEntry.listChildren(null, false);
    ArrayList childL = new ArrayList(4);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    String[] expectedA = new String[] {
      "http://www.example.com/testDir/branch1",
      "http://www.example.com/testDir/branch2",
      "http://www.example.com/testDir/leaf3",
      "http://www.example.com/testDir/leaf4"
      };
    assertIsomorphic(expectedA, childL);
  }

  public void testIllegalOperations() throws Exception {
    RepositoryNode leaf =
      repo.createNewNode("http://www.example.com/testDir/test.cache");
    assertFalse(leaf.hasContent());
    try {
      leaf.getCurrentVersion();
      fail("Cannot get current version if no content.");
    } catch (UnsupportedOperationException uoe) { }
    try {
      leaf.getContentSize();
      fail("Cannot get content size if no content.");
    } catch (UnsupportedOperationException uoe) { }
    try {
      leaf.getNodeContents();
      fail("Cannot get RepositoryNodeContents if no content.");
    } catch (UnsupportedOperationException uoe) { }
    try {
      leaf.sealNewVersion();
      fail("Cannot seal version if not open.");
    } catch (UnsupportedOperationException uoe) { }
    leaf.makeNewVersion();
    try {
      leaf.sealNewVersion();
      fail("Cannot seal version if getNewOutputStream() uncalled.");
    } catch (UnsupportedOperationException uoe) { }
    leaf.makeNewVersion();
    try {
      leaf.deactivateContent();
      fail("Cannot deactivate if currently open for writing.");
    } catch (UnsupportedOperationException uoe) { }
    writeToLeaf(leaf, "test stream");
    try {
      leaf.sealNewVersion();
      fail("Cannot seal version if setNewProperties() uncalled.");
    } catch (UnsupportedOperationException uoe) { }
    leaf.makeNewVersion();
    writeToLeaf(leaf, "test stream");
    leaf.setNewProperties(new Properties());
    leaf.sealNewVersion();
    assertEquals(1, leaf.getCurrentVersion());
    assertTrue(leaf.hasContent());
  }

  public void testVersionTimeout() throws Exception {
    TimeBase.setSimulated();
    RepositoryNode leaf =
      repo.createNewNode("http://www.example.com/testDir/test.cache");
    RepositoryNode leaf2 =
      repo.getNode("http://www.example.com/testDir/test.cache");
    leaf.makeNewVersion();
    try {
      leaf2.makeNewVersion();
      fail("Can't make new version while version open.");
    } catch (UnsupportedOperationException e) { }
    TimeBase.step(RepositoryNodeImpl.DEFAULT_VERSION_TIMEOUT/2);
    try {
      leaf2.makeNewVersion();
      fail("Can't make new version while version not timed out.");
    } catch (UnsupportedOperationException e) { }
    TimeBase.step(RepositoryNodeImpl.DEFAULT_VERSION_TIMEOUT/2);
    leaf2.makeNewVersion();
  }

  public void testMakeNewCache() throws Exception {
    RepositoryNode leaf =
      repo.createNewNode("http://www.example.com/testDir/test.cache");
    assertFalse(leaf.hasContent());
    try {
      leaf.getCurrentVersion();
      fail("Cannot get current version if no content.");
    } catch (UnsupportedOperationException uoe) { }
    leaf.makeNewVersion();
    writeToLeaf(leaf, "test stream");
    leaf.setNewProperties(new Properties());
    leaf.sealNewVersion();
    assertTrue(leaf.hasContent());
    assertEquals(1, leaf.getCurrentVersion());
  }

  public void testMakeNodeLocation() throws Exception {
    RepositoryNodeImpl leaf = (RepositoryNodeImpl)
        repo.createNewNode("http://www.example.com/testDir");
    String nodeLoc = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath,
							      mau);
    nodeLoc = LockssRepositoryImpl.mapUrlToFileLocation(nodeLoc,
        "http://www.example.com/testDir");
    File testFile = new File(nodeLoc);
    assertFalse(testFile.exists());
    leaf.createNodeLocation();
    assertTrue(testFile.exists());
    assertTrue(testFile.isDirectory());
  }

  public void testMakeNewVersion() throws Exception {
    Properties props = new Properties();
    props.setProperty("test 1", "value 1");
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream 1", props);
    assertEquals(1, leaf.getCurrentVersion());

    props = new Properties();
    props.setProperty("test 1", "value 2");
    leaf.makeNewVersion();
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream 2");
    leaf.sealNewVersion();
    assertEquals(2, leaf.getCurrentVersion());

    String resultStr = getLeafContent(leaf);
    assertEquals("test stream 2", resultStr);
    props = leaf.getNodeContents().getProperties();
    assertEquals("value 2", props.getProperty("test 1"));
  }

  static final int DEL_NODE_DIR = 1;
  static final int DEL_CONTENT_DIR = 2;
  static final int DEL_CONTENT_FILE = 3;
  static final int DEL_PROPS_FILE = 4;


  public void testDisappearingFile(int whichFile, boolean tryRead)
      throws Exception {
    String url = "http://www.example.com/foo.html";
    RepositoryNodeImpl leaf = (RepositoryNodeImpl)repo.createNewNode(url);
    String nodeLoc = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath,
							      mau);
    nodeLoc = LockssRepositoryImpl.mapUrlToFileLocation(nodeLoc, url);
    File testFile;
    switch (whichFile) {
    case DEL_NODE_DIR:
      testFile = new File(nodeLoc);
      break;
    case DEL_CONTENT_DIR:
      testFile = new File(nodeLoc, "#content");
      break;
    case DEL_CONTENT_FILE:
      testFile = new File(nodeLoc, "#content/current");
      break;
    case DEL_PROPS_FILE:
      testFile = new File(nodeLoc, "#content/current.props");
      break;
    default:
      throw new UnsupportedOperationException();
    }
    assertFalse(testFile.exists());

    Properties props1 = PropUtil.fromArgs("key1", "value 1");

    createContentVersion(leaf, "test content 11111", props1);
    assertEquals(1, leaf.getCurrentVersion());

    assertTrue(testFile.exists());
    switch (whichFile) {
    case DEL_NODE_DIR:
    case DEL_CONTENT_DIR:
      assertTrue(FileUtil.delTree(testFile));
      break;
    case DEL_CONTENT_FILE:
    case DEL_PROPS_FILE:
      assertTrue(testFile.delete());
      break;
    }
    assertFalse(testFile.exists());

    Properties props2 = PropUtil.fromArgs("key2", "value 2");
    RepositoryNode leaf2 = repo.createNewNode(url);
    assertSame(leaf, leaf2);
    assertTrue(leaf.hasContent());
    if (tryRead) {
      try {
	getLeafContent(leaf);
      } catch (LockssRepository.RepositoryStateException e) {
	// expected
      }
    }
    leaf2.makeNewVersion();

    writeToLeaf(leaf, "test content 22222");
    leaf.setNewProperties(props2);
    leaf.sealNewVersion();

    assertTrue(testFile.exists());
    int expver = 2;
    // if we tried to read while node or content dir was missing, version
    // number will have been reset.
    if (tryRead) {
      switch (whichFile) {
      case DEL_NODE_DIR:
      case DEL_CONTENT_DIR:
	expver = 1;
      }
    }
    assertEquals(expver, leaf.getCurrentVersion());

    assertEquals("test content 22222", getLeafContent(leaf));
    assertEquals("value 2", leaf.getNodeContents().getProperties().get("key2"));
  }

  public void testDisappearingNodeDir() throws Exception {
    testDisappearingFile(DEL_NODE_DIR, false);
  }

  public void testDisappearingContentDir() throws Exception {
    testDisappearingFile(DEL_CONTENT_DIR, false);
  }

  public void testDisappearingContentFile() throws Exception {
    testDisappearingFile(DEL_CONTENT_FILE, false);
  }

  public void testDisappearingPropsFile() throws Exception {
    testDisappearingFile(DEL_PROPS_FILE, false);
  }

  public void testDisappearingNodeDirWithRead() throws Exception {
    testDisappearingFile(DEL_NODE_DIR, true);
  }

  public void testDisappearingContentDirWithRead() throws Exception {
    testDisappearingFile(DEL_CONTENT_DIR, true);
  }

  public void testDisappearingContentFileWithRead() throws Exception {
    testDisappearingFile(DEL_CONTENT_FILE, true);
  }

  public void testDisappearingPropsFileWithRead() throws Exception {
    testDisappearingFile(DEL_PROPS_FILE, true);
  }

  public void testMakeNewVersionWithoutClosingStream() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream 1", new Properties());

    leaf.makeNewVersion();
    leaf.setNewProperties(new Properties());
    OutputStream os = leaf.getNewOutputStream();
    InputStream is = new StringInputStream("test stream 2");
    StreamUtil.copy(is, os);
    is.close();
    // don't close outputstream
    leaf.sealNewVersion();
    assertEquals(2, leaf.getCurrentVersion());
    String resultStr = getLeafContent(leaf);
    assertEquals("test stream 2", resultStr);
  }

  public void testMakeNewIdenticalVersionDefault() throws Exception {
    Properties props = new Properties();
    props.setProperty("test 1", "value 1");
    MyMockRepositoryNode leaf = new MyMockRepositoryNode(
        (RepositoryNodeImpl)createLeaf(
        "http://www.example.com/testDir/test.cache", "test stream", props));
    assertEquals(1, leaf.getCurrentVersion());
    // set the file extension
    leaf.dateValue = 123321;

    props = new Properties();
    props.setProperty("test 1", "value 2");
    leaf.makeNewVersion();
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream");
    leaf.sealNewVersion();
    assertEquals(1, leaf.getCurrentVersion());

    String resultStr = getLeafContent(leaf);
    assertEquals("test stream", resultStr);
    props = leaf.getNodeContents().getProperties();
    assertEquals("value 2", props.getProperty("test 1"));

    // make sure proper files exist
    tempDirPath = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath, mau);
    tempDirPath = LockssRepositoryImpl.mapUrlToFileLocation(tempDirPath,
        "http://www.example.com/testDir/test.cache");

    File testFileDir = new File(tempDirPath + "/#content");
    File[] files = testFileDir.listFiles();
    assertEquals(2, files.length);
    File testFile = new File(testFileDir, "current");
    assertTrue(testFile.exists());
    testFile = new File(testFileDir, "current.props");
    assertTrue(testFile.exists());
//    testFile = new File(testFileDir, "1.props-123321");
//    assertFalse(testFile.exists());
  }

  public void testMakeNewIdenticalVersionOldWay() throws Exception {
    props.setProperty(RepositoryNodeImpl.PARAM_KEEP_ALL_PROPS_FOR_DUPE_FILE,
                      "true");
    ConfigurationUtil.setCurrentConfigFromProps(props);

    Properties props = new Properties();
    props.setProperty("test 1", "value 1");
    MyMockRepositoryNode leaf = new MyMockRepositoryNode(
        (RepositoryNodeImpl)createLeaf(
        "http://www.example.com/testDir/test.cache", "test stream", props));
    assertEquals(1, leaf.getCurrentVersion());
    // set the file extension
    leaf.dateValue = 123321;

    props = new Properties();
    props.setProperty("test 1", "value 2");
    leaf.makeNewVersion();
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream");
    leaf.sealNewVersion();
    assertEquals(1, leaf.getCurrentVersion());

    String resultStr = getLeafContent(leaf);
    assertEquals("test stream", resultStr);
    props = leaf.getNodeContents().getProperties();
    assertEquals("value 2", props.getProperty("test 1"));

    // make sure proper files exist
    tempDirPath = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath, mau);
    tempDirPath = LockssRepositoryImpl.mapUrlToFileLocation(tempDirPath,
        "http://www.example.com/testDir/test.cache");

    File testFileDir = new File(tempDirPath + "/#content");
    File[] files = testFileDir.listFiles();
    assertEquals(3, files.length);
    File testFile = new File(testFileDir, "current");
    assertTrue(testFile.exists());
    testFile = new File(testFileDir, "current.props");
    assertTrue(testFile.exists());
    testFile = new File(testFileDir, "1.props-123321");
    assertTrue(testFile.exists());
  }

  public void testMakeNewIdenticalVersionNewWay() throws Exception {
    props.setProperty(RepositoryNodeImpl.PARAM_KEEP_ALL_PROPS_FOR_DUPE_FILE,
                      "false");
    ConfigurationUtil.setCurrentConfigFromProps(props);

    Properties props = new Properties();
    props.setProperty("test 1", "value 1");
    MyMockRepositoryNode leaf = new MyMockRepositoryNode(
        (RepositoryNodeImpl)createLeaf(
        "http://www.example.com/testDir/test.cache", "test stream", props));
    assertEquals(1, leaf.getCurrentVersion());
    // set the file extension
    leaf.dateValue = 123321;

    props = new Properties();
    props.setProperty("test 1", "value 2");
    leaf.makeNewVersion();
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream");
    leaf.sealNewVersion();
    assertEquals(1, leaf.getCurrentVersion());

    String resultStr = getLeafContent(leaf);
    assertEquals("test stream", resultStr);
    props = leaf.getNodeContents().getProperties();
    assertEquals("value 2", props.getProperty("test 1"));

    // make sure proper files exist
    tempDirPath = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath, mau);
    tempDirPath = LockssRepositoryImpl.mapUrlToFileLocation(tempDirPath,
        "http://www.example.com/testDir/test.cache");

    File testFileDir = new File(tempDirPath + "/#content");
    File[] files = testFileDir.listFiles();
    assertEquals(2, files.length);
    File testFile = new File(testFileDir, "current");
    assertTrue(testFile.exists());
    testFile = new File(testFileDir, "current.props");
    assertTrue(testFile.exists());
//    testFile = new File(testFileDir, "1.props-123321");
//    assertFalse(testFile.exists());
  }

  public void testIdenticalVersionFixesVersionError() throws Exception {
    Properties props = new Properties();
    MyMockRepositoryNode leaf = new MyMockRepositoryNode(
        (RepositoryNodeImpl)createLeaf(
        "http://www.example.com/testDir/test.cache", "test stream", props));
    assertEquals(1, leaf.getCurrentVersion());

    props = new Properties();
    leaf.makeNewVersion();
    leaf.setNewProperties(props);
    // set to error state
    leaf.currentVersion = 0;
    writeToLeaf(leaf, "test stream");
    assertEquals(0, leaf.currentVersion);
    leaf.sealNewVersion();
    // fixes error state, even though identical
    assertEquals(1, leaf.getCurrentVersion());
  }

  public void testMakeNewVersionFixesVersionError() throws Exception {
    Properties props = new Properties();
    MyMockRepositoryNode leaf = new MyMockRepositoryNode(
        (RepositoryNodeImpl)createLeaf(
        "http://www.example.com/testDir/test.cache", "test stream", props));
    assertEquals(1, leaf.getCurrentVersion());

    props = new Properties();
    leaf.makeNewVersion();
    // set to error state
    leaf.currentVersion = -1;
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream2");
    leaf.sealNewVersion();
    // fixes error state
    assertEquals(1, leaf.getCurrentVersion());
  }

  public void testGetInputStream1() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream", null);
    String resultStr = getLeafContent(leaf);
    assertEquals("test stream", resultStr);
  }

  public void testGetProperties1() throws Exception {
    Properties props = new Properties();
    props.setProperty("test 1", "value 1");
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream", props);

    RepositoryNode.RepositoryNodeContents contents = leaf.getNodeContents();
    props = contents.getProperties();
    // close stream to allow the file to be renamed later
    // XXX 'getProperties()' creates an input stream, and 'release()' just
    // sets it to null.  The rename still fails in Windows unless the stream
    // is closed first.
    contents.getInputStream().close();
    contents.release();

    assertEquals("value 1", props.getProperty("test 1"));

    leaf.makeNewVersion();
    props = new Properties();
    props.setProperty("test 1", "value 2");
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream 2");
    leaf.sealNewVersion();

    props = leaf.getNodeContents().getProperties();
    assertEquals("value 2", props.getProperty("test 1"));
  }

  RepositoryNode createNodeWithCorruptProps(String url) throws Exception {
    Properties props = new Properties();
    props.setProperty("test 1", "value 1");
    RepositoryNode leaf = createLeaf(url, "test stream", props);

    RepositoryNodeImpl leafImpl = (RepositoryNodeImpl)leaf;
    File propsFile = new File(leafImpl.getContentDir(),
			      RepositoryNodeImpl.CURRENT_PROPS_FILENAME);
    // Write a Malformed unicode escape that will cause Properties.load()
    // to throw
    OutputStream os =
      new BufferedOutputStream(new FileOutputStream(propsFile, true));
    os.write("\\uxxxxfoo=bar".getBytes());
    os.close();
    return leaf;
  }

  public void testCorruptProperties1() throws Exception {
    RepositoryNode leaf =
      createNodeWithCorruptProps("http://www.example.com/testDir/test.cache");

    assertFalse(leaf.hasContent());
    assertTrue(leaf.isDeleted());
    leaf.makeNewVersion();
    writeToLeaf(leaf, "test stream");
    leaf.setNewProperties(new Properties());
    leaf.sealNewVersion();
    assertTrue(leaf.hasContent());
    assertFalse(leaf.isDeleted());
  }

  public void testCorruptProperties2() throws Exception {
    String stem = "http://www.example.com/testDir";
    RepositoryNode leaf = createNodeWithCorruptProps(stem + "/test.cache");
    RepositoryNode leaf2 = createLeaf(stem + "/foo", "test stream", props);

    RepositoryNode dirEntry =
        repo.getNode("http://www.example.com/testDir");
    Iterator childIt = dirEntry.listChildren(null, false);
    assertEquals(ListUtil.list(leaf2), ListUtil.fromIterator(childIt));
  }

  static String cntnt(int ix) {
    return "content " + ix + "ABCDEFGHIJKLMNOPQRSTUVWXYZ".substring(0, ix);
  }
  static int lngth(int ix) {
    return cntnt(ix).length();
  }

  public void testGetNodeVersion() throws Exception {
    int max = 5;
    String url = "http://www.example.com/versionedcontent.txt";
    String key = "key";
    String val = "grrl";
    Properties props = new Properties();

    RepositoryNode leaf = repo.createNewNode(url);
    // create several versions
    for (int ix = 1; ix <= max; ix++) {
      props.setProperty(key, val+ix);
      createContentVersion(leaf, cntnt(ix), props);
    }
    // getNodeVersion(current) should return the main node
    assertEquals(leaf, leaf.getNodeVersion(leaf.getCurrentVersion()));

    // loop through other versions checking version, content, props
    for (int ix = 1; ix < max; ix++) {
      RepositoryNodeVersion nodeVer = leaf.getNodeVersion(ix);
      log.debug("ver: " + nodeVer.getVersion() + ", content: " +
		getLeafContent(nodeVer));
      assertEquals(ix, nodeVer.getVersion());

      assertEquals(cntnt(ix), getLeafContent(nodeVer));
      assertEquals(lngth(ix), nodeVer.getContentSize());
      props = nodeVer.getNodeContents().getProperties();
      assertEquals(val+ix, props.getProperty(key));
    }
  }

  public void testGetNodeVersions() throws Exception {
    int max = 5;
    String url = "http://www.example.com/versionedcontent.txt";
    String key = "key";
    String val = "grrl";
    Properties props = new Properties();

    RepositoryNode leaf = repo.createNewNode(url);
    // create several versions
    for (int ix = 1; ix <= max; ix++) {
      props.setProperty(key, val+ix);
      createContentVersion(leaf, cntnt(ix), props);
    }
    // check expected current version number
    assertEquals(max, leaf.getCurrentVersion());
    assertEquals(max, leaf.getVersion());

    // checking version, content, props of current version
    assertEquals(cntnt(max), getLeafContent(leaf));
    assertEquals(lngth(max), leaf.getContentSize());
    props = leaf.getNodeContents().getProperties();
    assertEquals(val+max, props.getProperty(key));

    // ask for all older versions
    RepositoryNodeVersion[] vers = leaf.getNodeVersions();
    assertEquals(max, vers.length);
    // loop through them checking version, content, props
    for (int ix = 0; ix < max-1; ix++) {
      int exp = max - ix;
      RepositoryNodeVersion nodeVer = vers[ix];
      log.debug("ver: " + nodeVer.getVersion() + ", content: " +
		getLeafContent(nodeVer));
      assertEquals(exp, nodeVer.getVersion());

      assertEquals(cntnt(exp), getLeafContent(nodeVer));
      assertEquals(lngth(exp), nodeVer.getContentSize());
      props = nodeVer.getNodeContents().getProperties();
      assertEquals(val+exp, props.getProperty(key));
    }

    // now ask for and check a subset of the older versions
    assertTrue("max must be at least 4 for this test", max >= 4);
    int numver = max - 2;
    vers = leaf.getNodeVersions(numver);
    assertEquals(numver, vers.length);
    for (int ix = 0; ix < numver-1; ix++) {
      int exp = max - ix;
      RepositoryNodeVersion nodeVer = vers[ix];
      log.debug("ver: " + nodeVer.getVersion() + ", content: " +
		getLeafContent(nodeVer));
      assertEquals(exp, nodeVer.getVersion());

      assertEquals(cntnt(exp), getLeafContent(nodeVer));
      assertEquals(lngth(exp), nodeVer.getContentSize());
      props = nodeVer.getNodeContents().getProperties();
      assertEquals(val+exp, props.getProperty(key));
    }
  }

  public void testIllegalVersionOperations() throws Exception {
    RepositoryNode.RepositoryNodeContents rnc;
    RepositoryNodeVersion nv;

    RepositoryNode leaf =
      repo.createNewNode("http://www.example.com/testDir/test.cache");
    try {
      nv = leaf.getNodeVersion(7);
      fail("No content, shouldn't be able to get versioned node: " + nv);
    } catch (UnsupportedOperationException e) { }
    // create first version
    Properties props = new Properties();
    props.setProperty("key", "val1");
    createContentVersion(leaf, cntnt(1), props);

    // We're allowed to get a RepositoryNodeVersion when the version
    // doesn't exist ...
    nv = leaf.getNodeVersion(7);
    // but all operations on it should throw
    try {
      nv.getContentSize();
      fail("No version; shouldn't get content size");
    } catch (UnsupportedOperationException e) { }
    try {
      rnc = nv.getNodeContents();
      fail("No version; shouldn't get RepositoryNodeContents");
    } catch (UnsupportedOperationException e) { }
  }

  public void testDirContent() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream", null);
    assertTrue(leaf.hasContent());

    RepositoryNode dir =
        repo.getNode("http://www.example.com/testDir");
    dir.makeNewVersion();
    writeToLeaf(dir, "test stream");
    dir.setNewProperties(new Properties());
    dir.sealNewVersion();
    assertTrue(dir.hasContent());

    dir = createLeaf("http://www.example.com/testDir/test.cache/new.test",
                     "test stream", null);
    assertTrue(dir.hasContent());
  }

  public void testNodeSize() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream", null);
    assertTrue(leaf.hasContent());
    assertEquals(11, (int)leaf.getContentSize());
  }

  public void testTreeSize() throws Exception {
    createLeaf("http://www.example.com/testDir", "test", null);
    createLeaf("http://www.example.com/testDir/test1", "test1", null);
    createLeaf("http://www.example.com/testDir/test2", "test2", null);
    createLeaf("http://www.example.com/testDir/test3/branch1",
               "test33", null);
    createLeaf("http://www.example.com/testDir/test3/branch2",
               "test33", null);

    RepositoryNode leaf = repo.getNode("http://www.example.com/testDir");
    assertEquals(-1, leaf.getTreeContentSize(null, false));
    assertEquals(26, leaf.getTreeContentSize(null, true));
    assertEquals(26, leaf.getTreeContentSize(null, false));
    leaf = repo.getNode("http://www.example.com/testDir/test1");
    assertEquals(5, leaf.getTreeContentSize(null, true));
    leaf = repo.getNode("http://www.example.com/testDir/test3");
    assertEquals(12, leaf.getTreeContentSize(null, true));
    CachedUrlSetSpec cuss =
      new RangeCachedUrlSetSpec("http://www.example.com/testDir/test3",
				"/branch1", "/branch1");
    assertEquals(6, leaf.getTreeContentSize(cuss, true));
  }

  public void testDetermineParentNode() throws Exception {
    repo.createNewNode("http://www.example.com");
    repo.createNewNode("http://www.example.com/test");
    assertNotNull(repo.getNode("http://www.example.com/test"));
    RepositoryNodeImpl node = (RepositoryNodeImpl)repo.createNewNode(
      "http://www.example.com/test/branch");
    assertEquals("http://www.example.com/test/branch", node.getNodeUrl());
    node = node.determineParentNode();
    assertEquals("http://www.example.com/test", node.getNodeUrl());
    node = node.determineParentNode();
    assertEquals("http://www.example.com", node.getNodeUrl());
    node = node.determineParentNode();
    assertEquals(AuUrl.PROTOCOL, node.getNodeUrl());
    node = node.determineParentNode();
    assertEquals(AuUrl.PROTOCOL, node.getNodeUrl());
  }

  public void testCacheInvalidation() throws Exception {
    RepositoryNodeImpl root =
        (RepositoryNodeImpl)createLeaf("http://www.example.com",
                                       "test", null);
    RepositoryNodeImpl branch =
        (RepositoryNodeImpl)createLeaf("http://www.example.com/branch",
                                       "test", null);
    RepositoryNodeImpl branch2 =
        (RepositoryNodeImpl)createLeaf("http://www.example.com/branch/branch2",
                                       "test", null);
    // This one has directory level with no node prop file, to check that
    // cache invalidation traverses them correctly
    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)createLeaf("http://www.example.com/branch/branch2/a/b/c/leaf",
                                       "test", null);
    assertNull(branch.nodeProps.getProperty(TREE_SIZE_PROPERTY));
    assertNull(leaf.nodeProps.getProperty(TREE_SIZE_PROPERTY));
    // force invalidation to happen
    branch.nodeProps.setProperty(TREE_SIZE_PROPERTY, "789");
    branch.invalidateCachedValues(true);
    // should now be explicitly marked invalid
    assertEquals(RepositoryNodeImpl.INVALID,
		 branch.nodeProps.getProperty(TREE_SIZE_PROPERTY));
    assertEquals(RepositoryNodeImpl.INVALID,
		 branch.nodeProps.getProperty(CHILD_COUNT_PROPERTY));
    // fake prop set at root to check invalidation stops properly
    root.nodeProps.setProperty(TREE_SIZE_PROPERTY, "789");
    root.nodeProps.setProperty(CHILD_COUNT_PROPERTY, "3");
    // don't set branch so the invalidate stops there
    branch2.nodeProps.setProperty(TREE_SIZE_PROPERTY, "456");
    branch2.nodeProps.setProperty(CHILD_COUNT_PROPERTY, "1");
    leaf.nodeProps.setProperty(TREE_SIZE_PROPERTY, "123");
    leaf.nodeProps.setProperty(CHILD_COUNT_PROPERTY, "0");

    leaf.invalidateCachedValues(true);
    // shoulddn't be set here anymore
    assertFalse(isPropValid(leaf.nodeProps.getProperty(TREE_SIZE_PROPERTY)));
    assertFalse(isPropValid(leaf.nodeProps.getProperty(CHILD_COUNT_PROPERTY)));
    // or here (requires recursing up through dirs that have no node props
    // file)
    assertFalse(isPropValid(branch2.nodeProps.getProperty(TREE_SIZE_PROPERTY)));
    assertFalse(isPropValid(branch2.nodeProps.getProperty(CHILD_COUNT_PROPERTY)));
    // still invalid, recursion should have stopped here
    assertFalse(isPropValid(branch.nodeProps.getProperty(TREE_SIZE_PROPERTY)));
    assertFalse(isPropValid(branch.nodeProps.getProperty(CHILD_COUNT_PROPERTY)));
    // so not cleared these
    assertTrue(isPropValid(root.nodeProps.getProperty(TREE_SIZE_PROPERTY)));
    assertTrue(isPropValid(root.nodeProps.getProperty(CHILD_COUNT_PROPERTY)));
    assertEquals("789", root.nodeProps.getProperty(TREE_SIZE_PROPERTY));
    assertEquals("3", root.nodeProps.getProperty(CHILD_COUNT_PROPERTY));
  }

  boolean isPropValid(String val) {
    return RepositoryNodeImpl.isPropValid(val);
  }

  boolean isPropInvalid(String val) {
    return RepositoryNodeImpl.isPropInvalid(val);
  }

  public void testTreeSizeCaching() throws Exception {
    createLeaf("http://www.example.com/testDir", "test", null);

    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)repo.getNode("http://www.example.com/testDir");
    assertNull(leaf.nodeProps.getProperty(TREE_SIZE_PROPERTY));
    assertEquals(4, leaf.getTreeContentSize(null, true));
    assertEquals("4", leaf.nodeProps.getProperty(TREE_SIZE_PROPERTY));
    leaf.markAsDeleted();
    assertTrue(isPropInvalid(leaf.nodeProps.getProperty(TREE_SIZE_PROPERTY)));
    assertEquals(0, leaf.getTreeContentSize(null, true));
    assertEquals("0", leaf.nodeProps.getProperty(TREE_SIZE_PROPERTY));
  }

  public void testChildCount() throws Exception {
    createLeaf("http://www.example.com/testDir", "test", null);

    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)repo.getNode("http://www.example.com/testDir");
    assertNull(leaf.nodeProps.getProperty(CHILD_COUNT_PROPERTY));
    assertEquals(0, leaf.getChildCount());
    assertEquals("0", leaf.nodeProps.getProperty(CHILD_COUNT_PROPERTY));

    createLeaf("http://www.example.com/testDir/test1", "test1", null);
    createLeaf("http://www.example.com/testDir/test2", "test2", null);
    assertEquals(2, leaf.getChildCount());
    assertEquals("2", leaf.nodeProps.getProperty(CHILD_COUNT_PROPERTY));
  }

  public void testDeactivate() throws Exception {
    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)createLeaf("http://www.example.com/test1",
                                       "test stream", null);
    assertTrue(leaf.hasContent());
    assertFalse(leaf.isContentInactive());
    assertEquals(1, leaf.getCurrentVersion());
    assertNull(leaf.nodeProps.getProperty(RepositoryNodeImpl.INACTIVE_CONTENT_PROPERTY));

    leaf.deactivateContent();
    assertFalse(leaf.hasContent());
    assertTrue(leaf.isContentInactive());
    assertEquals(RepositoryNodeImpl.INACTIVE_VERSION, leaf.getCurrentVersion());
    assertEquals("true", leaf.nodeProps.getProperty(RepositoryNodeImpl.INACTIVE_CONTENT_PROPERTY));
  }

  public void testDelete1() throws Exception {
    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)createLeaf("http://www.example.com/test1",
                                       "test stream", null);
    assertTrue(leaf.hasContent());
    assertFalse(leaf.isDeleted());
    assertEquals(1, leaf.getCurrentVersion());
    assertNull(leaf.nodeProps.getProperty(RepositoryNodeImpl.DELETION_PROPERTY));

    leaf.markAsDeleted();
    assertFalse(leaf.hasContent());
    assertTrue(leaf.isDeleted());
    assertEquals(RepositoryNodeImpl.DELETED_VERSION, leaf.getCurrentVersion());
    assertEquals("true", leaf.nodeProps.getProperty(RepositoryNodeImpl.DELETION_PROPERTY));
  }

  public void testUnDelete() throws Exception {
    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)createLeaf("http://www.example.com/test1",
                                       "test stream", null);
    leaf.markAsDeleted();
    assertTrue(leaf.isDeleted());
    assertEquals(RepositoryNodeImpl.DELETED_VERSION, leaf.getCurrentVersion());

    leaf.markAsNotDeleted();
    assertFalse(leaf.isContentInactive());
    assertFalse(leaf.isDeleted());
    assertEquals(1, leaf.getCurrentVersion());
    // make to null, not 'false'
    assertNull(leaf.nodeProps.getProperty(RepositoryNodeImpl.DELETION_PROPERTY));
    String resultStr = getLeafContent(leaf);
    assertEquals("test stream", resultStr);
  }

  public void testRestoreLastVersion() throws Exception {
    Properties props = new Properties();
    props.setProperty("test 1", "value 1");
    RepositoryNode leaf =
        createLeaf("http://www.example.com/test1", "test stream 1", props);
    assertEquals(1, leaf.getCurrentVersion());

    props = new Properties();
    props.setProperty("test 1", "value 2");
    leaf.makeNewVersion();
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream 2");
    leaf.sealNewVersion();
    assertEquals(2, leaf.getCurrentVersion());

    leaf.restoreLastVersion();
    assertEquals(1, leaf.getCurrentVersion());

    String resultStr = getLeafContent(leaf);
    assertEquals("test stream 1", resultStr);
    props = leaf.getNodeContents().getProperties();
    assertEquals("value 1", props.getProperty("test 1"));
  }

  public void testReactivateViaRestore() throws Exception {
    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)createLeaf("http://www.example.com/test1",
                                       "test stream", null);
    leaf.deactivateContent();
    assertTrue(leaf.isContentInactive());
    assertEquals(RepositoryNodeImpl.INACTIVE_VERSION, leaf.getCurrentVersion());

    leaf.restoreLastVersion();
    assertFalse(leaf.isContentInactive());
    assertEquals(1, leaf.getCurrentVersion());
    // back to null, not 'false'
    assertNull(leaf.nodeProps.getProperty(RepositoryNodeImpl.INACTIVE_CONTENT_PROPERTY));
    String resultStr = getLeafContent(leaf);
    assertEquals("test stream", resultStr);
  }

  public void testReactivateViaNewVersion() throws Exception {
    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)createLeaf("http://www.example.com/test1",
                                       "test stream", null);
    leaf.deactivateContent();
    assertTrue(leaf.isContentInactive());
    assertEquals(RepositoryNodeImpl.INACTIVE_VERSION, leaf.getCurrentVersion());

    Properties props = new Properties();
    props.setProperty("test 1", "value 2");
    leaf.makeNewVersion();
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream 2");
    leaf.sealNewVersion();
    assertFalse(leaf.isContentInactive());
    assertEquals(2, leaf.getCurrentVersion());
    String resultStr = getLeafContent(leaf);
    assertEquals("test stream 2", resultStr);

    File lastProps = new File(leaf.contentDir, "1.props");
    assertTrue(lastProps.exists());
    InputStream is =
        new BufferedInputStream(new FileInputStream(lastProps));
    props.load(is);
    is.close();
    // make sure the 'was inactive' property hasn't been lost
    assertEquals("true",
                 props.getProperty(RepositoryNodeImpl.NODE_WAS_INACTIVE_PROPERTY));
  }

  public void testAbandonReactivateViaNewVersion() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/test1", "test stream", null);
    leaf.deactivateContent();
    assertTrue(leaf.isContentInactive());
    assertEquals(RepositoryNodeImpl.INACTIVE_VERSION, leaf.getCurrentVersion());

    Properties props = new Properties();
    props.setProperty("test 1", "value 2");
    leaf.makeNewVersion();
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream 2");
    leaf.abandonNewVersion();
    assertTrue(leaf.isContentInactive());
    assertEquals(RepositoryNodeImpl.INACTIVE_VERSION, leaf.getCurrentVersion());
  }

  public void testIsLeaf() throws Exception {
    createLeaf("http://www.example.com/testDir/test1", "test stream", null);
    createLeaf("http://www.example.com/testDir/branch1", "test stream", null);
    createLeaf("http://www.example.com/testDir/branch1/test4", "test stream", null);

    RepositoryNode leaf = repo.getNode("http://www.example.com/testDir/test1");
    assertTrue(leaf.isLeaf());
    leaf = repo.getNode("http://www.example.com/testDir/branch1");
    assertFalse(leaf.isLeaf());
  }

  public void testListInactiveNodes() throws Exception {
    createLeaf("http://www.example.com/testDir/test1", "test stream", null);
    createLeaf("http://www.example.com/testDir/test2", "test stream", null);
    createLeaf("http://www.example.com/testDir/test3", "test stream", null);
    createLeaf("http://www.example.com/testDir/branch1", "test stream", null);
    createLeaf("http://www.example.com/testDir/branch1/test4", "test stream", null);
    createLeaf("http://www.example.com/testDir/branch2", "test stream", null);
    createLeaf("http://www.example.com/testDir/branch2/test5", "test stream", null);

    RepositoryNode dirEntry = repo.getNode("http://www.example.com/testDir");
    Iterator childIt = dirEntry.listChildren(null, false);
    ArrayList childL = new ArrayList(3);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    String[] expectedA = new String[] {
      "http://www.example.com/testDir/branch1",
      "http://www.example.com/testDir/branch2",
      "http://www.example.com/testDir/test1",
      "http://www.example.com/testDir/test2",
      "http://www.example.com/testDir/test3"
      };
    assertIsomorphic(expectedA, childL);

    RepositoryNode leaf = repo.getNode("http://www.example.com/testDir/test2");
    leaf.deactivateContent();
    // this next shouldn't be excluded since it isn't a leaf node
    leaf = repo.getNode("http://www.example.com/testDir/branch1");
    leaf.deactivateContent();
    // this next should be excluded because it's deleted
    leaf = repo.getNode("http://www.example.com/testDir/branch2");
    leaf.markAsDeleted();

    childIt = dirEntry.listChildren(null, false);
    childL = new ArrayList(2);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    expectedA = new String[] {
      "http://www.example.com/testDir/branch1",
      "http://www.example.com/testDir/test1",
      "http://www.example.com/testDir/test3"
      };
    assertIsomorphic("Excluding inactive nodes failed.", expectedA, childL);

    childIt = dirEntry.listChildren(null, true);
    childL = new ArrayList(3);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    expectedA = new String[] {
      "http://www.example.com/testDir/branch1",
      "http://www.example.com/testDir/test1",
      "http://www.example.com/testDir/test2",
      "http://www.example.com/testDir/test3"
      };
    assertIsomorphic("Including inactive nodes failed.", expectedA, childL);
  }

  public void testDeleteInnerNode() throws Exception {
    createLeaf("http://www.example.com/testDir/test1", "test stream", null);
    createLeaf("http://www.example.com/testDir/test2", "test stream", null);

    RepositoryNode dirEntry = repo.getNode("http://www.example.com/testDir");
    assertFalse(dirEntry.isDeleted());
    dirEntry.markAsDeleted();
    assertTrue(dirEntry.isDeleted());
    dirEntry.markAsNotDeleted();
    assertFalse(dirEntry.isDeleted());
  }

  public void testGetFileStrings() throws Exception {
    RepositoryNodeImpl node = (RepositoryNodeImpl)repo.createNewNode(
        "http://www.example.com/test.url");
    node.initNodeRoot();
    String contentStr = FileUtil.sysDepPath(node.nodeLocation + "/#content");
    assertEquals(contentStr, node.getContentDir().toString());
    contentStr = contentStr + File.separator;
    String expectedStr = contentStr + "123";
    assertEquals(expectedStr,
                 node.getVersionedCacheFile(123).getAbsolutePath());
    expectedStr = contentStr + "123.props";
    assertEquals(expectedStr,
                 node.getVersionedPropsFile(123).getAbsolutePath());
    expectedStr = contentStr + "inactive";
    assertEquals(expectedStr, node.getInactiveCacheFile().getAbsolutePath());
    expectedStr = contentStr + "inactive.props";
    assertEquals(expectedStr, node.getInactivePropsFile().getAbsolutePath());
  }

  public void testCheckNodeConsistency() throws Exception {
    // check returns proper values for errors
    MyMockRepositoryNode leaf = new MyMockRepositoryNode(
        (RepositoryNodeImpl)repo.createNewNode("http://www.example.com/testDir"));
    leaf.makeNewVersion();
    // should abort and return true since version open
    leaf.failRootConsist = true;
    assertTrue(leaf.checkNodeConsistency());

    // finish write
    leaf.setNewProperties(new Properties());
    writeToLeaf(leaf, "test stream");
    leaf.sealNewVersion();

    // should return false if node root fails
    assertFalse(leaf.checkNodeConsistency());
    leaf.failRootConsist = false;
    assertTrue(leaf.checkNodeConsistency());

    // check returns false if content fails
    leaf.failContentConsist = true;
    assertFalse(leaf.checkNodeConsistency());
    leaf.failContentConsist = false;
    assertTrue(leaf.checkNodeConsistency());

    // check returns false if current info load fails
    leaf.failEnsureCurrentLoaded = true;
    assertFalse(leaf.checkNodeConsistency());
    leaf.failEnsureCurrentLoaded = false;
    assertTrue(leaf.checkNodeConsistency());
  }

  public void testCheckNodeRootConsistency() throws Exception {
    MyMockRepositoryNode leaf = new MyMockRepositoryNode(
        (RepositoryNodeImpl)repo.createNewNode("http://www.example.com/testDir"));
    leaf.createNodeLocation();
    assertTrue(leaf.nodeRootFile.exists());
    // returns true when normal
    assertTrue(leaf.checkNodeRootConsistency());

    leaf.nodeRootFile.delete();
    assertFalse(leaf.nodeRootFile.exists());
    // creates dir, returns true when missing
    assertTrue(leaf.checkNodeRootConsistency());
    assertTrue(leaf.nodeRootFile.exists());
    assertTrue(leaf.nodeRootFile.isDirectory());

    // fail node props load
    leaf.getChildCount();
    assertTrue(leaf.nodePropsFile.exists());
    File renameFile = new File(leaf.nodePropsFile.getAbsolutePath()+
                               RepositoryNodeImpl.FAULTY_FILE_EXTENSION);
    assertFalse(renameFile.exists());
    leaf.failPropsLoad = true;
    assertTrue(leaf.checkNodeRootConsistency());
    assertFalse(leaf.nodePropsFile.exists());
    assertTrue(renameFile.exists());
  }

  public void testCheckContentConsistency() throws Exception {
    MyMockRepositoryNode leaf = new MyMockRepositoryNode(
        (RepositoryNodeImpl)createLeaf("http://www.example.com/testDir",
        "test stream", null));
    leaf.ensureCurrentInfoLoaded();

    // should return false if content dir fails
    MyMockRepositoryNode.failEnsureDirExists = true;
    assertFalse(leaf.checkContentConsistency());
    MyMockRepositoryNode.failEnsureDirExists = false;
    assertTrue(leaf.checkContentConsistency());

    // should return false if content file absent
    File renameFile =
        new File(leaf.currentCacheFile.getAbsolutePath()+"RENAME");
    assertTrue(PlatformUtil.updateAtomically(leaf.currentCacheFile, renameFile));
    assertFalse(leaf.checkContentConsistency());
    PlatformUtil.updateAtomically(renameFile, leaf.currentCacheFile);
    assertTrue(leaf.checkContentConsistency());

    // should return false if content props absent
    PlatformUtil.updateAtomically(leaf.currentPropsFile, renameFile);
    assertFalse(leaf.checkContentConsistency());
    PlatformUtil.updateAtomically(renameFile, leaf.currentPropsFile);
    assertTrue(leaf.checkContentConsistency());

    // should return false if inactive and files missing
    leaf.currentVersion = RepositoryNodeImpl.INACTIVE_VERSION;
    assertFalse(leaf.checkContentConsistency());
    PlatformUtil.updateAtomically(leaf.currentPropsFile, leaf.getInactivePropsFile());
    assertFalse(leaf.checkContentConsistency());
    PlatformUtil.updateAtomically(leaf.currentCacheFile, leaf.getInactiveCacheFile());
    assertTrue(leaf.checkContentConsistency());
    PlatformUtil.updateAtomically(leaf.getInactivePropsFile(), leaf.currentPropsFile);
    assertFalse(leaf.checkContentConsistency());
    // finish restoring
    PlatformUtil.updateAtomically(leaf.getInactiveCacheFile(), leaf.currentCacheFile);
    leaf.currentVersion = 1;
    assertTrue(leaf.checkContentConsistency());

    // remove residual files
    // - create files
    FileOutputStream fos = new FileOutputStream(leaf.tempCacheFile);
    StringInputStream sis = new StringInputStream("test stream");
    StreamUtil.copy(sis, fos);
    fos.close();
    sis.close();

    fos = new FileOutputStream(leaf.tempPropsFile);
    sis = new StringInputStream("test stream");
    StreamUtil.copy(sis, fos);
    fos.close();
    sis.close();

    // should be removed
    assertTrue(leaf.tempCacheFile.exists());
    assertTrue(leaf.tempPropsFile.exists());
    assertTrue(leaf.checkContentConsistency());
    assertFalse(leaf.tempCacheFile.exists());
    assertFalse(leaf.tempPropsFile.exists());
}


  public void testEnsureDirExists() throws Exception {
    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)createLeaf("http://www.example.com", null, null);
    File testDir = new File(tempDirPath, "testDir");
    // should return true if dir exists
    testDir.mkdir();
    assertTrue(testDir.exists());
    assertTrue(testDir.isDirectory());
    assertTrue(leaf.ensureDirExists(testDir));

    // should create dir, return true if not exists
    testDir.delete();
    assertFalse(testDir.exists());
    assertTrue(leaf.ensureDirExists(testDir));
    assertTrue(testDir.exists());
    assertTrue(testDir.isDirectory());

    // should rename file, create dir, return true if file exists
    // -create file
    testDir.delete();
    FileOutputStream fos = new FileOutputStream(testDir);
    StringInputStream sis = new StringInputStream("test stream");
    StreamUtil.copy(sis, fos);
    fos.close();
    sis.close();
    assertTrue(testDir.exists());
    assertTrue(testDir.isFile());

    // rename via 'ensureDirExists()'
    File renameFile = new File(tempDirPath, "testDir"+
        RepositoryNodeImpl.FAULTY_FILE_EXTENSION);
    assertFalse(renameFile.exists());
    assertTrue(leaf.ensureDirExists(testDir));
    assertTrue(testDir.isDirectory());
    assertEquals("test stream", StringUtil.fromFile(renameFile));
  }

  public void testCheckFileExists() throws Exception {
    // return false if doesn't exist
    File testFile = new File(tempDirPath, "testFile");
    assertFalse(testFile.exists());
    assertFalse(RepositoryNodeImpl.checkFileExists(testFile, "test file"));

    // rename if dir (to make room for file creation), then return false
    testFile.mkdir();
    File renameDir = new File(tempDirPath, "testFile"+
        RepositoryNodeImpl.FAULTY_FILE_EXTENSION);
    assertTrue(testFile.exists());
    assertTrue(testFile.isDirectory());
    assertFalse(renameDir.exists());
    assertFalse(RepositoryNodeImpl.checkFileExists(testFile, "test file"));
    assertFalse(testFile.exists());
    assertTrue(renameDir.exists());
    assertTrue(renameDir.isDirectory());

    // return true if exists
    FileOutputStream fos = new FileOutputStream(testFile);
    StringInputStream sis = new StringInputStream("test stream");
    StreamUtil.copy(sis, fos);
    fos.close();
    sis.close();
    assertTrue(testFile.exists());
    assertTrue(testFile.isFile());
    assertTrue(RepositoryNodeImpl.checkFileExists(testFile, "test file"));
    assertEquals("test stream", StringUtil.fromFile(testFile));
  }

  public void testCheckChildCountCacheAccuracy() throws Exception {
    createLeaf("http://www.example.com/testDir/branch2", "test stream", null);
    createLeaf("http://www.example.com/testDir/branch3", "test stream", null);

    RepositoryNodeImpl dirEntry =
        (RepositoryNodeImpl)repo.getNode("http://www.example.com/testDir");
    assertEquals(2, dirEntry.getChildCount());
    assertEquals("2",
        dirEntry.nodeProps.getProperty(RepositoryNodeImpl.CHILD_COUNT_PROPERTY));

    // check that no change to valid count cache
    dirEntry.checkChildCountCacheAccuracy();
    assertEquals("2",
        dirEntry.nodeProps.getProperty(RepositoryNodeImpl.CHILD_COUNT_PROPERTY));

    // check that invalid cache removed
    dirEntry.nodeProps.setProperty(RepositoryNodeImpl.CHILD_COUNT_PROPERTY, "3");
    dirEntry.checkChildCountCacheAccuracy();
    assertEquals(RepositoryNodeImpl.INVALID,
                 dirEntry.nodeProps.getProperty(RepositoryNodeImpl.CHILD_COUNT_PROPERTY));
  }

  // The following tests are for RepositoryFileVersion methods.
  
  public void testRFVInputStream() throws Exception {
    int i;
    int ix;
    InputStream istrResult;
    InputStream istrText;
    StringBuilder sbRandomText;
    
    int max = 5;
    String url = "http://www.example.com/versionedcontent.txt";
    String key = "key";
    String val = "grrl";
    Properties myProps = new Properties();

    RepositoryNode leaf = repo.createNewNode(url);
    // create several versions
    for (ix = 1; ix <= max; ix++) {
      myProps.setProperty(key, val+ix);
      // Notice that this sets initial, default text...
      createContentVersion(leaf, cntnt(ix), myProps);
    }
    
    // We need to get multiple versions, because RepositoryFileVersionImpl
    // handles the last version differently than the other versions!
    for (ix = 1; ix <= max; ix++) {
      // Create text for the repository file version.
      sbRandomText = new StringBuilder();
      for (i = 0; i < 100; i++) {
        // Series of random letters...
        sbRandomText.append((char) (Math.random() * 26) + 65);
      }
      
      // Enter it into the RFV.
      istrText = new ByteArrayInputStream(sbRandomText.toString().getBytes());
      RepositoryFileVersion rfvText = leaf.getNodeVersion(ix);
      rfvText.setInputStream(istrText);
      rfvText.setProperties(myProps);
      
      rfvText.commit();
      
      // Retrieve it from the RFV.
      istrResult = rfvText.getInputStream();
      
      // Verify that what's sent and what's retrieved are the same.
      istrText = new ByteArrayInputStream(sbRandomText.toString().getBytes());
      assertTrue(StreamUtil.compare(istrResult, istrText));
    }
  }
  
  public void testRFVIsDeleted() throws Exception {
    int ix;
    
    int max = 5;
    String url = "http://www.example.com/versionedcontent.txt";
    String key = "key";
    String val = "grrl";
    Properties myProps = new Properties();

    RepositoryNode leaf = repo.createNewNode(url);
    // create several versions
    for (ix = 1; ix <= max; ix++) {
      myProps.setProperty(key, val+ix);
      // Notice that this sets initial, default text...
      createContentVersion(leaf, cntnt(ix), myProps);
    }
    
    // We need to get multiple versions, because RepositoryFileVersionImpl
    // handles the last version differently than the other versions!
    for (ix = 1; ix <= max; ix++) {
      RepositoryFileVersion rfvDeleted = leaf.getNodeVersion(ix);
      
      // Delete and check.
      rfvDeleted.delete();
      assertTrue(rfvDeleted.isDeleted());
      
      // Undelete and check.
      rfvDeleted.undelete();
      assertFalse(rfvDeleted.isDeleted());
      
      // Redelete and check.
      rfvDeleted.delete();
      assertTrue(rfvDeleted.isDeleted());
      
      // We leave one version deleted in order to verify that 
      // it doesn't affect the next...
    }
  }
  
  public void testRFVProperties() throws Exception {
    int i;
    int ix;
    InputStream istrText;
    Properties myProps;
    Properties propsResult;
    StringBuilder sbRandomText;
    String strResult;
    
    int max = 5;
    String url = "http://www.example.com/versionedcontent.txt";
    String key = "key";
    String val = "grrl";

    myProps = new Properties();
    RepositoryNode leaf = repo.createNewNode(url);
    // create several versions
    for (ix = 1; ix <= max; ix++) {
      myProps.setProperty(key, val+ix);
      // Notice that this sets initial, default text...
      createContentVersion(leaf, cntnt(ix), myProps);
    }
    
    // We need to get multiple versions, because RepositoryFileVersionImpl
    // handles the last version differently than the other versions!
    for (ix = 1; ix <= max; ix++) {
      // Create text for the properties.
      sbRandomText = new StringBuilder();
      for (i = 0; i < 100; i++) {
        // Series of random letters...
        sbRandomText.append((char) ((Math.random() * 26) + 65));
      }
      
      // Enter props into the RFV.
      RepositoryFileVersion rfvText = leaf.getNodeVersion(ix);
      myProps = new Properties();
      myProps.setProperty(key, sbRandomText.toString());
      rfvText.setProperties(myProps);
      
      istrText = new ByteArrayInputStream(cntnt(20).getBytes());
      rfvText.setInputStream(istrText);
      
      rfvText.commit();
      
      // Retrieve props from the RFV.
      propsResult = rfvText.getProperties();
      
      // Verify that what's sent and what's retrieved are the same.
      strResult = propsResult.getProperty(key);
      assertEquals(sbRandomText.toString(), strResult);
    }
  }
  
  // End of RepositoryFileVersion interface tests.
  
  // Start of RepositoryFile interface tests.
  
  // Note that RepositoryFile.getNodeUrl() is tested above.
    
  /**
   * Test method for RepositoryFile.getProperties()
   * This obviously also tests .setProperties().
   * 
   * This code is based on the tests for RepositoryFileVersion.Properties.
   * 
   * @throws Exception
   */
  public final void testRFGetProperties() throws Exception {
    int i;
    int ix;
    Properties propsResult;
    StringBuilder sbRandomText;
    String strResult;
    
    int max = 5;
    String url = "http://www.example.com/versionedcontent.txt";
    String key = "key";
    String val = "grrl";
    Properties myProps = new Properties();

    RepositoryNode leaf = repo.createNewNode(url);
    // create several versions
    for (ix = 1; ix <= max; ix++) {
      myProps.setProperty(key, val+ix);
      // Notice that this sets initial, default text...
      createContentVersion(leaf, cntnt(ix), myProps);
    }
    
    // Create text for the properties.
    sbRandomText = new StringBuilder();
    for (i = 0; i < 100; i++) {
      // Series of random letters...
      sbRandomText.append((char) ((Math.random() * 26) + 65));
    }
    
    RepositoryFile rfText = leaf;
    
    // Enter props into the RFV.
    myProps = new Properties();
    myProps.setProperty(key, sbRandomText.toString());
    rfText.setProperties(myProps);
                
    // Retrieve props from the RFV.
    propsResult = rfText.getProperties();
    
    // Verify that what's sent and what's retrieved are the same.
    strResult = propsResult.getProperty(key);
    assertEquals(sbRandomText.toString(), strResult);
  }
  
    
  /**
   * Test method for RepositoryFile.createNewVersion()
   * @throws Exception
   */
  public final void testRFCreateNewVersion() throws Exception {
    int i;
    InputStream istrContent;
    Properties myProps = new Properties();
    RepositoryFileVersion[] arrfvNewVersion = new RepositoryFileVersion[10];
    String url = "http://www.example.com/versionedcontent.txt";

    RepositoryFile rfTest = repo.createNewNode(url);
    myProps.put("key", "value");
    
    for (i = 0; i < 10; i++) {
      arrfvNewVersion[i] = rfTest.createNewVersion();
      
      istrContent = new ByteArrayInputStream(cntnt(i).getBytes());
      arrfvNewVersion[i].setInputStream(istrContent);
      arrfvNewVersion[i].setProperties(myProps);
      arrfvNewVersion[i].commit();
    }

    // Just verify that we have the right number of children.
    assertEquals(10, rfTest.listVersions().size());
  }
  
  
  /**
   * Test method for RepositoryFile.getAgreeingPeerIdSet()
   * It also tests .setAgreeingPeerIdSet.
   * 
   * Most material in this test comes from TestRepositoryFileImpl.testGetAgreeingPeerIdSet.
   * 
   * @throws Exception
   */
  
  private final String k_filenamePPIS = "PPIS.data";
  private final static String k_strPeerIdentityOne = "TCP:[127.0.0.2]:0";
  private final static String k_strPeerIdentityTwo = "TCP:[192.168.0.128]:0";

  public final void testRFGetAgreeingPeerIdSet() throws Exception {
    File filePPIS;
    MockIdentityManager idman;
    PeerIdentity piOne;
    PeerIdentity piTwo;
    PersistentPeerIdSet ppisSource;
    PersistentPeerIdSet ppisRetrieve;
    RepositoryFile rfPeerIdSet;
    String url = "http://www.example.com/versionedcontent.txt";
        
    idmgr.addPeerIdentity(k_strPeerIdentityOne, new MockPeerIdentity(k_strPeerIdentityOne));
    idmgr.addPeerIdentity(k_strPeerIdentityTwo, new MockPeerIdentity(k_strPeerIdentityTwo));
    piOne = idmgr.findPeerIdentity(k_strPeerIdentityOne);
    piTwo = idmgr.findPeerIdentity(k_strPeerIdentityTwo);
    
    // Construct and populate a PersistentPeerIdSet.
    filePPIS = FileTestUtil.tempFile(k_filenamePPIS);
    ppisSource = new PersistentPeerIdSetImpl(new StreamerFile(filePPIS), idmgr);
    ppisSource.add(piOne);
    ppisSource.add(piTwo);
    ppisSource.store();
    
    // Construct and populate the Repository Node.
    rfPeerIdSet = repo.createNewNode(url);    
    rfPeerIdSet.setAgreeingPeerIdSet(ppisSource);
    
    // Test.
    ppisRetrieve = rfPeerIdSet.getAgreeingPeerIdSet();
    assertEquals(2, ppisRetrieve.size());
    assertTrue(ppisRetrieve.contains(piOne));
    assertTrue(ppisRetrieve.contains(piTwo));
  }
  
  
  /**
   * Test method for RepositoryFile.getContentSize()
   * @throws Exception
   */
  public final void testRFGetContentSize() throws Exception {
    byte[] arbyContent;
    RepositoryFileVersion[] arrfvVersions;
    int b;
    InputStream istrContent;
    int lenContent;
    long lenTotal;
    Node nodeGetContentSize;
    Properties props = new Properties();
    RepositoryFile rfGetContentSize;
    String url = "http://www.example.com/versionedcontent.txt";
    int ver;
    
    // We assume that the RepositoryFileVersion.getContentSize()
    // works correctly.
    
    // Create a file with multiple versions.
    arrfvVersions = new RepositoryFileVersion[k_numTestVersions];
    
    rfGetContentSize = repo.createNewNode(url);

    lenTotal = 0;
    for (ver = 0; ver < k_numTestVersions; ver++) {
      arrfvVersions[ver] = rfGetContentSize.createNewVersion();
      
      lenContent = (int) (Math.random() * 200) + 1;
      lenTotal += lenContent;
      
      arbyContent = new byte[lenContent];
      for (b = 0; b < lenContent; b++) {
        arbyContent[b] = (byte) (65 + Math.random() * 26);
      }
      
      istrContent = new ByteArrayInputStream(arbyContent);
      arrfvVersions[ver].setInputStream(istrContent);
      arrfvVersions[ver].setProperties(props);
      arrfvVersions[ver].commit();
    }
    
    // Test that 'getContentSize(false)' is the total of all lengths.
    assertEquals(lenTotal, rfGetContentSize.getContentSize(false));
  }
  
  
  /**
   * Test method for RepositoryFile.getPreferredVersion()
   * @throws Exception
   */
  public final void testRFGetPreferredVersion() throws Exception {
    // The version 1 repository has no way to set the preferred version;
    // it only returns the last version given.  
    int ix;
    
    int max = 5;
    String url = "http://www.example.com/versionedcontent.txt";
    String key = "key";
    String val = "grrl";
    Properties myProps = new Properties();
    Properties propTest;
    
    RepositoryNode leaf = repo.createNewNode(url);
    // create several versions
    for (ix = 1; ix <= max; ix++) {
      myProps.setProperty(key, val+ix);
      // Notice that this sets initial, default text...
      createContentVersion(leaf, cntnt(ix), myProps);
    }

    RepositoryFile rfGetVersion = leaf;
    RepositoryFileVersion rfvVersion = rfGetVersion.getPreferredVersion();

    // Verify that we have the right version through properties...
    propTest = rfvVersion.getProperties();
    assertEquals(propTest.getProperty(key), val+max);
  }
  
  
  /**
   * Test method for RepositoryFile.listVersions()
   * @throws Exception
   */
  public final void testRFListVersions() throws Exception {
    // The version 1 repository has no way to set the preferred version;
    // it only returns the last version given.  
    int ix;
    
    int max = 5;
    String url = "http://www.example.com/versionedcontent.txt";
    String key = "key";
    String val = "grrl";
    Properties myProps = new Properties();
    Properties propTest;
    
    RepositoryNode leaf = repo.createNewNode(url);
    // create several versions
    for (ix = 1; ix <= max; ix++) {
      myProps.setProperty(key, val+ix);
      // Notice that this sets initial, default text...
      createContentVersion(leaf, cntnt(ix), myProps);
    }

    RepositoryFile rfGetVersion = leaf;
    List<RepositoryFileVersion> lirfvVersion = rfGetVersion.listVersions();

    // Verify that we have the right versions through properties...
    // Note: This test may need to be tuned if the list is in a different order.
    ix = 1;
    for (RepositoryFileVersion rfv : lirfvVersion) {
      propTest = rfv.getProperties();
      assertEquals(propTest.getProperty(key), val+ix);
      ix++;
    }
  }
  
  
  // Testing methods used for the version 2 repository node...
  
  // This method is heavily based on the testTreeSize() test.
  // NOTE: The v1 repository does NOT take the third parameter into account.
  
  public void testRNTreeContentSize3() throws Exception {
    createLeaf("http://www.example.com/testDir", "test", null);
    createLeaf("http://www.example.com/testDir/test1", "test1", null);
    createLeaf("http://www.example.com/testDir/test2", "test2", null);
    createLeaf("http://www.example.com/testDir/test3/branch1",
               "test33", null);
    createLeaf("http://www.example.com/testDir/test3/branch2",
               "test33", null);

    RepositoryNode leaf = repo.getNode("http://www.example.com/testDir");
    assertEquals(-1, leaf.getTreeContentSize(null, false, false));
    assertEquals(26, leaf.getTreeContentSize(null, true, false));
    assertEquals(26, leaf.getTreeContentSize(null, true, true));
    assertEquals(26, leaf.getTreeContentSize(null, false, false));
    assertEquals(26, leaf.getTreeContentSize(null, false, true));
    leaf = repo.getNode("http://www.example.com/testDir/test1");
    assertEquals(5, leaf.getTreeContentSize(null, true, false));
    assertEquals(5, leaf.getTreeContentSize(null, true, true));
    leaf = repo.getNode("http://www.example.com/testDir/test3");
    assertEquals(12, leaf.getTreeContentSize(null, true, false));
    assertEquals(12, leaf.getTreeContentSize(null, true, true));
    CachedUrlSetSpec cuss =
      new RangeCachedUrlSetSpec("http://www.example.com/testDir/test3",
                                "/branch1", "/branch1");
    assertEquals(6, leaf.getTreeContentSize(cuss, true, false));
    assertEquals(6, leaf.getTreeContentSize(cuss, true, true));
  }

  
  // Test getFileList(CachedUrlSetSpec, boolean)
  public void testRNGetFileList() throws Exception {
    CachedUrlSetSpec cuss;
    List<org.lockss.repository.v2.RepositoryFile> lirfFiles;
    RepositoryNode rnDelete;
    RepositoryNode rnNode;
    
    createLeaf("http://www.example.com/RNGetFileList/file1.txt", "test", null);
    createLeaf("http://www.example.com/RNGetFileList/file2.txt", "test1", null);
    createLeaf("http://www.example.com/RNGetFileList/file3.txt", "test2", null);
    createLeaf("http://www.example.com/RNGetFileList/file4.txt", "test33", null);
    createLeaf("http://www.example.com/RNGetFileList/file5.txt", "test33", null);
        
    // Test against all files.
    rnNode = repo.getNode("http://www.example.com/RNGetFileList/");
    lirfFiles = rnNode.getFileList(null, false);
    assertEquals(5, lirfFiles.size());
    for (org.lockss.repository.v2.RepositoryFile rfFile : lirfFiles) {
      assertTrue(rfFile.getNodeUrl().contains("file"));
    }
    
    // Delete two files.
    rnDelete = repo.getNode("http://www.example.com/RNGetFileList/file4.txt");
    rnDelete.delete();
    rnDelete = repo.getNode("http://www.example.com/RNGetFileList/file5.txt");
    rnDelete.delete();
    
    // Test with deleted nodes.
    lirfFiles = rnNode.getFileList(null, false);
    assertEquals(3, lirfFiles.size());
    lirfFiles = rnNode.getFileList(null, true);
    assertEquals(5, lirfFiles.size());
    
    // Test against the CachedUrlSetSpec: it's present.
    cuss = new SingleNodeCachedUrlSetSpec("http://www.example.com/RNGetFileList/file2.txt");
    lirfFiles = rnNode.getFileList(cuss, false);
    assertEquals(1, lirfFiles.size());
    lirfFiles = rnNode.getFileList(cuss, true);
    assertEquals(1, lirfFiles.size());

    // Test against the CachedUrlSetSpec: it's deleted.
    cuss = new SingleNodeCachedUrlSetSpec("http://www.example.com/RNGetFileList/file5.txt");
    lirfFiles = rnNode.getFileList(cuss, false);
    assertEquals(0, lirfFiles.size());
    lirfFiles = rnNode.getFileList(cuss, true);
    assertEquals(1, lirfFiles.size());

    // Test against the CachedUrlSetSpec: it's absent.
    cuss = new SingleNodeCachedUrlSetSpec("http://www.example.com/RNGetFileList/notpresent");
    lirfFiles = rnNode.getFileList(cuss, false);
    assertEquals(0, lirfFiles.size());
    lirfFiles = rnNode.getFileList(cuss, true);
    assertEquals(0, lirfFiles.size());
  }
  
  
  // Test getFiles(int max, boolean)
  public void testRNGetFiles() throws Exception {
    CachedUrlSetSpec cuss;
    org.lockss.repository.v2.RepositoryFile[] arrfFiles;
    RepositoryNode rnDelete;
    RepositoryNode rnNode;
    
    createLeaf("http://www.example.com/RNGetFiles/file1.txt", "test", null);
    createLeaf("http://www.example.com/RNGetFiles/file2.txt", "test1", null);
    createLeaf("http://www.example.com/RNGetFiles/file3.txt", "test2", null);
    createLeaf("http://www.example.com/RNGetFiles/file4.txt", "test33", null);
    createLeaf("http://www.example.com/RNGetFiles/file5.txt", "test33", null);
        
    // Test against all files.
    rnNode = repo.getNode("http://www.example.com/RNGetFiles/");
    arrfFiles = rnNode.getFiles(10, true);
    assertEquals(5, arrfFiles.length);
    for (org.lockss.repository.v2.RepositoryFile rfFile : arrfFiles) {
      assertTrue(rfFile.getNodeUrl().contains("file"));
    }
    
    arrfFiles = rnNode.getFiles(10, false);
    assertEquals(5, arrfFiles.length);
    for (org.lockss.repository.v2.RepositoryFile rfFile : arrfFiles) {
      assertTrue(rfFile.getNodeUrl().contains("file"));
    }

    // Test the maxVersions...
    arrfFiles = rnNode.getFiles(3, true);
    assertEquals(3, arrfFiles.length);
    
    // Delete a few nodes...
    rnDelete = repo.getNode("http://www.example.com/RNGetFiles/file4.txt");
    rnDelete.delete();
    rnDelete = repo.getNode("http://www.example.com/RNGetFiles/file5.txt");
    rnDelete.delete();
    
    // Test the deletions...
    arrfFiles = rnNode.getFiles(10, false);
    assertEquals(3, arrfFiles.length);
    arrfFiles = rnNode.getFiles(10, true);
    assertEquals(5, arrfFiles.length);
  }
  
  
  // Test makeNewRepositoryFile(String)
  public void testRNMakeNewRepositoryFile() throws Exception {
    RepositoryFile rfChild;
    RepositoryNode rnParent;
    
    createLeaf("http://www.example.com/RNMakeNewRepositoryFile", null, null);
    rnParent = repo.getNode("http://www.example.com/RNMakeNewRepositoryFile");
    rfChild = rnParent.makeNewRepositoryFile("child.txt");
    
    // Check the URL of the child.
    assertEquals(rfChild.getNodeUrl(), "http://www.example.com/RNMakeNewRepositoryFile/child.txt");
    
    // I don't know what else to test here...
  }
  
  
  // Test makeNewRepositoryNode(String)
  public void testRNMakeNewRepositoryNode() throws Exception {
    org.lockss.repository.v2.RepositoryNode rnChild;
    RepositoryNode rnParent;
    
    createLeaf("http://www.example.com/RNMakeNewRepositoryFile", null, null);
    rnParent = repo.getNode("http://www.example.com/RNMakeNewRepositoryFile");
    rnChild = rnParent.makeNewRepositoryNode("child.txt");
    
    // Check the URL of the child.
    assertEquals(rnChild.getNodeUrl(), "http://www.example.com/RNMakeNewRepositoryFile/child.txt");
    
    // I don't know what else to test here...
  }
  
  
  // Additional methods used for testing...
  
  private RepositoryNode createLeaf(String url, String content,
      Properties theProps) throws Exception {
    return createLeaf(repo, url, content, theProps);
  }

  public static RepositoryNode createLeaf(LockssRepository repo, String url,
      String content, Properties props) throws Exception {
    RepositoryNode leaf = repo.createNewNode(url);
    createContentVersion(leaf, content, props);
    return leaf;
  }

  public static void createContentVersion(RepositoryNode leaf,
					  String content, Properties props)
      throws Exception {
    leaf.makeNewVersion();
    writeToLeaf(leaf, content);
    if (props==null) {
      props = new Properties();
    }
    leaf.setNewProperties(props);
    leaf.sealNewVersion();
  }

  public static void writeToLeaf(RepositoryNode leaf, String content)
      throws Exception {
    if (content==null) {
      content = "";
    }
    OutputStream os = leaf.getNewOutputStream();
    InputStream is = new StringInputStream(content);
    StreamUtil.copy(is, os);
    os.close();
    is.close();
  }

  public static String getLeafContent(RepositoryNodeVersion leaf)
      throws IOException {
    return getRNCContent(leaf.getNodeContents());
  }

  public static String getRNCContent(RepositoryNode.RepositoryNodeContents rnc)
      throws IOException {
    InputStream is = rnc.getInputStream();
    OutputStream baos = new ByteArrayOutputStream(20);
    StreamUtil.copy(is, baos);
    is.close();
    String resultStr = baos.toString();
    baos.close();
    return resultStr;
  }

  public static void main(String[] argv) {
    String[] testCaseList = { TestRepositoryNodeImpl.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

  // this class overrides 'getDatedVersionedPropsFile()' so I can
  // manipulate the file names for testing.  Also allows 'loadNodeProps()
  // to fail on demand
  static class MyMockRepositoryNode extends RepositoryNodeImpl {
    long dateValue;
    boolean failPropsLoad = false;
    boolean failRootConsist = false;
    boolean failContentConsist = false;
    boolean failEnsureCurrentLoaded = false;
    static boolean failEnsureDirExists = false;

    MyMockRepositoryNode(RepositoryNodeImpl nodeImpl) {
      super(nodeImpl.url, nodeImpl.nodeLocation, nodeImpl.repository, new MockArchivalUnit());
    }

    File getDatedVersionedPropsFile(int version, long date) {
      StringBuffer buffer = new StringBuffer();
      buffer.append(version);
      buffer.append(PROPS_EXTENSION);
      buffer.append("-");
      buffer.append(dateValue);
      return new File(getContentDir(), buffer.toString());
    }

    void loadNodeProps(boolean okIfNotThere) {
      if (failPropsLoad) {
        throw new LockssRepository.RepositoryStateException("Couldn't load properties file.");
      } else {
        super.loadNodeProps(okIfNotThere);
      }
    }

    boolean checkNodeRootConsistency() {
      if (failRootConsist) {
        return false;
      } 
      
      return super.checkNodeRootConsistency();
    }

    boolean checkContentConsistency() {
      if (failContentConsist) {
        return false;
      } 
      
      return super.checkContentConsistency();
    }

    void ensureCurrentInfoLoaded() {
      if (failEnsureCurrentLoaded) {
        throw new LockssRepository.RepositoryStateException("Couldn't load current info.");
      } 
      
      super.ensureCurrentInfoLoaded();
    }

    boolean ensureDirExists(File dirFile) {
      if (failEnsureDirExists) {
        return false;
      } 
      
      return super.ensureDirExists(dirFile);
    }
  }

  static class MyLockssRepositoryImpl extends LockssRepositoryImpl {
    boolean dontNormalize = false;
    void setDontNormalize(boolean val) {
      dontNormalize = val;
    }

    MyLockssRepositoryImpl(String rootPath) {
      super(rootPath, new MockArchivalUnit());
    }

    public String canonicalizePath(String url)
	throws MalformedURLException {
      if (dontNormalize) {
        return url;
      }
      
      return super.canonicalizePath(url);
    }

    public static LockssRepository createNewLockssRepository(ArchivalUnit au) {
      String root = getRepositoryRoot(au);
      if (root == null) {
	throw new LockssRepository.RepositoryStateException("null root");
      }
      String auDir = LockssRepositoryImpl.mapAuToFileLocation(root, au);
      log.debug("repo: " + auDir + ", au: " + au.getName());
//       staticCacheLocation = extendCacheLocation(root);
      LockssRepositoryImpl repo = new MyLockssRepositoryImpl(auDir);
      Plugin plugin = au.getPlugin();
      if (plugin != null) {
 	LockssDaemon daemon = plugin.getDaemon();
	if (daemon != null) {
	  RepositoryManager mgr = daemon.getRepositoryManager();
	  if (mgr != null) {
	    mgr.setRepositoryForPath(auDir, repo);
	  }
	}
      }
      return repo;
    }


  }
}
