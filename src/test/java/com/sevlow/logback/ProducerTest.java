package com.sevlow.logback;

import cls.Cls;
import cls.Cls.Log;
import cls.Cls.Log.Content;
import cls.Cls.LogGroup;
import cls.Cls.LogGroupList;
import com.sevlow.cls.config.ClsConfig;
import com.sevlow.cls.logback.Producer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.junit.Before;
import org.junit.Test;

public class ProducerTest {

  private Producer producer;

  private static final String SECRET_ID = "";
  private static final String SECRET_KEY = "";
  private static final String TOPIC_ID = "";

  @Before
  public void before() {
    ClsConfig config = new ClsConfig();
    config.setRegion("ap-guangzhou");
    config.setSecretId(SECRET_ID);
    config.setSecretKey(SECRET_KEY);
    config.setDebug(true);
    this.producer = new Producer(config);
  }

  @Test
  public void testSend() throws IOException, InterruptedException {
    this.producer.lz4Upload(TOPIC_ID, lgl());
    Thread.sleep(20 * 1000);
  }

  private Cls.LogGroupList lgl() throws IOException {

    InputStreamReader isReader = new InputStreamReader(
        ProducerTest.class.getResourceAsStream("/test_big_value.json"));
    BufferedReader reader = new BufferedReader(isReader);
    StringBuffer sb = new StringBuffer();
    String str;
    while ((str = reader.readLine()) != null) {
      sb.append(str);
    }
    String bigValue = sb.toString();
    return LogGroupList.newBuilder()
        .addLogGroupList(LogGroup.newBuilder()
            .addLogs(Log.newBuilder()
                .setTime(System.currentTimeMillis())
                .addContents(Content.newBuilder()
                    .setKey("formattedMessage")
                    .setValue("info->".concat(bigValue))
                    .build())
                .build())
            .build())
        .build();

  }

}
