/*
 * $Id: TestTaskRunner.java,v 1.1.2.1 2003-11-17 22:50:43 tlipkis Exp $
 */

/*
n
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

package org.lockss.scheduler;

import java.io.*;
import java.util.*;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.test.*;


/**
 * Test class for <code>org.lockss.scheduler.TaskRunner</code>
 */

public class TestTaskRunner extends LockssTestCase {
  public static Class testedClasses[] = {
    org.lockss.scheduler.TaskRunner.class
  };

  static Logger log = Logger.getLogger("TestTaskRunner");
  private TaskRunner tr;
  private SchedFact fact;
  private List removedChunks;
  private List removedTasks;

  public void setUp() throws Exception {
    super.setUp();
    TimeBase.setSimulated();
    removedChunks = new ArrayList();
    removedTasks = new ArrayList();
    fact = new SchedFact(null);
    tr = new MockTaskRunner(fact);
  }

  public void tearDown() throws Exception {
    TimeBase.setReal();
    super.tearDown();
  }

  StepTask task(long start, long end, long duration) {
    return new StepperTask(Deadline.at(start), Deadline.at(end),
			   duration, null, null, new MockStepper());
  }

  StepTask task(long start, long end, long duration, TaskCallback cb) {
    return new StepperTask(Deadline.at(start), Deadline.at(end),
			   duration, cb, null, new MockStepper());
  }

  StepTask task(long start, long end, long duration,
		TaskCallback cb, Stepper stepper) {
    return new StepperTask(Deadline.at(start), Deadline.at(end),
			   duration, cb, null, stepper);
  }

  BackgroundTask btask(long start, long end, double loadFactor,
		      TaskCallback cb) {
    return new BackgroundTask(Deadline.at(start), Deadline.at(end),
			   loadFactor, cb);
  }

  // make a Chunk for the task
  Schedule.Chunk chunk(StepTask task) {
    return new Schedule.Chunk(task, task.getEarlistStart(),
			      task.getLatestFinish(),
			      task.curEst());
  }

  // make a BackgroundEvent for the task
  Schedule.BackgroundEvent bEvent(BackgroundTask task,
				  Schedule.EventType event) {
    return new Schedule.BackgroundEvent(task,
					(event == Schedule.EventType.START
					 ? task.getStart() : task.getFinish()),
					event);
  }

  // make a Schedule with one chunk per task
  Schedule sched(List tasks) {
    List events = new ArrayList();
    for (Iterator iter = tasks.iterator(); iter.hasNext(); ) {
      Object obj = iter.next();
      if (obj instanceof Schedule.Event) {
	events.add(obj);
      } else {
	SchedulableTask task = (SchedulableTask)obj;
	if (task.isBackgroundTask()) {
	  events.add(bEvent((BackgroundTask)task, Schedule.EventType.START));
	} else {
	  events.add(chunk((StepTask)task));
	}
      }
    }
    Schedule s = new Schedule(events);
    return s;
  }

  // ensure addToSchedule returns false if (Mock)Scheduler returns false
  public void testAddToScheduleFail() {
    fact.setResult(null);
    StepTask t1 = task(100, 200, 50);
    assertFalse(tr.addToSchedule(t1));
    assertNull(tr.getAcceptedTasks());
  }

  // ensure addToSchedule updates structures if (Mock)Scheduler returns true
  public void testAddToScheduleOk() {
    StepTask t1 = task(100, 200, 50);
    StepTask t2 = task(100, 200, 100);
    Schedule sched = sched(ListUtil.list(t1));
    fact.setResult(sched);
    assertTrue(tr.addToSchedule(t1));
    assertIsomorphic(ListUtil.list(t1), fact.tasks);
    assertTrue(tr.addToSchedule(t2));
    assertEquals(SetUtil.set(t1, t2), SetUtil.theSet(fact.tasks));
    assertEquals(sched, tr.getCurrentSchedule());
    assertEquals(SetUtil.set(t1, t2), SetUtil.theSet(tr.getAcceptedTasks()));
  }

  public void testIsTaskSchedulable() {
    fact.setResult(null);
    StepTask t1 = task(100, 200, 50);
    assertFalse(tr.isTaskSchedulable(t1));
    fact.setResult(sched(ListUtil.list(t1)));
    assertTrue(tr.isTaskSchedulable(t1));
  }

  public void testFindChunkTaskToRun() {
    assertFalse(tr.findTaskToRun());
    StepTask t1 = task(100, 200, 100);
    StepTask t2 = task(100, 300, 50);

    Schedule s = sched(ListUtil.list(t1, t2));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));
    assertTrue(tr.addToSchedule(t2));
    assertFalse(tr.findTaskToRun());
    assertEquals(Deadline.at(100), tr.runningDeadline);

    TimeBase.setSimulated(101);
    assertTrue(tr.findTaskToRun());
    assertEquals(t1, tr.runningTask);
    assertEquals(t1.getLatestFinish(), tr.runningDeadline);
    assertEquals(s.getEvents().get(0), tr.runningChunk);
  }

  public void testFindOverrunTaskToRun() {
    assertFalse(tr.findTaskToRun());
    StepTask t1 = task(100, 200, 100);
    Schedule s = sched(ListUtil.list(t1));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));
    assertFalse(tr.findTaskToRun());
    assertEquals(Deadline.at(100), tr.runningDeadline);

    StepTask t2 = task(0, 300, 50);
    tr.addOverrunner(t2);
    assertTrue(tr.findTaskToRun());
    assertEquals(t2, tr.runningTask);
    assertEquals(Deadline.at(100), tr.runningDeadline);
    assertNull(tr.runningChunk);
  }

  public void testFindTaskToRunRemovesExpiredChunks() {
    assertFalse(tr.findTaskToRun());
    StepTask t1 = task(100, 200, 100);
    StepTask t2 = task(100, 300, 50);
    StepTask texp1 = task(0, 0, 50);
    StepTask texp2 = task(0, 0, 50);

    Schedule s = sched(ListUtil.list(texp1, texp2, t1, t2));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));
    assertTrue(tr.addToSchedule(t2));
    assertFalse(tr.findTaskToRun());
    assertEquals(2, removedChunks.size());
    assertEquals(SetUtil.set(texp1, texp2),
		 SetUtil.set(((Schedule.Chunk)removedChunks.get(0)).getTask(),
			     ((Schedule.Chunk)removedChunks.get(1)).getTask()));
  }

  public void testFindTaskToRunRemovesExpiredOverrunners() {
    assertFalse(tr.findTaskToRun());
    StepTask t1 = task(100, 200, 100);
    StepTask t2 = task(100, 300, 50);
    StepTask texp1 = task(0, 0, 50);
    StepTask texp2 = task(0, 0, 49);

    Schedule s = sched(ListUtil.list(t1, t2));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));
    assertTrue(tr.addToSchedule(t2));
    tr.addOverrunner(texp1);
    tr.addOverrunner(texp2);

    // if this fails, it might be because the sorted list/set is treating
    // sort-order equivalence as object equality, which we don't want
    assertEquals(2, tr.getOverrunTasks().size());
    assertFalse(tr.findTaskToRun());
    assertEquals(0, removedChunks.size());
    assertEquals(2, removedTasks.size());
    assertEquals(SetUtil.set(texp1, texp2),
		 SetUtil.set((StepTask)removedTasks.get(0),
			     (StepTask)removedTasks.get(1)));
  }

  public void testRemoveChunk() {
    StepTask t1 = task(100, 200, 100);
    Schedule s = sched(ListUtil.list(t1));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));
    assertIsomorphic(ListUtil.list(t1), tr.getAcceptedTasks());

    Schedule.Chunk chunk = (Schedule.Chunk)s.getEvents().get(0);
    assertTrue(tr.getCurrentSchedule().getEvents().contains(chunk));
    tr.removeChunk(chunk);
    assertFalse(tr.getCurrentSchedule().getEvents().contains(chunk));
  }

  // This should generate an impossible state log, and leave the task in
  // acceptedTasks
  public void testRemoveChunkTaskEnd() {
    final List finished = new ArrayList();
    StepTask t1 = task(100, 200, 100, new TaskCallback() {
	public void taskEvent(SchedulableTask task, Schedule.EventType event) {
	  if (log.isDebug2()) {
	    log.debug2("testRemoveChunkTaskEnd event " + event);
	  }
	  if (event == Schedule.EventType.FINISH) {
	    finished.add(task);
	  }
	}});
    Schedule s = sched(ListUtil.list(t1));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));

    Schedule.Chunk chunk = (Schedule.Chunk)s.getEvents().get(0);
    assertTrue(tr.getCurrentSchedule().getEvents().contains(chunk));
    chunk.setTaskEnd();
    tr.removeChunk(chunk);
    assertFalse(tr.getCurrentSchedule().getEvents().contains(chunk));
    assertEmpty(tr.getAcceptedTasks());
    assertIsomorphic(ListUtil.list(t1), finished);
  }

  // remove task-ending chunk, past task deadline, s.b. Timeout error.
  public void testRemoveChunkTaskEndTimeout() {
    final List finished = new ArrayList();
    StepTask t1 = task(100, 200, 100, new TaskCallback() {
	public void taskEvent(SchedulableTask task, Schedule.EventType event) {
	  if (log.isDebug2()) {
	    log.debug2("testRemoveChunkTaskEndTimeout callback");
	  }
	  if (event == Schedule.EventType.FINISH) {
	    finished.add(task);
	  }
	}});
    Schedule s = sched(ListUtil.list(t1));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));

    Schedule.Chunk chunk = (Schedule.Chunk)s.getEvents().get(0);
    assertTrue(tr.getCurrentSchedule().getEvents().contains(chunk));
    chunk.setTaskEnd();
    TimeBase.setSimulated(201);
    tr.removeChunk(chunk);
    assertFalse(tr.getCurrentSchedule().getEvents().contains(chunk));
    assertSame(t1, finished.get(0));
    assertNotNull(t1.e);
    assertTrue(t1.e.toString(), t1.e instanceof SchedService.Timeout);
    assertEmpty(tr.getAcceptedTasks());
  }

  // remove overrunnable task-ending chunk, before deadline, 
  public void testRemoveChunkTaskEndOver() {
    final List finished = new ArrayList();
    StepTask t1 = task(100, 200, 100, new TaskCallback() {
	public void taskEvent(SchedulableTask task, Schedule.EventType event) {
	  if (log.isDebug2()) {
	    log.debug2("testRemoveChunkTaskEndOver callback");
	  }
	  if (event == Schedule.EventType.FINISH) {
	    finished.add(task);
	  }
	}});
    t1.setOverrunAllowed(true);
    Schedule s = sched(ListUtil.list(t1));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));

    Schedule.Chunk chunk = (Schedule.Chunk)s.getEvents().get(0);
    assertTrue(tr.getCurrentSchedule().getEvents().contains(chunk));
    chunk.setTaskEnd();
    tr.removeChunk(chunk);
    assertFalse(tr.getCurrentSchedule().getEvents().contains(chunk));
    assertEmpty(finished);
    assertIsomorphic(ListUtil.list(t1), tr.getAcceptedTasks());
    assertIsomorphic(SetUtil.set(t1), tr.getOverrunTasks());
  }


  // Background event record
  class BERec {
    Deadline when;
    BackgroundTask task;
    Schedule.EventType event;
    BERec(Deadline when, BackgroundTask task, Schedule.EventType event) {
      this.when = when;
      this.task = task;
      this.event = event;
    }
    BERec(long when, BackgroundTask task, Schedule.EventType event) {
      this.when = Deadline.at(when);
      this.task = task;
      this.event = event;
    }
    public boolean equals(Object obj) {
      if (obj instanceof BERec) {
	BERec o = (BERec)obj;
	return when.equals(o.when) &&
	  task.equals(o.task) &&
	  event == o.event;
      }
      return false;
    }
    public String toString() {
      return "[BERec: " + event + ", " + when + ", " + task + "]";
    }
  }


  public void testBackground() {
    final List rec = new ArrayList();
    TaskCallback cb = new TaskCallback() {
	public void taskEvent(SchedulableTask task, Schedule.EventType event) {
	  rec.add(new BERec(Deadline.in(0), (BackgroundTask)task, event));
	}};
    assertFalse(tr.findTaskToRun());
    BackgroundTask t1 = btask(100, 200, .1, cb);
    BackgroundTask t2 = btask(100, 300, .2, cb);
    BackgroundTask t3 = btask(150, 200, .4, cb);

    Schedule s = sched(ListUtil.list(bEvent(t1, Schedule.EventType.START),
				     bEvent(t2, Schedule.EventType.START),
				     bEvent(t3, Schedule.EventType.START),
				     bEvent(t1, Schedule.EventType.FINISH),
				     bEvent(t3, Schedule.EventType.FINISH),
				     bEvent(t2, Schedule.EventType.FINISH)));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));
    assertTrue(tr.addToSchedule(t2));
    assertTrue(tr.addToSchedule(t3));
    assertEquals(3, tr.getAcceptedTasks().size());
    assertIsomorphic(ListUtil.list(t1, t2, t3), tr.getAcceptedTasks());
    assertFalse(tr.findTaskToRun());
    assertEquals(0, rec.size());
    assertEquals(0, tr.getBackgroundLoadFactor(), .005);
    assertEquals(Deadline.at(100), tr.runningDeadline);

    TimeBase.setSimulated(101);
    assertFalse(tr.findTaskToRun());
    assertEquals(2, rec.size());
    assertEquals(.3, tr.getBackgroundLoadFactor(), .005);
    TimeBase.setSimulated(151);
    assertFalse(tr.findTaskToRun());
    assertEquals(3, rec.size());
    assertEquals(.7, tr.getBackgroundLoadFactor(), .005);
    assertEquals(3, tr.getAcceptedTasks().size());
    TimeBase.setSimulated(201);
    assertFalse(tr.findTaskToRun());
    assertEquals(5, rec.size());
    assertEquals(.2, tr.getBackgroundLoadFactor(), .005);
    assertEquals(1, tr.getAcceptedTasks().size());
    t2.taskIsFinished();
    TimeBase.setSimulated(202);
    assertFalse(tr.findTaskToRun());
    assertEquals(6, rec.size());
    assertEquals(0, tr.getBackgroundLoadFactor(), .005);
    assertEquals(0, tr.getAcceptedTasks().size());
    TimeBase.setSimulated(301);
    assertFalse(tr.findTaskToRun());
    assertEquals(6, rec.size());
    assertEquals(0, tr.getBackgroundLoadFactor(), .005);
    List exp = ListUtil.list(new BERec(101, t1, Schedule.EventType.START),
			     new BERec(101, t2, Schedule.EventType.START),
			     new BERec(151, t3, Schedule.EventType.START),
			     new BERec(201, t1, Schedule.EventType.FINISH),
			     new BERec(201, t3, Schedule.EventType.FINISH),
			     new BERec(202, t2, Schedule.EventType.FINISH));
    assertEquals(exp, rec);
  }


  public void testRunStepsOneTaskAndCallback() {
    final List finished = new ArrayList();
    TaskCallback cb = new TaskCallback() {
	public void taskEvent(SchedulableTask task, Schedule.EventType event) {
	  if (event == Schedule.EventType.FINISH) {
	    finished.add(task);
	  }
	}};

    StepTask t1 = task(100, 200, 100, cb, new MockStepper(10, -10));
    Schedule s = sched(ListUtil.list(t1));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));
    TimeBase.setSimulated(101);
    assertTrue(tr.findTaskToRun());
    Interrupter intr = null;
    try {
      intr = interruptMeIn(TIMEOUT_SHOULDNT, true);
      tr.runSteps(Boolean.TRUE);
      intr.cancel();
    } catch (Exception e) {
      log.error("runSteps threw:", e);
    } finally {
      if (intr.did()) {
	fail("runSteps looped");
      }
    }
    assertSame(t1, finished.get(0));
    assertNull(t1.e);
  }

  public void testRunStepsWithOverrunDisallowed() {
    StepTask t1 = task(100, 300, 100, null, new MockStepper(15, -10));
    //    t1.setOverrunAllowed(true);
    StepTask t2 = task(150, 250, 100, null, new MockStepper(10, -10));
    Schedule s = sched(ListUtil.list(t1, t2));
    fact.setResult(s);
    assertTrue(tr.addToSchedule(t1));
    assertTrue(tr.addToSchedule(t2));
    TimeBase.setSimulated(101);
    Interrupter intr = null;
    try {
      intr = interruptMeIn(TIMEOUT_SHOULDNT, true);
      while(tr.findTaskToRun()) {
	tr.runSteps(Boolean.TRUE);
      }
      intr.cancel();
    } catch (Exception e) {
      log.error("runSteps threw:", e);
    } finally {
      if (intr.did()) {
	fail("runSteps looped");
      }
    }
    assertEquals(SetUtil.set(t1, t2), SetUtil.theSet(removedTasks));
    assertTrue(t1.e.toString(), t1.e instanceof SchedService.Overrun);
  }

  public void testRunStepsWithOverrunAllowed() {
    StepTask t1 = task(100, 500, 30, null, new MockStepper(15, -10));
    t1.setOverrunAllowed(true);
    StepTask t2 = task(150, 250, 100, null, new MockStepper(10, -10));
    tr = new MockTaskRunner(new TaskRunner.SchedulerFactory () {
	public Scheduler createScheduler(Collection tasks) {
	  return new SortScheduler(tasks);
	}});
    assertTrue(tr.addToSchedule(t1));
    assertTrue(tr.addToSchedule(t2));
    TimeBase.setSimulated(101);
    assertTrue(tr.findTaskToRun());
    Interrupter intr = null;
    try {
      intr = interruptMeIn(TIMEOUT_SHOULDNT, true);
      while(tr.findTaskToRun()) {
	tr.runSteps(Boolean.TRUE);
      }
      intr.cancel();
    } catch (Exception e) {
      log.error("runSteps threw:", e);
    } finally {
      if (intr.did()) {
	fail("runSteps looped");
      }
    }
    assertNull(t1.e);
    assertTrue(t1.hasOverrun());
  }


  // test resched with overrun task doesn't lose task.
  public void testRunStepsWithOverrunAllowedPlusResched() {
    StepTask t1 = task(100, 500, 30, null, new MockStepper(15, -10));
    t1.setOverrunAllowed(true);
    StepTask t2 = task(150, 250, 100, null, new MockStepper(10, -10));
    tr = new MockTaskRunner(new TaskRunner.SchedulerFactory () {
	public Scheduler createScheduler(Collection tasks) {
	  return new SortScheduler(tasks);
	}});
    assertTrue(tr.addToSchedule(t1));
    assertEmpty(tr.getOverrunTasks());
    TimeBase.setSimulated(101);
    assertTrue(tr.findTaskToRun());
    t1.timeUsed = 1000;
    assertTrue(t1.hasOverrun());
    assertEmpty(tr.getOverrunTasks());
    assertTrue(tr.addToSchedule(t2));
    assertEquals(SetUtil.set(t1), SetUtil.theSet(tr.getOverrunTasks()));
  }

  class MockTaskRunner extends TaskRunner {
    MockTaskRunner(TaskRunner.SchedulerFactory fact) {
      super(fact);
    }

    void removeChunk(Schedule.Chunk chunk) {
      removedChunks.add(chunk);
      super.removeChunk(chunk);
    }

    void removeTask(SchedulableTask task) {
      removedTasks.add(task);
      super.removeTask(task);
    }
  }

  class SchedFact implements TaskRunner.SchedulerFactory {
    Schedule resultSchedule;
    Collection tasks;

    public SchedFact(Schedule resultSchedule) {
      this.resultSchedule = resultSchedule;
    }

    public SchedFact() {
      this(null);
    }

    public void setResult(Schedule resultSchedule) {
      this.resultSchedule = resultSchedule;
    }

    public Scheduler createScheduler(Collection tasks) {
      this.tasks = tasks;
      return new MockScheduler(resultSchedule);
    }
  }

  class MockScheduler implements Scheduler {
    Schedule sched;

    MockScheduler(Schedule sched) {
      this.sched = sched;
    }

    public boolean createSchedule() {
      return sched != null;
    }

    public Schedule getSchedule() {
      return sched;
    }
  }

  class MockStepper implements Stepper {
    int nSteps = 1;			// not finished by default
    int eachStepTime = 0;

    MockStepper() {
    }

    /** Make a stepper that repeats n times: wait until time elapsed, or
     * advance simulated time,
     * @param nSteps number of steps to execute before isFinished returns
     * true.
     * @param eachStepTime if >0, ms to sleep on each step.  if <0, step
     * @return some measure of amount of work done.
     * simulated time by abs(eachStepTime) on each step.
     */
    MockStepper(int nSteps, int eachStepTime) {
      this.nSteps = nSteps;
      this.eachStepTime = eachStepTime;
    }

    public int computeStep(int metric) {
      int work = 0;
      if (nSteps-- > 0) {
	if (eachStepTime > 0) {
	  Deadline time = Deadline.in(eachStepTime);
	  while (!time.expired()) {
	    try {
	      Thread.sleep(1);
	    }catch (InterruptedException e) {
	      throw new RuntimeException(e.toString());
	    }
	    work++;
	  }
	} else {
	  work = -eachStepTime;
	  TimeBase.step(work);
	  try {
	    Thread.sleep(1);
	  } catch (InterruptedException e) {
	    throw new RuntimeException(e.toString());
	  }
	}
      }
      return work;
    }

    public boolean isFinished() {
      return nSteps <= 0;
    }

    void setFinished() {
      nSteps = 0;
    }
  }
}
