/*
 * $Id: HighWireDrupalPermissionCheckerFactory.java,v 1.2 2015-01-23 08:10:37 etenbrink Exp $
 */

/*

Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.highwire;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

import org.lockss.daemon.*;
import org.lockss.daemon.Crawler.CrawlerFacade;
import org.lockss.plugin.*;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;

public class HighWireDrupalPermissionCheckerFactory implements PermissionCheckerFactory {
  
  public static class DrupalProbePermissionChecker extends ProbePermissionChecker {
    
    private static final Logger logger = Logger.getLogger(DrupalProbePermissionChecker.class);
    
    @Override
    public boolean checkPermission(CrawlerFacade crawlFacade,
        Reader inputReader, String permissionUrl) {
      
      BufferedReader in = new BufferedReader(inputReader); 
      boolean ret = true;
      try {
        in.mark(102400);
        if (!StringUtil.containsString(in, "platform = DRUPAL", true)) {
          logger.siteError(" ");
          logger.siteError("       ===============        ");
          logger.siteError(" ");
          logger.siteError("DRUPAL flag NOT found");
          logger.siteError(" ");
          logger.siteError("       ===============        ");
          logger.siteError(" ");
          ret = false;
        }
        in.reset();
      } catch (IOException e) {
        logger.warning("IOException checking drupal flag", e);
      }
      if (ret) {
        ret = super.checkPermission(crawlFacade, in, permissionUrl);
      }
      return ret;
    }
    
    public DrupalProbePermissionChecker() {
      super();
    }
  }
  
  @Override
  public List<DrupalProbePermissionChecker> createPermissionCheckers(ArchivalUnit au) {
    List<DrupalProbePermissionChecker> list = new ArrayList<DrupalProbePermissionChecker>(1);
    list.add(new DrupalProbePermissionChecker());
    return list;
  }
}

