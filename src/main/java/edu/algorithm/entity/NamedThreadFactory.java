package edu.algorithm.entity;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {
    protected static final AtomicInteger POOL_SEQ = new AtomicInteger(1);
    protected final AtomicInteger mThreadNum;
    protected final String mPrefix;
    protected final boolean mDaemon;
    protected final ThreadGroup mGroup;

    public NamedThreadFactory() {
        this("pool-" + POOL_SEQ.getAndIncrement(), false);
    }

    public NamedThreadFactory(String prefix) {
        this(prefix, false);
    }

    public NamedThreadFactory(String prefix, boolean daemon) {
        this.mThreadNum = new AtomicInteger(1);
        this.mPrefix = prefix + "-thread-";
        this.mDaemon = daemon;
        SecurityManager s = System.getSecurityManager();
        this.mGroup = s == null ? Thread.currentThread().getThreadGroup() : s.getThreadGroup();
    }

    public Thread newThread(Runnable runnable) {
        String name = this.mPrefix + this.mThreadNum.getAndIncrement();
        Thread ret = new Thread(this.mGroup, runnable, name, 0L);
        ret.setDaemon(this.mDaemon);
        return ret;
    }

    public ThreadGroup getThreadGroup() {
        return this.mGroup;
    }
}
