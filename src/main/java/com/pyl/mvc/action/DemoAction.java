package com.pyl.mvc.action;

import com.pyl.mvc.service.impl.DemoService;
import com.pyl.mvcframework.webmvc.annotaion.PYLAutowired;
import com.pyl.mvcframework.webmvc.annotaion.PYLController;
import com.pyl.mvcframework.webmvc.annotaion.PYLRequestMapping;
import com.pyl.mvcframework.webmvc.annotaion.PYLRequestParam;

import java.util.IdentityHashMap;

/**
 * @Auther: Administrator
 * @Date: 2018/6/22/022 15:53
 * @Description:
 */
@PYLController
@PYLRequestMapping("/demo")
public class DemoAction {

    @PYLAutowired
    private DemoService demoService;

    @PYLRequestMapping("/add")
    public void add(@PYLRequestParam("name") String name){
        demoService.test(name);
    }

    public static void main(String[] args) {
        IdentityHashMap map = new IdentityHashMap();
        map.put("a","1");
        map.put("ab".substring(0,1),"1");
        map.put(new String("a"),"1");
        map.put(null,"1");
        System.out.println(map.size());
    }
}
