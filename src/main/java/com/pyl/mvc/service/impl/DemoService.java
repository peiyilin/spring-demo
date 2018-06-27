package com.pyl.mvc.service.impl;

import com.pyl.mvc.service.IDemoService;
import com.pyl.mvcframework.webmvc.annotaion.PYLService;

/**
 * @Auther: Administrator
 * @Date: 2018/6/25/025 20:36
 * @Description:
 */
@PYLService
public class DemoService implements IDemoService {
    public void test(String name) {
        System.out.println("调用方法成功！name="+name);
    }
}
