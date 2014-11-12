/*
 * $Id: PermissionUrlConsumer.java,v 1.1 2014-11-12 20:11:23 wkwilson Exp $
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

package org.lockss.crawler;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.lockss.crawler.PermissionMap.IgnoreCloseInputStream;
import org.lockss.crawler.PermissionRecord.PermissionStatus;
import org.lockss.daemon.Crawler;
import org.lockss.daemon.LockssWatchdog;
import org.lockss.daemon.PermissionChecker;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.AuUtil;
import org.lockss.plugin.FetchedUrlData;
import org.lockss.plugin.UrlCacher;
import org.lockss.plugin.UrlConsumer;
import org.lockss.plugin.base.SimpleUrlConsumer;
import org.lockss.util.Logger;

public class PermissionUrlConsumer extends SimpleUrlConsumer {
  static Logger logger = Logger.getLogger(PermissionUrlConsumer.class);
  protected PermissionMap permMap;
  protected String charset;
  
  protected enum PermissionLogic {
    OR_CHECKER, AND_CHECKER
  }
  
  public PermissionUrlConsumer(Crawler.CrawlerFacade crawlFacade,
      FetchedUrlData fud, PermissionMap permMap) {
    super(crawlFacade, fud);
    this.permMap = permMap;
    charset = AuUtil.getCharsetOrDefault(fud.headers);
  }
  
  public void consume() throws IOException {
    boolean permOk = false;
    // if we didn't find at least one required lockss permission - fail.
    if (!checkPermission(permMap.getDaemonPermissionCheckers(),
        PermissionLogic.OR_CHECKER)) {
      logger.siteError("No (C)LOCKSS crawl permission on " + fud.fetchUrl);
      permMap.setPermissionResult(fud.fetchUrl,
          PermissionStatus.PERMISSION_NOT_OK);
    } else {
      Collection<PermissionChecker> pluginPermissionCheckers = 
          permMap.getPluginPermissionCheckers();
      if (pluginPermissionCheckers != null && 
          !pluginPermissionCheckers.isEmpty()) {
        if (!checkPermission(pluginPermissionCheckers,
            PermissionLogic.AND_CHECKER)) {
          logger.siteError("No plugin crawl permission on " + fud.fetchUrl);
          permMap.setPermissionResult(fud.fetchUrl,
              PermissionStatus.PERMISSION_NOT_OK);
        } else {
          permOk = true;
        }
      } else {
        permOk = true;
      }
    }
    if (permOk) {
      fud.resetInputStream();
      super.consume();
      permMap.setPermissionResult(fud.fetchUrl,
          PermissionStatus.PERMISSION_OK);
    }
  }
  
  protected boolean checkPermission(
      Collection<PermissionChecker> permCheckers, PermissionLogic logic)
          throws IOException {
    PermissionChecker checker;
    BufferedInputStream is = new BufferedInputStream(fud.input);
    // check the lockss checkers and find at least one checker that matches
    for (Iterator<PermissionChecker> it = permCheckers.iterator();
        it.hasNext(); ) {
      // allow us to reread contents if reasonable size
      is.mark(crawlFacade.permissonStreamResetMax());
      checker = it.next();

      // XXX Some PermissionCheckers close their stream.  This is a
      // workaround until they're fixed.
      Reader reader = new InputStreamReader(new IgnoreCloseInputStream(is),
              charset);
      if (checker.checkPermission(crawlFacade, reader, fud.fetchUrl)) {
        if(logic == PermissionLogic.OR_CHECKER){
          logger.debug3("Found permission on "+checker);
          return true; //we just need one permission to be successful here
        }
      } else if(logic == PermissionLogic.AND_CHECKER) {
        //All permissions must be successful fail
        return false;
      }
      if (it.hasNext()) {
        fud.resetInputStream();
        is = new BufferedInputStream(fud.input);
      } else if(logic == PermissionLogic.AND_CHECKER) {
        //All permissions have been successfull and we reached the end
        //An empty and checker will return true
        return true;
      }
    }
    //reached the end without finding permission
    return false; 
  }

}
