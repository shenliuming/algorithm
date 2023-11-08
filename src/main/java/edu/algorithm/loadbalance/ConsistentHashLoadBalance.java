package edu.algorithm.loadbalance;

import edu.algorithm.entity.Invocation;
import edu.algorithm.entity.RpcStatus;
import edu.algorithm.entity.Server;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.io.Bytes;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;


public class ConsistentHashLoadBalance {


    // 提供请求之间的隔离，相同参数的请求总是发到同一提供者
    private final ConcurrentMap<String, ConsistentHashSelector<?>> selectors = new ConcurrentHashMap();

    public ConsistentHashLoadBalance() {
    }


    protected Server doSelect(List<Server> serverList,Invocation invocation) {
        // 获取方法名
        String methodName = "edu.example.method";
        // key格式：接口名.方法名
        String key = serverList.get(0).getIp() + "." + methodName;
//        String key =   "test." + methodName;
        //  identityHashCode 用来识别invokers是否发生过变更
        int invokersHashCode = this.getCorrespondingHashCode(serverList);
        // 根key，从缓存中获取 ConsistentHashSelector
        ConsistentHashSelector<?> selector = (ConsistentHashSelector)this.selectors.get(key);
        // 若不存在"接口.方法名"对应的选择器，或是Invoker列表已经发生了变更，则初始化一个选择器。
        if (selector == null || selector.identityHashCode != invokersHashCode) {
            // 若不存在"接口.方法名"对应的选择器，或是Invoker列表已经发生了变更，则初始化一个选择器
            this.selectors.put(key, new ConsistentHashSelector(serverList, methodName, invokersHashCode));
            selector = (ConsistentHashSelector)this.selectors.get(key);
        }
        // 通过选择器去选择一个 服务实例
        return selector.select(invocation);
    }

    public <T> int getCorrespondingHashCode(List<Server> serverList) {
        return serverList.hashCode();
    }

    private static final class ConsistentHashSelector<T> {
        /**
         * 存储Hash值与节点映射关系的TreeMap
         */
        private final TreeMap<Long, Server> virtualInvokers = new TreeMap();
        /**
         * 节点数目
         */
        private final int replicaNumber = 160;
        /**
         * 用来识别Invoker列表是否发生变更的Hash码
         */
        private final int identityHashCode;
        /**
         * 请求中用来作Hash映射的参数的索引
         */
        private final int[] argumentIndex;
        /**
         * 服务器请求 变更的数量
         */
        private Map<String, AtomicLong> serverRequestCountMap = new ConcurrentHashMap();
        /**
         * 总请求次数
         */
        private AtomicLong totalRequestCount;
        /**
         * 服务的数量
         */
        private int serverCount;
        /**
         * 过载因子
         */
        private static final double OVERLOAD_RATIO_THREAD = 1.5;

    ConsistentHashSelector(List<Server> serverList, String methodName, int identityHashCode) {
        // hashcode
        this.identityHashCode = identityHashCode;
//            String url = serverList.get(0).getIp();
//            this.replicaNumber = serverList.get(0).getMethodParameter(methodName,"hash.nodes",160);
        // 获取 hash.arguments ，缺省是 0 然后进行切割
        // 一致性Hash负载均衡涉及到两个主要的配置参数为hash.arguments 与hash.nodes。

        // 缺省只对第一个参数Hash，如果要修改，请配置  <dubbo:parameter key="hash.arguments" value="0,1" />
        //  hash.arguments ： 当进行调用时候根据调用方法的哪几个参数生成key，
        // 并根据key来通过一致性hash算法来选择调用结点。例如调用方法invoke(String s1,String s2);
        // 若hash.arguments为1(默认值)，则仅取invoke的参数1（s1）来生成hashCode
        // 到底使用那个入参去 继续hash 计算
        String[] index = new String[]{"0"}; // 获取Hash映射参数的下标配置项，这里默认使用 0

        this.argumentIndex = new int[index.length];

        for(int i = 0; i < index.length; ++i) {
            this.argumentIndex[i] = Integer.parseInt(index[i]);
        }

        Iterator var14 = serverList.iterator();
        // 把所有的服务实例 通过计算 映射服务到哈希环上
        while(var14.hasNext()) {
            Server server = (Server) var14.next();
            // 获取地址，这里根据IP 去计算
            String address = server.getIp() +":"+ server.getPort();

            for(int i = 0; i < this.replicaNumber / 4; ++i) {
                byte[] digest = Bytes.getMD5(address + i);

                for(int h = 0; h < 4; ++h) {
                    // 计算当前服务实例的位置 通过识别码 digest 与  4294967295L（2^32-1) 取模，这里的取模是通过位运算
                    // 这里的hash 是通过高低位运算去掉高位，低位空位补零然后与 2^32-1 取模 得到落点，作用就是 把我们的服务器 映射到4个分段去
                    long m = this.hash(digest, h);
                    // 得到落点 long 然后映射服务
                    this.virtualInvokers.put(m, server);
                }
            }
        }

        this.totalRequestCount = new AtomicLong(0L);
        this.serverCount = serverList.size();
        this.serverRequestCountMap.clear();
    }

        public Server select(Invocation invocation) {
            // 根据invocation的【参数值】来确定key，默认使用第一个参数来做hash计算
            String key = this.toKey(invocation.getArguments());
            //  获取【参数值】的md5编码
            byte[] digest = Bytes.getMD5(key);
            return this.selectForKey(this.hash(digest, 0));
        }
        // 根据参数索引获取参数，并将所有参数拼接成字符串
        private String toKey(Object[] args) {
            // 这里就是根据 参数列表 拼装成一个字符串了
            StringBuilder buf = new StringBuilder();
            int[] var3 = this.argumentIndex;
            int var4 = var3.length;

            for(int var5 = 0; var5 < var4; ++var5) {
                int i = var3[var5];
                if (i >= 0 && i < args.length) {
                    buf.append(args[i]);
                }
            }

            return buf.toString();
        }

        // 根据参数字符串的md5编码找出Invoker
        private Server selectForKey(long hash) {
            // 获取跟当前 hash 相同的 Entry，如果不存在，就返回 大于 这个hash 的最小的一个条目，如果也不存在，就返回null
            Map.Entry<Long, Server> entry = this.virtualInvokers.ceilingEntry(hash);
            // 如果没有 就去第一个，可以看成是一个环
            if (entry == null) {
                entry = this.virtualInvokers.firstEntry();
            }

            // 这里获取 服务实例的地址
            String serverAddress = ((Server)entry.getValue()).getIp() +":"+ entry.getValue().getPort();
            // 最大请求数 除以 服务列表 再乘以 1.5 得到overloadThread
            // 然后从访问列表里找，是否有这个当前服务实例的访问记录，如果有就判断它的访问次数 是否大于等于 overloadThread
            // 目的是为了找到一个访问次数较少的一个服务器
            for(double overloadThread = (double)this.totalRequestCount.get() /
                    (double)this.serverCount * 1.5; this.serverRequestCountMap.containsKey(serverAddress) &&
                    (double)((AtomicLong)this.serverRequestCountMap.get(serverAddress)).get() >= overloadThread;
                    serverAddress = ((Server)entry.getValue()).getIp() +":"+ entry.getValue().getPort()) {

                // 找到一个最近的，如果没有就开始新的一环
                entry = this.getNextInvokerNode(this.virtualInvokers, entry);
            }

            // 保存访问次数
            if (!this.serverRequestCountMap.containsKey(serverAddress)) {
                this.serverRequestCountMap.put(serverAddress, new AtomicLong(1L));
            } else {
                ((AtomicLong)this.serverRequestCountMap.get(serverAddress)).incrementAndGet();
            }

            this.totalRequestCount.incrementAndGet();
            return entry.getValue();
        }

        private Map.Entry<Long, Server> getNextInvokerNode(TreeMap<Long, Server> virtualInvokers, Map.Entry<Long, Server> entry) {
            // 获取大于指定键值的项，如果不存在，就从第一个开始，这里的作用就是获取一个大于entry的最近的一个entry 如果不存在开启新的一个轮了
            Map.Entry<Long, Server> nextEntry = virtualInvokers.higherEntry(entry.getKey());
            return nextEntry == null ? virtualInvokers.firstEntry() : nextEntry;
        }

        private long hash(byte[] digest, int number) {
            // 这串源码的作用是 把md5值去除高位 拆成4部分之后与 2的32次方取模 得到4个分布值
            // byte[] digest = {60,-15, -74, 11, 122, -68, -124, -79, 100, -110, -34, -27, -48, 87, 33, -60};
            // {60,-15, -74, 11}
            //        long a = 60 & 255 ; 60
            //        long b = -15 & 255 << 8; 65250
            //        long c = -74 & 255 << 16; 16711680
            //        long d = 11 & 255 << 24; 0
            //        System.out.println(a|b|c|d); a+b+c+d = 60 + 65250 + 16711680 + 0 =16777020
            // 然后 16777020 % 4294967295L
            return ((long)(digest[3 + number * 4] & 255) << 24 |
                    (long)(digest[2 + number * 4] & 255) << 16 |
                    (long)(digest[1 + number * 4] & 255) << 8 |
                    (long)(digest[number * 4] & 255))
                    & 4294967295L;
        }
    }


    public static void main(String[] args) {

        List<Server> serverList = new ArrayList<>();
        serverList.add(new Server(1,"服务器1","8080","127.0.0.1",0,0,0,""));
        serverList.add(new Server(2,"服务器2","8090","127.0.0.2",0,0,0,""));
        serverList.add(new Server(3,"服务器3","8088","127.0.0.3",0,0,0,""));
        serverList.add(new Server(4,"服务器4","8099","127.0.0.4",0,0,0,""));
        serverList.add(new Server(5,"服务器5","8070","127.0.0.5",0,0,0,""));
        serverList.add(new Server(6,"服务器6","8060","127.0.0.6",0,0,0,""));
        serverList.add(new Server(7,"服务器7","8050","127.0.0.7",0,0,0,""));



        ConsistentHashLoadBalance hashLoadBalance = new ConsistentHashLoadBalance();
        Invocation invocation = new Invocation();
        invocation.setArguments(new String[]{"loadbalance"});
        ConcurrentMap<String, ConsistentHashSelector<?>> selectors = null;
        for (int i = 0; i < 10; i++) {
            invocation = new Invocation();
            invocation.setArguments(new String[]{""+i});
            // 负载均衡策略的执行，即是在所有的Provider中选出一个，作为当前Consumer的远程调用对象
            System.out.println(hashLoadBalance.doSelect(serverList,invocation).toString());
            selectors = hashLoadBalance.selectors;
        }
        System.out.println(selectors.toString());


    }
}
