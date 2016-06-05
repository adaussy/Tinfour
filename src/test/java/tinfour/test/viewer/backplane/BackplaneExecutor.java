/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---------------------------------------------------------------------
 */

/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 04/2016  G. Lucas     Created
 *
 * Notes:
 *
 *  This class is designed using the singleton pattern and on-demand
 *  initialization as proposed by Bill Pugh.  See
 *  https://en.wikipedia.org/wiki/Singleton_pattern#Initialization-on-demand_holder_idiom
 *
 *  Note that, in practice, there is only a single instance of this class
 *  provided that only one class loader is used.  Since this example
 *  application does not use custom class loaders, the expected behavior
 *  of this class is to follow the singleton pattern.
 * -----------------------------------------------------------------------
 */
package tinfour.test.viewer.backplane;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides a singleton class which serves as multi-threaded utility
 * for performing time-consuming processes including file access,
 * rendering, and analysis.
 */
final class BackplaneExecutor {

  @SuppressWarnings("PMD")
  private static class BackplaneExecutorHolder {

    private static final BackplaneExecutor INSTANCE = new BackplaneExecutor();
  }

  /**
   * Get the single instance of the BackplaneExecutor associated with
   * the application
   *
   * @return a valid instance.
   */
  public static BackplaneExecutor getInstance() {
    return BackplaneExecutorHolder.INSTANCE;
  }

  private final int nThreadsInPool;
  private final ThreadPoolExecutor executor;
  private final List<IModelViewTask> taskList;

  /**
   * A private constructor to deter application code from making
   * instantiations of this class.
   */
  private BackplaneExecutor() {
    // If at all possible, the number of threads we claim should
    // be less than the number of processors.  That way, even during
    // the heaviest model/view processing, there will still be one
    // unencumbered processor available to the Event Dispatching Thread
    // and our user interface will remain responsive.
    int nAvailableProcessors = Runtime.getRuntime().availableProcessors();
    if (nAvailableProcessors > 6) {
      nThreadsInPool = 4; // no sense getting greedy
    } else if (nAvailableProcessors > 2) {
      nThreadsInPool = nAvailableProcessors - 2;
    } else {
      nThreadsInPool = 1;
    }

    // The custom thread factory is used for no better reason than
    // to ensure that the threads are given identifiable names.
    // This comes in handy sometimes when debugging.
    BackplaneThreadFactory factory = new BackplaneThreadFactory("Backplane");
    executor = new MvThreadPoolExecutor(nThreadsInPool, nThreadsInPool,
      1000, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>() {
        private static final long serialVersionUID = 1;
      },
      factory);

    taskList = new ArrayList<IModelViewTask>();
  }

  /**
   * Use the thread pool executor allocated for processing model
   * and view related tasks to process a runnable.
   *
   * @param r a valid runnable
   */
  void runTask(Runnable r) {
    if (r instanceof IModelViewTask) {
      synchronized (taskList) {
        taskList.add((IModelViewTask) r);
      }
    }
    executor.execute(r);

  }

  /**
   * Mark all tasks as cancelled and remove any pending tasks from the
   * queue. Any tasks currently running will continue to do so until they
   * check their own cancellation flags and exit their run menthods.
   */
  void cancelAllTasks() {
    List<IModelViewTask> cancelledList = new ArrayList<>();
    synchronized (taskList) {
      for (IModelViewTask t : taskList) {
        t.cancel();
        cancelledList.add(t);
      }
      taskList.clear();
    }
    for (IModelViewTask t : cancelledList) {
      executor.remove(t);
    }
  }

  /**
   * A custom thread pool executor which extends the base class
   * to provide custom handling for IModelViewTask instances
   */
  private class MvThreadPoolExecutor extends ThreadPoolExecutor {

    public MvThreadPoolExecutor(
      int corePoolSize, int maximumPoolSize,
      long keepAliveTime, TimeUnit unit,
      BlockingQueue<Runnable> workQueue,
      ThreadFactory threadFactory) {
      super(corePoolSize, maximumPoolSize,
        keepAliveTime, unit,
        workQueue,
        threadFactory);
    }

    //  @Override
    //  public void beforeExecute(Thread t, Runnable r) {
    //      if (r instanceof IModelViewTask) {
    //        synchronized (taskList) {
    //          taskList.add((IModelViewTask) r);
    //        }
    //      }
    //    super.beforeExecute(t, r);
    //  }
    @Override
    public void afterExecute(Runnable r, Throwable t) {
      super.afterExecute(r, t);
      if (r instanceof IModelViewTask) {
        synchronized (taskList) {
          taskList.remove((IModelViewTask) r);
        }
      }
    }

  }

  private class BackplaneThreadFactory implements ThreadFactory {

    final String name;
    final AtomicInteger serialID;

    public BackplaneThreadFactory(String name) {
      this.name = name;
      serialID = new AtomicInteger();
    }

    @Override
    public Thread newThread(Runnable r) {
      ThreadFactory tf = Executors.defaultThreadFactory();
      Thread t = tf.newThread(r);
      int index = serialID.getAndAdd(1);
      t.setName(name + "_" + index);
      return t;
    }

  }

  int getCorePoolSize() {
    return executor.getCorePoolSize();
  }

}
