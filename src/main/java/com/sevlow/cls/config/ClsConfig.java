package com.sevlow.cls.config;

import lombok.Data;

/**
 * @author einsitang
 */
@Data
public class ClsConfig {

  private static final String INTERNAL_HOST_URL_TEMPLATE = "%s.cls.tencentyun.com";

  private static final String EXTERNAL_HOST_URL_TEMPLATE = "%s.cls.tencentcs.com";

  private String secretId;

  private String secretKey;

  private String region;

  /**
   * 是否腾讯云内部使用
   */
  private boolean isInternal = false;

  private int retries = 3;

  private boolean isDebug = false;

  public void setRegion(REGION region) {
    this.region = region.name;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getHost() {
    String hostUrlTemplate = isInternal ? INTERNAL_HOST_URL_TEMPLATE : EXTERNAL_HOST_URL_TEMPLATE;
    return String.format(hostUrlTemplate, this.region);
  }

  public enum REGION {

    /**
     * 北京
     */
    BEIJING("ap-beijing"),

    /**
     * 广州
     */
    GUANGZHOU("ap-guangzhou"),

    /**
     * 上海
     */
    SHANGHAI("ap-shanghai"),

    /**
     * 南京
     */
    NANJING("ap-nanjing"),

    /**
     * 成都
     */
    CHENGDU("ap-chengdu"),

    /**
     * 重庆
     */
    CHONGQING("ap-chongqing"),

    /**
     * 香港
     */
    HONGKONG("ap-hongkong"),

    /**
     * 硅谷
     */
    SILICONVALLEY("na-siliconvalley"),

    /**
     * 弗吉尼亚
     */
    ASHBURN("na-ashburn"),

    /**
     * 新加坡
     */
    SINGAPORE("ap-singapore"),

    /**
     * 孟买
     */
    MUMBAI("ap-mumbai"),

    /**
     * 法兰克福
     */
    FRANKFURT("eu-frankfurt"),

    /**
     * 东京
     */
    TOKYO("ap-tokyo"),

    /**
     * 首尔
     */
    SEOUL("ap-seoul"),

    /**
     * 莫斯科
     */
    MOSCOW("eu-moscow"),

    /**
     * 深圳金融
     */
    SHENZHEN_FSI("ap-shenzhen-fsi"),

    /**
     * 上海金融
     */
    SHANGHAI_FSI("ap-shanghai-fsi"),

    /**
     * 北京金融
     */
    BEIJING_FSI("ap-beijing-fsi");

    /**
     * name of region
     */
    private final String name;

    private REGION(String name) {
      this.name = name;
    }

  }

}
