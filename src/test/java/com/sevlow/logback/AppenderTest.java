package com.sevlow.logback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppenderTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(AppenderTest.class);

  private static CountDownLatch latch;

  @BeforeClass
  public static void before() {
    latch = new CountDownLatch(1);
  }

  @AfterClass
  public static void checkStatusList() throws InterruptedException {
    latch.await(10, TimeUnit.MINUTES);
  }

  @Test
  public void testMessage() {
    LOGGER.warn("warn message on cls logback.");
    LOGGER.debug("debug message on cls logback.");
    LOGGER.error("error message on cls logback.");

    try {
      throw new RuntimeException("just throw runtime exception for logback test");
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }

  }


}
