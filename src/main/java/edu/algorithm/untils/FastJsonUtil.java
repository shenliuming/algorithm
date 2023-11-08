package edu.algorithm.untils;

import com.alibaba.fastjson.JSONObject;

public class FastJsonUtil {


    public static void main(String[] args) {
        Object o = JSONObject.parseObject("{id:1}",Object.class);
    }
}
