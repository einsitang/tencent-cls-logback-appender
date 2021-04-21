package com.sevlow.cls.logback;

import cls.Cls.LogGroupList;
import com.google.common.collect.Maps;
import com.sevlow.cls.QcloudClsSignature;
import com.sevlow.cls.config.ClsConfig;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Dispatcher;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.NonNull;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;

public class Producer {

  private final static String API_UPLOAD = "/structuredlog";

  private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

  private static final MediaType PROTOBUF = MediaType.parse("application/x-protobuf");

  private final ClsConfig config;

  private static boolean IS_INITIALIZED = false;

  public Producer(ClsConfig config) {
    this.config = config;
    trySSlDisable();
  }

  /**
   * 如果debug模式开启，则关闭ssl校验
   */
  private void trySSlDisable() {
    if (config.isDebug() && !IS_INITIALIZED) {
      X509TrustManager xtm = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      };

      SSLContext sslContext = null;
      try {
        sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, new TrustManager[]{xtm}, new SecureRandom());
      } catch (NoSuchAlgorithmException | KeyManagementException e) {
        e.printStackTrace();
      }
      HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
          return true;
        }
      };

      assert sslContext != null;
      HTTP_CLIENT.setSslSocketFactory(sslContext.getSocketFactory());
      HTTP_CLIENT.setHostnameVerifier(DO_NOT_VERIFY);
      IS_INITIALIZED = true;
    }
  }

  /**
   * 签名
   *
   * @param method             方法 : get post delete put head
   * @param uri                api uri
   * @param formatedParameters 请求参数
   * @param formatedHeaders    请求头
   * @return 包含签名的授权信息 "Authorization"
   */
  public String sign(@NonNull String method, @NonNull String uri,
      Map<String, String> formatedParameters,
      Map<String, String> formatedHeaders) throws UnsupportedEncodingException {

    if (formatedHeaders == null) {
      formatedHeaders = Maps.newHashMap();
    }
    formatedHeaders.put("Host", config.getHost());

    String sign = QcloudClsSignature
        .buildSignature(config.getSecretId(), config.getSecretKey(), method, uri,
            formatedParameters,
            formatedHeaders, 300000);

    return sign;
  }

  public void lz4Upload(String topicId, LogGroupList lgl) throws IOException {

    String url = "https://".concat(config.getHost())
        .concat(API_UPLOAD).concat("?topic_id=").concat(topicId);

    String method = "POST";

    Map<String, String> formatedParameters = Maps.newHashMap();
    formatedParameters.put("topic_id", topicId);
    Map<String, String> formatedHeaders = Maps.newHashMap();

    formatedHeaders.put("x-cls-compress-type", "lz4");
    formatedHeaders.put("Host", this.config.getHost());

    String authorization = this.sign(method, API_UPLOAD, formatedParameters, formatedHeaders);

//    log.debug("压缩前 : {}", lgl.toByteArray().length);
    byte[] data = lz4com(lgl.toByteArray());
//    log.debug("压缩后(lz4) : {}", data.length);

    Request request = new Request.Builder()
        .header("Authorization", authorization)
        .header("x-cls-compress-type", "lz4")
        .url(url)
        .method(method, RequestBody.create(PROTOBUF, data))
        .build();

//    Response response = HTTP_CLIENT.newCall(request).execute();
//    String body = response.body().string();
//    log.debug("upload response -> http status code : {}", response.code());
//    log.debug("upload response -> body : {}", body);
    HTTP_CLIENT.newCall(request).enqueue(new Callback() {

      private int execCount = 0;

      @Override
      public void onFailure(Request request, IOException e) {
//        log.info("failure send log");
//        log.info(e.getMessage(), e);
//        debugQueueInfo();
        if (e instanceof UnknownHostException) {
          // unknown hosts , retry
          // timeout , retry
          this.execCount++;
          if (execCount <= Producer.this.config.getRetries()) {
//            log.debug("正在重试 : {} / {}", this.execCount, Producer.this.config.getRetries());
            HTTP_CLIENT.newCall(request).enqueue(this);
          }
        }

      }

      @Override
      public void onResponse(Response response) throws IOException {
//        String body = response.body().string();
//        log.debug("upload response -> http status code : {}", response.code());
//        log.debug("upload response -> body : {}", body);
//        debugQueueInfo();
      }
    });

  }

  private byte[] lz4com(byte[] data) {
    int decompressedLength = data.length;
    LZ4Factory factory = LZ4Factory.fastestInstance();
    LZ4Compressor compressor = factory.fastCompressor();
    int maxCompressedLength = compressor.maxCompressedLength(decompressedLength);
    byte[] compressed = new byte[maxCompressedLength];
    int compressedLength = compressor
        .compress(data, 0, decompressedLength, compressed, 0, maxCompressedLength);
    return Arrays.copyOf(compressed, compressedLength);
  }

  /**
   * lz4 压缩
   *
   * @param data 压缩前数据
   * @return 压缩后数据
   * @throws IOException IO异常
   */
  private byte[] lz4Compress(byte[] data) throws IOException {
    try {
      //1.zip压缩
      ByteArrayOutputStream baOs = new ByteArrayOutputStream();
      LZ4BlockOutputStream lz4Os = new LZ4BlockOutputStream(baOs);
      lz4Os.write(data);
      lz4Os.flush();
      lz4Os.close();//关闭流 输出缓存区内容  否则解压时eof异常

      //2.获取并转义压缩内容
      return baOs.toByteArray();
    } catch (IOException e) {
      throw e;
    }

  }

  /**
   * lz4 解压
   *
   * @param data 压缩后数据
   * @return 还原数据
   * @throws IOException IO异常
   */
  private byte[] lz4Uncompress(byte[] data) throws IOException {
    //1.zip解压缩
    try {
      //1.zip解压缩
      ByteArrayOutputStream baOs = new ByteArrayOutputStream();
      ByteArrayInputStream baIs = new ByteArrayInputStream(data);
      LZ4BlockInputStream zipIs = new LZ4BlockInputStream(baIs);

      byte[] temp = new byte[256];
      int n;
      while ((n = zipIs.read(temp)) >= 0) {
        baOs.write(temp, 0, n);
      }

      return baOs.toByteArray();
    } catch (IOException e) {
      throw e;
    }
  }

  /**
   * 打印队列信息 (debug)
   */
  private void debugQueueInfo() {
    Dispatcher dispatcher = HTTP_CLIENT.getDispatcher();
    int queuedCallCount = dispatcher.getQueuedCallCount();
    int runningCallCount = dispatcher.getRunningCallCount();
//    log.debug("静候队列数 : {} / 执行队列数 : {}", queuedCallCount, runningCallCount);
  }

}
