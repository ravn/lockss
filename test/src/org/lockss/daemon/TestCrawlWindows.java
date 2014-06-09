/*
 * $Id: TestCrawlWindows.java,v 1.9 2014-06-09 23:53:28 tlipkis Exp $
 */

/*

Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
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

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import org.lockss.util.*;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockCrawlWindow;

/**
 * This is the test class for org.lockss.daemon.CrawlWindowRule.Window
 */
public class TestCrawlWindows extends LockssTestCase {
  Calendar start;
  Calendar end;
  Calendar testCal;

  static TimeZone GMT = TimeZone.getTimeZone("GMT");

  public void setUp() throws Exception {
    super.setUp();

    start = Calendar.getInstance();
    end = Calendar.getInstance();
    testCal = Calendar.getInstance();
  }

  public void testDefaultTimeZone() {
    CrawlWindows.BaseCalCrawlWindow interval =
        new CrawlWindows.Interval(start, end, CrawlWindows.HOUR_OF_DAY, null);
    assertEquals(TimeZone.getDefault().getID(), interval.getTimeZoneId());
  }

  public void testTimeZone() {
    start = Calendar.getInstance(GMT);
    start.set(Calendar.HOUR_OF_DAY, 7);
    start.set(Calendar.MINUTE, 0);
    end = Calendar.getInstance(GMT);
    end.set(Calendar.HOUR_OF_DAY, 8);
    // since the end point is non-inclusive, end a minute after the hour
    end.set(Calendar.MINUTE, 1);

    // same time zone
    CrawlWindows.BaseCalCrawlWindow interval =
        new CrawlWindows.Interval(start, end, CrawlWindows.TIME, GMT);

    testCal = Calendar.getInstance(GMT);
    testCal.set(Calendar.MINUTE, 0);
    testCal.set(Calendar.HOUR_OF_DAY, 8);
    assertTrue(interval.canCrawl(testCal.getTime()));
    testCal.set(Calendar.HOUR_OF_DAY, 6);
    assertFalse(interval.canCrawl(testCal.getTime()));

    // different time zone
    CrawlWindows.BaseCalCrawlWindow interval2 =
        new CrawlWindows.Interval(start, end, CrawlWindows.TIME,
				  TimeZone.getTimeZone("GMT+1:00"));
    testCal.set(Calendar.HOUR_OF_DAY, 8);
    assertFalse(interval2.canCrawl(testCal.getTime()));
    testCal.set(Calendar.HOUR_OF_DAY, 6);
    assertTrue(interval2.canCrawl(testCal.getTime()));
  }

  public void testANDSetNull() {
    try {
      CrawlWindow cw = new CrawlWindows.And(null);
      fail("CrawlWindows.AND with null list should throw");
    } catch (NullPointerException e) { }
    CrawlWindow cw = new CrawlWindows.And(Collections.EMPTY_SET);
    assertTrue(cw.canCrawl(testCal.getTime()));
  }

  public void testORSetNull() {
    try {
      CrawlWindow cw = new CrawlWindows.Or(null);
      fail("CrawlWindows.OR with null list should throw");
    } catch (NullPointerException e) { }
    CrawlWindow cw = new CrawlWindows.Or(Collections.EMPTY_SET);
    assertFalse(cw.canCrawl(testCal.getTime()));
  }

  public void testNOTNull() {
    try {
      CrawlWindow cw = new CrawlWindows.Not(null);
      fail("CrawlWindows.NOT with null window should throw");
    } catch (NullPointerException e) { }
  }

  public void testWindowSetWrongClass() {
    try {
      CrawlWindow cw = new CrawlWindows.And(SetUtil.set("foo"));
      fail("CrawlWindows.WindowSet with list of non-CrawlWindows should throw");
    } catch (ClassCastException e) { }
  }

  public void testANDSet() {
    MockCrawlWindow win1 = new MockCrawlWindow();
    win1.setAllowCrawl(false);
    MockCrawlWindow win2 = new MockCrawlWindow();
    win2.setAllowCrawl(false);

    Set s = SetUtil.set(win1, win2);
    CrawlWindow andWin = new CrawlWindows.And(s);
    assertFalse(andWin.canCrawl(testCal.getTime()));
    win1.setAllowCrawl(true);
    assertFalse(andWin.canCrawl(testCal.getTime()));
    win1.setAllowCrawl(false);
    win2.setAllowCrawl(true);
    assertFalse(andWin.canCrawl(testCal.getTime()));
    win1.setAllowCrawl(true);
    assertTrue(andWin.canCrawl(testCal.getTime()));
  }

  public void testORSet() {
    MockCrawlWindow win1 = new MockCrawlWindow();
    win1.setAllowCrawl(false);
    MockCrawlWindow win2 = new MockCrawlWindow();
    win2.setAllowCrawl(false);

    Set s = SetUtil.set(win1, win2);
    CrawlWindow orWin = new CrawlWindows.Or(s);
    assertFalse(orWin.canCrawl(testCal.getTime()));
    win1.setAllowCrawl(true);
    assertTrue(orWin.canCrawl(testCal.getTime()));
    win1.setAllowCrawl(false);
    win2.setAllowCrawl(true);
    assertTrue(orWin.canCrawl(testCal.getTime()));
    win1.setAllowCrawl(true);
    assertTrue(orWin.canCrawl(testCal.getTime()));
  }

  public void testNOTWindow() {
    MockCrawlWindow win1 = new MockCrawlWindow();
    win1.setAllowCrawl(false);

    CrawlWindow notWin = new CrawlWindows.Not(win1);
    assertTrue(notWin.canCrawl(testCal.getTime()));
    win1.setAllowCrawl(true);
    assertFalse(notWin.canCrawl(testCal.getTime()));
  }

  public void testIntervalFieldStandard() {
    // Tue->Fri
    start.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
    end.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);

    CrawlWindows.Interval interval = new CrawlWindows.Interval(start, end,
        CrawlWindows.DAY_OF_WEEK, null);

    testCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
    assertFalse(interval.isMatch(testCal));
  }

  public void testIntervalFieldWrapAround() {
    // invert interval
    // Fri->Tue
    start.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
    end.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
    CrawlWindows.Interval interval = new CrawlWindows.Interval(start, end,
        CrawlWindows.DAY_OF_WEEK, null);

    testCal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY);
    assertFalse(interval.isMatch(testCal));
  }

  public void testIntervalTimeStandard() {
    // interval from 7:30->15:45
    start.set(Calendar.HOUR_OF_DAY, 7);
    start.set(Calendar.MINUTE, 30);
    end.set(Calendar.HOUR_OF_DAY, 15);
    end.set(Calendar.MINUTE, 45);
    CrawlWindows.Interval interval = new CrawlWindows.Interval(start, end,
        CrawlWindows.TIME, null);

    testCal.set(Calendar.HOUR_OF_DAY, 6);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.HOUR_OF_DAY, 7);
    testCal.set(Calendar.MINUTE, 25);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.MINUTE, 30);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.MINUTE, 47);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.HOUR_OF_DAY, 12);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.HOUR_OF_DAY, 15);
    testCal.set(Calendar.MINUTE, 5);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.MINUTE, 44);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.MINUTE, 45);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.HOUR_OF_DAY, 17);
    testCal.set(Calendar.MINUTE, 0);
    assertFalse(interval.isMatch(testCal));
  }

  public void testIntervalTimeWrapAround() {
    // interval from 15:30->7:45
    start.set(Calendar.HOUR_OF_DAY, 15);
    start.set(Calendar.MINUTE, 30);
    end.set(Calendar.HOUR_OF_DAY, 7);
    end.set(Calendar.MINUTE, 45);

    CrawlWindows.Interval interval = new CrawlWindows.Interval(start, end,
        CrawlWindows.TIME, null);

    testCal.set(Calendar.HOUR_OF_DAY, 14);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.HOUR_OF_DAY, 15);
    testCal.set(Calendar.MINUTE, 25);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.MINUTE, 30);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.MINUTE, 47);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.HOUR_OF_DAY, 17);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.HOUR_OF_DAY, 1);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.HOUR_OF_DAY, 7);
    testCal.set(Calendar.MINUTE, 5);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.MINUTE, 44);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.MINUTE, 45);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.HOUR_OF_DAY, 8);
    testCal.set(Calendar.MINUTE, 0);
    assertFalse(interval.isMatch(testCal));
  }

  public void testMultipleFields() {
    // Tue->Fri, 1st week of month
    start.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
    end.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
    start.set(Calendar.WEEK_OF_MONTH, 1);
    end.set(Calendar.WEEK_OF_MONTH, 1);

    CrawlWindows.Interval interval = new CrawlWindows.Interval(start, end,
        CrawlWindows.WEEK_OF_MONTH + CrawlWindows.DAY_OF_WEEK,
        null);

    testCal.set(Calendar.WEEK_OF_MONTH, 1);
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
    assertTrue(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
    assertFalse(interval.isMatch(testCal));

    testCal.set(Calendar.WEEK_OF_MONTH, 2);
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
    assertFalse(interval.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
    assertFalse(interval.isMatch(testCal));
  }

  public void testANDIntervals() {
    // interval from 7->15
    start.set(Calendar.HOUR_OF_DAY, 7);
    end.set(Calendar.HOUR_OF_DAY, 15);
    end.set(Calendar.MINUTE, 0);
    CrawlWindows.Interval interval = new CrawlWindows.Interval(start, end,
        CrawlWindows.TIME, null);

    // interval from 7:30->15:45
    Calendar start2 = Calendar.getInstance();
    start2.set(Calendar.DAY_OF_MONTH, 1);
    Calendar end2 = Calendar.getInstance();
    end2.set(Calendar.DAY_OF_MONTH, 5);
    CrawlWindows.Interval interval2 = new CrawlWindows.Interval(start2, end2,
        CrawlWindows.DAY_OF_MONTH, null);

    Set intSet = SetUtil.set(interval, interval2);
    CrawlWindows.And andPair = new CrawlWindows.And(intSet);
    CrawlWindows.Or orPair = new CrawlWindows.Or(intSet);

    // both true
    testCal.set(Calendar.HOUR_OF_DAY, 8);
    testCal.set(Calendar.DAY_OF_MONTH, 1);
    assertTrue(andPair.canCrawl(testCal.getTime()));
    assertTrue(orPair.canCrawl(testCal.getTime()));

    // one false
    testCal.set(Calendar.HOUR_OF_DAY, 6);
    assertFalse(andPair.canCrawl(testCal.getTime()));
    assertTrue(orPair.canCrawl(testCal.getTime()));
    testCal.set(Calendar.HOUR_OF_DAY, 8);
    testCal.set(Calendar.DAY_OF_MONTH, 16);
    assertFalse(andPair.canCrawl(testCal.getTime()));
    assertTrue(orPair.canCrawl(testCal.getTime()));

    // both false
    testCal.set(Calendar.HOUR_OF_DAY, 6);
    testCal.set(Calendar.DAY_OF_MONTH, 16);
    assertFalse(andPair.canCrawl(testCal.getTime()));
    assertFalse(orPair.canCrawl(testCal.getTime()));
  }

  public void testFieldSet() {
    // M,W,F enum
    Calendar cal1 = Calendar.getInstance();
    Calendar cal2 = Calendar.getInstance();
    Calendar cal3 = Calendar.getInstance();
    cal1.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
    cal2.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
    cal3.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);

    CrawlWindows.FieldSet fieldSet =
        new CrawlWindows.FieldSet(SetUtil.set(cal1, cal2, cal3),
                                   CrawlWindows.DAY_OF_WEEK, null);
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
    assertTrue(fieldSet.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
    assertFalse(fieldSet.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
    assertTrue(fieldSet.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY);
    assertFalse(fieldSet.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
    assertTrue(fieldSet.isMatch(testCal));
  }

  public void testMultipleFieldSet() {
    // M-1st,W-2nd enum
    Calendar cal1 = Calendar.getInstance();
    Calendar cal2 = Calendar.getInstance();
    cal1.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
    cal1.set(Calendar.WEEK_OF_MONTH, 1);
    cal2.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
    cal2.set(Calendar.WEEK_OF_MONTH, 2);

    CrawlWindows.FieldSet fieldSet =
        new CrawlWindows.FieldSet(SetUtil.set(cal1, cal2),
                                   CrawlWindows.DAY_OF_WEEK +
                                   CrawlWindows.WEEK_OF_MONTH, null);

    testCal.set(Calendar.WEEK_OF_MONTH, 1);
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
    assertTrue(fieldSet.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
    assertFalse(fieldSet.isMatch(testCal));

    testCal.set(Calendar.WEEK_OF_MONTH, 2);
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
    assertFalse(fieldSet.isMatch(testCal));
    testCal.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
    assertTrue(fieldSet.isMatch(testCal));
  }

  public void testGetCrawlIntervals() {
    start.set(Calendar.HOUR_OF_DAY, 13);
    start.set(Calendar.MINUTE, 55);
    // make sure the dates are constant between all the calendars
    end.setTime(start.getTime());
    end.set(Calendar.HOUR_OF_DAY, 14);
    end.set(Calendar.MINUTE, 0);

    CrawlWindow interval = new CrawlWindows.Interval(start, end,
        CrawlWindows.TIME, null);
    Calendar start2 = Calendar.getInstance();
    Calendar end2 = Calendar.getInstance();
    start2.setTime(start.getTime());
    start2.set(Calendar.HOUR_OF_DAY, 14);
    start2.set(Calendar.MINUTE, 5);
    end2.setTime(start.getTime());
    end2.set(Calendar.HOUR_OF_DAY, 14);
    end2.set(Calendar.MINUTE, 15);
    CrawlWindow interval2 = new CrawlWindows.Interval(start2, end2,
        CrawlWindows.TIME, null);

    CrawlWindow orWin = new CrawlWindows.Or(SetUtil.set(interval, interval2));

    Calendar start3 = Calendar.getInstance();
    Calendar end3 = Calendar.getInstance();
    start3.setTime(start.getTime());
    start3.set(Calendar.HOUR_OF_DAY, 13);
    start3.set(Calendar.MINUTE, 50);
    // make sure the dates are constant between start and end
    end3.setTime(start.getTime());
    end3.set(Calendar.HOUR_OF_DAY, 14);
    end3.set(Calendar.MINUTE, 10);

    List expectedList = ListUtil.list(
        new TimeInterval(start.getTime(), end.getTime()),
        // last interval ends early
        new TimeInterval(start2.getTime(), end3.getTime()));
    List results = CrawlWindows.getCrawlIntervals(orWin,
                                                  start3.getTime(),
                                                  end3.getTime());
    assertIsomorphic(expectedList, results);
    long numMinutes = TimeInterval.getTotalTime(results) / (Constants.MINUTE);
    assertEquals(10, numMinutes);
  }

  public void testDaily() {
    CrawlWindows.Daily win;

    // Open from 2:00am to 7:00am GMT
    win = new CrawlWindows.Daily("2:00", "7:00", "GMT");
    assertEquals("Daily from 2:00 to 7:00, GMT", win.toString());
    assertFalse(win.canCrawl(new Date("1/1/1 0:0 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/1 1:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/2 1:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/6 1:59 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 2:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 6:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 6:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/1 7:00 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/1 23:59 GMT")));

    // Open from 7:00am to 10:00pm, timezone GMT-0700 = 14:00 - 05:00 GMT
    win = new CrawlWindows.Daily("7:00", "22:00", "GMT-0700");
    assertEquals("Daily from 7:00 to 22:00, GMT-0700", win.toString());
    assertFalse(win.canCrawl(new Date("1/1/1 13:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/2 13:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/4 13:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/7 13:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/1 6:59 GMT-0700")));
    assertTrue(win.canCrawl(new Date("1/1/1 14:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 14:01 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 15:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 23:59 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 00:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 01:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 04:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/1 05:01 GMT")));

    // Open from 10:00pm to 6:00am GMT
    win = new CrawlWindows.Daily("22:00", "6:00", "GMT");
    assertEquals("Daily from 22:00 to 6:00, GMT", win.toString());
    assertFalse(win.canCrawl(new Date("1/1/1 21:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/2 21:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/4 21:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/7 21:59 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 22:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 22:01 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 23:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 23:59 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 00:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 01:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/1 05:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/1 06:01 GMT")));
  }

  static final String SUNDAY = "March 3, 2013";
  static final String MONDAY = "March 4, 2013";
  static final String TUESDAY = "March 5, 2013";
  static final String WEDNESDAY = "March 6, 2013";
  static final String THURSDAY = "March 7, 2013";
  static final String FRIDAY = "March 8, 2013";
  static final String SATURDAY = "March 9, 2013";


  public void testDailyWithDays() {
    CrawlWindows.Daily win;
    win = new CrawlWindows.Daily("7:00", "22:00", null, "GMT");
    assertEquals("Daily from 7:00 to 22:00, GMT", win.toString());

    try {
      new CrawlWindows.Daily("7:00", "22:00", "", "GMT");
    } catch (IllegalArgumentException e) {
    }

    try {
      new CrawlWindows.Daily("7:00", "22:00", "not;days", "GMT");
    } catch (IllegalArgumentException e) {
    }

    // weekdays
    win = new CrawlWindows.Daily("7:00", "22:00", "2;3;4;5;6", "GMT");
    assertEquals("Days 2;3;4;5;6 from 7:00 to 22:00, GMT", win.toString());
    assertFalse(win.canCrawl(new Date(SUNDAY + " 7:00 GMT")));
    assertTrue(win.canCrawl(new Date(MONDAY + " 7:00 GMT")));
    assertTrue(win.canCrawl(new Date(TUESDAY + " 7:00 GMT")));
    assertTrue(win.canCrawl(new Date(WEDNESDAY + " 7:00 GMT")));
    assertTrue(win.canCrawl(new Date(THURSDAY + " 7:00 GMT")));
    assertTrue(win.canCrawl(new Date(FRIDAY + " 7:00 GMT")));
    assertFalse(win.canCrawl(new Date(SATURDAY + " 7:00 GMT")));

    // weekends
    win = new CrawlWindows.Daily("7:00", "22:00", "1;7;1", "GMT");
    assertEquals("Days 1;7 from 7:00 to 22:00, GMT", win.toString());

    assertTrue(win.canCrawl(new Date(SUNDAY + " 7:00 GMT")));
    assertFalse(win.canCrawl(new Date(MONDAY + " 7:00 GMT")));
    assertFalse(win.canCrawl(new Date(TUESDAY + " 7:00 GMT")));
    assertFalse(win.canCrawl(new Date(WEDNESDAY + " 7:00 GMT")));
    assertFalse(win.canCrawl(new Date(THURSDAY + " 7:00 GMT")));
    assertFalse(win.canCrawl(new Date(FRIDAY + " 7:00 GMT")));
    assertTrue(win.canCrawl(new Date(SATURDAY + " 7:00 GMT")));
  }

  static String daily1 =
    "<org.lockss.daemon.CrawlWindows-Daily>\n" +
    "<from>8:00</from>\n" +
    "<to>22:00</to>\n" +
    "<timeZoneId>GMT-0700</timeZoneId>\n" +
    "</org.lockss.daemon.CrawlWindows-Daily>\n";

  static String daily2 =
    "<org.lockss.daemon.CrawlWindows-Daily>\n" +
    "<from>20:00</from>\n" +
    "<to>6:00</to>\n" +
    "<timeZoneId>GMT</timeZoneId>\n" +
    "<daysOfWeek>2;3;4;5;6</daysOfWeek>\n" +
    "</org.lockss.daemon.CrawlWindows-Daily>\n";

  CrawlWindow deserWindow(String input) throws Exception {
    ObjectSerializer deser = new XStreamSerializer(getMockLockssDaemon());
    return (CrawlWindow)deser.deserialize(new StringReader(input));
  }

  public void testDeserDaily() throws Exception {
    CrawlWindows.Daily win;
    
    win = (CrawlWindows.Daily)deserWindow(daily1);
    assertEquals("Daily from 8:00 to 22:00, GMT-0700", win.toString());
    assertFalse(win.canCrawl(new Date("1/1/1 7:59 GMT-0700")));
    assertTrue(win.canCrawl(new Date("1/1/1 8:00 GMT-0700")));
    assertTrue(win.canCrawl(new Date("1/1/1 21:59 GMT-0700")));
    assertFalse(win.canCrawl(new Date("1/1/1 22:01 GMT-0700")));

    assertFalse(win.canCrawl(new Date("1/1/2 14:59 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/2 15:00 GMT")));
    assertTrue(win.canCrawl(new Date("1/1/2 4:59 GMT")));
    assertFalse(win.canCrawl(new Date("1/1/2 5:01 GMT")));

    win = (CrawlWindows.Daily)deserWindow(daily2);
    assertEquals("Days 2;3;4;5;6 from 20:00 to 6:00, GMT", win.toString());

    assertFalse(win.canCrawl(new Date(SUNDAY + " 5:00 GMT")));
    assertTrue(win.canCrawl(new Date(MONDAY + " 5:00 GMT")));
    assertFalse(win.canCrawl(new Date(MONDAY + " 7:00 GMT")));
    assertTrue(win.canCrawl(new Date(TUESDAY + " 5:00 GMT")));
    assertTrue(win.canCrawl(new Date(WEDNESDAY + " 5:00 GMT")));
    assertTrue(win.canCrawl(new Date(THURSDAY + " 5:00 GMT")));
    assertTrue(win.canCrawl(new Date(FRIDAY + " 5:00 GMT")));
    assertFalse(win.canCrawl(new Date(SATURDAY + " 5:00 GMT")));
  }

  static String and1 =
    "<org.lockss.daemon.CrawlWindows-And>\n" +
    "<windows>\n" +
    daily1 +
    daily2 +
    "</windows>\n" +
    "</org.lockss.daemon.CrawlWindows-And>\n";

  public void testDeserAnd() throws Exception {
    CrawlWindows.And win;
    
    win = (CrawlWindows.And)deserWindow(and1);
    assertEquals(2, win.windows.size());
    for (Object x : win.windows) {
      assertClass(CrawlWindows.Daily.class, x);
    }
  }

  static String or1 =
    "<org.lockss.daemon.CrawlWindows-Or>\n" +
    "<windows>\n" +
    daily2 +
    daily1 +
    "</windows>\n" +
    "</org.lockss.daemon.CrawlWindows-Or>\n";

  public void testDeserOr() throws Exception {
    CrawlWindows.Or win;
    
    win = (CrawlWindows.Or)deserWindow(or1);
    assertEquals(2, win.windows.size());
    for (Object x : win.windows) {
      assertClass(CrawlWindows.Daily.class, x);
    }
  }

  static String not1 =
    "<org.lockss.daemon.CrawlWindows-Not>\n" +
    "<window class=\"org.lockss.daemon.CrawlWindows-Daily\">\n" +
    "  <from>8:00</from>\n" +
    "  <to>22:00</to>\n" +
    "  <timeZoneId>GMT-0600</timeZoneId>\n" +
    "</window>\n" +
    "</org.lockss.daemon.CrawlWindows-Not>\n";

  public void testDeserNot() throws Exception {
    CrawlWindows.Not win;
    
    win = (CrawlWindows.Not)deserWindow(not1);
    assertClass(CrawlWindows.Daily.class, win.window);
  }


  public void testAlways() {
    CrawlWindow win = new CrawlWindows.Always();
    assertTrue(win.canCrawl());
    assertTrue(win.canCrawl(new Date(0)));
    assertTrue(win.canCrawl(new Date(Constants.HOUR)));
    assertTrue(win.canCrawl(new Date(2 * Constants.HOUR)));
    assertTrue(win.canCrawl(new Date(12 * Constants.HOUR)));
    assertTrue(win.canCrawl(new Date(23 * Constants.HOUR)));
    assertTrue(win.canCrawl(new Date(Constants.DAY + 2 * Constants.HOUR)));
    assertTrue(win.canCrawl(new Date(Constants.YEAR + 2 * Constants.HOUR)));
    assertTrue(win.canCrawl(new Date(100 * Constants.YEAR
				     + 2 * Constants.HOUR)));
  }

  public void testNever() {
    CrawlWindow win = new CrawlWindows.Never();
    assertFalse(win.canCrawl());
    assertFalse(win.canCrawl(new Date(0)));
    assertFalse(win.canCrawl(new Date(Constants.HOUR)));
    assertFalse(win.canCrawl(new Date(2 * Constants.HOUR)));
    assertFalse(win.canCrawl(new Date(12 * Constants.HOUR)));
    assertFalse(win.canCrawl(new Date(23 * Constants.HOUR)));
    assertFalse(win.canCrawl(new Date(Constants.DAY + 2 * Constants.HOUR)));
    assertFalse(win.canCrawl(new Date(Constants.YEAR + 2 * Constants.HOUR)));
    assertFalse(win.canCrawl(new Date(100 * Constants.YEAR
				      + 2 * Constants.HOUR)));
  }

  void assertEqualWin(CrawlWindow w1, CrawlWindow w2) {
    assertTrue(w1.equals(w2));
    assertTrue(w2.equals(w1));
    assertEquals(w1.hashCode(), w2.hashCode());
  }

  void assertNotEqualWin(CrawlWindow w1, CrawlWindow w2) {
    assertFalse(w1.equals(w2));
    assertFalse(w2.equals(w1));
    // might fail if hashCode() or HashCodeBuilder changes
    assertNotEquals(w1.hashCode(), w2.hashCode());
  }

  public void testEquals() {
    CrawlWindow wnever = new CrawlWindows.Never();
    CrawlWindow walways = new CrawlWindows.Always();
    assertEqualWin(wnever, wnever);
    assertNotEqualWin(wnever, walways);
    assertEqualWin(walways, walways);
    assertEqualWin(walways, new CrawlWindows.Always());

    CrawlWindow wdaily = new CrawlWindows.Daily("2:00", "7:00", "GMT");
    assertEqualWin(wdaily, wdaily);
    assertEqualWin(wdaily, new CrawlWindows.Daily("2:00", "7:00", "GMT"));
    assertNotEqualWin(wdaily, new CrawlWindows.Daily("2:01", "7:00", "GMT"));
    assertNotEqualWin(wdaily, new CrawlWindows.Daily("2:00", "7:01", "GMT"));
    assertNotEqualWin(wdaily, new CrawlWindows.Daily("2:00", "7:00", "PST"));

    assertNotEqualWin(wdaily, wnever);
    assertNotEqualWin(wdaily, walways);

    CrawlWindow wwkends = new CrawlWindows.Daily("2:00", "7:00", "1;7", "GMT");
    assertEqualWin(wwkends, wwkends);
    assertEqualWin(wwkends, new CrawlWindows.Daily("2:00", "7:00", "7;1",
						   "GMT"));
    assertNotEqualWin(wwkends, new CrawlWindows.Daily("2:00", "7:00", "1;2;7",
						      "GMT"));

    start.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
    end.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
    CrawlWindow winterval =
      new CrawlWindows.Interval(start, end, CrawlWindows.DAY_OF_WEEK, GMT);
    assertEqualWin(winterval, winterval);
    assertEqualWin(winterval,
		   new CrawlWindows.Interval(start, end,
					     CrawlWindows.DAY_OF_WEEK, GMT));
    assertNotEqualWin(winterval,
		      new CrawlWindows.Interval(start, start,
						CrawlWindows.DAY_OF_WEEK, GMT));
    assertNotEqualWin(winterval,
		      new CrawlWindows.Interval(end, end,
						CrawlWindows.DAY_OF_WEEK, GMT));
    assertNotEqualWin(winterval,
		      new CrawlWindows.Interval(start, end,
						CrawlWindows.DAY_OF_MONTH, GMT));
    assertNotEqualWin(winterval,
		      new CrawlWindows.Interval(start, end,
						CrawlWindows.DAY_OF_WEEK,
						TimeZone.getTimeZone("PST")));
  }
}

