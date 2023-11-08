package edu.algorithm.loadbalance;

import edu.algorithm.entity.Server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

public class RandomLoadBalance {


    private boolean needWeightLoadBalance = false;

    protected int getWeight(Server server){
        int weight;
        return Math.max(weight = server.getWeight() > 0 ? server.getWeight() : 100, 0);
    }

    public Server select(List<Server> serverList){
        int length = serverList.size();
        if(!needWeightLoadBalance){
             return serverList.get(ThreadLocalRandom.current().nextInt(length));
        }else {
            boolean sameWeight = true;
            int[] weights = new int[length];
            int totalWeight = 0;

            int offset;
            int i;
            for(offset = 0; offset < length; ++offset) {
                i = this.getWeight(serverList.get(offset));
                totalWeight += i;
                weights[offset] = totalWeight;
                // 目的是为了算出整个列表里的权重是存在差异的
                // 通过 总权重 是否等于 当前权重 * index 去校验是否存在差异
                if (sameWeight && totalWeight != i * (offset + 1)) {
                    sameWeight = false;
                }
            }
            if(totalWeight > 0 && !sameWeight){
                // 计算权重综合sum，在1和sum 之间选择一个offset。
                offset = ThreadLocalRandom.current().nextInt(totalWeight);
                // 遍历整个权重list，如果遇到大于 offset 就选择该项
                for(i = 0; i < length; ++i) {
                    if (offset < weights[i]) {
                        return  serverList.get(i);
                    }
                }
            }
            return serverList.get(ThreadLocalRandom.current().nextInt(length));
        }
    }


    public static void main(String[] args) throws InterruptedException {
        List<Server> serverList = new ArrayList<>();
        serverList.add(new Server(1,"服务器1","8080","192.168.2.2",80));
        serverList.add(new Server(2,"服务器2","8080","192.168.2.5",30));
        serverList.add(new Server(3,"服务器3","8080","192.168.2.8",40));
        serverList.add(new Server(4,"服务器4","8080","192.168.3.2",10));
        serverList.add(new Server(5,"服务器5","8080","192.168.3.5",89));
        serverList.add(new Server(6,"服务器6","8080","192.168.3.8",60));

        RandomLoadBalance loadBalance = new RandomLoadBalance();
        loadBalance.needWeightLoadBalance = true;
        Server server = loadBalance.select(serverList);
        System.out.println(server.toString());
    }



}
