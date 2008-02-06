package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public class MapReduceIndex<Key, Value, Input> implements UpdatableIndex<Key,Value, Input> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapReduceIndex");
  private final DataIndexer<Key, Value, Input> myIndexer;
  private final IndexStorage<Key, Value> myStorage;
  
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  
  private final Alarm myFlushAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final Runnable myFlushRequest = new Runnable() {
    public void run() {
      final Lock lock = getWriteLock();
      lock.lock();
      try {
        myStorage.flush();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        lock.unlock();
      }
    }
  };

  public MapReduceIndex(DataIndexer<Key, Value, Input> indexer, final IndexStorage<Key, Value> storage) {
    myIndexer = indexer;
    myStorage = storage;
  }

  public IndexStorage<Key, Value> getStorage() {
    return myStorage;
  }

  public void clear() throws StorageException {
    final Lock lock = getWriteLock();
    lock.lock();
    try {
      myStorage.clear();
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    finally {
      lock.unlock();
    }
  }

  public void dispose() {
    final Lock lock = getWriteLock();
    lock.lock();
    try {
      myStorage.close();
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    finally {
      lock.unlock();
    }
  }

  public Lock getReadLock() {
    return myLock.readLock();
  }

  public Lock getWriteLock() {
    return myLock.writeLock();
  }

  @NotNull
  public ValueContainer<Value> getData(final Key key) throws StorageException {
    final Lock lock = getReadLock();
    lock.lock();
    try {
      return myStorage.read(key);
    }
    finally {
      lock.unlock();
      scheduleFlush();
    }
  }

  public void removeData(final Key key) throws StorageException {
    final Lock lock = getWriteLock();
    lock.lock();
    try {
      myStorage.remove(key);
    }
    finally {
      lock.unlock();
      scheduleFlush();
    }
  }

  public void update(int inputId, @Nullable Input content, @Nullable Input oldContent) throws StorageException {
    final Map<Key, Value> oldData = oldContent != null? myIndexer.map(oldContent) : Collections.<Key, Value>emptyMap();
    final Map<Key, Value> data    = content != null? myIndexer.map(content) : Collections.<Key, Value>emptyMap();

    final Set<Key> allKeys = new HashSet<Key>(oldData.size() + data.size());
    allKeys.addAll(oldData.keySet());
    allKeys.addAll(data.keySet());

    if (allKeys.size() > 0) {
      for (Key key : allKeys) {
        // remove outdated values
        final Value oldValue = oldData.get(key);
        final Lock writeLock = getWriteLock();
        if (oldValue != null) {
          writeLock.lock();
          try {
            myStorage.removeValue(key, inputId, oldValue);
          }
          finally {
            writeLock.unlock();
          }
        }
        // add new values
        final Value newValue = data.get(key);
        if (newValue != null) {
          writeLock.lock();
          try {
            myStorage.addValue(key, inputId, newValue);
          }
          finally {
            writeLock.unlock();
          }
        }
      }
      scheduleFlush();
    }
  }

  private void scheduleFlush() {
    myFlushAlarm.cancelAllRequests();
    myFlushAlarm.addRequest(myFlushRequest, 15000 /* 15 sec */);
  }

}
