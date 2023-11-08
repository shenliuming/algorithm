package edu.algorithm.loadbalance;

import edu.algorithm.entity.NamedThreadFactory;
import edu.algorithm.entity.RpcStatus;
import edu.algorithm.entity.Server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShortestResponseLoadBalance {

    public static final String NAME = "shortestresponse";
    private int slidePeriod = 30000;
    private ConcurrentMap<String, SlideWindowData> methodMap = new ConcurrentHashMap();
    private AtomicBoolean onResetSlideWindow = new AtomicBoolean(false);
    private volatile long lastUpdateTime = System.currentTimeMillis();
    private ExecutorService executorService = Executors
            .newCachedThreadPool(new NamedThreadFactory("Dubbo-framework-shared-handler", true));


    public static class SlideWindowData {
        private long succeededOffset;
        private long succeededElapsedOffset;
        public RpcStatus rpcStatus;

        public SlideWindowData(RpcStatus rpcStatus) {
            this.rpcStatus = rpcStatus;
            this.succeededOffset = 0L;
            this.succeededElapsedOffset = 0L;
        }

        public void reset() {
            this.succeededOffset = this.rpcStatus.getSucceeded();
            this.succeededElapsedOffset = this.rpcStatus.getSucceededElapsed();
        }

        private long getSucceededAverageElapsed() {
            long succeed = this.rpcStatus.getSucceeded() - this.succeededOffset;
            return succeed == 0L ? 0L : (this.rpcStatus.getSucceededElapsed() - this.succeededElapsedOffset) / succeed;
        }

        public long getEstimateResponse() {
            int active = this.rpcStatus.getActive() + 1;
            return this.getSucceededAverageElapsed() * (long)active;
        }

        public String toString(){

            return "SlideWindowData{" +
                    "succeededOffset:" + this.succeededOffset + " " +
                    "succeededElapsedOffset:" + this.succeededElapsedOffset + " "  +
                    "rpcStatus:" + this.rpcStatus.toString()  +
                    "}";
        }
    }

    public ShortestResponseLoadBalance() {
    }


    protected Server select(List<Server> serverList) {
        // 获取服务列表的List
        int length = serverList.size();
        long shortestResponse = Long.MAX_VALUE;
        int shortestCount = 0;
        // 最快响应时间
        int[] shortestIndexes = new int[length];
        // 权重
        int[] weights = new int[length];
        int totalWeight = 0;
        int firstWeight = 0;
        boolean sameWeight = true;

        int offsetWeight;
        for(offsetWeight = 0; offsetWeight < length; ++offsetWeight) {
            // 获取当前服务实例
            Server server = serverList.get(offsetWeight);
            // 获取当前服务实例的状态数据
            RpcStatus rpcStatus = RpcStatus.getStatus(server.getIp());
            // 通过状态数据拿到 当前服务的 滑动窗口数据
            SlideWindowData slideWindowData = (SlideWindowData)this.methodMap.computeIfAbsent(server.getIp(), (key) -> {
                return new SlideWindowData(rpcStatus);
            });
            // 通过滑动窗口拿到 预估的响应时间
            long estimateResponse = slideWindowData.getEstimateResponse();
            // 获取当前服务的权重
            int afterWarmup = server.getWeight();
            // 把服务的权重保存起来
            weights[offsetWeight] = afterWarmup;
            // 如果当前服务的 预估响应时间 小于 当前最短的响应时间
            if (estimateResponse < shortestResponse) {
                // 更改赋值
                shortestResponse = estimateResponse;
                shortestCount = 1;
                shortestIndexes[0] = offsetWeight;
                totalWeight = afterWarmup;
                firstWeight = afterWarmup;
                sameWeight = true;
            } else if (estimateResponse == shortestResponse) {
                // 如果预估的响应时间 和当前的相等， 添加访问次数，设置权重
                shortestIndexes[shortestCount++] = offsetWeight;
                // 增加整体权重
                totalWeight += afterWarmup;
                if (sameWeight && offsetWeight > 0 && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        // 30s 开启一个定时任务 去重置 滑动窗口
        if (System.currentTimeMillis() - this.lastUpdateTime > (long)this.slidePeriod &&
                this.onResetSlideWindow.compareAndSet(false, true)) {
            this.executorService.execute(() -> {
                this.methodMap.values().forEach(SlideWindowData::reset);
                this.lastUpdateTime = System.currentTimeMillis();
                this.onResetSlideWindow.set(false);
            });
        }
        // 得出最短响应时间的服务器
        if (shortestCount == 1) {
            return serverList.get(shortestIndexes[0]);
        } else {

            if (!sameWeight && totalWeight > 0) {
                offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
                for(int i = 0; i < shortestCount; ++i) {
                    int shortestIndex = shortestIndexes[i];
                    offsetWeight -= weights[shortestIndex];
                    if (offsetWeight < 0) {
                        return  serverList.get(shortestIndex);
                    }
                }
            }

            return serverList.get(shortestIndexes[ThreadLocalRandom.current().nextInt(shortestCount)]);
        }
    }


    public static void main(String[] args) {
        List<Server> serverList = new ArrayList<>();
        serverList.add(new Server(1,"服务器1","8080","127.0.0.1",90,60));
        serverList.add(new Server(2,"服务器2","8080","127.0.0.2",80,80));
        serverList.add(new Server(3,"服务器3","8080","127.0.0.3",70,90));
        serverList.add(new Server(4,"服务器4","8080","127.0.0.4",60,60));
        serverList.add(new Server(5,"服务器5","8080","127.0.0.5",50,80));
        serverList.add(new Server(6,"服务器6","8080","127.0.0.6",40,60));
        serverList.add(new Server(7,"服务器7","8080","127.0.0.7",30,80));

        ShortestResponseLoadBalance loadBalance = new ShortestResponseLoadBalance();
        for (int i = 0; i < 10; i++) {
            Server server = loadBalance.select(serverList);
            RpcStatus.beginCount(server.getIp());
            RpcStatus.endCount(RpcStatus.getStatus(server.getIp()), server.getElapsed(),true);
            System.out.println("被选中的服务器：" + server.toString());
        }

        Iterator iterator = loadBalance.methodMap.entrySet().iterator();

        while (iterator.hasNext()){
            Map.Entry<String, SlideWindowData>  entry = (Map.Entry<String, SlideWindowData>) iterator.next();
            System.out.println("key"  + entry.getKey() +",value :" + entry.getValue().toString());
        }
    }


}
