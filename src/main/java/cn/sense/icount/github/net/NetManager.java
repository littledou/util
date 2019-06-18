package cn.sense.icount.github.net;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

import cn.sense.icount.github.base.BaseApp;
import cn.sense.icount.github.util.DLog;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class NetManager {

    private static String token = "";

    static Map<String, String> heads = new HashMap<>();

    private Retrofit retrofit;

    private boolean needCache = true;

    public static final String MEDIA_TYPE_TEXT = "text/plain";
    public static final String MEDIA_TYPE_JSON = "application/json;charset=utf-8";
    public static final String MEDIA_TYPE_IMAGE = "image/*";
    public static final String MEDIA_TYPE_FORM = "multipart/form-data";
    public static final String ASYNC = "async";

    public NetManager(boolean needCache) {
        this.needCache = needCache;
    }

    public void addHeads(String key, String value) {
        heads.put(key, value);
    }

    public Retrofit initRetrofit(String api_base) {
        this.retrofit = (new Retrofit.Builder()).baseUrl(api_base)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(defaultOkHttpClient())
                .build();

        return retrofit;
    }

    private OkHttpClient defaultOkHttpClient() {
        OkHttpClient.Builder client = new OkHttpClient.Builder();
        client.writeTimeout(30 * 1000, TimeUnit.MILLISECONDS);
        client.readTimeout(20 * 1000, TimeUnit.MILLISECONDS);
        client.connectTimeout(15 * 1000, TimeUnit.MILLISECONDS);
        if(needCache) {
            //设置缓存路径
            File httpCacheDirectory = new File(BaseApp.getAppContext().getCacheDir(), "okhttpCache");
            //设置缓存 10M
            Cache cache = new Cache(httpCacheDirectory, 10 * 1024 * 1024);
            client.cache(cache);
            client.addInterceptor(REWRITE_CACHE_CONTROL_INTERCEPTOR);
        }else{
            client.addInterceptor(NO_CACHE_CONTROL_INTERCEPTOR);
        }
        //设置拦截器
//        client.addNetworkInterceptor(REWRITE_CACHE_CONTROL_INTERCEPTOR);
//        client.addInterceptor(REWRITE_CACHE_CONTROL_INTERCEPTOR);

        client.addInterceptor(LOGGING_INTERCEPTOR);

        SSLSocketFactory sslSocketFactory = HttpsUtils.getSslSocketFactory(null, null, null);
        client.sslSocketFactory(sslSocketFactory)
                .hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });
        return client.build();
    }

    private static final Interceptor LOGGING_INTERCEPTOR = new Interceptor() {
        @Override
        public Response intercept(Interceptor.Chain chain) throws IOException {
            Request request = chain.request();
            long t1 = System.nanoTime();
            okhttp3.Response response = chain.proceed(chain.request());
            long t2 = System.nanoTime();
            okhttp3.MediaType mediaType = response.body().contentType();
            String content = response.body().string();
            DLog.i("-----LoggingInterceptor----- :\nrequest url:" + request.url() + "\ntime:" + (t2 - t1) / 1e6d + "\nbody:" + content + "\n");
            return response.newBuilder()
                    .body(okhttp3.ResponseBody.create(mediaType, content))
                    .build();
        }
    };

    private static final Interceptor REWRITE_CACHE_CONTROL_INTERCEPTOR = new Interceptor() {

        @Override
        public Response intercept(Interceptor.Chain chain) throws IOException {
            //方案一：有网和没有网都是先读缓存
//                Request request = chain.request();
//                Log.i(TAG, "request=" + request);
//                Response response = chain.proceed(request);
//                Log.i(TAG, "response=" + response);
//
//                String cacheControl = request.cacheControl().toString();
//                if (TextUtils.isEmpty(cacheControl)) {
//                    cacheControl = "public, max-age=60";
//                }
//                return response.newBuilder()
//                        .header("Cache-Control", cacheControl)
//                        .removeHeader("Pragma")
//                        .build();

            //方案二：无网读缓存，有网根据过期时间重新请求

            Request request = chain.request();
            Request.Builder builder = request.newBuilder();

            boolean netWorkConnection = NetUtils.hasNetWorkConection(BaseApp.getAppContext());
            if (!netWorkConnection) {
                builder.cacheControl(CacheControl.FORCE_CACHE);
            }

            builder.header("Accept", "application/json");

            if (heads != null && heads.size() > 0) {
                final Set<String> keys = heads.keySet();
                for (String key : keys) {
                    builder.header(key, heads.get(key));
                }
            }

            request = builder.build();

            Response response = chain.proceed(request);

            if (netWorkConnection) {
                //有网的时候读接口上的@Headers里的配置，你可以在这里进行统一的设置
                String cacheControl = request.cacheControl().toString();
                response.newBuilder()
                        .removeHeader("Pragma")// 清除头信息，因为服务器如果不支持，会返回一些干扰信息，不清除下面无法生效
                        .header("Cache-Control", cacheControl)
                        .build();
            } else {
                int maxStale = 60 * 60 * 24 * 7;
                response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, only-if-cached, max-stale=" + maxStale)
                        .build();
            }
            return response;
        }
    };

    private static final Interceptor NO_CACHE_CONTROL_INTERCEPTOR = new Interceptor() {

        @Override
        public Response intercept(Interceptor.Chain chain) throws IOException {
            Request request = chain.request();
            Request.Builder builder = request.newBuilder();
            builder.header("Accept", "application/json");

            CacheControl.Builder cacheBuilder = new CacheControl.Builder();
            CacheControl cacheControl = cacheBuilder.noCache().noStore().build();
            request = builder.cacheControl(cacheControl).build();

            return chain.proceed(request);
        }
    };
}
