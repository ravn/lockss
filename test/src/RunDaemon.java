/*
 * $Id: RunDaemon.java,v 1.17 2003-02-13 06:28:52 claire Exp $
 */

/*

Copyright (c) 2000-2002 Board of Trustees of Leland Stanford Jr. University,
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

import java.util.*;
import java.io.*;
import java.net.*;
import org.lockss.daemon.*;
import org.lockss.hasher.HashService;
import org.lockss.protocol.LcapComm;
import org.lockss.plugin.simulated.*;
import org.lockss.test.*;
import org.lockss.protocol.*;
import org.lockss.poller.*;
import org.lockss.util.*;
import org.lockss.crawler.*;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.plugin.PluginManager;

public class RunDaemon {
  private static final String DEFAULT_DIR_PATH = "./";

  static final String PARAM_CACHE_LOCATION =
    LockssRepositoryImpl.PARAM_CACHE_LOCATION;
  static final String PARAM_SHOULD_CALL_POLL =
    Configuration.PREFIX+"shouldCallPoll";
  static final String PARAM_POLL_TYPE =
    Configuration.PREFIX+"poll.type";

  private static Logger log = Logger.getLogger("RunDaemon");

  private SimulatedArchivalUnit sau = null;
  private List propUrls = null;
  private String dirPath = null;
  private PollManager pollManager = null;

  public static void main(String argv[]) {
    Vector urls = new Vector();
    for (int i=0; i<argv.length; i++) {
      urls.add(argv[i]);
    }
    try {
      RunDaemon daemon = new RunDaemon(urls);
      daemon.runDaemon();
    } catch (Throwable e) {
      System.err.println("Exception thrown in main loop:");
      e.printStackTrace();
    } finally {
//        System.err.println("Exiting");
//        System.exit(0);
    }
  }

  RunDaemon(List propUrls){
    this.propUrls = propUrls;
  }

  public void runDaemon() {
    MockLockssDaemon daemon = new MockLockssDaemon(propUrls);
    Configuration.startHandler(propUrls);
    System.err.println("Sleeping so config can get set");
    Configuration.waitConfig();
    System.err.println("Awake");

    daemon.getHashService().startService();
    daemon.getCommManager();
    pollManager = daemon.getPollManager();
    dirPath =
      Configuration.getParam(PARAM_CACHE_LOCATION, DEFAULT_DIR_PATH);
    boolean shouldCallPoll =
      Configuration.getBooleanParam(PARAM_SHOULD_CALL_POLL,
				    false);

    sau = new SimulatedArchivalUnit(dirPath);
    daemon.getPluginManager().registerArchivalUnit(sau);

    int poll_type = Configuration.getIntParam(PARAM_POLL_TYPE,
        LcapMessage.CONTENT_POLL_REQ);

    createContent();
    crawlContent();
    if (shouldCallPoll) {
      try {
	Thread.currentThread().sleep(1000);
        String url = "http://www.example.com/";
        ArchivalUnit au = daemon.getPluginManager().findArchivalUnit(url);
        CachedUrlSet cus = au.makeCachedUrlSet(url, null, null);
	pollManager.requestPoll(cus, null, null, poll_type);
      } catch (Exception e) {
	e.printStackTrace();
      }
    }
    else {
    }
  }

  private void createContent() {
    SimulatedContentGenerator scgen = sau.getContentGenerator();
    scgen.setTreeDepth(2);
    scgen.setNumBranches(2);
    scgen.setNumFilesPerBranch(2);
    scgen.setFileTypes(scgen.FILE_TYPE_HTML+scgen.FILE_TYPE_TXT);
    scgen.setAbnormalFile("1,1", 1);

    sau.generateContentTree();
  }

  private void crawlContent() {
    CrawlSpec spec = new CrawlSpec(sau.SIMULATED_URL_START, null);
    Crawler crawler = new GoslingCrawlerImpl();
    crawler.doCrawl(sau, spec.getStartingUrls(), true, Deadline.NEVER);
  }
}
