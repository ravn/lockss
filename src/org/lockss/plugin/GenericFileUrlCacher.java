/*
 * $Id: GenericFileUrlCacher.java,v 1.20 2003-08-02 00:16:05 eaalto Exp $
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

package org.lockss.plugin;

import java.io.*;
import java.util.Properties;
import org.lockss.daemon.*;
import org.lockss.util.StreamUtil;
import org.lockss.repository.*;
import org.lockss.plugin.base.*;
import org.lockss.app.*;

/**
 * This is an abstract file implementation of UrlCacher which uses the
 * {@link LockssRepository}. The source for the content needs to be provided
 * in any extension.
 *
 * @author  Emil Aalto
 * @version 0.0
 */

public abstract class GenericFileUrlCacher extends BaseUrlCacher {
  private LockssRepository repository;

  public GenericFileUrlCacher(CachedUrlSet owner, String url) {
    super(owner, url);
    ArchivalUnit au = owner.getArchivalUnit();
    repository = au.getPlugin().getDaemon().getLockssRepository(au);
  }

  protected void storeContent(InputStream input, Properties headers)
      throws IOException {
    logger.debug3("Caching url '"+url+"'");
    RepositoryNode leaf = repository.createNewNode(url);
    leaf.makeNewVersion();

    OutputStream os = leaf.getNewOutputStream();
    StreamUtil.copy(input, os);
    os.close();
    input.close();

    leaf.setNewProperties(headers);

    leaf.sealNewVersion();
  }
}

