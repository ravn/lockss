/*
 * $Id: DatedPeerIdSet.java,v 1.2 2013-03-20 19:54:11 tlipkis Exp $
 */

/*

Copyright (c) 2000-2013 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.protocol;

import java.io.IOException;

/**
 * @author edwardsb
 *
 * This method adds a date (implemented as a long) to the set.
 */
public interface DatedPeerIdSet extends PersistentPeerIdSet {
  /**
   * Notice that the "date" being set is actually a long.  The code internally uses long for dates.
   * You can convert between dates and longs with:
   * 
   *   Date d -> long
   *   d.getTime()
   *   
   *   long l -> Date
   *   Date newDate = new Date();
   *   newDate.setTime(l);
   *   
   * @param l
   */
  public void setDate(long l) throws IOException;
  
  /**
   * 
   * @return long
   */
  public long getDate() throws IOException;
}
