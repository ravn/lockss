/*
 * $Id: RepositoryManagerManagerImpl.java,v 1.1.2.3 2009-10-19 23:04:57 edwardsb1 Exp $
 */
/*
 Copyright (c) 2000-2009 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.repository.jcr;

import java.io.*;
import java.net.*;
import java.util.*;

import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.repository.LockssRepositoryException;
import org.lockss.repository.v2.*;
import org.lockss.util.*;
import org.lockss.util.PlatformUtil.DF;

/**
 * @author edwardsb
 *
 */
public class RepositoryManagerManagerImpl extends BaseLockssDaemonManager 
implements RepositoryManagerManager {
  // Constants
  private static final String k_schemeJcr = "jcr";
  
  // Static variables
  private static Logger logger = Logger.getLogger("JcrRepositoryHelperFactory");
  
  // Class Variables
  Map<String, CollectionOfAuRepositories> m_mapAuidToCoar;
  
  public RepositoryManagerManagerImpl() {
    // TODO
  }

  // Initialization code ---
  
  /**
   * Called at the start of the RepositoryManagerManagerImpl.
   */
  public void startService() {
    super.startService();
    
    m_mapAuidToCoar = new HashMap<String, CollectionOfAuRepositories>();
  }

  // Routines.
  
  /**
   * This version only works with JCR CollectionOfAuRepositories.  It will
   * need to be expanded to work with Unix CollectionOfAuRepositories.
   * 
   * @param AUID  The name of the AUID
   * @param RepositorySpec  The specification for the files of the repository.
   * @see org.lockss.repository.v2.RepositoryManagerManager#addToAUIDtoCoar(java.lang.String, java.lang.String)
   * @throws LockssRepositoryException
   * @throws URISyntaxException
   * @throws IOException
   */
  public void addToAUIDtoCoar(String AUID, String repositorySpec) 
  throws LockssRepositoryException, URISyntaxException, IOException {
    CollectionOfAuRepositories coar;
    File path;
    String strPath;
    String strScheme;
    URI uriRepositorySpec;
    
    // The RepositorySpec is a URI.  
    uriRepositorySpec = new URI(repositorySpec);
    strScheme = uriRepositorySpec.getScheme();
    
    if (strScheme.equalsIgnoreCase(k_schemeJcr)) {
      strPath = uriRepositorySpec.getSchemeSpecificPart();
      path = new File(strPath);
      coar = new CollectionOfAuRepositoriesImpl(path);
      m_mapAuidToCoar.put(AUID, coar);
    } else {
      logger.error("Unknown scheme.  Please use jcr: as your scheme.");
      throw new LockssRepositoryException("Unknown scheme.");
    }
        
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#doSizeCalc(org.lockss.repository.v2.RepositoryNode)
   */
  public void doSizeCalc(RepositoryNode node) {
    // TODO
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#findLeastFullRepository()
   */
  public String findLeastFullRepository() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#getAuRepository(java.lang.String, org.lockss.plugin.ArchivalUnit)
   */
  public LockssAuRepository getAuRepository(String coarSpec, ArchivalUnit au) {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#getDiskFullThreshold()
   */
  public DF getDiskFullThreshold() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#getDiskWarnThreshold()
   */
  public DF getDiskWarnThreshold() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#getExistingCoarSpecsForAuid(java.lang.String)
   */
  public List<String> getExistingCoarSpecsForAuid(String auid) {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#getGlobalNodeCache()
   */
  public UniqueRefLruCache getGlobalNodeCache() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#getRepository(java.lang.String, org.lockss.plugin.ArchivalUnit)
   */
  public LockssAuRepository getRepository(String nameRepository, ArchivalUnit au) {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#getRepositoryDf()
   */
  public DF getRepositoryDf() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#getRepositoryList()
   */
  public List<String> getRepositoryList() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#getRepositoryMap()
   */
  public Map<String, DF> getRepositoryMap() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.lockss.repository.v2.RepositoryManagerManager#queueSizeCale(org.lockss.plugin.ArchivalUnit, org.lockss.repository.v2.RepositoryNode)
   */
  public void queueSizeCale(ArchivalUnit au, RepositoryNode node) {
    // TODO Auto-generated method stub

  }

  /* (non-Javadoc)
   * @see org.lockss.app.ConfigurableManager#setConfig(org.lockss.config.Configuration, org.lockss.config.Configuration, org.lockss.config.Configuration.Differences)
   */
  public void setConfig(Configuration newConfig, Configuration prevConfig,
      Differences changedKeys) {
    // TODO Auto-generated method stub

  }

}
