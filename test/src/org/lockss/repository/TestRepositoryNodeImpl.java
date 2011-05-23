/*
 * $Id: TestRepositoryNodeImpl.java,v 1.61.10.5 2011-05-23 22:34:24 dshr Exp $
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
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileType;
import org.apache.commons.vfs.FileSelector;
import org.apache.commons.vfs.AllFileSelector;
import org.apache.commons.vfs.FileSystem;
import org.apache.commons.vfs.FileSystemManager;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.VFS;

import org.lockss.test.*;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.protocol.*;

/**
 * This is the test class for org.lockss.repository.RepositoryNodeImpl
 */
public class TestRepositoryNodeImpl extends LockssTestCase {
  private static Logger logger = Logger.getLogger("TestRepositoryNodeImpl");
  static final String TREE_SIZE_PROPERTY =
    RepositoryNodeImpl.TREE_SIZE_PROPERTY;
  static final String CHILD_COUNT_PROPERTY =
    RepositoryNodeImpl.CHILD_COUNT_PROPERTY;

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
    String tempDirURI = RepositoryManager.LOCAL_REPO_PROTOCOL + tempDirPath;
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirURI);
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
  public void dontTestFileAssumptions() throws Exception {
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

  public void dontTestGetNodeUrl() {
    RepositoryNode node = new RepositoryNodeImpl("testUrl", "testDir", repo);
    assertEquals("testUrl", node.getNodeUrl());
    node = new RepositoryNodeImpl("testUrl/test.txt", "testUrl/test.txt", repo);
    assertEquals("testUrl/test.txt", node.getNodeUrl());
  }

  public void testFileLocation() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/branch1/leaf1",
                   "test stream", null);
    tempDirPath = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath, mau);
    logger.debug3("tempDirPath: " + tempDirPath);
    tempDirPath =
      LockssRepositoryImpl.mapUrlToFileLocation(tempDirPath,
						"http://www.example.com/testDir/branch1/leaf1").substring("file://".length());
    File testFile = new File(tempDirPath);
    logger.debug3("FileLocation: " + tempDirPath);
    assertTrue(tempDirPath, testFile.exists());
    testFile = new File(tempDirPath + "/#content/current");
    logger.debug3("FileLocation: " + tempDirPath + "/#content/current");
    assertTrue(tempDirPath + "/#content/current", testFile.exists());
    testFile = new File(tempDirPath + "/#content/current.props");
    logger.debug3("FileLocation: " + tempDirPath + "/#content/current.props");
    assertTrue(tempDirPath + "/#content/current.props", testFile.exists());
    testFile = new File(tempDirPath + "/#node_props");
    logger.debug3("FileLocation: " + tempDirPath + "/#node_props");
    assertFalse(tempDirPath  + "/#node_props", testFile.exists());
    testFile = new File(tempDirPath + "/#agreement");
    logger.debug3("FileLocation: " + tempDirPath + "/#agreement");
    assertFalse(tempDirPath + "/#agreement", testFile.exists());
  }
  
  public void dontTestUpdateAgreementCreatesFile() throws Exception {
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
    assertTrue(tempDirPath +  "#agreement", testFile.exists());
  }

  public void dontTestUpdateAndLoadAgreement() throws Exception {
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
  
  public void dontTestVersionFileLocation() throws Exception {
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

  public void dontTestInactiveFileLocation() throws Exception {
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

  public void dontTestDeleteFileLocation() throws Exception {
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

  public void dontTestListEntriesNonexistentDir() throws Exception {
    RepositoryNode node = new RepositoryNodeImpl("foo-no-url", "foo-no-dir",
						 repo);
    try {
      node.listChildren(null, false);
      fail("listChildren() is nonexistent dir should throw");
    } catch (LockssRepository.RepositoryStateException e) {
    }
  }

  public void dontTestListEntries() throws Exception {
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

  FileObject normalizeFile(RepositoryNodeImpl node, String name) {
    FileObject file = node.getFileObject(name);
    return node.normalize(file);
  }

  String normalizeName(RepositoryNodeImpl node, String name) {
    String ret = node.normalize(name);
    logger.debug3("normalize(" + name + ") = " + ret);
    return ret;
  }

  public void dontTestNormalizeUrlEncodingCase() throws Exception {
	if (!PlatformUtil.getInstance().isCaseSensitiveFileSystem()) {
	    log.debug("Skipping testNormalizeUrlEncodingCase: file system is not case sensitive.");
	    return;
	}
    RepositoryNodeImpl node = new RepositoryNodeImpl("foo", "bar", repo);
    // nothing to normalize
    String fileName = "foo/bar/baz";
    FileObject file = node.getFileObject(fileName);
    assertSame(file, node.normalize(node.getFileObject(fileName)));
    fileName = "foo/bar/ba%ABz";
    file = node.getFileObject(fileName);
    assertSame(file, node.normalize(node.getFileObject(fileName)));
    // unnormalized in parent dir name is left alone
    fileName = "ba%abz/bar";
    file = node.getFileObject(fileName);
    assertSame(file, node.normalize(node.getFileObject(fileName)));
    fileName = "foo/ba%abz/bar";
    file = node.getFileObject(fileName);
    assertSame(file, node.normalize(node.getFileObject(fileName)));
    // should be normalized
    assertEquals("ba%ABz", normalizeName(node, "ba%aBz"));
    //assertEquals("/ba%ABz", normalizeName(node, "/ba%aBz"));
    assertEquals("foo/bar/ba%ABz", normalizeName(node, "foo/bar/ba%aBz"));
    assertEquals("foo/bar/ba%ABz", normalizeName(node, "foo/bar/ba%Abz"));
    assertEquals("foo/bar/ba%ABz", normalizeName(node, "foo/bar/ba%abz"));
    assertEquals("foo/bar/ba%abz/ba%ABz", normalizeName(node, "foo/bar/ba%abz/ba%abz"));
  }

  public void dontTestNormalizeTrailingQuestion() throws Exception {
    RepositoryNodeImpl node = new RepositoryNodeImpl("foo", "bar", repo);
    // nothing to normalize
    String fileName = "foo/bar/baz";
    FileObject file = node.getFileObject(fileName);
    assertSame(file, normalizeFile(node, fileName));
    fileName = "foo/bar/ba?z";
    file = node.getFileObject(fileName);
    assertSame(file, normalizeFile(node, fileName));
    // unnormalized in parent dir name is left alone
    fileName = "ba?/bar";
    file = node.getFileObject(fileName);
    assertSame(file, normalizeFile(node, fileName));
    // should be normalized
    assertEquals("baz", normalizeName(node, "baz?"));
    fileName = "/ba";
    file = node.getFileObject(fileName);
    assertEquals(file.getName().getPath(), normalizeName(node, fileName + "?"));
    fileName = "foo/bar/bar";
    file = node.getFileObject(fileName);
    assertEquals(file.getName().getPath().substring("/bar/".length()),
		 normalizeName(node, fileName + "?"));
    fileName = "foo/ba?r/bar";
    file = node.getFileObject(fileName);
    assertEquals(file.getName().getPath().substring("/bar/".length()),
		 normalizeName(node, fileName + "?"));
    fileName = "foo/bar?/bar";
    file = node.getFileObject(fileName);
    assertEquals(file.getName().getPath().substring("/bar/".length()),
		 normalizeName(node, fileName + "?"));

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

  public void dontTestFixUnnormalized_Rename() throws Exception {
    if (!PlatformUtil.getInstance().isCaseSensitiveFileSystem()) {
      log.debug("Skipping testFixUnnormalized_Rename: file system is not case sensitive.");
      return;
    }
    if (true) {
      log.debug("Skipping testFixUnnormalized_Rename: file system does url decoding.");
      return;
    }
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

  public void dontTestFixUnnormalizedMultiple_Delete() throws Exception {
	if (!PlatformUtil.getInstance().isCaseSensitiveFileSystem()) {
	    log.debug("Skipping testFixUnnormalizedMultiple_Delete: file system is not case sensitive.");
	    return;
	}
    if (true) {
      log.debug("Skipping testFixUnnormalized_Rename: file system does url decoding.");
      return;
    }
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

  public void dontTestFixUnnormalizedMultiple_DeleteMultiple() throws Exception {
	if (!PlatformUtil.getInstance().isCaseSensitiveFileSystem()) {
	    log.debug("Skipping testFixUnnormalizedMultiple_DeleteMultiple: file system is not case sensitive.");
	    return;
	}
    if (true) {
      log.debug("Skipping testFixUnnormalized_Rename: file system does url decoding.");
      return;
    }
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

  public void dontTestFixUnnormalized_DontFixParent() throws Exception {
	if (!PlatformUtil.getInstance().isCaseSensitiveFileSystem()) {
	    log.debug("Skipping testFixUnnormalized_DontFixParent: file system is not case sensitive.");
	    return;
	}
    if (true) {
      log.debug("Skipping testFixUnnormalized_Rename: file system does url decoding.");
      return;
    }
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

  public void dontTestEntrySort() throws Exception {
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

  public void dontTestIllegalOperations() throws Exception {
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

  public void dontTestVersionTimeout() throws Exception {
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

  public void dontTestMakeNewCache() throws Exception {
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

  public void dontTestMakeNodeLocation() throws Exception {
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

  public void dontTestMakeNewVersion() throws Exception {
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
    // NB - since this test modifies the file system it *has* to use
    // the VFS interface even if the file system is local.
    String url = "http://www.example.com/foo.html";
    RepositoryNodeImpl leaf = (RepositoryNodeImpl)repo.createNewNode(url);
    String nodeLoc = LockssRepositoryImpl.mapAuToFileLocation(tempDirPath,
							      mau);
    nodeLoc = LockssRepositoryImpl.mapUrlToFileLocation(nodeLoc, url);
    logger.debug3("testDisappearingFile(" + whichFile + ") " + nodeLoc + " " +
		  url);
    FileObject testFile;
    switch (whichFile) {
    case DEL_NODE_DIR:
      testFile = VFS.getManager().resolveFile(nodeLoc);
      break;
    case DEL_CONTENT_DIR:
      testFile = VFS.getManager().resolveFile(nodeLoc + File.separator + "#content");
      break;
    case DEL_CONTENT_FILE:
      testFile = VFS.getManager().resolveFile(nodeLoc + File.separator + "#content/current");
      break;
    case DEL_PROPS_FILE:
      testFile = VFS.getManager().resolveFile(nodeLoc + File.separator + "#content/current.props");
      break;
    default:
      throw new UnsupportedOperationException();
    }
    assertFalse(testFile.exists());
    logger.debug3("testFile: " + testFile.getName().getPath());

    Properties props1 = PropUtil.fromArgs("key1", "value 1");

    createContentVersion(leaf, "test content 11111", props1);
    assertEquals(1, leaf.getCurrentVersion());

    assertTrue(testFile.exists());
    logger.debug3("Deleting: " + testFile.getName().getPath());
    switch (whichFile) {
    case DEL_NODE_DIR:
    case DEL_CONTENT_DIR:
      assertTrue(testFile.delete(new AllFileSelector()) > 0);
      break;
    case DEL_CONTENT_FILE:
    case DEL_PROPS_FILE:
      assertTrue(testFile.delete());
      break;
    }
    assertFalse(testFile.exists());

    Properties props2 = PropUtil.fromArgs("key2", "value 2");
    RepositoryNode leaf2 = repo.createNewNode(url);
    assertFalse(testFile.exists());
    assertSame(leaf, leaf2);
    assertTrue(leaf.hasContent());
    if (tryRead) {
      try {
	getLeafContent(leaf);
      } catch (LockssRepository.RepositoryStateException e) {
	// expected
      }
    }
    // assertFalse(testFile.exists());
    leaf2.makeNewVersion();
    switch (whichFile) {
    case DEL_NODE_DIR:
    case DEL_CONTENT_DIR:
      assertTrue(testFile.exists());
      break;
    case DEL_CONTENT_FILE:
    case DEL_PROPS_FILE:
      assertFalse(testFile.exists());
      break;
    }

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

  public void dontTestDisappearingNodeDir() throws Exception {
    testDisappearingFile(DEL_NODE_DIR, false);
  }

  public void dontTestDisappearingContentDir() throws Exception {
    testDisappearingFile(DEL_CONTENT_DIR, false);
  }

  public void dontTestDisappearingContentFile() throws Exception {
    testDisappearingFile(DEL_CONTENT_FILE, false);
  }

  public void dontTestDisappearingPropsFile() throws Exception {
    testDisappearingFile(DEL_PROPS_FILE, false);
  }

  public void dontTestDisappearingNodeDirWithRead() throws Exception {
    testDisappearingFile(DEL_NODE_DIR, true);
  }

  public void dontTestDisappearingContentDirWithRead() throws Exception {
    testDisappearingFile(DEL_CONTENT_DIR, true);
  }

  public void dontTestDisappearingContentFileWithRead() throws Exception {
    testDisappearingFile(DEL_CONTENT_FILE, true);
  }

  public void dontTestDisappearingPropsFileWithRead() throws Exception {
    testDisappearingFile(DEL_PROPS_FILE, true);
  }

  public void dontTestMakeNewVersionWithoutClosingStream() throws Exception {
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

  public void dontTestMakeNewIdenticalVersionDefault() throws Exception {
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

  public void dontTestMakeNewIdenticalVersionOldWay() throws Exception {
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
    for (int i = 0; i < files.length; i++) {
      logger.debug3(files[i].getPath());
    }
    assertEquals(3, files.length);
    File testFile = new File(testFileDir, "current");
    assertTrue(testFile.exists());
    testFile = new File(testFileDir, "current.props");
    assertTrue(testFile.exists());
    testFile = new File(testFileDir, "1.props-123321");
    assertTrue(testFile.exists());
  }

  public void dontTestMakeNewIdenticalVersionNewWay() throws Exception {
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

  public void dontTestIdenticalVersionFixesVersionError() throws Exception {
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

  public void dontTestMakeNewVersionFixesVersionError() throws Exception {
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

  public void dontTestGetInputStream() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream", null);
    String resultStr = getLeafContent(leaf);
    assertEquals("test stream", resultStr);
  }

  public void dontTestGetProperties() throws Exception {
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
    FileObject propFile =
      leafImpl.getFileObject(RepositoryNodeImpl.CONTENT_DIR +
			     File.separator +
			     RepositoryNodeImpl.CURRENT_PROPS_FILENAME);
    OutputStream os =
      new BufferedOutputStream(propFile.getContent().getOutputStream());
    // Write a Malformed unicode escape that will cause Properties.load()
    // to throw
    os.write("\\uxxxxfoo=bar".getBytes());
    os.close();
    return leaf;
  }

  public void dontTestCorruptProperties1() throws Exception {
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

  public void dontTestCorruptProperties2() throws Exception {
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

  public void dontTestGetNodeVersion() throws Exception {
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

  public void dontTestGetNodeVersions() throws Exception {
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

  public void dontTestIllegalVersionOperations() throws Exception {
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

  public void dontTestDirContent() throws Exception {
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

  public void dontTestNodeSize() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream", null);
    assertTrue(leaf.hasContent());
    assertEquals(11, (int)leaf.getContentSize());
  }

  public void dontTestTreeSize() throws Exception {
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

  public void dontTestDetermineParentNode() throws Exception {
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

  public void dontTestCacheInvalidation() throws Exception {
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

  public void dontTestTreeSizeCaching() throws Exception {
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

  public void dontTestChildCount() throws Exception {
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

  public void dontTestDeactivate() throws Exception {
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

  public void dontTestDelete() throws Exception {
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

  public void dontTestUnDelete() throws Exception {
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

  public void dontTestRestoreLastVersion() throws Exception {
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

  public void dontTestReactivateViaRestore() throws Exception {
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

  public void dontTestReactivateViaNewVersion() throws Exception {
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

    FileObject lastProps = leaf.getFileObject(RepositoryNodeImpl.CONTENT_DIR +
					      File.separator + "1.props");
    assertTrue(lastProps.exists());
    InputStream is =
      new BufferedInputStream(lastProps.getContent().getInputStream());
    props.load(is);
    is.close();
    // make sure the 'was inactive' property hasn't been lost
    assertEquals("true",
                 props.getProperty(RepositoryNodeImpl.NODE_WAS_INACTIVE_PROPERTY));
  }

  public void dontTestAbandonReactivateViaNewVersion() throws Exception {
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

  public void dontTestIsLeaf() throws Exception {
    createLeaf("http://www.example.com/testDir/test1", "test stream", null);
    createLeaf("http://www.example.com/testDir/branch1", "test stream", null);
    createLeaf("http://www.example.com/testDir/branch1/test4", "test stream", null);

    RepositoryNode leaf = repo.getNode("http://www.example.com/testDir/test1");
    assertTrue(leaf.isLeaf());
    leaf = repo.getNode("http://www.example.com/testDir/branch1");
    assertFalse(leaf.isLeaf());
  }

  public void dontTestListInactiveNodes() throws Exception {
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

  public void dontTestDeleteInnerNode() throws Exception {
    createLeaf("http://www.example.com/testDir/test1", "test stream", null);
    createLeaf("http://www.example.com/testDir/test2", "test stream", null);

    RepositoryNode dirEntry = repo.getNode("http://www.example.com/testDir");
    assertFalse(dirEntry.isDeleted());
    dirEntry.markAsDeleted();
    assertTrue(dirEntry.isDeleted());
    dirEntry.markAsNotDeleted();
    assertFalse(dirEntry.isDeleted());
  }

  public void dontTestGetFileStrings() throws Exception {
    RepositoryNodeImpl node = (RepositoryNodeImpl)repo.createNewNode(
        "http://www.example.com/test.url");
    node.initNodeRoot();
    String contentStr = FileUtil.sysDepPath(node.nodeLocation + "/#content");
    assertEquals(contentStr, node.getContentDir().getName().getPath());
    contentStr = contentStr + File.separator;
    String expectedStr = contentStr + "123";
    assertEquals(expectedStr,
                 node.getVersionedCacheFile(123).getName().getPath());
    expectedStr = contentStr + "123.props";
    assertEquals(expectedStr,
                 node.getVersionedPropsFile(123).getName().getPath());
    expectedStr = contentStr + "inactive";
    assertEquals(expectedStr, node.getInactiveCacheFile().getName().getPath());
    expectedStr = contentStr + "inactive.props";
    assertEquals(expectedStr, node.getInactivePropsFile().getName().getPath());
  }

  public void dontTestCheckNodeConsistency() throws Exception {
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

  public void dontTestCheckNodeRootConsistency() throws Exception {
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
    assertEquals(FileType.FOLDER, leaf.getFileObject("").getType());

    // fail node props load
    leaf.getChildCount();
    assertTrue(leaf.nodePropsFile.exists());
    FileObject renameFile =
      leaf.getFileObject(RepositoryNodeImpl.NODE_PROPS_FILENAME +
			 RepositoryNodeImpl.FAULTY_FILE_EXTENSION);
    assertFalse(renameFile.exists());
    leaf.failPropsLoad = true;
    assertTrue(leaf.checkNodeRootConsistency());
    assertFalse(leaf.nodePropsFile.exists());
    assertTrue(renameFile.exists());
  }

  public void dontTestCheckContentConsistency() throws Exception {
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
    FileObject renameFile = leaf.getFileObject(RepositoryNodeImpl.CONTENT_DIR +
					       File.separator + "RENAME");
    FileObject leafFile = leaf.getFileObject(RepositoryNodeImpl.CONTENT_DIR +
					     File.separator +
					     RepositoryNodeImpl.CURRENT_FILENAME);
    assertTrue(RepositoryNodeImpl.updateAtomically(leafFile, renameFile));
    assertFalse(leaf.checkContentConsistency());
    RepositoryNodeImpl.updateAtomically(renameFile, leafFile);
    assertTrue(leaf.checkContentConsistency());

    // should return false if content props absent
    RepositoryNodeImpl.updateAtomically(leafFile, renameFile);
    assertFalse(leaf.checkContentConsistency());
    RepositoryNodeImpl.updateAtomically(renameFile, leafFile);
    assertTrue(leaf.checkContentConsistency());

    // should return false if inactive and files missing
    leaf.currentVersion = RepositoryNodeImpl.INACTIVE_VERSION;
    assertFalse(leaf.checkContentConsistency());
    RepositoryNodeImpl.updateAtomically(leaf.currentPropsFile,
					leaf.getInactivePropsFile());
    assertFalse(leaf.checkContentConsistency());
    RepositoryNodeImpl.updateAtomically(leaf.currentCacheFile,
					leaf.getInactiveCacheFile());
    assertTrue(leaf.checkContentConsistency());
    RepositoryNodeImpl.updateAtomically(leaf.getInactivePropsFile(),
					leaf.currentPropsFile);
    assertFalse(leaf.checkContentConsistency());
    // finish restoring
    RepositoryNodeImpl.updateAtomically(leaf.getInactiveCacheFile(),
					leaf.currentCacheFile);
    leaf.currentVersion = 1;
    assertTrue(leaf.checkContentConsistency());

    // remove residual files
    // - create files
    OutputStream fos = leaf.tempCacheFile.getContent().getOutputStream();
    StringInputStream sis = new StringInputStream("test stream");
    StreamUtil.copy(sis, fos);
    fos.close();
    sis.close();

    fos = leaf.tempPropsFile.getContent().getOutputStream();
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


  static final String testPath = "testDir";
  public void dontTestEnsureDirExists() throws Exception {
    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)createLeaf("http://www.example.com", null, null);
    FileObject testDir = leaf.getFileObject(testPath);
    assertFalse(testDir.exists());
    // should create dir, return true since it doesn't exist
    assertTrue(leaf.ensureDirExists(testDir));
    testDir = leaf.getFileObject(testPath);
    assertTrue(testDir.exists());
    assertEquals(FileType.FOLDER, testDir.getType());
    // should return true now that dir exists
    assertTrue(leaf.ensureDirExists(testDir));
    testDir = leaf.getFileObject(testPath);
    assertTrue(testDir.exists());
    assertEquals(FileType.FOLDER, testDir.getType());
    // delete the dir, replace with a file
    assertTrue(testDir.delete());
    testDir = leaf.getFileObject(testPath);
    assertFalse(testDir.exists());
    OutputStream fos = testDir.getContent().getOutputStream();
    assertNotNull(fos);
    StringInputStream sis = new StringInputStream("test stream");
    StreamUtil.copy(sis, fos);
    fos.close();
    sis.close();
    assertTrue(testDir.exists());
    assertEquals(FileType.FILE, testDir.getType());
    FileObject renameFile =
      leaf.getFileObject(testPath + RepositoryNodeImpl.FAULTY_FILE_EXTENSION);
    assertFalse(renameFile.exists());
    // Should rename file, create dir, return true
    assertTrue(leaf.ensureDirExists(testDir));
    testDir = leaf.getFileObject(testPath);
    assertTrue(testDir.exists());
    assertEquals(FileType.FOLDER, testDir.getType());
    renameFile =
      leaf.getFileObject(testPath + RepositoryNodeImpl.FAULTY_FILE_EXTENSION);
    assertTrue(renameFile.exists());
    assertEquals(FileType.FILE, renameFile.getType());
    assertEquals("test stream",
		 StringUtil.fromInputStream(renameFile.getContent().getInputStream()));
    assertTrue(renameFile.delete());
    assertTrue(testDir.delete());
  }

  public void dontTestCheckFileExists() throws Exception {
    RepositoryNodeImpl leaf =
        (RepositoryNodeImpl)createLeaf("http://www.example.com", null, null);
    FileObject testFile = leaf.getFileObject(testPath);
    assertFalse(testFile.exists());
    OutputStream fos = testFile.getContent().getOutputStream();
    assertNotNull(fos);
    StringInputStream sis = new StringInputStream("test stream");
    StreamUtil.copy(sis, fos);
    fos.close();
    sis.close();
    assertTrue(testFile.exists());
    assertEquals(FileType.FILE, testFile.getType());
    // Return true if file exists
    assertTrue(leaf.checkFileExists(testFile, "test file"));
    testFile = leaf.getFileObject(testPath);
    assertTrue(testFile.exists());
    assertEquals(FileType.FILE, testFile.getType());
    assertTrue(testFile.delete());
    assertFalse(testFile.exists());
    // Return false if file does not exist
    assertFalse(leaf.checkFileExists(testFile, "test file"));
    testFile = leaf.getFileObject(testPath);
    assertFalse(testFile.exists());
    assertTrue(leaf.ensureDirExists(testFile));
    testFile = leaf.getFileObject(testPath);
    assertTrue(testFile.exists());
    assertEquals(FileType.FOLDER, testFile.getType());
    // Return false and rename if dir exists
    assertFalse(leaf.checkFileExists(testFile, "test file"));
    testFile = leaf.getFileObject(testPath);
    assertFalse(testFile.exists());
    testFile = leaf.getFileObject(testPath +
				  RepositoryNodeImpl.FAULTY_FILE_EXTENSION);
    assertTrue(testFile.exists());
    assertEquals(FileType.FOLDER, testFile.getType());
    assertTrue(testFile.delete());
  }

  public void dontTestCheckChildCountCacheAccuracy() throws Exception {
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

  private RepositoryNode createLeaf(String url, String content,
      Properties props) throws Exception {
    return createLeaf(repo, url, content, props);
  }

  public static RepositoryNode createLeaf(LockssRepository repo, String url,
      String content, Properties props) throws Exception {
    logger.debug3("createLeaf: " + url);
    RepositoryNode leaf = repo.createNewNode(url);
    createContentVersion(leaf, content, props);
    return leaf;
  }

  public static void createContentVersion(RepositoryNode leaf,
					  String content, Properties props)
      throws Exception {
    logger.debug3("createNewVersion: makeNewVersion");
    leaf.makeNewVersion();
    logger.debug3("createNewVersion: writeToLeaf");
    writeToLeaf(leaf, content);
    if (props==null) {
      props = new Properties();
    }
    logger.debug3("createNewVersion: setNewProperties");
    leaf.setNewProperties(props);
    logger.debug3("createNewVersion: sealNewVersion");
    leaf.sealNewVersion();
    logger.debug3("createNewVersion: done");
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
      super(nodeImpl.url, nodeImpl.nodeLocation, nodeImpl.repository);
    }

    protected FileObject getDatedVersionedPropsFile(int version, long date) {
      StringBuffer buffer = new StringBuffer();
      buffer.append(version);
      buffer.append(PROPS_EXTENSION);
      buffer.append("-");
      buffer.append(dateValue);
      try {
	return getContentDir().resolveFile(buffer.toString());
      } catch (FileSystemException e) {
	return null;
      }
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
      } else {
        return super.checkNodeRootConsistency();
      }
    }

    boolean checkContentConsistency() {
      if (failContentConsist) {
        return false;
      } else {
        return super.checkContentConsistency();
      }
    }

    void ensureCurrentInfoLoaded() {
      if (failEnsureCurrentLoaded) {
        throw new LockssRepository.RepositoryStateException("Couldn't load current info.");
      } else {
        super.ensureCurrentInfoLoaded();
      }
    }

    boolean ensureDirExists(FileObject dirFile) {
      if (failEnsureDirExists) {
        return false;
      } else {
        return super.ensureDirExists(dirFile);
      }
    }
  }

  static class MyLockssRepositoryImpl extends LockssRepositoryImpl {
    boolean dontNormalize = false;
    void setDontNormalize(boolean val) {
      dontNormalize = val;
    }

    MyLockssRepositoryImpl(String rootPath) {
      super(rootPath);
    }

    public String canonicalizePath(String url)
	throws MalformedURLException {
      if (dontNormalize) return url;
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
