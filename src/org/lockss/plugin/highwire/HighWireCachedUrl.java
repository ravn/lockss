/*
 * $Id: HighWireCachedUrl.java,v 1.4 2002-10-16 04:57:03 tal Exp $
 */

/*

Copyright (c) 2002 Board of Trustees of Leland Stanford Jr. University,
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

import java.io.*;
import java.net.*;
import java.util.*;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.plugin.*;

/**
 * This is the CachedUrl object for the HighWirePlugin
 *
 * @author  Thomas S. Robertson
 * @version 0.0
 */

public class HighWireCachedUrl extends BaseCachedUrl {
  private URLConnection conn;
  private Properties headers;

  public HighWireCachedUrl(CachedUrlSet owner, String url){
    super(owner, url);
  }

  //CachedUrl methods
  public String getUrl(){
    return url;
  }

  public boolean exists(){
    File file = new File(mapUrlToFileName());
    return file.exists();
  }


  // Read interface - used by the proxy.

  public InputStream openForReading(){
    File file = new File(mapUrlToFileName());
    try{
      if (file.exists()){
	return new FileInputStream(file);
      }
    }
    catch (FileNotFoundException fnfe){
      fnfe.printStackTrace();
    }
    return null;
  }

  public Properties getProperties(){
    try {
      File file = new File(mapUrlToFileName() + ".props");
      InputStream is = new FileInputStream(file);
      Properties props = new Properties();
      props.load(is);
      is.close();
      return props;
    } catch (IOException e) {
      return headers;
    }
  }

  private String mapUrlToFileName(){
    int idx = url.indexOf("://")+3;
    int lastSlashIdx = url.lastIndexOf("/");
    int lastPeriodIdx = url.lastIndexOf(".");

    StringBuffer fileName = new StringBuffer("./");
    fileName.append(url.substring(idx));

    if (lastSlashIdx >= lastPeriodIdx){
      if (url.charAt(url.length()-1) != '/'){
	fileName.append('/');
      }
      fileName.append("index.html");
    }
    return fileName.toString();
  }


}

