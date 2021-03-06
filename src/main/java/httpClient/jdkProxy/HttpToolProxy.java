package httpClient.jdkProxy;

import httpClient.client.HttpClientManager;
import httpClient.connection.SocksProxyHttpContext;
import httpClient.connection.SocksProxyRule;
import httpClient.expcetion.HttpToolException;
import httpClient.response.HttpResoponseHandler;
import httpClient.factory.reqeustBuilder.HttpReqesutBuilderStaticFactory;
import httpClient.factory.reqeustBuilder.HttpRequestBuilder;
import httpClient.request.HttpRequestConfig;
import httpClient.request.HttpRequestConfigAdapter;
import httpClient.request.HttpRequestConfigParser;
import httpClient.request.HttpRequestCustomConfig;
import httpClient.response.ReturnTypeResolverStaticFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spring.PropertiesResolver;
import util.LogUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Date;


public class HttpToolProxy<T> implements InvocationHandler {

    private static Logger logger = LoggerFactory.getLogger(HttpToolProxy.class);

    private Class<T> httpToolInterface;
    // 默认的httpClient
    private HttpClientManager httpClientManager;
    // resolve properties
    private PropertiesResolver propertiesResolver;
    // reponse handle
    private HttpResoponseHandler resoponseHandler;

    private SocksProxyRule proxyRule;

    public HttpToolProxy(Class<T> httpToolInterface,
                         HttpClientManager httpClientManager,
                         PropertiesResolver propertiesResolver,
                         HttpResoponseHandler resoponseHandler,
                         SocksProxyRule proxyRule) {

        Asserts.notNull(httpToolInterface, "httpToolInterface");
        Asserts.notNull(httpClientManager, "httpClientManager");
        Asserts.notNull(propertiesResolver, "propertiesResolver");
        Asserts.notNull(resoponseHandler, "resoponseHandler");

        this.httpToolInterface = httpToolInterface;
        this.httpClientManager = httpClientManager;
        this.propertiesResolver = propertiesResolver;
        this.resoponseHandler = resoponseHandler;
        this.proxyRule = proxyRule;
    }

    /**
     * 如果执行过程中出现异常，则继续向外抛出
     *
     * @param proxy
     * @param method
     * @param args
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        HttpRequestCustomConfig customConfig = HttpRequestConfigParser.parse(httpToolInterface, method, args);
        HttpRequestConfig httpRequestConfig = new HttpRequestConfigAdapter(customConfig, propertiesResolver);
        HttpRequestBuilder requestBuilder = HttpReqesutBuilderStaticFactory.createHttpRequestBuilder(httpRequestConfig);
        HttpRequestBase httpRequest = requestBuilder.build();
        HttpContext context = needToUseSocksProxy(httpRequest.getURI())
                ? SocksProxyHttpContext.create(proxyRule)
                : HttpClientContext.create();

        CloseableHttpClient httpClient = httpClientManager.getHttpClient();
        if (httpClient == null) {
            throw new HttpToolException("http client is null");
        }
        HttpResponse httpResponse = null;
        try {
            Date startTime = new Date();
            httpResponse = httpClient.execute(httpRequest, context);
            Date endTime = new Date();
            String str = resoponseHandler.handle(method, httpRequest, httpResponse);
            /**
             * print http info
             */
            LogUtil.debug(logger, "[sendTime]{} - [receiveTime]{}\n[request]{}\n[httpProxy]{}\n[header]{}\n[param]{}\n[body]{}\n[response]{}\n",
                    String.format("%tF %<tH:%<tM:%<tS.%<tL", startTime),
                    String.format("%tF %<tH:%<tM:%<tS.%<tL", endTime),
                    httpRequest,
                    httpRequestConfig.getHttpProxy(),
                    httpRequest.getAllHeaders(),
                    httpRequestConfig.getParameters(),
                    httpRequestConfig.getReqBody(),
                    str);
            Type returnType = method.getReturnType();
            return ReturnTypeResolverStaticFactory.getReturnTypeResolver(returnType).resolve(str, returnType);
        } catch (Throwable e) {
            throw e;
        }
    }

    private Boolean needToUseSocksProxy(URI uri) {
        return uri != null && proxyRule != null && proxyRule.checkUseProxy(uri);
    }
}
