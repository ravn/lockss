package org.lockss.test;

import org.lockss.app.LockssDaemon;
import java.util.*;
import org.lockss.hasher.*;
import org.lockss.protocol.*;
import org.lockss.poller.*;
import org.lockss.state.*;
import org.lockss.repository.*;
import org.lockss.proxy.*;
import org.lockss.crawler.*;
import org.lockss.plugin.*;
import org.lockss.app.*;
import org.lockss.daemon.status.*;

public class MockLockssDaemon extends LockssDaemon {
  HashService hashService = null;
  PollManager pollManager = null;
  LcapComm commManager = null;
  LcapRouter routerManager = null;
  LockssRepositoryService lockssRepositoryService = null;
  HistoryRepository historyRepository = null;
  ProxyManager proxyManager = null;
  CrawlManager crawlManager = null;
  PluginManager pluginManager = null;
  IdentityManager identityManager = null;
  NodeManagerService nodeManagerService = null;
  StatusService statusService = null;

  public MockLockssDaemon() {
    this(null);
  }

  public MockLockssDaemon(List urls) {
    super(urls);
  }

  public void startDaemon() throws Exception {
  }

  public void stopDaemon() {
    hashService = null;
    pollManager = null;
    commManager = null;
    lockssRepositoryService = null;
    historyRepository = null;
    proxyManager = null;
    crawlManager = null;
    pluginManager = null;
    identityManager = null;
    nodeManagerService = null;
    statusService = null;
    //super.stopDaemon();

    regulator.freeAllLocks();
  }

  /**
   * return the hash service instance
   * @return the HashService
   */
  public HashService getHashService() {
    if (hashService == null) {
      hashService = new HashService();
      try {
        hashService.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.HASH_SERVICE, hashService);
    }
    return hashService;
  }

  /**
   * return the poll manager instance
   * @return the PollManager
   */
  public PollManager getPollManager() {
    if (pollManager == null) {
      pollManager = new PollManager();
      try {
        pollManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.POLL_MANAGER, pollManager);
    }
    return pollManager;
  }

  /**
   * return the communication manager instance
   * @return the LcapComm
   */
  public LcapComm getCommManager() {
    if (commManager == null) {
      commManager = new LcapComm();
      try {
        commManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.COMM_MANAGER, commManager);
    }
    return commManager;
  }

  /**
   * return the router manager instance
   * @return the LcapRouter
   */
  public LcapRouter getRouterManager() {
    if (routerManager == null) {
      routerManager = new LcapRouter();
      try {
        routerManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.ROUTER_MANAGER, routerManager);
    }
    return routerManager;
  }

  /**
   * return the lockss repository service
   * @return the LockssRepositoryService
   */
  public LockssRepositoryService getLockssRepositoryService() {
    if (lockssRepositoryService == null) {
      lockssRepositoryService = new LockssRepositoryServiceImpl();
      try {
        lockssRepositoryService.initService(this);
      } catch (LockssDaemonException ex) { }
      theManagers.put(LockssDaemon.LOCKSS_REPOSITORY_SERVICE,
                      lockssRepositoryService);
    }
    return lockssRepositoryService;
  }
  /**
   * get a Lockss Repository instance.
   * @param au the ArchivalUnit
   * @return the LockssRepository
   */
  public LockssRepository getLockssRepository(ArchivalUnit au) {
    getLockssRepositoryService().addLockssRepository(au);
    return getLockssRepositoryService().getLockssRepository(au);
  }

  /**
   * return the history repository instance
   * @return the HistoryRepository
   */
  public HistoryRepository getHistoryRepository() {
    if (historyRepository == null) {
      HistoryRepositoryImpl impl = new HistoryRepositoryImpl();
      try {
        impl.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      historyRepository = impl;
      theManagers.put(LockssDaemon.HISTORY_REPOSITORY, historyRepository);
    }
    return historyRepository;
  }

  /**
   * return the node manager service
   * @return the NodeManagerService
   */
  public NodeManagerService getNodeManagerService() {
    if (nodeManagerService == null) {
      nodeManagerService = new NodeManagerServiceImpl();
      try {
        nodeManagerService.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.NODE_MANAGER_SERVICE, nodeManagerService);
    }
    return nodeManagerService;
  }


  /**
   * return the node manager instance.  Uses NodeManagerService.
   * @param au the ArchivalUnit
   * @return the NodeManager
   */
  public NodeManager getNodeManager(ArchivalUnit au) {
    getNodeManagerService().addNodeManager(au);
    return nodeManagerService.getNodeManager(au);
  }

  /**
   * return the proxy manager instance
   * @return the ProxyManager
   */
  public ProxyManager getProxyManager() {
    if (proxyManager == null) {
      proxyManager = new ProxyManager();
      try {
        proxyManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.PROXY_MANAGER, proxyManager);
    }
    return proxyManager;
  }

  /**
   * return the crawl manager instance
   * @return the CrawlManager
   */
  public CrawlManager getCrawlManager() {
    if (crawlManager == null) {
      CrawlManagerImpl impl = new CrawlManagerImpl();
      try {
        impl.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      crawlManager = impl;
      theManagers.put(LockssDaemon.CRAWL_MANAGER, crawlManager);
    }
    return crawlManager;
  }

  /**
   * return the plugin manager instance
   * @return the PluginManager
   */
  public PluginManager getPluginManager() {
    if (pluginManager == null) {
      pluginManager = new PluginManager();
      try {
        pluginManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.PLUGIN_MANAGER, pluginManager);
    }
    return pluginManager;
  }

  /**
   * return the Identity Manager
   * @return IdentityManager
   */

  public IdentityManager getIdentityManager() {
    if (identityManager == null) {
      identityManager = new IdentityManager();
      try {
        identityManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
    }
    theManagers.put(LockssDaemon.IDENTITY_MANAGER, identityManager);
    return identityManager;
  }

  public StatusService getStatusService() {
    if (statusService == null) {
      StatusServiceImpl impl = new StatusServiceImpl();
      try {
        impl.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      statusService = impl;
      theManagers.put(LockssDaemon.STATUS_SERVICE, statusService);
    }

    return statusService;
  }


  /**
   * Set the CommManager
   * @param commMan the new manager
   */
  public void setCommManager(LcapComm commMan) {
    commManager = commMan;
    theManagers.put(LockssDaemon.COMM_MANAGER, commManager);
  }

  /**
   * Set the RouterManager
   * @param routerMan the new manager
   */
  public void setRouterManager(LcapRouter routerMan) {
    routerManager = routerMan;
    theManagers.put(LockssDaemon.ROUTER_MANAGER, routerManager);
  }

  /**
   * Set the CrawlManager
   * @param crawlMan the new manager
   */
  public void setCrawlManager(CrawlManager crawlMan) {
    crawlManager = crawlMan;
    theManagers.put(LockssDaemon.CRAWL_MANAGER, crawlManager);
  }

  /**
   * Set the HashService
   * @param hashServ the new service
   */
  public void setHashService(HashService hashServ) {
    hashService = hashServ;
    theManagers.put(LockssDaemon.HASH_SERVICE, hashService);
  }

  /**
   * Set the HistoryRepository
   * @param histRepo the new repository
   */
  public void setHistoryRepository(HistoryRepository histRepo) {
    historyRepository = histRepo;
    theManagers.put(LockssDaemon.HISTORY_REPOSITORY, historyRepository);
  }

  /**
   * Set the IdentityManager
   * @param idMan the new manager
   */
  public void setIdentityManager(IdentityManager idMan) {
    identityManager = idMan;
    theManagers.put(LockssDaemon.IDENTITY_MANAGER, identityManager);
  }

  /**
   * Set the LockssRepositoryService
   * @param lockssRepoService the new repository service
   */
  public void setLockssRepositoryService(LockssRepositoryService
                                         lockssRepoService) {
    lockssRepositoryService = lockssRepoService;
    theManagers.put(LockssDaemon.LOCKSS_REPOSITORY_SERVICE,
                    lockssRepositoryService);
  }

  /**
   * Set the LockssRepository for a given AU.  Requires a
   * MocKLockssRepositoryService.
   * @param repo the new repository
   * @param au the ArchivalUnit
   */
  public void setLockssRepository(LockssRepository repo, ArchivalUnit au) {
    getLockssRepositoryService();
    if (lockssRepositoryService instanceof MockLockssRepositoryService) {
      ((MockLockssRepositoryService)lockssRepositoryService).auMaps.put(au, repo);
    } else {
      throw new UnsupportedOperationException("Couldn't setLockssRepository" +
                                              "with a non-Mock service.");
    }
  }

  /**
   * Set a new NodeManagerService.
   * @param nms the new service
   */
  public void setNodeManagerService(NodeManagerService nms) {
    nodeManagerService = nms;
    theManagers.put(LockssDaemon.NODE_MANAGER_SERVICE, nodeManagerService);
  }

  /**
   * Set the NodeManager for a given AU.  Requires a MocKNodeManagerService.
   * @param nodeMan the new manager
   * @param au the ArchivalUnit
   */
  public void setNodeManager(NodeManager nodeMan, ArchivalUnit au) {
    getNodeManagerService();
    if (nodeManagerService instanceof MockNodeManagerService) {
      ((MockNodeManagerService)nodeManagerService).auMaps.put(au, nodeMan);
    } else {
      throw new UnsupportedOperationException("Couldn't setNodeManager with "+
                                              "a non-Mock service.");
    }
  }

  /**
   * Set the PluginManager
   * @param pluginMan the new manager
   */
  public void setPluginManager(PluginManager pluginMan) {
    pluginManager = pluginMan;
    theManagers.put(LockssDaemon.PLUGIN_MANAGER, pluginManager);
  }

  /**
   * Set the PollManager
   * @param pollMan the new manager
   */
  public void setPollManager(PollManager pollMan) {
    pollManager = pollMan;
    theManagers.put(LockssDaemon.POLL_MANAGER, pollManager);
  }

  /**
   * Set the ProxyManager
   * @param proxyMgr the new manager
   */
  public void setProxyManager(ProxyManager proxyMgr) {
    proxyManager = proxyMgr;
    theManagers.put(LockssDaemon.PROXY_MANAGER, proxyManager);
  }

  private boolean daemonInited = false;
  private boolean daemonRunning = false;

  /** Return true iff all managers have been inited */
  public boolean isDaemonInited() {
    return daemonInited;
  }

  /** Return true iff all managers have been started */
  public boolean isDaemonRunning() {
    return daemonRunning;
  }

  /** set daemonInited */
  public void setDaemonInited(boolean val) {
    daemonInited = val;
  }

  /** set daemonRunning */
  public void setDaemonRunning(boolean val) {
    daemonRunning = val;
  }
}
