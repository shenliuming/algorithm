//package edu.algorithm.loadbalance;
//
//import edu.algorithm.entity.Invocation;
//import edu.algorithm.entity.NamedThreadFactory;
//import edu.algorithm.entity.RpcStatus;
//import edu.algorithm.entity.Server;
//
//import java.util.ArrayList;
//import java.util.Iterator;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ThreadLocalRandom;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//public class AdaptivePowerOfTwoChoice  {
//
//    public static final String NAME = "adaptiveP2C";
//
//    private int slidePeriod = 30000;
//
//    private ExecutorService executorService = Executors
//            .newCachedThreadPool(new NamedThreadFactory("Dubbo-framework-shared-handler-adaptiveP2C", true));
//
//    private volatile long lastUpdateTime = System.currentTimeMillis();
//
//    private AtomicBoolean onResetSlideWindow = new AtomicBoolean(false);
//
//    private ConcurrentHashMap<RpcStatus,SlideWindowData> methodMap = new ConcurrentHashMap<>();
//
//
//    protected static class SlideWindowData{
//
//        private RpcStatus rpcStatus;
//
//        private long totalOffset;
//
//        private long succeedOffset;
//
//        private long totalElapsedOffset;
//
//        private long succeedElapsedOffset;
//
//        public SlideWindowData(RpcStatus rpcStatus){
//            this.rpcStatus = rpcStatus;
//            this.totalOffset = 0;
//            this.totalElapsedOffset = 0;
//            this.succeedElapsedOffset = 0;
//            this.succeedOffset = 0;
//        }
//
//        public void reset(){
//            this.totalOffset = rpcStatus.getTotal();
//            this.succeedOffset = rpcStatus.getSucceeded();
//            this.totalElapsedOffset = rpcStatus.getTotalElapsed();
//            this.succeedElapsedOffset = rpcStatus.getSucceededElapsed();
//        }
//
//        public double getLatency(){
//            if((this.rpcStatus.getSucceeded() - this.succeedOffset) == 0){
//                return 0;
//            }
//            return (double) (this.rpcStatus.getSucceededElapsed() - this.succeedElapsedOffset)/(this.rpcStatus.getSucceeded() - this.succeedOffset);
//        }
//
//        public long getAccept(){
//            return (this.rpcStatus.getSucceeded() - this.succeedOffset);
//        }
//
//        private long getRequest(){
//            return (this.rpcStatus.getTotal() - this.totalOffset);
//        }
//
//    }
//
//    private double getNormlize(double x){
//        return x/(1 + x);
//    }
//
//    public double getWeight(Server server, Invocation invocation, double averageLatency){
//        // 用 IP地址存储 每个服务的 访问次数和统计情况
//        RpcStatus rpcStatus = RpcStatus.getStatus(server.getIp());
//        SlideWindowData slideWindowData = methodMap.get(rpcStatus);
//        double latency = (1 + averageLatency)/(1 + slideWindowData.getLatency());
//
//        return (1 + slideWindowData.getAccept())/(1 + slideWindowData.getRequest()) * getNormlize(latency);
//    }
//
//    private  Server leastWeight(Server invoker1,Server  invoker2,Invocation invocation,double averageLatency){
//        double weight1 = getWeight(invoker1,invocation,averageLatency);
//        double weight2 = getWeight(invoker2,invocation,averageLatency);
//        if(weight1 >= weight2)
//            return invoker1;
//        return invoker2;
//    }
//
//    public <T> double getAverageLatency(List<Server> invokers){
//        double averageLatency = 0;
//        double totalLatency = 0;
//        int length = invokers.size();
//        for(int i = 0; i < length; i++){
//            RpcStatus rpcStatus = RpcStatus.getStatus(invokers.get(i).getIp());
//            SlideWindowData slideWindowData = methodMap.computeIfAbsent(rpcStatus,SlideWindowData::new);
//            totalLatency += slideWindowData.getLatency();
//        }
//        averageLatency = totalLatency/length;
//        return averageLatency;
//    }
//
//
//    protected Server select(List<Server> invokers, Invocation invocation) {
//        // 获取服务实例 数组长度
//        int length = invokers.size();
//        if(length == 1)
//            return invokers.get(0);
//
//        double averageLatency = 0;
//        double totalLatency = 0;
//
//        while(!onResetSlideWindow.compareAndSet(false,true)){}
//
//        // 为每个服务实例都创建一RpcStatus 窗口，记录
//        for(int i = 0; i < length; i++){
//            RpcStatus rpcStatus = RpcStatus.getStatus(invokers.get(i).getIp());
//            SlideWindowData slideWindowData = methodMap.computeIfAbsent(rpcStatus,SlideWindowData::new);
//            totalLatency += slideWindowData.getLatency();
//        }
//        onResetSlideWindow.set(false);
//
//        averageLatency = totalLatency/length;
//
//        if(length == 2)
//            return leastWeight(invokers.get(0),invokers.get(1),invocation,averageLatency);
//
//        int pos1 = ThreadLocalRandom.current().nextInt(length);
//        int pos2 = ThreadLocalRandom.current().nextInt(length - 1);
//
//        if(pos2 >= pos1){
//            pos2 = pos2 + 1;
//        }
//
//        Server result = leastWeight(invokers.get(pos1),invokers.get(pos2),invocation,averageLatency);
//
//        if(System.currentTimeMillis() - lastUpdateTime > slidePeriod && onResetSlideWindow.compareAndSet(false,true)){
//                    executorService.execute(() -> {
//                        methodMap.values().forEach(SlideWindowData::reset);
//                        lastUpdateTime = System.currentTimeMillis();
//                        onResetSlideWindow.set(false);
//                    });
//        }
//        return result;
//    }
//
//
//    public static void main(String[] args) {
//        List<Server> serverList = new ArrayList<>();
//        serverList.add(new Server(1,"服务器1","8080","127.0.0.1",90,20,0,""));
//        serverList.add(new Server(2,"服务器2","8090","127.0.0.2",90,20,0,""));
//        serverList.add(new Server(3,"服务器3","8088","127.0.0.3",80,30,0,""));
//        serverList.add(new Server(4,"服务器4","8099","127.0.0.4",80,30,0,""));
//        serverList.add(new Server(5,"服务器5","8070","127.0.0.5",70,35,0,""));
//        serverList.add(new Server(6,"服务器6","8060","127.0.0.6",88,28,0,""));
//        serverList.add(new Server(7,"服务器7","8050","127.0.0.7",78,38,0,""));
//
//
//
//        AdaptivePowerOfTwoChoice powerOfTwoChoice = new AdaptivePowerOfTwoChoice();
//        Invocation invocation = new Invocation();
//        invocation.setArguments(new String[]{"454545"});
//
//        for (int i = 0; i < 1000; i++) {
//            Server server = powerOfTwoChoice.select(serverList,invocation);
//            System.out.println(server.toString());
//
//            RpcStatus.beginCount(server.getIp());
//        }
//
//        Iterator iterator = RpcStatus.SERVICE_STATISTICS.entrySet().iterator();
//        while (iterator.hasNext()){
//            Map.Entry<String,RpcStatus> entry = (Map.Entry<String, RpcStatus>) iterator.next();
//            System.out.println("Key: "+entry.getKey()+" Value: "+entry.getValue().toString() +  " avg:" + entry.getValue().getAverageElapsed());
//        }
//
//    }
//
//}
