/*
 * $Id: TestCollectionUtil.java,v 1.8 2003-11-13 19:52:43 troberts Exp $
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
import junit.framework.TestCase;
import org.lockss.test.*;


/**
 * Test class for org.lockss.util.ArrayIterator
 */

public class TestCollectionUtil extends LockssTestCase {
  public static Class testedClasses[] = {
    org.lockss.util.CollectionUtil.class
  };

  public TestCollectionUtil(String msg) {
    super(msg);
  }

  public void testEmptyIter() {
    assertFalse(CollectionUtil.EMPTY_ITERATOR.hasNext());
    // make sure both next() and remove() throw the advertised exceptions
    try {
      CollectionUtil.EMPTY_ITERATOR.next();
      fail("next() should throw NoSuchElementException");
    } catch (NoSuchElementException e) {
    }
    try {
      CollectionUtil.EMPTY_ITERATOR.remove();
      fail("remove() should throw UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
    }
  }

  public void testIsDisjoint() {
    // empty sets are disjoint, as intersection is empty
    assertTrue(CollectionUtil.isDisjoint(Collections.EMPTY_LIST,
					 Collections.EMPTY_LIST));
    assertTrue(CollectionUtil.isDisjoint(ListUtil.list("1"),
					 Collections.EMPTY_LIST));
    assertFalse(CollectionUtil.isDisjoint(ListUtil.list("1"),
					  ListUtil.list("1")));
    assertFalse(CollectionUtil.isDisjoint(ListUtil.list("1", "2"),
					  ListUtil.list("1", "2")));
    assertFalse(CollectionUtil.isDisjoint(ListUtil.list("1", "2"),
					  ListUtil.list("2", "3")));
    assertTrue(CollectionUtil.isDisjoint(ListUtil.list("1"),
					 ListUtil.list("2")));
    assertTrue(CollectionUtil.isDisjoint(ListUtil.list("1", "2"),
					  ListUtil.list("3", "4")));
  }

  public void testIsoIter() {
    Vector v = new Vector();
    List l = new ArrayList();
    assertTrue(CollectionUtil.isIsomorphic(v.iterator(), l.iterator()));
    v.add("one");
    assertTrue( ! CollectionUtil.isIsomorphic(v.iterator(), l.iterator()));
    l.add("one");
    assertTrue(CollectionUtil.isIsomorphic(v.iterator(), l.iterator()));
    l.add("two");
    assertTrue( ! CollectionUtil.isIsomorphic(v.iterator(), l.iterator()));
    v.add("two");
    assertTrue(CollectionUtil.isIsomorphic(v.iterator(), l.iterator()));
    l.add("three");
    v.add("fweet");
    assertTrue( ! CollectionUtil.isIsomorphic(v.iterator(), l.iterator()));
  }

  public void testIsoColls() {
    Vector v1 = new Vector();
    Vector v2 = new Vector();
    Object a0[] = {};
    Object a1[] = {"12", "34"};
    assertTrue(CollectionUtil.isIsomorphic(v1, v2));
    assertTrue(CollectionUtil.isIsomorphic(v1, a0));
    assertFalse(CollectionUtil.isIsomorphic(v1, a1));
    v1.add(a1[0]);
    v1.add(a1[1]);
    assertTrue(CollectionUtil.isIsomorphic(v1, a1));
    assertFalse(CollectionUtil.isIsomorphic(new HashSet(), new Vector()));
    assertFalse(CollectionUtil.isIsomorphic(new Vector(), new HashSet()));
    Set s1 = new HashSet();
    Set s2 = new HashSet();
    s1.add("a");
    assertFalse(CollectionUtil.isIsomorphic(s1, s2));
    s2.add("a");
    assertTrue(CollectionUtil.isIsomorphic(s1, s2));
    Vector v = new Vector();
    v.add("a");
    assertFalse(CollectionUtil.isIsomorphic(s1, v));
    assertFalse(CollectionUtil.isIsomorphic(v, s1));
    s1.add("1");
    s1.add("2");
    s2.add("2");
    s2.add("1");
    assertTrue(CollectionUtil.isIsomorphic(s1, s2));
  }

  public void testRemoveElementThrowsIfNullCollection() {
    try {
      CollectionUtil.removeElement(null);
      fail("removeElement should have thrown for a null collection");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testRemoveElementThrowsIfEmptyCollection() {
    assertNull(CollectionUtil.removeElement(SetUtil.set()));
  }

  public void testRemoveElement() {
    Set set = SetUtil.set("str1", "str2", "str3");
    Set expectedElements = SetUtil.set("str1", "str2", "str3");
    String element = (String) CollectionUtil.removeElement(set);
    assertContains(expectedElements, element);
    assertDoesNotContain(set, element);
  }

}
