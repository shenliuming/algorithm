package edu.algorithm.base;

import java.util.ArrayList;
import java.util.List;

public class RoundRobinLoadBalancer {
    private List<String> serverList;
    private int currentIndex;

    public RoundRobinLoadBalancer(List<String> serverList) {
        this.serverList = serverList;
        this.currentIndex = 0;
    }

    public String getNextServer() {
        String server = serverList.get(currentIndex);
        currentIndex = (currentIndex + 1) % serverList.size();
        return server;
    }

    // 示例使用
    public static void main(String[] args) {
        // 创建服务器列表
        List<String> serverList = new ArrayList<>();
        serverList.add("Server1");
        serverList.add("Server2");
        serverList.add("Server3");

        // 创建轮询负载均衡器实例
        RoundRobinLoadBalancer loadBalancer = new RoundRobinLoadBalancer(serverList);

        // 模拟请求分发
        for (int i = 0; i < 10; i++) {
            String server = loadBalancer.getNextServer();
            System.out.println("Request " + (i + 1) + " sent to server: " + server);
        }
    }
}