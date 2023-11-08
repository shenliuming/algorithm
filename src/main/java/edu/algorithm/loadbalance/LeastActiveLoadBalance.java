package edu.algorithm.loadbalance;

import edu.algorithm.entity.RpcStatus;
import edu.algorithm.entity.Server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 加权最少活跃调用优先
 */
public class LeastActiveLoadBalance {

    public static final String NAME = "leastactive";
//
//    private static final ConcurrentMap<String, RpcStatus> SERVICE_STATISTICS = new ConcurrentHashMap();
    // 要找出 这个个服务列表里，活跃度最少的一个服务器，并且选中，0 我们要根据权重去做加权随机，
    public Server select(List<Server> serverList){
        // 权重、最少使用、响应时间
        int length = serverList.size();
        int leastActive = -1;
        int leastCount = 0;
        int[] leastIndexes = new int[length];
        int[] weights = new int[length];
        int totalWeight = 0;
        int firstWeight = 0;
        boolean sameWeight = true;

        int offsetWeight;
        int leastIndex;

        // 第一步 找到当前 活跃度最少的服务，如果相同则通过 加权随机算法选择一个
        for (offsetWeight = 0;offsetWeight < length; ++offsetWeight) {
            // 根据下标去拿服务
            Server server = serverList.get(offsetWeight);
            // 获取当前服务的最少活跃度
            leastIndex = RpcStatus.getStatus(server.getIp()).getActive();
            // 获取当前服务的权重
            int afterWarmup = server.getWeight();
            // 把权重存在数组里，到时候用于动态判断
            weights[offsetWeight] = afterWarmup;
            // 不是第一次访问 并且 当前服务实例大于等于 最近一次服务实例 连接数
            if(leastActive != -1 && leastIndex >= leastActive){
                // 当前服务实例的 最少活跃度 等于 最近服务实例的活跃度
                if(leastIndex == leastActive){
                    // 保存每个服务实例的 最少活跃度
                    leastIndexes[leastCount++] = offsetWeight;
                    // 计算总权重
                    totalWeight += afterWarmup;
                    // 用于判断
                    if(sameWeight && afterWarmup != firstWeight){
                        sameWeight = false;
                    }
                }
            }else {
                leastActive = leastIndex;
                leastCount = 1;
                leastIndexes[0] = offsetWeight;
                totalWeight = afterWarmup;
                firstWeight = afterWarmup;
                sameWeight = true;
            }
        }
        // 如果是只有一个服务，有一个最少活跃度的服务被选中，
        if(leastCount == 1){
            return serverList.get(leastIndexes[0]);
        }else {
            // 权重不相同，并且 总权重大于0
            if (!sameWeight && totalWeight > 0) {
                // 根据总权重去拿一个 随机数， 加权随机算法
                offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);

                for(int i = 0; i < leastCount; ++i) {
                    leastIndex = leastIndexes[i];
                    offsetWeight -= weights[leastIndex];
                    if (offsetWeight < 0) {
                        return serverList.get(leastIndex);
                    }
                }
            }
            return serverList.get(leastIndexes[ThreadLocalRandom.current().nextInt(leastCount)]);
        }

    }

    public static void main(String[] args) {
        List<Server> serverList = new ArrayList<>();
        serverList.add(new Server(1,"服务器2","8080","127.0.0.1",90,100));
        serverList.add(new Server(2,"服务器1","8080","127.0.0.2",60,110));
        serverList.add(new Server(3,"服务器3","8080","127.0.0.3",50,120));
        serverList.add(new Server(4,"服务器4","8080","127.0.0.4",40,130));
        serverList.add(new Server(5,"服务器5","8080","127.0.0.5",30,140));
        serverList.add(new Server(6,"服务器6","8080","127.0.0.6",20,150));
        serverList.add(new Server(7,"服务器7","8080","127.0.0.7",10,160));

        LeastActiveLoadBalance loadBalance = new LeastActiveLoadBalance();

        for (int i = 0; i < 10; i++) {
            Server server = loadBalance.select(serverList);
            RpcStatus.beginCount(server.getIp());
            System.out.println("被选中的服务器：" + server.toString());
        }
        Iterator iterator = RpcStatus.SERVICE_STATISTICS.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<String,RpcStatus> entry = (Map.Entry<String, RpcStatus>) iterator.next();
            System.out.println("Key: "+entry.getKey()+" Value: "+entry.getValue().toString() +  " avg:" + entry.getValue().getAverageElapsed());
        }

//        for (int i = 0; i < 20; i++) {
//            Server server = loadBalance.select(serverList);
//            RpcStatus.beginCount(server.getIp());
//            RpcStatus.endCount(RpcStatus.getStatus(server.getIp()),  server.getElapsed(),true);
//            System.out.println(server.toString());
//        }
//
//        iterator = RpcStatus.SERVICE_STATISTICS.entrySet().iterator();
//        while (iterator.hasNext()){
//            Map.Entry<String,RpcStatus> entry = (Map.Entry<String, RpcStatus>) iterator.next();
//            System.out.println("Key: "+entry.getKey()+" Value: "+entry.getValue().toString() + " avg:" + entry.getValue().getAverageElapsed());
//        }

    }

}
