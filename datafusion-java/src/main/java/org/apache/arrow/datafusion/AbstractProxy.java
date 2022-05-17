package org.apache.arrow.datafusion;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractProxy implements AutoCloseable, NativeProxy {
  private static final Logger logger = LoggerFactory.getLogger(AbstractProxy.class);
  private final long pointer;
  private final AtomicBoolean closed;
  private final ConcurrentMap<Long, AbstractProxy> children;

  protected AbstractProxy(long pointer) {
    this.pointer = pointer;
    if (logger.isDebugEnabled()) {
      logger.debug("Obtaining {}@{}", getClass().getSimpleName(), Long.toHexString(pointer));
    }
    this.closed = new AtomicBoolean(false);
    this.children = new ConcurrentHashMap<>();
  }

  protected final void registerChild(AbstractProxy child) {
    AbstractProxy old = children.putIfAbsent(child.getPointer(), child);
    if (old != null) {
      logger.warn("duplicated registry for {}: {}", child.getPointer(), old);
    }
  }

  protected final boolean isClosed() {
    return closed.get();
  }

  @Override
  public final long getPointer() {
    return pointer;
  }

  abstract void doClose(long pointer) throws Exception;

  @Override
  public final void close() throws Exception {
    if (closed.compareAndSet(false, true)) {
      for (AbstractProxy child : children.values()) {
        // detection to avoid cycle
        if (!child.isClosed()) {
          child.close();
        }
      }
      if (logger.isDebugEnabled()) {
        logger.debug("Closing {}@{}", getClass().getSimpleName(), Long.toHexString(pointer));
      }
      doClose(pointer);
    } else {
      logger.warn("Failed to close {}, maybe already closed?", getPointer());
    }
  }
}
