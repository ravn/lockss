/*
 * $Id: SingleCrawlStatus.java,v 1.1.2.1 2005-01-19 17:05:02 troberts Exp $
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

package org.lockss.crawler;
import java.util.*;
import org.lockss.daemon.status.*;
import org.lockss.daemon.*;
// import org.lockss.app.*;
import org.lockss.plugin.*;
// import org.lockss.plugin.base.*;
import org.lockss.util.*;

public class SingleCrawlStatus implements StatusAccessor {
  private CrawlManagerStatus cmStatus = null;

  private static final String URL = "url";
  private static final String CRAWL_ERROR = "crawl_error";

  private static final String FETCHED_TABLE_NAME = "fetched";
  private static final String ERROR_TABLE_NAME = "error";
  private static final String NOT_MODIFIED_TABLE_NAME = "not-modified";
  private static final String PARSED_TABLE_NAME = "parsed";

  private static List colDescsFetched =
    ListUtil.list(new ColumnDescriptor(URL, "URL Fetched",
				       ColumnDescriptor.TYPE_STRING));
    
  private static List colDescsNotModified =
    ListUtil.list(new ColumnDescriptor(URL, "URL Not-Modified",
				       ColumnDescriptor.TYPE_STRING));

  private static List colDescsParsed =
    ListUtil.list(new ColumnDescriptor(URL, "URL Parsed",
				       ColumnDescriptor.TYPE_STRING));

  private static List colDescsError =
    ListUtil.list(new ColumnDescriptor(URL, "URL",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor(CRAWL_ERROR, "Error",
				       ColumnDescriptor.TYPE_STRING));

  public SingleCrawlStatus(CrawlManagerStatus cmStatus) {
    this.cmStatus = cmStatus;
  }

  public void populateTable(StatusTable table) {
    if (table == null) {
      throw new IllegalArgumentException("Called with null table");
    } else if (table.getKey() == null) {
      throw new IllegalArgumentException("SingleCrawlStatus requires a key");
    }
    String key = table.getKey();
    table.setColumnDescriptors(getColDescs(key));
    table.setTitle(getTableTitle(key));
    table.setRows(makeRows(key));
  }
  
  private String getTableTitle(String key) {
    Crawler.Status status =
      (Crawler.Status)cmStatus.getStatusObject(getIndexFromKey(key));
    ArchivalUnit au = status.getAu();
    String tableStr = getTableStrFromKey(key);
    if (FETCHED_TABLE_NAME.equals(tableStr)) {
      return "URLs fetched during crawl of "+au.getName();
    } else if (ERROR_TABLE_NAME.equals(tableStr)) {
      return "Errors during crawl of "+au.getName();
    } else if (NOT_MODIFIED_TABLE_NAME.equals(tableStr)) {
      return "URLs not modified during crawl of "+au.getName();
    } else if (PARSED_TABLE_NAME.equals(tableStr)) {
      return "URLs parsed during crawl of "+au.getName();
    }
    return "";
  }


  private String getTableStrFromKey(String key) {
    return key.substring(0, key.indexOf(";"));
  }

  private int getIndexFromKey(String key) {
    return Integer.parseInt(key.substring(key.indexOf(";")+1));
  }

  private List getColDescs(String key) {
    String tableStr = getTableStrFromKey(key);

    if (FETCHED_TABLE_NAME.equals(tableStr)) {
      return colDescsFetched;
    } else if (ERROR_TABLE_NAME.equals(tableStr)) {
      return colDescsError;
    } else if (NOT_MODIFIED_TABLE_NAME.equals(tableStr)) {
      return colDescsNotModified;
    } else if (PARSED_TABLE_NAME.equals(tableStr)) {
      return colDescsParsed;
    }
    return null;
  }

  private List makeRows(String key) {
    Crawler.Status status =
      (Crawler.Status)cmStatus.getStatusObject(getIndexFromKey(key));
    String tableStr = getTableStrFromKey(key);
    List rows = null;

    if (FETCHED_TABLE_NAME.equals(tableStr)) {
      rows = urlSetToRows(status.getUrlsFetched());
    } else if (NOT_MODIFIED_TABLE_NAME.equals(tableStr)) {
      rows = urlSetToRows(status.getUrlsNotModified());
    } else if (PARSED_TABLE_NAME.equals(tableStr)) {
      rows = urlSetToRows(status.getUrlsParsed());
    } else if (ERROR_TABLE_NAME.equals(tableStr)) {
      Map errorMap = status.getUrlsWithErrors();
      Set errorUrls = errorMap.keySet();
      rows = new ArrayList(errorUrls.size());
      for (Iterator it = errorUrls.iterator(); it.hasNext();) {
	String url = (String)it.next();
 	rows.add(makeRow(url, (String)errorMap.get(url)));
      }
    }
    return rows;
  }

  /**
   * Take a set of URLs and make a row for each, where row{"URL"}=<url>
   */
  private List urlSetToRows(Set urls) {
    List rows = new ArrayList(urls.size());
    for (Iterator it = urls.iterator(); it.hasNext();) {
      String url = (String)it.next(); 
      rows.add(makeRow(url));
    }
    return rows;
  }
    
  private Map makeRow(String url) {
    Map row = new HashMap();
    row.put(URL, url);   
    return row;
  }

  private Map makeRow(String url, String error) {
    Map row = makeRow(url);
    row.put(CRAWL_ERROR, error);   
    return row;
  }

  public String getDisplayName() {
    throw new UnsupportedOperationException("No generic name for SingleCrawlStatus");
  }

  public boolean requiresKey() {
    return true;
  }
}

