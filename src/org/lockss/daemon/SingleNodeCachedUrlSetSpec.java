/*
 * $Id: SingleNodeCachedUrlSetSpec.java,v 1.3 2003-06-03 05:49:33 tal Exp $
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

package org.lockss.daemon;

/**
 * SingleNodeCachedUrlSetSpec represents a single node (URL).  It is used to
 * refer to the single content file at a node, not including the children.
 */
public class SingleNodeCachedUrlSetSpec implements CachedUrlSetSpec {
  private String url;

  /**
   * Create a SingleNodeCachedUrlSetSpec
   * @param url the url
   */
  public SingleNodeCachedUrlSetSpec(String url) {
    this.url = url;
  }

  /**
   * Returns the url.
   * @return the url
   */
  public String getUrl() {
    return url;
  }

  /**
   * @param url the url to match
   * @return true if the url is equal to this spec's url.
   */
  public boolean matches(String url) {
    return this.url.equals(url);
  }

  /** @return false */
  public boolean isAU() {
    return false;
  }

  /** @return true */
  public boolean isSingleNode() {
    return true;
  }

  /** @return false */
  public boolean isRangeRestricted() {
    return false;
  }

  public boolean isDisjoint(CachedUrlSetSpec spec) {
    if (spec.isSingleNode()) {
      return !equals(spec);
    }
    if (spec.isAU()) {
      return false;
    }
    return !spec.subsumes(this);
  }

  public boolean subsumes(CachedUrlSetSpec spec) {
    if (spec.isSingleNode()) {
      return equals(spec);
    }
    return false;
  }

  /**
   * overrides Object.toString()
   * @return String representaion of this object
   */
  public String toString() {
    return "[SNCUSS: "+url+"]";
  }

  /**
   * Overrides Object.equals().
   * Compares the lists and REs of the two specs.
   * @param obj the other spec
   * @return true if the lists and REs are equal
   */
  public boolean equals(Object obj) {
    if (obj instanceof SingleNodeCachedUrlSetSpec) {
      return url.equals(((SingleNodeCachedUrlSetSpec)obj).getUrl());
    } else {
      return false;
    }
  }

  /**
   * Overrides Object.hashCode().
   * Returns the hash of the strings
   * @return the hashcode
   */
  public int hashCode() {
    return url.hashCode();
  }
}
