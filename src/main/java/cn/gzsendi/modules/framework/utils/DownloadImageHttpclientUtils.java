package cn.gzsendi.modules.framework.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadImageHttpclientUtils {

	private static Logger logger = LoggerFactory.getLogger(DownloadImageHttpclientUtils.class);
	private static final int CONNECT_TIMEOUT = 10000;// 设置连接建立的超时时间为10000ms
	private static final int SOCKET_TIMEOUT = 30000; // 多少时间没有数据传输
	private static final int HttpIdelTimeout = 30000;//空闲时间
	private static final int HttpMonitorInterval = 10000;//多久检查一次
	private static final int MAX_CONN = 200; // 最大连接数
	private static final int Max_PRE_ROUTE = 200; //设置到路由的最大连接数,
	private static CloseableHttpClient httpClient; // 发送请求的客户端单例
	private static PoolingHttpClientConnectionManager manager; // 连接池管理类
	private static ScheduledExecutorService monitorExecutor;
	
	private final static Object syncLock = new Object(); // 相当于线程锁,用于线程安全
	
	private static RequestConfig requestConfig = RequestConfig.custom()
			.setConnectionRequestTimeout(CONNECT_TIMEOUT)
			.setConnectTimeout(CONNECT_TIMEOUT)
			.setSocketTimeout(SOCKET_TIMEOUT).build();

	private static CloseableHttpClient getHttpClient() {

		if (httpClient == null) {
			// 多线程下多个线程同时调用getHttpClient容易导致重复创建httpClient对象的问题,所以加上了同步锁
			synchronized (syncLock) {
				if (httpClient == null) {
					
					try {
						httpClient = createHttpClient();
					} catch (KeyManagementException e) {
						logger.error("error",e);
					} catch (NoSuchAlgorithmException e) {
						logger.error("error",e);
					} catch (KeyStoreException e) {
						logger.error("error",e);
					}
					
					// 开启监控线程,对异常和空闲线程进行关闭
					monitorExecutor = Executors.newScheduledThreadPool(1);
					monitorExecutor.scheduleAtFixedRate(new TimerTask() {
						@Override
						public void run() {
							
							// 关闭异常连接
							manager.closeExpiredConnections();
							
							// 关闭5s空闲的连接
							manager.closeIdleConnections(HttpIdelTimeout,TimeUnit.MILLISECONDS);
							
							//logger.info(manager.getTotalStats().toString());
							//logger.info("close expired and idle for over "+HttpIdelTimeout+"ms connection");
						}
						
					}, HttpMonitorInterval, HttpMonitorInterval, TimeUnit.MILLISECONDS);
				}
			}
		}
		return httpClient;
	}

	/**
	 * 构建httpclient实例
	 * @return
	 * @throws KeyStoreException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyManagementException 
	 */
	private static CloseableHttpClient createHttpClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
		
		SSLContextBuilder builder = new SSLContextBuilder();
        // 全部信任 不做身份鉴定
        builder.loadTrustMaterial(null, new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                return true;
            }
        });
		
		ConnectionSocketFactory plainSocketFactory = PlainConnectionSocketFactory.getSocketFactory();
		LayeredConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
		Registry<ConnectionSocketFactory> registry = RegistryBuilder
				.<ConnectionSocketFactory> create()
				.register("http", plainSocketFactory)
				.register("https", sslSocketFactory).build();

		manager = new PoolingHttpClientConnectionManager(registry);
		// 设置连接参数
		manager.setMaxTotal(MAX_CONN); // 最大连接数
		manager.setDefaultMaxPerRoute(Max_PRE_ROUTE); // 路由最大连接数

		// 请求失败时,进行请求重试
		HttpRequestRetryHandler handler = new HttpRequestRetryHandler() {
			
			@Override
			public boolean retryRequest(IOException e, int i,	HttpContext httpContext) {
				
				if (i > 3) {
					// 重试超过3次,放弃请求
					logger.error("retry has more than 3 time, give up request");
					return false;
				}
				if (e instanceof NoHttpResponseException) {
					// 服务器没有响应,可能是服务器断开了连接,应该重试
					logger.error("receive no response from server, retry");
					return true;
				}
				if (e instanceof SSLHandshakeException) {
					// SSL握手异常
					logger.error("SSL hand shake exception");
					return false;
				}
				if (e instanceof InterruptedIOException) {
					// 超时
					logger.error("InterruptedIOException");
					return false;
				}
				if (e instanceof UnknownHostException) {
					// 服务器不可达
					logger.error("server host unknown");
					return false;
				}
				if (e instanceof ConnectTimeoutException) {
					// 连接超时
					logger.error("Connection Time out");
					return false;
				}
				if (e instanceof SSLException) {
					logger.error("SSLException");
					return false;
				}

				HttpClientContext context = HttpClientContext.adapt(httpContext);
				HttpRequest request = context.getRequest();
				
				if (!(request instanceof HttpEntityEnclosingRequest)) {
					// 如果请求不是关闭连接的请求
					return true;
				}
				return false;
			}
		};

		CloseableHttpClient client = HttpClients.custom().setConnectionManager(manager).setRetryHandler(handler).build();
		return client;
	}

	/**
	 * 关闭连接池
	 */
	public static void closeConnectionPool() {
		
		if(manager != null) manager.close();
		if(monitorExecutor != null) monitorExecutor.shutdown();
		try {if(httpClient != null) httpClient.close();} catch (IOException e) {logger.error("error",e);}
		
		manager = null;
		monitorExecutor = null;
		httpClient = null;
		
	}
	
	/**
	 * 下载url中指定的图片到path中去，以fileName保存，
	 * @param url
	 * @param fileName
	 * @param path
	 */
	public static void downloadPicture(String imageUrl,String fileName,String path){
		
		
		HttpGet httpGet = new HttpGet(imageUrl);
		httpGet.setHeader("User-Agent","Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36");
		httpGet.setConfig(requestConfig);
		
		CloseableHttpResponse response = null;
		InputStream in = null;

		try {
			
			response = getHttpClient().execute(httpGet,HttpClientContext.create());
			
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				in = entity.getContent();
				
				//如果path传进来是/结束的话处理一下，先去掉。
				path = path.endsWith("/")?path.substring(0,path.lastIndexOf("/")):path;
				FileOutputStream out = new FileOutputStream(new File(path +"/" +fileName));
				IOUtils.copy(in, out);
				out.close();
				
			}
			
		} catch (IOException e) {
			logger.error("error",e);
		} finally {
			try {
				if (in != null) in.close();
			} catch (IOException e) {
				logger.error("error",e);
			}
			
			try {
				if (response != null) response.close();
			} catch (IOException e) {
				logger.error("error",e);
			}
		}
		
		
	}


	public static void main(String[] args) throws InterruptedException {
		
		String url = "https://10.128.18.12:1443/ngx/proxy?i=aHR0cDovLzEwLjEyOC4xOC4xMjY6NjEyMC9waWM/MGRkZTY5ZTZlLTFpMDc3MSo5NDhlPTcxaTRtKmVwPXQ2cDBpPWQxcyppM2QxZCo9KjRiN2k5NmIzYjU3MTA1ODM3LS1iMWI3M2MtMzIwejFhNXM9OTZjaTAxPSZBY2Nlc3NLZXlJZD1zUmJ6NWgxMTZVYU8rdERGJkV4cGlyZXM9MTY0MDk1NzE5MiZTaWduYXR1cmU9UGV5SGZZZEhrTXRlRWJIVURjdTZDREhQVWUwPSZBbGdvcml0aG09MA==";
		String fileName = "11.jpg";
		String path = "D:/temp/";
		DownloadImageHttpclientUtils.downloadPicture(url,fileName,path);
		//关闭连接池，正式环境中这个不要关闭
		DownloadImageHttpclientUtils.closeConnectionPool();
		
	}
}
