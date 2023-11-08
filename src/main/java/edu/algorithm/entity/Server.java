package edu.algorithm.entity;

import lombok.Data;

@Data
public class Server {
    // 服务器 id
    private int id;
    // 服务器 名称
    private String name;
    // 服务器 端口号
    private String port;
    // 服务器 ip地址
    private String ip;
    // 权重
    private int weight;
    // 消耗的时间（响应时间）
    private int elapsed;

    private int replicaNumber;

    private String hashArguments;

    private int timeOut;

    public Server(int id, String name, String port, String ip, int weight) {
        this.id = id;
        this.name = name;
        this.port = port;
        this.ip = ip;
        this.weight = weight;
    }
    public Server(int id, String name, String port, String ip, int weight,int elapsed) {
        this.id = id;
        this.name = name;
        this.port = port;
        this.ip = ip;
        this.weight = weight;
        this.elapsed = elapsed;
    }
    public Server(int id, String name, String port, String ip, int weight,int elapsed,int replicaNumber,String hashArguments) {
        this.id = id;
        this.name = name;
        this.port = port;
        this.ip = ip;
        this.weight = weight;
        this.elapsed = elapsed;
        this.replicaNumber = replicaNumber;
        this.hashArguments = hashArguments;
    }

    public Server(int id, String name, String port, String ip, int weight,int elapsed,int replicaNumber,String hashArguments,int timeOut) {
        this.id = id;
        this.name = name;
        this.port = port;
        this.ip = ip;
        this.weight = weight;
        this.elapsed = elapsed;
        this.replicaNumber = replicaNumber;
        this.hashArguments = hashArguments;
        this.timeOut = timeOut;
    }

    @Override
    public String toString() {
        return "Server{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", port='" + port + '\'' +
                ", ip='" + ip + '\'' +
                ", weight=" + weight +
                '}';
    }
}
