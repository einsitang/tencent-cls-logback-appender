package com.sevlow.cls.logback;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import cls.Cls.Log;
import cls.Cls.Log.Content;
import cls.Cls.LogGroup;
import cls.Cls.LogGroupList;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.sevlow.cls.ConsoleLog;
import com.sevlow.cls.config.ClsConfig;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;


/**
 * 发送任务重试最大重试 3 次
 * <p>
 * 单次发送任务数据包（logs）上限 1w 条
 * <p>
 * 周期 3 秒 必然触发发送任务
 * <p>
 * 最大日志缓存数据 2w 条,超过则扔掉旧数据
 * <p>
 * 并发线程 3 条
 *
 * @param <E> EventObject
 * @author einsitang
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class LoghubAppender<E> extends UnsynchronizedAppenderBase<E> {

  private static final String CLASS_NAME = LoghubAppender.class.getName();

  private static String CONTEXT_FLOW_PREFIX = UUID.randomUUID().toString().replace("-", "");

  private static LongAdder GLOBAL_COUNTER = new LongAdder();

  private static LongAdder CONTEXT_FLOW = new LongAdder();

  // 发送任务重试最大重试 3 次
  private static int MAX_SEND_RETRIES = 3;

  //单次发送任务数据包（logs）最低 3000 条
  private static int MIN_SEND_PACK_LOGS = 3_000;

  //单次发送任务数据包（logs）最高 8000 条
  private static int MAX_SEND_PACK_LOGS = 8_000;

  // 最大缓存数据包 2w 条
  private static int MAX_CACHE_PACK_LOGS = 20_000;

  // 最小触发周期任务
  private static int MIN_SEND_INTERVAL = 1;

  // 最小触发周期任务
  private static int MAX_SEND_INTERVAL = 5;

  // 并发任务数
  private static int CONCURRENT_THREAD_TASKS = 4;

  private String topicId;

  private String region;

  private String secretId;

  private String secretKey;

  private String mdcFields;

  private String debug;
  private String isInternal;
  private String source;
  private String hostname;
  private String ip;

  private int sendInterval = MIN_SEND_INTERVAL;
  private int sendPackLogs = MIN_SEND_PACK_LOGS;

  protected String timeZone = "UTC";
  protected String timeFormat = "yyyy-MM-dd'T'HH:mmZ";
  protected DateTimeFormatter formatter;

  private ScheduledExecutorService timerExecutor;
  private Producer producer;
  private ConsoleLog consoleLog;

  private Queue<LogItem> logItemList = Queues.newConcurrentLinkedQueue();

  private boolean isDebug() {
    if (debug == null) {
      return false;
    }
    return "TRUE".equalsIgnoreCase(debug);
  }

  @Override
  public void start() {
    try {
      doStart();
    } catch (Exception e) {
      addError("Failed to start LoghubAppender.", e);
    }
  }

  private void doStart() {

    producer = createProducer();
    consoleLog = createConsoleLog();
    formatter = DateTimeFormat.forPattern(timeFormat).withZone(DateTimeZone.forID(timeZone));

    if (null == source) {
      source = getSource();
    }
    ip = getIp();
    hostname = getHostname();

    super.start();
    ThreadFactory namedThreadFactory =
        new ThreadFactoryBuilder().setNameFormat("appender-timer-executor-thread-%d").build();
    timerExecutor = Executors.newScheduledThreadPool(CONCURRENT_THREAD_TASKS, namedThreadFactory);
    timerExecutor.scheduleAtFixedRate(() -> {
      consoleLog.log("定时器启动 : " + new DateTime().toString(formatter));
//      log.debug("定时器启动 {}", new DateTime().toString(formatter));
      try {
        send();
      } catch (Exception e) {
//        log.error("appender 定时器发生错误");
//        log.error(e.getMessage(), e);
      }
    }, sendInterval, sendInterval, TimeUnit.SECONDS);
  }

  private ConsoleLog createConsoleLog() {
    return new ConsoleLog(CLASS_NAME, isDebug());
  }

  private Producer createProducer() {
    ClsConfig config = new ClsConfig();
    config.setRegion(region);
    config.setSecretId(secretId);
    config.setSecretKey(secretKey);
    config.setRetries(MAX_SEND_RETRIES);
    config.setInternal("TRUE".equalsIgnoreCase(isInternal));
    config.setDebug(isDebug());
    return new Producer(config);
  }

  @Override
  public void stop() {
    super.stop();
    consoleLog.log("appender timer executor 停止...");
    if (timerExecutor != null && !timerExecutor.isShutdown()) {
      timerExecutor.shutdown();
    }
    // empty logItemList
    while (!logItemList.isEmpty()) {
      send();
    }
  }


  @Override
  public void append(E eventObject) {
    try {
      appendEvent(eventObject);
    } catch (Exception e) {
      addError("Failed to append event.", e);
    }
  }

  private void appendEvent(E eventObject) {
//    log.debug("触发 appendEvent {}", eventObject);
    if (!(eventObject instanceof LoggingEvent)) {
      return;
    }

    //超过发送两
    GLOBAL_COUNTER.increment();
    if (GLOBAL_COUNTER.longValue() >= MAX_CACHE_PACK_LOGS) {
      return;
    }

    LoggingEvent event = (LoggingEvent) eventObject;
    LogItem logItem = new LogItem();

    String loggerName = event.getLoggerName()
        .concat("(")
        .concat(String.valueOf(event.getCallerData()[0].getLineNumber()))
        .concat(")");
    String level = event.getLevel().levelStr;
    String message = event.getMessage();
    String threadName = event.getThreadName();
    String formattedMessage = event.getFormattedMessage();
    Long timestamp = event.getTimeStamp();
    String datetime = new DateTime(timestamp).toString(formatter);
    IThrowableProxy iThrowableProxy = event.getThrowableProxy();
    String throwable = null;
    if (iThrowableProxy != null) {
      throwable = getExceptionInfo(iThrowableProxy);
      throwable += throwasStack(event.getThrowableProxy().getStackTraceElementProxyArray());
    }

    logItem.setTime(timestamp);
    logItem.setDatetime(datetime);
    logItem.setLevel(level);
    logItem.setLoggerName(loggerName);
    logItem.setThreadName(threadName);
    logItem.setMessage(message);
    logItem.setFormattedMessage(formattedMessage);
    logItem.setThrowable(throwable);
    logItem.setHostname(hostname);
    logItem.setIp(ip);

    Optional.ofNullable(mdcFields).ifPresent(
        f -> event.getMDCPropertyMap().entrySet().stream()
            .filter(v -> Arrays.stream(f.split(",")).anyMatch(i -> i.equals(v.getKey())))
            .forEach(map -> logItem.getMdcFields().put(map.getKey(), map.getValue()))

    );

    logItemList.add(logItem);

    if (GLOBAL_COUNTER.intValue() >= sendPackLogs) {
      send();
    }
  }

  private String getExceptionInfo(IThrowableProxy iThrowableProxy) {
    String s = iThrowableProxy.getClassName();
    String message = iThrowableProxy.getMessage();
    return (message != null) ? (s + ": " + message) : s;
  }

  private String throwasStack(StackTraceElementProxy[] stackTraceElementProxyArray) {
    StringBuilder builder = new StringBuilder();
    for (StackTraceElementProxy step : stackTraceElementProxyArray) {
      builder.append(CoreConstants.LINE_SEPARATOR);
      String string = step.toString();
      builder.append(CoreConstants.TAB).append(string);
      ThrowableProxyUtil.subjoinPackagingData(builder, step);
    }
    return builder.toString();
  }

  private void send() {
    // 发送日志
//    log.debug("发送日志");
    int count = 0;
    if (logItemList.isEmpty()) {
//      log.debug("日志信息为空,跳过发送日志");
      return;
    }

    CONTEXT_FLOW.increment();
    LogItem logItem = null;
    LogGroup.Builder logGroupBuilder = LogGroup.newBuilder();
    logGroupBuilder
        .setContextFlow(CONTEXT_FLOW_PREFIX.concat("-" + CONTEXT_FLOW.longValue()));
    while ((logItem = logItemList.poll()) != null) {

      Log.Builder logBuilder = Log.newBuilder();
      logBuilder
          .setTime(logItem.getTime())
          .addContents(content(LogItem.FIELD_TIME, String.valueOf(logItem.getTime())))
          .addContents(content(LogItem.FIELD_DATETIME, logItem.getDatetime()))
          .addContents(content(LogItem.FIELD_LEVEL, logItem.getLevel()))
          .addContents(content(LogItem.FIELD_LOGGER_NAME, logItem.getLoggerName()))
          .addContents(content(LogItem.FIELD_THREAD_NAME, logItem.getThreadName()))
          .addContents(content(LogItem.FIELD_IP, logItem.getIp()))
          .addContents(content(LogItem.FIELD_HOSTNAME, logItem.getHostname()))
//          .addContents(content(LogItem.FIELD_MESSAGE, logItem.getMessage()))
          .addContents(content(LogItem.FIELD_FORMATTED_MESSAGE, logItem.getFormattedMessage()));

      if (null != logItem.getThrowable()) {
        logBuilder.addContents(content(LogItem.FIELD_THROWABLE, logItem.getThrowable()));
      }
      if (!logItem.getMdcFields().isEmpty()) {
        logItem.getMdcFields().forEach((key, value) -> logBuilder.addContents(content(key, value)));
      }

      logGroupBuilder.setSource(this.source);
      logGroupBuilder.addLogs(logBuilder.build());

      count++;
      if (count >= sendPackLogs) {
        break;
      }
    }

//    System.out.println("发送条数:".concat(String.valueOf(count)));
    GLOBAL_COUNTER.add(-count);
    try {
      producer.lz4Upload(this.topicId, LogGroupList.newBuilder()
          .addLogGroupList(logGroupBuilder.build())
          .build());
    } catch (IOException e) {
      // retry ?
    }


  }

  private InetAddress getInetAddress() {
    try {
      return InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      addError(e.getMessage());
    }
    return null;
  }

  private String getIp() {
    InetAddress addr = getInetAddress();
    if (addr != null) {
      return addr.getHostAddress();
    }
    return "";
  }

  private String getHostname() {
    InetAddress addr = getInetAddress();
    if (addr != null) {
      return addr.getHostName();
    }
    return "";
  }

  private String getSource() {
    return getHostname().concat("(".concat(getIp()).concat(")"));
  }

  private Content content(String key, String value) {
    return Content.newBuilder()
        .setKey(key)
        .setValue(value)
        .build();
  }

  public void setSendInterval(int sendInterval) {
    if (sendInterval > MAX_SEND_INTERVAL) {
      sendInterval = MAX_SEND_INTERVAL;
    }
    if (sendInterval < MIN_SEND_INTERVAL) {
      sendInterval = MIN_SEND_INTERVAL;
    }

    this.sendInterval = sendInterval;
  }

  public void setSendPackLogs(int sendPackLogs) {
    if (sendPackLogs > MAX_SEND_PACK_LOGS) {
      sendPackLogs = MAX_SEND_PACK_LOGS;
    }
    if (sendPackLogs < MIN_SEND_PACK_LOGS) {
      sendPackLogs = MIN_SEND_PACK_LOGS;
    }
    this.sendPackLogs = sendPackLogs;
  }
}
