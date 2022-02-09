# Tencent CLS Logback Appender [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)



## 腾讯云日志服务logback插件

`tencent-cls-logback-appender` 是基于腾讯云日志服务(CLS)开放接口的一个logback日志上传组件

特性：

- 简单易用
- 资源占用低
- 默认使用lz4数据压缩,后台异步并发上传

已知问题：

- ~~使用主动上报的方式无法使用 "**上下文检索**" , 需要等待腾讯云开放该功能 (`LogGroup.contextFlow`)~~


样例日志:
```
datetime: 2021-04-21 18:33:40
hostname: EinsiTangdeMacBook-ProM1.local
formattedMessage: Shutting down the Executor Pool for PollingServerListUpdater
level: ERROR
ip: 10.7.1.227
time: 1619001220095
loggerName: com.netflix.loadbalancer.PollingServerListUpdater(53)
threadName: Thread-33
throwable: java.lang.RuntimeException: com.netflix.client.ClientException: Load balancer does not have available server for client: et-pom-service
	at org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient.execute(LoadBalancerFeignClient.java:90)
	at feign.SynchronousMethodHandler.executeAndDecode(SynchronousMethodHandler.java:110)
	at feign...
```
参数字段:
+ `datetime`  格式化日期
+ `hostname` 服务器主机名
+ `formattedMessage` 格式化信息
+ `level` 日志级别
+ `ip` 服务器IP
+ `time` 日志打印时间 (时间戳格式)
+ `loggerName`日志语句代码及位置
+ `threadName` 线程名称
+ `throwable` 日志异常栈信息 (如果有的话)

## 版本依赖
* lombok 1.18.8
* logback 1.2.3
* protobuf 3.13.0
* grpc-java 1.34.0
* ok-http 2.7.4


## 使用

###  1. maven 工程依赖引入

```
<dependency>
    <groupId>com.sevlow.logback</groupId>
    <artifactId>tencent-cls-logback-appender</artifactId>
    <!-- <version>1.1.0</version> -->
    <version>1.2.0-SNAPSHOT</version>
</dependency>
```

### 2. 修改logbook.xml配置

`logback.xml` 参考
```
<configuration>
  <!-- %m输出的信息,%p日志级别,%t线程名,%d日期,%c类的全名,%i索引【从数字0开始递增】,,, -->
  <!-- appender是configuration的子节点，是负责写日志的组件。 -->

  <!-- ConsoleAppender：把日志输出到控制台 -->
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d %highlight(%-5level) | %boldYellow(%thread) |
        %boldMagenta(%logger{20})(%file:%line\)- %m%n
      </pattern>
      <!-- 控制台也要使用UTF-8，不要使用GBK，否则会中文乱码 -->
      <charset>UTF-8</charset>
    </encoder>
  </appender>

  <!--为了防止进程退出时，内存中的数据丢失，请加上此选项-->
  <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>

  <appender name="CLS" class="com.sevlow.cls.logback.LoghubAppender">
    <!--必选项-->
    <!-- 账号及网络配置 -->
    <region>ap-guangzhou</region>
    <secretId>SECRET ID</secretId>
    <secretKey>SECRET KEY</secretKey>
    <topicId>CLS TOPIC ID</topicId>
    <!--必选项 (end)-->

    <!-- 可选项 -->
    <!-- 是否腾讯云内部上报:如果在外网则务必设置为false,默认false -->
    <isInternal>false</isInternal>
    <!-- 是否打开调试输出 -->
    <debug>false</debug>

    <!-- 发送频率,秒,[1-5] -->
    <sendInterval>1</sendInterval>
    <!-- 发送日志包条数 [3000-8000] -->
    <sendPackLogs>3000</sendPackLogs>
    <!-- 可选项 设置 time 字段呈现的格式 -->
    <timeFormat>yyyy-MM-dd HH:mm:ss</timeFormat>
    <!-- 可选项 设置 time 字段呈现的时区 -->
    <timeZone>Asia/Shanghai</timeZone>
  </appender>

  <!-- 控制台输出日志级别 -->
  <root level="info">
    <appender-ref ref="STDOUT"/>
    <appender-ref ref="CLS"/>
  </root>
</configuration>
```
**必填属性：**

+ `<region>` 为腾讯云CLS[可用地域简称](https://cloud.tencent.com/document/product/614/18940)
+ `<secretId>`/`<secretKey>`请在腾讯云`访问管理`中设置，并提供足够的资源权限
+ `<topicId>`为日志主题ID

**选填属性：**
+ `<timeFormat>` 设置日期格式,默认: `yyyy-MM-dd HH:mm:ss`
+ `<timeZone>` 设置时区 ,默认 : `UTC`,中国时区可以填入`Asia/Shanghai`
+ `<sendInterval>` 上报周期,默认为1,单位为秒,表示每秒至少上报一次日志,可是范围为1~5秒
+ `<isInternal>` 是否腾讯云内部上报:如果在**外网则务必设置为false**,默认false
+ `<debug>` 是否打开调试输出

## 编译打包

### 编译
`mvn clean compile`

### 本地安装
`mvn install`

> 如果你当前的开发环境基于 `Apple Silicon` 架构
> 在编译时请使用x86_64程序编译`protobuf`文件,只需要附加参数：
>
>  -Dos.detected.name=osx -Dos.detected.arch=x86_64 -Dos.detected.classifier=osx-x86_64 
>
> 编译完整命令:
>
> mvn clean compile  -Dos.detected.name=osx -Dos.detected.arch=x86_64 -Dos.detected.classifier=osx-x86_64 