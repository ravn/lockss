/*
 * $Id: FixedTimedMap.java,v 1.3 2003-12-17 02:09:45 tlipkis Exp $
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

package org.lockss.util;

import java.util.*;
import org.apache.commons.collections.*;

/**
 * FixedTimedMap implements the Map interface.  It has the
 * additional property that entries expire on an interval specified
 * by a parameter to the constructor.  The interval is calculated from the
 * time at which the entry was added to the map.  This map is a hash map; as
 * such, classes used as keys should have a defined <code>hashCode</code>
 * method that obeys the contract for hash codes.
 * @author Tyrone Nicholas
 * @version 1.0
 */

public class FixedTimedMap extends TimedMap implements Map
{

  /** Amount of time each entry has before it will be deleted */
  int interval;

  /** Constructor.
   * @param interval Interval after which entries expire, in milliseconds.
   */

  public FixedTimedMap(int interval)
  {
    this.interval = interval;
    this.keytimes = new HashMap();
    this.entries = new SequencedHashMap();
  }

  void updateEntries()
  {
    while (!entries.isEmpty())
    {
      Object obj = ((SequencedHashMap)entries).getFirstKey();
      Deadline entry = (Deadline)keytimes.get(obj);
      if (entry.expired())
      {
        keytimes.remove(obj);
        entries.remove(obj);
      }
      else
        return;
    }
  }

  boolean areThereExpiredEntries() {
    if (entries.isEmpty()) {
      return false;
    }
    Object obj = ((SequencedHashMap)entries).getFirstKey();
    Deadline first = (Deadline)keytimes.get(obj);
    return first.expired();
  }



  public Object remove(Object key) {
    updateEntries();
    keytimes.remove(key);
    return entries.remove(key);
  }

  public Object put(Object key, Object value)
  {
    updateEntries();
    Deadline deadline = Deadline.in(interval);
    keytimes.put(key,deadline);
    return entries.put(key, value);
  }

  /** Places all entries from another map into this one.  Each entry gets its
   * own expiry time based on when it was added in real time.
   * @param t Map whose entries are being added to this map.
   */

  public void putAll(Map t)
  {
    updateEntries();
    Iterator it = t.entrySet().iterator();
    while (it.hasNext())
    {
      Map.Entry entry = (Map.Entry) it.next();
      put(entry.getKey(), entry.getValue());
    }
  }
}
