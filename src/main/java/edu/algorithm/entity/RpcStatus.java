package edu.algorithm.entity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RpcStatus {
    // 用于保存所有服务实例的
    public static final ConcurrentMap<String, RpcStatus> SERVICE_STATISTICS = new ConcurrentHashMap();

    public final ConcurrentMap<String, Object> values = new ConcurrentHashMap();
    // 每个服务器的最少活跃连接数
    public final AtomicInteger active = new AtomicInteger();
    public final AtomicLong total = new AtomicLong();
    public final AtomicInteger failed = new AtomicInteger();
    public final AtomicLong totalElapsed = new AtomicLong();
    public final AtomicLong failedElapsed = new AtomicLong();
    public final AtomicLong maxElapsed = new AtomicLong();
    public final AtomicLong failedMaxElapsed = new AtomicLong();
    public final AtomicLong succeededMaxElapsed = new AtomicLong();


    private RpcStatus() {
    }

    public static RpcStatus getStatus(String uri) {
        return (RpcStatus)SERVICE_STATISTICS.computeIfAbsent(uri, (key) -> {
            return new RpcStatus();
        });
    }

    public static void removeStatus(String uri) {
        SERVICE_STATISTICS.remove(uri);
    }

    public static void beginCount(String uri ) {
        beginCount(uri, Integer.MAX_VALUE);
    }
    // 请求处理开始，记录每个服务器活跃连接数
    public static boolean beginCount(String url, int max) {
        max = max <= 0 ? Integer.MAX_VALUE : max;
        RpcStatus appStatus = getStatus(url);
        if (appStatus.active.get() == Integer.MAX_VALUE) {
            return false;
        } else {
            int i;
            do {
                i = appStatus.active.get();
                if (i == Integer.MAX_VALUE || i + 1 > max) {
                    return false;
                }
            } while(!appStatus.active.compareAndSet(i, i + 1));

            appStatus.active.incrementAndGet();
            return true;
        }
    }
    // 请求处理完成后，更新选择服务的活跃连接数
    public static void endCount(RpcStatus status, long elapsed, boolean succeeded) {
        status.active.decrementAndGet();
        status.total.incrementAndGet();
        status.totalElapsed.addAndGet(elapsed);
        if (status.maxElapsed.get() < elapsed) {
            status.maxElapsed.set(elapsed);
        }

        if (succeeded) {
            if (status.succeededMaxElapsed.get() < elapsed) {
                status.succeededMaxElapsed.set(elapsed);
            }
        } else {
            status.failed.incrementAndGet();
            status.failedElapsed.addAndGet(elapsed);
            if (status.failedMaxElapsed.get() < elapsed) {
                status.failedMaxElapsed.set(elapsed);
            }
        }
    }

    public int getActive() {
        return this.active.get();
    }

    public void set(String key, Object value) {
        this.values.put(key, value);
    }

    public Object get(String key) {
        return this.values.get(key);
    }


    public long getTotal() {
        return this.total.longValue();
    }

    public long getTotalElapsed() {
        return this.totalElapsed.get();
    }

    public long getAverageElapsed() {
        long total = this.getTotal();
        return total == 0L ? 0L : this.getTotalElapsed() / total;
    }

    public long getMaxElapsed() {
        return this.maxElapsed.get();
    }

    public int getFailed() {
        return this.failed.get();
    }

    public long getFailedElapsed() {
        return this.failedElapsed.get();
    }

    public long getFailedAverageElapsed() {
        long failed = (long)this.getFailed();
        return failed == 0L ? 0L : this.getFailedElapsed() / failed;
    }

    public long getFailedMaxElapsed() {
        return this.failedMaxElapsed.get();
    }

    public long getSucceeded() {
        return this.getTotal() - (long)this.getFailed();
    }

    public long getSucceededElapsed() {
        return this.getTotalElapsed() - this.getFailedElapsed();
    }

    public long getSucceededAverageElapsed() {
        long succeeded = this.getSucceeded();
        return succeeded == 0L ? 0L : this.getSucceededElapsed() / succeeded;
    }

    public long getSucceededMaxElapsed() {
        return this.succeededMaxElapsed.get();
    }

    public long getAverageTps() {
        return this.getTotalElapsed() >= 1000L ? this.getTotal() / (this.getTotalElapsed() / 1000L) : this.getTotal();
    }

    @Override
    public String toString() {
        return "RpcStatus{" +
                "values=" + values +
                ", active=" + active +
                ", total=" + total +
                ", failed=" + failed +
                ", totalElapsed=" + totalElapsed +
                ", failedElapsed=" + failedElapsed +
                ", maxElapsed=" + maxElapsed +
                ", failedMaxElapsed=" + failedMaxElapsed +
                ", succeededMaxElapsed=" + succeededMaxElapsed +
                '}';
    }
}
