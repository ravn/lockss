/*
 * $Id: FifoQueue.java,v 1.1 2002-11-05 21:07:02 tal Exp $
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

package org.lockss.util;

import java.util.*;

/**
 * A thread-safe FIFO queue
 */
public class FifoQueue implements Queue {
  private Vector queue = new Vector();

  /** 
   * Create a new FIFO queue. 
   */
  public FifoQueue() {
  }

  /**
   * Add to the end of the queue. */
  public synchronized Object put(Object obj) {
    if (obj == null) {
      throw new NullPointerException("Attempt to put null element on Queue");
    }
    queue.addElement(obj);
    // supposedly allows the highest priority thread to run first
    notifyAll();
    return obj;
  }

  /** 
   * Remove from the beginning of the queue. Does not return until
   * an object appears in the queue and becomes available to this thread.
   */
  public synchronized Object get(ProbabilisticTimer timer)
      throws InterruptedException {
    while (queue.isEmpty() && !timer.expired()) {
      try {
	timer.setThread();
	this.wait(timer.getRemainingTime());
      } catch (InterruptedException e) {
	break;
//  	  continue;
      } finally {
	timer.clearThread();
      }
    }
    if (!queue.isEmpty()) {
      // remove from beginning
      Object obj = queue.firstElement();
      queue.removeElementAt(0);
      return obj;
    } else {
      return null;
    }
  }

  /** 
   * Return first element on queue, without removing it.
   * @return The element at the head of the queue, or null if queue is empty
   */
  public synchronized Object peek() {
    return (queue.isEmpty() ? null : queue.firstElement());
  }
}
