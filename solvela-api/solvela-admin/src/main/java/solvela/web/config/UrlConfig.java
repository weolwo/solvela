package solvela.web.config;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.web.AllowAnonymous;
import solvela.web.RequiresPermission;
import solvela.base.util.SolvelaCollectionUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * url配置
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2022-05-30 21:22:12
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Configuration
@Slf4j
public class UrlConfig {

    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 获取每个方法的请求路径
     */
    @Bean
    public Map<Method, Set<String>> methodUrlMap() {
        Map<Method, Set<String>> methodUrlMap = new HashMap<>();
        //获取url与类和方法的对应信息
        Map<RequestMappingInfo, HandlerMethod> map = requestMappingHandlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : map.entrySet()) {
            RequestMappingInfo requestMappingInfo = entry.getKey();
           PathPatternsRequestCondition pathPatternsCondition = requestMappingInfo.getPathPatternsCondition();
            if(pathPatternsCondition == null){
               continue;
            }

            Set<String> urls = pathPatternsCondition.getPatternValues();
            if (SolvelaCollectionUtil.isEmpty(urls)) {
                continue;
            }
            HandlerMethod handlerMethod = entry.getValue();
            methodUrlMap.put(handlerMethod.getMethod(), urls);
        }
        return methodUrlMap;
    }

    /**
     * 需要进行 url 权限校验的方法：菜单配置页拿它当「接口地址」下拉。
     *
     * <p>判据从「没标 @SaIgnore 且没标 @AllowAnonymous」改成「标了 @RequiresPermission」——
     * 这是一次收窄，而且是对的：原先只要没显式忽略就会被列进来，于是下拉里混着
     * 一堆本来就不做权限校验的接口（比如各种下拉数据源），运营给菜单挂上去也不会生效。
     *
     * @param methodUrlMap
     * @return
     */
    @Bean
    public List<RequestUrl> authUrl(Map<Method, Set<String>> methodUrlMap) {
        List<RequestUrl> authUrlList = new ArrayList<>();
        for (Map.Entry<Method, Set<String>> entry : methodUrlMap.entrySet()) {
            Method method = entry.getKey();
            if (method.getAnnotation(RequiresPermission.class) == null) {
                continue;
            }
            Set<String> urlSet = entry.getValue();
            List<RequestUrl> requestUrlList = this.buildRequestUrl(method, urlSet);
            authUrlList.addAll(requestUrlList);
        }
        return authUrlList;
    }

    private List<RequestUrl> buildRequestUrl(Method method, Set<String> urlSet) {
        List<RequestUrl> requestUrlList = new ArrayList<>();
        if (SolvelaCollectionUtil.isEmpty(urlSet)) {
            return requestUrlList;
        }
        //url对应的方法名称
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        String controllerName = className.substring(className.lastIndexOf('.') + 1);
        String name = controllerName + "." + methodName;
        //swagger 说明信息
        String methodComment = null;
        Operation apiOperation = method.getAnnotation(Operation.class);
        if (apiOperation != null) {
            methodComment = apiOperation.summary();
        }
        for (String url : urlSet) {
            requestUrlList.add(new RequestUrl(methodComment, name, url));
        }
        return requestUrlList;
    }


    /**
     * 获取无需登录可以匿名访问的url信息
     *
     * @return
     */
    @Bean
    public List<String> allowAnonymousUrlList(Map<Method, Set<String>> methodUrlMap) {
        List<String> allowAnonymousUrlList = new ArrayList<>();
        for (Map.Entry<Method, Set<String>> entry : methodUrlMap.entrySet()) {
            Method method = entry.getKey();
            AllowAnonymous allowAnonymous = method.getAnnotation(AllowAnonymous.class);
            if (null == allowAnonymous) {
                continue;
            }
            allowAnonymousUrlList.addAll(entry.getValue());
        }
        log.info("不需要登录的URL：{}", allowAnonymousUrlList);
        return allowAnonymousUrlList;
    }


}