package com.pyl.mvcframework.webmvc.servlet;

import com.pyl.mvcframework.webmvc.annotaion.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @Auther: Administrator
 * @Date: 2018/6/22/022 16:40
 * @Description:
 */
public class PYLDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    //存放扫描的类
    private List<String> classNames = new ArrayList<String>();

    //ioc容器
    private Map<String,Object> ioc = new HashMap<String, Object>();

    //HandleMapping容器
    private List<Handle> handleMappingList = new ArrayList<Handle>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //6、等待请求
        try {
            doDispatch(req,resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {

        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();
        uri = uri.replace(contextPath,"").replaceAll("/+","/");

        Boolean flag = false;
        for (Handle handle : handleMappingList) {
            if (uri.equals(handle.url)){
                flag =true;
                Method m = handle.method;
                System.out.println("本次请求的方法："+m);


                //方法调用
                Map parameterMap = req.getParameterMap();
                if (parameterMap.isEmpty()&&handle.paramMap.isEmpty()){
                    m.invoke(handle.controller,null);
                }else {
                    Map<String, Integer> paramMap = handle.paramMap;
                    String[] params = new String[paramMap.size()];
                    if (!paramMap.isEmpty()){
                        for (Map.Entry<String, Integer> entry : paramMap.entrySet()) {
                            if (parameterMap.containsKey(entry.getKey())){
                                Object value = parameterMap.get(entry.getKey());
                                params[entry.getValue()] = ((String[]) parameterMap.get(entry.getKey()))[0];
                            }
                        }
                    }
                    m.invoke(handle.controller,params);
                }
            }
        }
        if (!flag){
            resp.getWriter().println("404 method not Found");
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("=================================PYLDispatcherServlet init success");

        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2、扫描类
        doScanner(properties.getProperty("scanPackage"));
        //3、初始化扫描到的类
        doInstance();
        //4、实现依赖注入
        doAutowired();
        //5、初始化HandleMapping
        doInitHandleMapping();
    }

    private void doInitHandleMapping() {
        if (ioc.isEmpty())
            return;
        //对Controller的进行处理
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //controller实例
            Object controller = entry.getValue();
            Class<?> clazz = controller.getClass();

            //加注解的
            if (!clazz.isAnnotationPresent(PYLController.class))
                continue;

            String controllerUrl ="";
            if (clazz.isAnnotationPresent(PYLRequestMapping.class)){
                PYLRequestMapping requestMapping = clazz.getAnnotation(PYLRequestMapping.class);
                controllerUrl = requestMapping.value().trim();
            }
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(PYLRequestMapping.class)){
                    PYLRequestMapping methodMapping = method.getAnnotation(PYLRequestMapping.class);
                    String methodUrl = methodMapping.value().trim();
                    methodUrl = controllerUrl+methodUrl;
                    System.out.println("mapped： "+methodUrl+"，"+method);

                    Handle handle = new Handle(method,controller,methodUrl);
                    handleMappingList.add(handle);
                }
            }
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()){return;}
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {

            //不管私有还是公有，都进行注入
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(PYLAutowired.class))
                    continue;
                PYLAutowired autowired = field.getAnnotation(PYLAutowired.class);
                String beanName = autowired.value().trim();
                if ("".equals(beanName)){
                    beanName = field.getType().getName();
                    beanName = beanName.substring(beanName.lastIndexOf(".")+1);
                    beanName = lowerFirstCase(beanName);
                }
                field.setAccessible(true);

                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()){
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                //初始化有注解的类
                if (clazz.isAnnotationPresent(PYLController.class)){
                    Object obj = clazz.newInstance();
                    //放入IOC容器
                    //key值默认类名的首字母小写
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,obj);
                }else if (clazz.isAnnotationPresent(PYLService.class)){

                    //1、默认首字母小写
                    //2、如果是接口，要把实现类赋值给它
                    //3、如果name自定义，优先使用自定义
                    PYLService service = clazz.getAnnotation(PYLService.class);
                    String beanName = service.value();
                    if ("".equals(beanName.trim())){
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);

                    //解决子类引用赋值给父类的问题
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()){
                doScanner(scanPackage+"."+file.getName());
            }else {
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        if (contextConfigLocation.contains(":")){
            contextConfigLocation = contextConfigLocation.substring(contextConfigLocation.indexOf(":")+1);
        }
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (in != null){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private class Handle{
        protected Method method;//映射的方法
        protected Object controller;//方法对应的实例
        protected String url;
        protected Map<String,Integer> paramMap;//方法中加注解的参数


        public Handle(Method method, Object controller,String url) {
            this.method = method;
            this.controller = controller;
            this.url = url;
            paramMap = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            //提取方法中加了注解的参数
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i <parameterAnnotations.length ; i++) {
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof PYLRequestParam){
                        String paramName = ((PYLRequestParam) annotation).value();
                        if (!"".equals(paramName.trim())){
                            paramMap.put(paramName,i);
                        }
                    }
                }
            }
        }
    }
}
