package edu.algorithm.entity;

import org.apache.dubbo.common.utils.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class AdaptiveMetrics {

    public final ConcurrentMap<String, AdaptiveMetrics> metricsStatistics = new ConcurrentHashMap<>();
    //
    public long currentProviderTime = 0;
    // 是在 ProfilerServerFilter 的 onResponse 方法中经过计算得到的 cpu load  依赖其他组件计算出来的 CPU负载
    public double providerCPULoad = 0;
    // 是在 setProviderMetrics 方法里面维护的，其中 lastLatency 是在 ProfilerServerFilter 的 onResponse 方法中经过计算得到的 rt 值
    // /'leɪtənsɪ/
    public long lastLatency = 0;
    //  当前时间
    public long currentTime = 0;

    //Allow some time disorder
    public long pickTime = System.currentTimeMillis();

    public double beta = 0.5;
    // 是总的调用次数
    public final AtomicLong consumerReq = new AtomicLong();
    // 是在每次调用成功后在 AdaptiveLoadBalanceFilter 的 onResponse 方法中维护的值。
    public final AtomicLong consumerSuccess = new AtomicLong();
    // 每次出现调用异常时，维护的值
    public final AtomicLong errorReq = new AtomicLong();
    // 这是一个公式算出来的值 Vt = β * Vt-1 + (1 - β ) * θt
    // 指数加权移动平均值的控制图 (exponentially weighted moving average) ,  EWMA主要用于对网络的状态参数进行估计和平滑 , 负责得到 当前服务器的“平滑负载指标”
    // 有两个特点，1.不需要保存过去所有数值，2.计算量显著减少
    public double ewma = 0;

    public double getLoad(String idKey, int weight, int timeout) {
        AdaptiveMetrics metrics = getStatus(idKey);

        //If the time more than 2 times, mandatory selected 如果超时时间超过 2次，则强制选择
        if (System.currentTimeMillis() - metrics.pickTime > timeout * 2) {
            return 0;
        }

        if (metrics.currentTime > 0) {
            long multiple = (System.currentTimeMillis() - metrics.currentTime) / timeout + 1;
            if (multiple > 0) {
                if (metrics.currentProviderTime == metrics.currentTime) {
                    //penalty value
                    metrics.lastLatency = timeout * 2L;
                } else {
                    metrics.lastLatency = metrics.lastLatency >> multiple;
                }
                metrics.ewma = metrics.beta * metrics.ewma + (1 - metrics.beta) * metrics.lastLatency;
                metrics.currentTime = System.currentTimeMillis();
            }
        }

        long inflight = metrics.consumerReq.get() - metrics.consumerSuccess.get() - metrics.errorReq.get();
        // Vt = β * Vt-1 + (1 - β ) * θt
        //  服务器的CPU负载 乘以 ( (响应时间) * 当前正在处理的请求数 + 1) / ( 请求成功次数 ） / (( 请求次数 +1）*权重 +1)
        return metrics.providerCPULoad * (Math.sqrt(metrics.ewma) + 1) * (inflight + 1) / ((((double) metrics.consumerSuccess.get() / (double) (metrics.consumerReq.get() + 1)) * weight) + 1);
    }

    public AdaptiveMetrics getStatus(String idKey) {
        return ConcurrentHashMapUtils.computeIfAbsent(metricsStatistics, idKey, k -> new AdaptiveMetrics());
    }

    public void addConsumerReq(String idKey) {
        AdaptiveMetrics metrics = getStatus(idKey);
        metrics.consumerReq.incrementAndGet();
    }

    public void addConsumerSuccess(String idKey) {
        AdaptiveMetrics metrics = getStatus(idKey);
        metrics.consumerSuccess.incrementAndGet();
    }

    public void addErrorReq(String idKey) {
        AdaptiveMetrics metrics = getStatus(idKey);
        metrics.errorReq.incrementAndGet();
    }

    public void setPickTime(String idKey, long time) {
        AdaptiveMetrics metrics = getStatus(idKey);
        metrics.pickTime = time;
    }


    public void setProviderMetrics(String idKey, Map<String, String> metricsMap) {

        AdaptiveMetrics metrics = getStatus(idKey);

        long serviceTime = Long.parseLong(Optional.ofNullable(metricsMap.get("curTime")).filter(v -> StringUtils.isNumeric(v, false)).orElse("0"));
        //If server time is less than the current time, discard
        if (metrics.currentProviderTime > serviceTime) {
            return;
        }

        metrics.currentProviderTime = serviceTime;
        metrics.currentTime = serviceTime;
        metrics.providerCPULoad = Double.parseDouble(Optional.ofNullable(metricsMap.get("load")).filter(v -> StringUtils.isNumeric(v, true)).orElse("0"));
        metrics.lastLatency = Long.parseLong((Optional.ofNullable(metricsMap.get("rt")).filter(v -> StringUtils.isNumeric(v, false)).orElse("0")));

        metrics.beta = 0.5;
        metrics.ewma = metrics.beta * metrics.ewma + (1 - metrics.beta) * metrics.lastLatency;
    }
}