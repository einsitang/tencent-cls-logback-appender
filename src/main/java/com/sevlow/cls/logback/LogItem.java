package com.sevlow.cls.logback;

import com.google.common.collect.Maps;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;

@Data
public class LogItem implements Serializable {


  private static final long serialVersionUID = -7225395025683766121L;

  public static final String FIELD_TIME = "time";
  public static final String FIELD_DATETIME = "datetime";
  public static final String FIELD_LEVEL = "level";
  public static final String FIELD_LOGGER_NAME = "loggerName";
  public static final String FIELD_THREAD_NAME = "threadName";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_FORMATTED_MESSAGE = "formattedMessage";
  public static final String FIELD_THROWABLE = "throwable";
  public static final String FIELD_IP = "ip";
  public static final String FIELD_HOSTNAME = "hostname";

  private Long time;
  private String datetime;
  private String level;
  private String loggerName;
  private String threadName;
  private String message;
  private String formattedMessage;
  private String throwable;
  private String ip;
  private String hostname;
  private Map<String, String> mdcFields = Maps.newHashMap();

}
