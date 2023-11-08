package edu.algorithm.loadbalance;

import edu.algorithm.entity.Server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class RoundRobinLoadBalance {

    protected static class WeightedRoundRobin {
        // 当前服务的默认权重
        private int weight;
        // 当前服务的权重
        private AtomicLong current = new AtomicLong(0L);
        private long lastUpdate;
        protected WeightedRoundRobin() {
        }
        public int getWeight() {
            return this.weight;
        }
        // 重新初始化权重
        public void setWeight(int weight) {
            this.weight = weight;
            this.current.set(0L);
        }
        // 累加服务的权重
        public long increaseCurrent() {
            return this.current.addAndGet((long)this.weight);
        }
        // 当前服务的权重 减去 总权重
        public void sel(int total) {
            this.current.addAndGet((long)(-1 * total));
        }
        public long getLastUpdate() {
            return this.lastUpdate;
        }
        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }
    private ConcurrentMap<String, ConcurrentMap<String, WeightedRoundRobin>> methodWeightMap = new ConcurrentHashMap();


    protected Server select(List<Server> serverList){
        String key = "save key" + "edu.algorithm.loadbalance.RoundRobinLoadBalance.select";
        ConcurrentMap<String, WeightedRoundRobin> map = (ConcurrentMap)this.methodWeightMap.computeIfAbsent(key, (k) -> {
            return new ConcurrentHashMap();
        });

        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        long now = System.currentTimeMillis();
        int weight;
        Server selectedSever = null;
        WeightedRoundRobin selectedWRR = null;

        for (Iterator var12= serverList.iterator(); var12.hasNext(); totalWeight += weight){
            // 选择当前的服务
            Server server = (Server) var12.next();
            // 拿到权重
            weight = server.getWeight();
            int finalWeight = weight;
            // 把当前的服务封装成一个 WeightedRoundRobin 用于记录自身被选择的次数以及用于加权轮询选择
            WeightedRoundRobin weightedRoundRobin = (WeightedRoundRobin)map.computeIfAbsent(server.getIp(), (k) -> {
                WeightedRoundRobin wrr = new WeightedRoundRobin();
                wrr.setWeight(finalWeight);
                return wrr;
            });

            // 判断当前服务的权重如果不等于服务的权重，刷新权重
            if (weight != weightedRoundRobin.getWeight()) {
                weightedRoundRobin.setWeight(weight);
            }
            // 增加当前权重，当前没有被使用就会一直累计 current += weight
            long cur = weightedRoundRobin.increaseCurrent();
            weightedRoundRobin.setLastUpdate(now);
            // 通过这个判断，我们可以筛选出这个集合里 权重最高的一个服务实例
            if (cur > maxCurrent) {
                maxCurrent = cur;
                selectedSever = server;
                selectedWRR = weightedRoundRobin;
            }
        }
        // 如果一个服务超过 6w 毫秒 （1分钟）没有被访问，1分钟=60乘以1000=60000毫秒。就移除
//        if (serverList.size() != map.size()) {
//            map.entrySet().removeIf((item) -> {
//                return now - ((WeightedRoundRobin)item.getValue()).getLastUpdate() > 60000L;
//            });
//        }

        if(selectedSever != null){
            selectedWRR.sel(totalWeight);
            return selectedSever;
        }else {
            return serverList.get(0);
        }

    }

    public static void main(String[] args) throws Exception {
        List<Server> serverList = new ArrayList<>();
        // 1.收集可用服务器列表
        serverList.add(new Server(1,"服务器1","8080","192.168.2.2",80));
        serverList.add(new Server(2,"服务器2","8080","192.168.2.5",30));
        serverList.add(new Server(3,"服务器3","8080","192.168.2.8",40));
        serverList.add(new Server(4,"服务器4","8080","192.168.3.2",20));
        serverList.add(new Server(5,"服务器5","8080","192.168.3.5",99));
        serverList.add(new Server(6,"服务器6","8080","192.168.3.8",60));

        RoundRobinLoadBalance robinLoadBalance = new RoundRobinLoadBalance();

        for (int i = 0; i < 10; i++) {
            Server server = robinLoadBalance.select(serverList);
            System.out.println(server.toString());
        }

    }
}
