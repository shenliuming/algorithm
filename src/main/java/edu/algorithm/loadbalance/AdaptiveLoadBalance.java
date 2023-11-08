package edu.algorithm.loadbalance;

import com.alibaba.dubbo.rpc.RpcInvocation;
import edu.algorithm.entity.AdaptiveMetrics;

import edu.algorithm.entity.Invocation;
import edu.algorithm.entity.Server;
import org.apache.dubbo.common.constants.LoadbalanceRules;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Constants;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.apache.dubbo.common.constants.CommonConstants.*;

public class AdaptiveLoadBalance  {

    public static final String NAME = "adaptive";

    // default key
    private String attachmentKey = "mem,load";

    private final AdaptiveMetrics adaptiveMetrics;

    private  int DEFAULT_TIMEOUT = 1000;

    public AdaptiveLoadBalance(){
        adaptiveMetrics = new AdaptiveMetrics();
    }

    protected Server doSelect(List<Server> invokers, Invocation invocation) {
        // 通过P2C 算法 选择一个服务器
        Server invoker = selectByP2C(invokers,invocation);
        // 设置计算负载的参数
        invocation.setAttachment(Constants.ADAPTIVE_LOADBALANCE_ATTACHMENT_KEY,attachmentKey);
        // 请求开始处理，记录请求开始处理时间
        long startTime = System.currentTimeMillis();
        // 通过 invocation 传递开始时间，用于计算负载和吞吐量
        invocation.getAttributes().put(Constants.ADAPTIVE_LOADBALANCE_START_TIME,startTime);
        // 设置负载均衡器的 自适应算法
        invocation.getAttributes().put(LOADBALANCE_KEY, LoadbalanceRules.ADAPTIVE);
        // 保存消费者
        adaptiveMetrics.addConsumerReq(getServiceKey(invoker,invocation));
        // 保存选择的开始时间
        adaptiveMetrics.setPickTime(getServiceKey(invoker,invocation),startTime);

        return invoker;
    }

    /**
     * 根据服务列列表 随机选择两个服务器，然后对比负载情况，选择负载较轻的一台
     * @param invokers
     * @param invocation
     * @return
     */
    private  Server selectByP2C(List<Server> invokers, Invocation invocation){
        int length = invokers.size();
        if(length == 1) {
            return invokers.get(0);
        }

        if(length == 2) {
            return chooseLowLoadInvoker(invokers.get(0),invokers.get(1),invocation);
        }
        // 随机选择两个服务器
        int pos1 = ThreadLocalRandom.current().nextInt(length);
        int pos2 = ThreadLocalRandom.current().nextInt(length - 1);
        if (pos2 >= pos1) {
            pos2 = pos2 + 1;
        }
        // 动态计算两个服务器的负载情况，选择负载较轻的服务器
        return chooseLowLoadInvoker(invokers.get(pos1),invokers.get(pos2),invocation);
    }

    private String getServiceKey(Server invoker,Invocation invocation){

        String key = (String) invocation.getAttributes().get(invoker);
        if (StringUtils.isNotEmpty(key)){
            return key;
        }

        key = buildServiceKey(invoker,invocation);
        invocation.getAttributes().put(invoker,key);
        return key;
    }

    private String buildServiceKey(Server invoker,Invocation invocation){
//        URL url = invoker.getUrl();
//        StringBuilder sb = new StringBuilder(128);
//        sb.append(url.getAddress()).append(":").append(invocation.getProtocolServiceKey());
        // 目的是拿到一个 服务器的key ，这里默认拿ip地址
        return invoker.getIp();
    }

    private int getTimeout(Server invoker, Invocation invocation) {
//        URL url = invoker.getUrl();
//        String methodName = RpcUtils.getMethodName(invocation);
//        return (int) RpcUtils.getTimeout(url,methodName, RpcContext.getClientAttachment(),invocation, DEFAULT_TIMEOUT);
//
        // 目的是拿到一个 超时时间， 这里默认拿服务器的默认配置
        return invoker.getTimeOut();
    }

    private Server chooseLowLoadInvoker(Server invoker1,Server invoker2,Invocation invocation){
        int weight1 = invoker1.getWeight();
        int weight2 = invoker2.getWeight();
        int timeout1 = getTimeout(invoker1, invocation);
        int timeout2 = getTimeout(invoker2, invocation);
        long load1 = Double.doubleToLongBits(adaptiveMetrics.getLoad(getServiceKey(invoker1,invocation),weight1,timeout1 ));
        long load2 = Double.doubleToLongBits(adaptiveMetrics.getLoad(getServiceKey(invoker2,invocation),weight2,timeout2 ));

        // 负载相同的情况下
        if (load1 == load2) {
            // The sum of weights
            int totalWeight = weight1 + weight2;
            if (totalWeight > 0) {
                // 根据权重随机选择
                int offset = ThreadLocalRandom.current().nextInt(totalWeight);
                if (offset < weight1) {
                    return invoker1;
                }
                return invoker2;
            }
            // 默认权重为0时 随机选择
            return ThreadLocalRandom.current().nextInt(2) == 0 ? invoker1 : invoker2;
        }
        // 根据负载情况选择服务器，负载低的 被选择。
        return load1 > load2 ? invoker2 : invoker1;
    }

    public static void main(String[] args) {
        List<Server> serverList = new ArrayList<>();
        serverList.add(new Server(1,"服务器1","8080","127.0.0.1",90,0,0,"",2000));
        serverList.add(new Server(2,"服务器2","8090","127.0.0.2",80,0,0,"",3000));
        serverList.add(new Server(3,"服务器3","8088","127.0.0.3",70,0,0,"",3000));
        serverList.add(new Server(4,"服务器4","8099","127.0.0.4",70,0,0,"",3000));
        serverList.add(new Server(5,"服务器5","8070","127.0.0.5",80,0,0,"",3000));
        serverList.add(new Server(6,"服务器6","8060","127.0.0.6",70,0,0,"",4000));
        serverList.add(new Server(7,"服务器7","8050","127.0.0.7",80,0,0,"",5000));

        AdaptiveLoadBalance adaptiveLoadBalance = new AdaptiveLoadBalance();


        Invocation invocation = new Invocation();
        for (int i = 0; i < 1000; i++) {
            // 负载均衡策略的执行，即是在所有的Provider中选出一个，作为当前Consumer的远程调用对象
            System.out.println(adaptiveLoadBalance.doSelect(serverList,invocation).toString());
        }
        Iterator iterator = adaptiveLoadBalance.adaptiveMetrics.metricsStatistics.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<String, AdaptiveMetrics> entry = (Map.Entry<String, AdaptiveMetrics>) iterator.next();
            System.out.println("Key: "+entry.getKey()+" consumerReq: "+entry.getValue().consumerReq  );
        }
    }

}