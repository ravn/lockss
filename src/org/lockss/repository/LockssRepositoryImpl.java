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

package org.lockss.repository;
import java.io.*;
import java.util.*;

/**
 * LockssRepository is used to organize the urls being cached.
 */
public class LockssRepositoryImpl implements LockssRepository {
  /**
   * Name of top directory in which the urls are cached.
   */
  public static final String CACHE_ROOT_NAME = "cache";

  private String rootLocation;

  public LockssRepositoryImpl(String rootPath) {
    rootLocation = rootPath;
    if (rootLocation.charAt(rootLocation.length()-1) != File.separatorChar) {
      rootLocation += "/";
    }
  }

  public RepositoryEntry getRepositoryEntry(String url) {
    File entryDir = new File(rootLocation + mapUrlToCacheLocation(url));
    if (!entryDir.exists() || !entryDir.isDirectory()) {
      return null;
    }
    File leafFile = new File(entryDir, LeafEntryImpl.LEAF_FILE_NAME);
    if (leafFile.exists()) {
      return new LeafEntryImpl(url, rootLocation);
    } else {
      return new DirectoryEntryImpl(url, rootLocation);
    }
  }

  public LeafEntry createLeafEntry(String url) {
    return new LeafEntryImpl(url, rootLocation);
  }

  /**
   * mapUrlToCacheFileName() is the name mapping method used by the GenericFileUrlCacher.
   * This maps a given url to a cache file location.
   * It is also used by the GenericFileCachedUrl to extract the content.
   * It creates directories under a CACHE_ROOT location which mirror the html string.
   * So 'http://www.journal.org/issue1/index.html' would be cached in the file:
   * CACHE_ROOT/www.journal.org/http/issue1/index.html
   * @param url the url to translate
   * @return the file cache location
   */
  public static String mapUrlToCacheLocation(String url) {
    String fileName = CACHE_ROOT_NAME + File.separator;

    int idx = url.indexOf("://");
    if (idx>=0) {
      String prefix = url.substring(0, idx);
      String urlRemainder = url.substring(idx+3);
      idx = urlRemainder.indexOf("/");
      if (idx>=0) {
        fileName += urlRemainder.substring(0, idx) + File.separator;
        fileName += prefix + urlRemainder.substring(idx);
      } else {
        fileName += urlRemainder;
      }
    } else {
      fileName += url;
    }
    return fileName;
  }

}
