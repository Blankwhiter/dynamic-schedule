写在前面：本文采取的环境 1.docker下的mysql (对于用户使用的数据库版本 请引用对应的数据库驱动版本)，2.springboot(该demo采用mysql数据库，druid连接池，mybatis持久层)
 
项目地址：
码云： https://gitee.com/belonghuang/dynamic-schedule
github： https://github.com/Blankwhiter/dynamic-schedule

前端配套界面：https://github.com/Blankwhiter/vue-admin-template

更新日志：
1.加入定时器 给定时间 得出下次执行时间点 
参照 https://gitee.com/xxssyyyyssxx/cron-hms


# 第一步 MySQL环境准备（可跳过）
### 1.在centos窗口中，拉取mysql镜像，并且创建该容器：
```bash
docker pull mysql:5.7
docker run --name test-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=123456 -d mysql:5.7
```
*注：等容器启动成功后，读者可以尝试用Navicat连接 ：账号root， 密码123456.*

### 2.数据库表准备，以及测试数据
本人创建了名为**test**的数据库，并执行了下方语句创建表以及测试数据。
```mysql
-- 定时任务
CREATE TABLE `schedule_job` (
  `job_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务id',
  `bean_name` varchar(200) DEFAULT NULL COMMENT 'spring bean名称',
  `method_name` varchar(100) DEFAULT NULL COMMENT '方法名',
  `params` varchar(2000) DEFAULT NULL COMMENT '参数',
  `cron_expression` varchar(100) DEFAULT NULL COMMENT 'cron表达式',
  `status` tinyint(4) DEFAULT NULL COMMENT '任务状态  0：正常  1：暂停',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='定时任务';

-- 定时任务日志
CREATE TABLE `schedule_job_log` (
  `log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务日志id',
  `job_id` bigint(20) NOT NULL COMMENT '任务id',
  `bean_name` varchar(200) DEFAULT NULL COMMENT 'spring bean名称',
  `method_name` varchar(100) DEFAULT NULL COMMENT '方法名',
  `params` varchar(2000) DEFAULT NULL COMMENT '参数',
  `status` tinyint(4) NOT NULL COMMENT '任务状态    0：成功    1：失败',
  `error` varchar(2000) DEFAULT NULL COMMENT '失败信息',
  `times` int(11) NOT NULL COMMENT '耗时(单位：毫秒)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `job_id` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='定时任务日志';

INSERT INTO `schedule_job` (`bean_name`, `method_name`, `params`, `cron_expression`, `status`, `remark`, `create_time`) VALUES ('testTask', 'test', 'my-test', '0/10 * * * * ? *', '0', '有参数测试', '2018-12-01 13:15:11');
INSERT INTO `schedule_job` (`bean_name`, `method_name`, `params`, `cron_expression`, `status`, `remark`, `create_time`) VALUES ('testTask', 'test2', NULL, '0/15 * * * * ? *', '1', '无参数测试', '2018-12-03 14:45:12');

```
*注：上方语句分别创建了定时任务表，以及定时任务日志表，并且加入两条有参数（每隔10秒），无参数（每隔15秒）的两个定时器*

# 第二步 编写项目
项目结构一览：
![在这里插入图片描述](https://img-blog.csdnimg.cn/20190329083527691.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2JlbG9uZ2h1YW5nMTU3NDA1,size_16,color_FFFFFF,t_70)

### 1.引入项目所需jar，pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.1.1.RELEASE</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.example</groupId>
    <artifactId>dynamic-schedule</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>dynamic-schedule</name>
    <description>Demo project for Spring Boot</description>

    <properties>
        <java.version>1.8</java.version>
    </properties>

    <dependencies>
        <!--web模块-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <!--定时器-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-quartz</artifactId>
        </dependency>
        <!--mybatis-->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>1.3.2</version>
        </dependency>
        <!--mysql-->
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <scope>runtime</scope>
        </dependency>
        <!--druid 数据库连接池-->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-starter</artifactId>
            <version>1.1.10</version>
        </dependency>
        <!--lombok 实体类工具-->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <!--StringUitls工具-->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>3.0</version>
        </dependency>
        <!--定时器-->
        <dependency>
            <groupId>org.quartz-scheduler</groupId>
            <artifactId>quartz</artifactId>
            <version>2.3.0</version>
            <exclusions>
                <exclusion>
                    <groupId>com.mchange</groupId>
                    <artifactId>c3p0</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <!--分页插件 4.1.5 版本以上修复 selectProvider不兼容问题-->
        <dependency>
            <groupId>com.github.pagehelper</groupId>
            <artifactId>pagehelper</artifactId>
            <version>4.1.5</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>

            <!--mybatis generator begin-->
            <plugin>
                <!--Mybatis-generator插件,用于自动生成Mapper和POJO-->
                <groupId>org.mybatis.generator</groupId>
                <artifactId>mybatis-generator-maven-plugin</artifactId>
                <version>1.3.5</version>
                <configuration>
                    <!--配置文件的位置-->
                    <configurationFile>src/main/resources/generator/mybatis_generator_config.xml
                    </configurationFile>
                    <verbose>true</verbose>
                    <overwrite>true</overwrite>
                </configuration>
                <executions>
                    <execution>
                        <id>Generate MyBatis Artifacts</id>
                        <goals>
                            <goal>generate</goal>
                        </goals>
                    </execution>
                </executions>

                <dependencies>
                    <!--防止找不到驱动 报出如下异常：Exception getting JDBC Driver: com.mysql.jdbc.Driver-->
                    <dependency>
                        <groupId>mysql</groupId>
                        <artifactId>mysql-connector-java</artifactId>
                        <version>5.1.43</version>
                    </dependency>

                    <!-- https://mvnrepository.com/artifact/org.mybatis/mybatis -->
                    <dependency>
                        <groupId>org.mybatis</groupId>
                        <artifactId>mybatis</artifactId>
                        <version>3.4.4</version>
                    </dependency>
                    <!-- mybatis-generator-core 反向生成java代码-->
                    <dependency>
                        <groupId>org.mybatis.generator</groupId>
                        <artifactId>mybatis-generator-core</artifactId>
                        <version>1.3.5</version>
                    </dependency>

                </dependencies>
            </plugin>

            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>

```
### 2.配置项目环境 application.properties
```xml

server.port=8888
#数据库设置
spring.datasource.driverClassName=com.mysql.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost:3306/test?allowMultiQueries=true&useUnicode=true&characterEncoding=utf8&autoReconnect=true&useSSL=true
spring.datasource.username=root
spring.datasource.password=123456
#--------------------------
# 下面为连接池的补充设置，应用到上面所有数据源中
# 初始化大小，最小，最大
spring.datasource.initialSize=5
spring.datasource.minIdle=1
spring.datasource.maxActive=50
# 配置获取连接等待超时的时间
spring.datasource.maxWait=60000
# 配置间隔多久才进行一次检测，检测需要关闭的空闲连接，单位是毫秒
spring.datasource.timeBetweenEvictionRunsMillis=60000
# 配置一个连接在池中最小生存的时间，单位是毫秒
spring.datasource.minEvictableIdleTimeMillis=300000
spring.datasource.validationQuery=SELECT 1 FROM DUAL
spring.datasource.testWhileIdle=true
spring.datasource.testOnBorrow=false
spring.datasource.testOnReturn=false
# 打开PSCache，并且指定每个连接上PSCache的大小
spring.datasource.poolPreparedStatements=false
#spring.datasource.maxPoolPreparedStatementPerConnectionSize=20
# 配置监控统计拦截的filters，去掉后监控界面sql无法统计，'wall'用于防火墙
spring.datasource.filters=stat,wall,log4j
# 通过connectProperties属性来打开mergeSql功能；慢SQL记录
spring.datasource.connectionProperties=druid.stat.mergeSql=true;druid.stat.slowSqlMillis=5000
# 合并多个DruidDataSource的监控数据
#spring.datasource.useGlobalDataSourceStat=true


mybatis.mapper-locations=classpath:mappers/*Mapper.xml
mybatis.type-aliases-package=com.example.dynamicschedule.bean
mybatis.check-config-location=true
mybatis.config-location=classpath:mybatis-config.xml

```

### 3.启动上配置**MapperScan**注解,以及编写mybatis-config.xml
1.DynamicScheduleApplication.java
```java
package com.example.dynamicschedule;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = "com.example.dynamicschedule.dao")
public class DynamicScheduleApplication {

    public static void main(String[] args) {
        SpringApplication.run(DynamicScheduleApplication.class, args);
    }

}


```
2.mybatis-config.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE configuration
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
    <properties>
        <property name="dialect" value="mysql"/>
    </properties>
    <settings>
        <!-- 开启驼峰匹配 -->
        <setting name="mapUnderscoreToCamelCase" value="true"/>
        <!-- 这个配置使全局的映射器启用或禁用缓存。系统默认值是true，设置只是为了展示出来 -->
        <setting name="cacheEnabled" value="true"/>
        <!-- 全局启用或禁用延迟加载。当禁用时，所有关联对象都会即时加载。 系统默认值是true，设置只是为了展示出来 -->
        <setting name="lazyLoadingEnabled" value="true"/>
        <!-- 允许或不允许多种结果集从一个单独的语句中返回（需要适合的驱动）。 系统默认值是true，设置只是为了展示出来 -->
        <setting name="multipleResultSetsEnabled" value="true"/>
        <!--使用列标签代替列名。不同的驱动在这方便表现不同。参考驱动文档或充分测试两种方法来决定所使用的驱动。 系统默认值是true，设置只是为了展示出来 -->
        <setting name="useColumnLabel" value="true"/>
        <!--允许 JDBC 支持生成的键。需要适合的驱动。如果设置为 true 则这个设置强制生成的键被使用，尽管一些驱动拒绝兼容但仍然有效（比如
            Derby）。 系统默认值是false，设置只是为了展示出来 -->
        <setting name="useGeneratedKeys" value="false"/>
        <!--配置默认的执行器。SIMPLE 执行器没有什么特别之处。REUSE 执行器重用预处理语句。BATCH 执行器重用语句和批量更新 系统默认值是SIMPLE，设置只是为了展示出来 -->
        <setting name="defaultExecutorType" value="SIMPLE"/>
        <!--设置超时时间，它决定驱动等待一个数据库响应的时间。 系统默认值是null，设置只是为了展示出来 -->
        <setting name="defaultStatementTimeout" value="25000"/>

        <setting name="callSettersOnNulls" value="true"/> <!-- 返回空字段 -->

        <!-- 打印查询语句 -->
         <setting name="logImpl" value="STDOUT_LOGGING" />
    </settings>

    <!-- 分页助手 -->
      <plugins>
          <plugin interceptor="com.github.pagehelper.PageHelper">
              <!-- 数据库方言 -->
              <property name="dialect" value="mysql" />
              <property name="offsetAsPageNum" value="true" />
              <!-- 设置为true时，使用RowBounds分页会进行count查询 会去查询出总数 -->
              <property name="rowBoundsWithCount" value="true" />
              <property name="pageSizeZero" value="true" />
              <property name="reasonable" value="true" />
          </plugin>
      </plugins>
</configuration>

```

### 4.由项目的**resources**的**generator**配置文件，通过pom下配置的工具**mybatis-generator-maven-plugin**生成对应
<pre>bean目录下：
ScheduleJobExample
ScheduleJob
ScheduleJobLog
ScheduleJobLogExample
dao目录下：
ScheduleJobLogMapper
ScheduleJobMapper
mapper目录下：
ScheduleJobLogMapper.xml
ScheduleJobMapper.xml
</pre>

*注：由于此步骤已经有本人实现，不再过多赘述，如有疑问请留言评论，晚间本人将进行解答*

### 5.编写基础的返回信息类，异常捕捉，工具类
**base**目录下：
1.Constant.java
```java
package com.example.dynamicschedule.base;

/**
 * 常量
 */
public class Constant {
    /**
     * 任务调度参数key
     */
    public static final String JOB_PARAM_KEY = "JOB_PARAM_KEY";
    /**
     * 定时任务状态
     *
     */
    /**
     * 正常
     */
    public static final int NORMAL = 0;
    /**
     * 暂停
     */
    public static final int PAUSE =1;
}

```
2.ResultCode.java
```java
package com.example.dynamicschedule.base;

/**
 *  返回信息码
 */
public enum ResultCode {
    SUCCESS(0, "操作成功"),
    FAIL(1, "操作失败,请联系管理员"),
    PARAM_ERROR(2, "参数值格式有误"),
    RESOURCE_INVALID(3, "资源无效"),
    TOKEN_INVALID(11, "授权码无效"),
    CUSTOM_ERRORDESC(99);
    ResultCode(int code, String errorDesc) {
        Code = code;
        ErrorDesc = errorDesc;
    }
    ResultCode(int code) {
        Code = code;
    }
    private int Code;
    private String ErrorDesc;

    public int getCode() {
        return Code;
    }

    public void setCode(int code) {
        Code = code;
    }

    public String getErrorDesc() {
        return ErrorDesc;
    }

    public void setErrorDesc(String errorDesc) {
        ErrorDesc = errorDesc;
    }
}

```
3.ResultDTO.java
```java
package com.example.dynamicschedule.base;


import java.io.Serializable;


/**
 * 返回数据格式
 *
 */
public class ResultDTO implements Serializable {

	private int code;

	private String msg;

	private Object data;

	private int count;
	public ResultDTO(ResultCode code, Object result) {
		this.code = code.getCode();
		this.msg = code.getErrorDesc();
		this.data = result;
	}

	public ResultDTO(ResultCode code, String errorDesc, Object result) {
		this.code = code.getCode();
		this.msg = errorDesc;
		this.data = result;
	}

	public ResultDTO(int code, String errorDesc, Object result) {
		this.code = code;
		this.msg = errorDesc;
		this.data = result;
	}


}

```

**exception**目录下：
4.GenericException.java
```java
package com.example.dynamicschedule.exception;

/**
 * 自定义异常
 *
 */
public class GenericException extends RuntimeException {
	private static final long serialVersionUID = 1L;

    private String msg;
    private int code = 500;

    public GenericException(String msg) {
		super(msg);
		this.msg = msg;
	}

	public GenericException(String msg, Throwable e) {
		super(msg, e);
		this.msg = msg;
	}

	public GenericException(String msg, int code) {
		super(msg);
		this.msg = msg;
		this.code = code;
	}

	public GenericException(String msg, int code, Throwable e) {
		super(msg, e);
		this.msg = msg;
		this.code = code;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}


}

```
5.GenericExceptionHandler.java
```java
package com.example.dynamicschedule.exception;

import com.example.dynamicschedule.base.ResultDTO;
import com.example.dynamicschedule.utils.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 异常处理器
 *
 */
@RestControllerAdvice
@Slf4j
public class GenericExceptionHandler {

	/**
	 * 处理自定义异常
	 */
	@ExceptionHandler(GenericException.class)
	public ResultDTO handleRRException(GenericException e){
		if (e instanceof GenericException) {
			GenericException e1 = (GenericException) e;
			log.error(e1.getCode()+"");
			return ResultUtils.getResultDTO(e1.getCode(), e1.getMessage(), null);
		}
		log.error(e.getCause().getMessage());
		log.error(e.getMessage());

		return ResultUtils.getFail(null);

	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResultDTO handlerNoFoundException(java.lang.Exception e) {
		log.error(e.getMessage(), e);
		return ResultUtils.getResultDTO(404, "路径不存在，请检查路径是否正确",null);
	}

	@ExceptionHandler(DuplicateKeyException.class)
	public ResultDTO handleDuplicateKeyException(DuplicateKeyException e){
		log.error(e.getMessage(), e);
		return ResultUtils.getResultDTO(99,"数据库中已存在该记录",null);
	}



	@ExceptionHandler(java.lang.Exception.class)
	public ResultDTO handleException(java.lang.Exception e){
		log.error(e.getMessage(), e);
		return ResultUtils.getFail(null);
	}
}

```

**utils**目录下：
6.ResultUtils.java
```java
package com.example.dynamicschedule.utils;


import com.example.dynamicschedule.base.ResultCode;
import com.example.dynamicschedule.base.ResultDTO;

/**
 *返回信息工具
 */
public final class ResultUtils {
    private ResultUtils() {
    }

    public static ResultDTO getSuccess(Object result) {
        return new ResultDTO(ResultCode.SUCCESS, result);
    }



    public static ResultDTO getFail(Object result) {
        return new ResultDTO(ResultCode.FAIL, result);
    }

    public static ResultDTO getParamError(Object result) {
        return new ResultDTO(ResultCode.PARAM_ERROR, result);
    }

    public static ResultDTO getResourceInvalid(Object result) {
        return new ResultDTO(ResultCode.RESOURCE_INVALID, result);
    }

    public static ResultDTO getTokenInvalid(Object result) {
        return new ResultDTO(ResultCode.TOKEN_INVALID, result);
    }

    public static ResultDTO getCustomErrordesc(String errorDesc, Object result) {
        return new ResultDTO(ResultCode.CUSTOM_ERRORDESC, errorDesc, result);
    }

    public static ResultDTO getResultDTO(int code, String errorDesc, Object result) {
        return new ResultDTO(code, errorDesc, result);
    }
}

```
7.ScheduleJobUtils.java
```java
package com.example.dynamicschedule.utils;

import com.example.dynamicschedule.base.Constant;
import com.example.dynamicschedule.bean.ScheduleJob;
import com.example.dynamicschedule.bean.ScheduleJobLog;

import com.example.dynamicschedule.dao.ScheduleJobLogMapper;
import com.example.dynamicschedule.service.ScheduleJobLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/**
 * 定时任务工具
 *
 */
@Slf4j
@Component
public class ScheduleJobUtils extends QuartzJobBean {

	private ExecutorService service = Executors.newSingleThreadExecutor();

	@Autowired
	private ScheduleJobLogMapper scheduleJobLogMapper;
	
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        ScheduleJob scheduleJob = (ScheduleJob ) context.getMergedJobDataMap()
        		.get(Constant.JOB_PARAM_KEY);

        //获取spring bean
//        ScheduleJobLogService scheduleJobLogService = (ScheduleJobLogService) SpringContextUtils.getBean("scheduleJobLogService");

        //数据库保存执行记录
        ScheduleJobLog jobLog = new ScheduleJobLog();
        jobLog.setJobId(scheduleJob.getJobId());
        jobLog.setBeanName(scheduleJob.getBeanName());
        jobLog.setMethodName(scheduleJob.getMethodName());
        jobLog.setParams(scheduleJob.getParams());
        jobLog.setCreateTime(new Date());

        //任务开始时间
        long startTime = System.currentTimeMillis();
		Byte zero = 0;

		Byte one=1;
        try {
            //执行任务
        	log.info("任务准备执行，任务ID：" + scheduleJob.getJobId());
            ScheduleRunnable task = new ScheduleRunnable(scheduleJob.getBeanName(),
            		scheduleJob.getMethodName(), scheduleJob.getParams());
            Future<?> future = service.submit(task);

			future.get();

			//任务执行总时长
			long times = System.currentTimeMillis() - startTime;
			jobLog.setTimes((int)times);
			//任务状态    0：成功    1：失败
			jobLog.setStatus(zero);

			log.info("任务执行完毕，任务ID：" + scheduleJob.getJobId() + "  总共耗时：" + times + "毫秒");
		} catch (Exception e) {
			log.error("任务执行失败，任务ID：" + scheduleJob.getJobId(), e);

			//任务执行总时长
			long times = System.currentTimeMillis() - startTime;
			jobLog.setTimes((int)times);

			//任务状态    0：成功    1：失败
			jobLog.setStatus(one);
			jobLog.setError(StringUtils.substring(e.toString(), 0, 2000));
		}finally {
			scheduleJobLogMapper.insertSelective(jobLog);
		}
    }
}


```

8.ScheduleRunnable.java
```java
package com.example.dynamicschedule.utils;


import com.example.dynamicschedule.exception.GenericException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * 执行定时任务实现
 *
 */
public class ScheduleRunnable implements Runnable {
	private Object target;
	private Method method;
	private String params;

	public ScheduleRunnable(String beanName, String methodName, String params) throws NoSuchMethodException, SecurityException {
		this.target = SpringContextUtils.getBean(beanName);
		this.params = params;

		if(StringUtils.isNotBlank(params)){
			this.method = target.getClass().getDeclaredMethod(methodName, String.class);
		}else{
			this.method = target.getClass().getDeclaredMethod(methodName);
		}
	}

	@Override
	public void run() {
		try {
			ReflectionUtils.makeAccessible(method);
			if(StringUtils.isNotBlank(params)){
				method.invoke(target, params);
			}else{
				method.invoke(target);
			}
		}catch (java.lang.Exception e) {
			throw new GenericException("执行定时任务失败", e);
		}
	}

}

```
9.ScheduleUtils.java
```java

package com.example.dynamicschedule.utils;


import com.example.dynamicschedule.base.Constant;
import com.example.dynamicschedule.bean.ScheduleJob;
import com.example.dynamicschedule.exception.GenericException;
import org.quartz.*;

/**
 * 定时任务工具类
 *
 */
public class ScheduleUtils {
    private final static String JOB_NAME = "TASK_";

    /**
     * 获取触发器key
     */
    public static TriggerKey getTriggerKey(Long jobId) {
        return TriggerKey.triggerKey(JOB_NAME + jobId);
    }

    /**
     * 获取jobKey
     */
    public static JobKey getJobKey(Long jobId) {
        return JobKey.jobKey(JOB_NAME + jobId);
    }

    /**
     * 获取表达式触发器
     */
    public static CronTrigger getCronTrigger(Scheduler scheduler, Long jobId) {
        try {
            return (CronTrigger) scheduler.getTrigger(getTriggerKey(jobId));
        } catch (SchedulerException e) {
            throw new GenericException("获取定时任务CronTrigger出现异常", e);
        }
    }

    /**
     * 创建定时任务
     */
    public static void createScheduleJob(Scheduler scheduler, ScheduleJob scheduleJob) {
        try {
        	//构建job信息
            JobDetail jobDetail = JobBuilder.newJob(ScheduleJobUtils.class).withIdentity(getJobKey(scheduleJob.getJobId())).build();

            //表达式调度构建器
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(scheduleJob.getCronExpression())
            		.withMisfireHandlingInstructionDoNothing();

            //按新的cronExpression表达式构建一个新的trigger
            CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(getTriggerKey(scheduleJob.getJobId())).withSchedule(scheduleBuilder).build();

            //放入参数，运行时的方法可以获取
            jobDetail.getJobDataMap().put(Constant.JOB_PARAM_KEY, scheduleJob);

            scheduler.scheduleJob(jobDetail, trigger);

            //暂停任务
            if(scheduleJob.getStatus() == Constant.PAUSE){
            	pauseJob(scheduler, scheduleJob.getJobId());
            }
        } catch (SchedulerException e) {
            throw new GenericException("创建定时任务失败", e);
        }
    }

    /**
     * 更新定时任务
     */
    public static void updateScheduleJob(Scheduler scheduler, ScheduleJob scheduleJob) {
        try {
            TriggerKey triggerKey = getTriggerKey(scheduleJob.getJobId());

            //表达式调度构建器
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(scheduleJob.getCronExpression())
            		.withMisfireHandlingInstructionDoNothing();

            CronTrigger trigger = getCronTrigger(scheduler, scheduleJob.getJobId());

            //按新的cronExpression表达式重新构建trigger
            trigger = trigger.getTriggerBuilder().withIdentity(triggerKey).withSchedule(scheduleBuilder).build();

            //参数
            trigger.getJobDataMap().put(Constant.JOB_PARAM_KEY, scheduleJob);

            scheduler.rescheduleJob(triggerKey, trigger);

            //暂停任务
            if(scheduleJob.getStatus() == Constant.PAUSE){
            	pauseJob(scheduler, scheduleJob.getJobId());
            }

        } catch (SchedulerException e) {
            throw new GenericException("更新定时任务失败", e);
        }
    }

    /**
     * 立即执行任务
     */
    public static void run(Scheduler scheduler, ScheduleJob scheduleJob) {
        try {
        	//参数
        	JobDataMap dataMap = new JobDataMap();
        	dataMap.put(Constant.JOB_PARAM_KEY, scheduleJob);

            scheduler.triggerJob(getJobKey(scheduleJob.getJobId()), dataMap);
        } catch (SchedulerException e) {
            throw new GenericException("立即执行定时任务失败", e);
        }
    }

    /**
     * 暂停任务
     */
    public static void pauseJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.pauseJob(getJobKey(jobId));
        } catch (SchedulerException e) {
            throw new GenericException("暂停定时任务失败", e);
        }
    }

    /**
     * 恢复任务
     */
    public static void resumeJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.resumeJob(getJobKey(jobId));
        } catch (SchedulerException e) {
            throw new GenericException("暂停定时任务失败", e);
        }
    }

    /**
     * 删除定时任务
     */
    public static void deleteScheduleJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.deleteJob(getJobKey(jobId));
        } catch (SchedulerException e) {
            throw new GenericException("删除定时任务失败", e);
        }
    }
}

```

10.SpringContextUtils.java
```java
package com.example.dynamicschedule.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring Context 工具类
 *
 */
@Component
public class SpringContextUtils implements ApplicationContextAware {
	public static ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		SpringContextUtils.applicationContext = applicationContext;
	}

	public static Object getBean(String name) {
		return applicationContext.getBean(name);
	}

	public static <T> T getBean(String name, Class<T> requiredType) {
		return applicationContext.getBean(name, requiredType);
	}

	public static boolean containsBean(String name) {
		return applicationContext.containsBean(name);
	}

	public static boolean isSingleton(String name) {
		return applicationContext.isSingleton(name);
	}

	public static Class<? extends Object> getType(String name) {
		return applicationContext.getType(name);
	}

}

```

### 6.编写mapper，dao，service，service实现，控制层，定时任务测试bean
**mappers**目录下：
1.ScheduleJobExtandMapper.xml（包含了批量更新，批量删除）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.dynamicschedule.dao.ScheduleJobExtandMapper">
    <update id="updateBatch" >
         update schedule_job set status =#{status} where   job_id in
        <foreach collection="ids"  item="id" open="(" separator="," close=")"  >
                 #{id,jdbcType=INTEGER}
        </foreach>
    </update>

    <delete id="deleteBatch" parameterType="java.util.List">
        delete from schedule_job where job_id in
         <foreach collection="ids"  item="id" open="(" separator="," close=")" index="index"  >
             #{id,jdbcType=INTEGER}
         </foreach>

    </delete>
</mapper>

```

**dao**目录下：
2.ScheduleJobExtandMapper.java
```java
package com.example.dynamicschedule.dao;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ScheduleJobExtandMapper {

    int updateBatch(@Param("ids") List ids,@Param("status") int status);

    int deleteBatch(@Param("ids") List<Long> ids);
}

```

**service**目录下：
3.ScheduleJobService.java
```java
package com.example.dynamicschedule.service;

import com.example.dynamicschedule.bean.ScheduleJob;
import com.github.pagehelper.PageInfo;

import java.util.List;
import java.util.Map;

/**
 * 定时任务
 *
 */
public interface ScheduleJobService   {

	PageInfo queryPage(Map<String, Object> params);

	/**
	 * 保存定时任务
	 */
	void save(ScheduleJob scheduleJob);

	/**
	 * 更新定时任务
	 */
	void update(ScheduleJob scheduleJob);

	/**
	 * 批量删除定时任务
	 */
	void deleteBatch(List<Long> jobIds);

	/**
	 * 批量更新定时任务状态
	 */
	int updateBatch(List<Long> jobIds, int status);

	/**
	 * 立即执行
	 */
	void run(List<Long> jobIds);

	/**
	 * 暂停运行
	 */
	void pause(List<Long> jobIds);

	/**
	 * 恢复运行
	 */
	void resume(List<Long> jobIds);
}

```
4.ScheduleJobLogService.java
```java
package com.example.dynamicschedule.service;

import com.github.pagehelper.PageInfo;

import java.util.Map;

/**
 * 定时任务日志
 *
 */
public interface ScheduleJobLogService {

	PageInfo queryPage(Map<String, Object> params);

}

```
**impl**目录下：
5.ScheduleJobServiceImpl.java
```java
package com.example.dynamicschedule.service.impl;


import com.example.dynamicschedule.base.Constant;
import com.example.dynamicschedule.bean.ScheduleJob;
import com.example.dynamicschedule.bean.ScheduleJobExample;
import com.example.dynamicschedule.dao.ScheduleJobExtandMapper;
import com.example.dynamicschedule.dao.ScheduleJobMapper;
import com.example.dynamicschedule.service.ScheduleJobService;
import com.example.dynamicschedule.utils.ScheduleUtils;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.*;

@Service("scheduleJobService")
public class ScheduleJobServiceImpl  implements ScheduleJobService {
	@Autowired
    private Scheduler scheduler;

	@Autowired
	private ScheduleJobMapper scheduleJobMapper;

	@Autowired
	private ScheduleJobExtandMapper scheduleJobExtandMapper;

	/**
	 * 项目启动时，初始化定时器
	 */
	@PostConstruct
	public void init(){
		ScheduleJobExample scheduleJobExample = new ScheduleJobExample();

		List<ScheduleJob> scheduleJobList = scheduleJobMapper.selectByExample(scheduleJobExample);


		for(ScheduleJob scheduleJob : scheduleJobList){
			CronTrigger cronTrigger = ScheduleUtils.getCronTrigger(scheduler, scheduleJob.getJobId());
            //如果不存在，则创建
            if(cronTrigger == null) {
                ScheduleUtils.createScheduleJob(scheduler, scheduleJob);
            }else {
                ScheduleUtils.updateScheduleJob(scheduler, scheduleJob);
            }
		}
	}

	@Override
	public PageInfo queryPage(Map<String, Object> params) {
		String beanName = (String)params.get("beanName");
		int page = Integer.parseInt(params.getOrDefault("page", "1").toString());
		int pageSize = Integer.parseInt(params.getOrDefault("pageSize", "10").toString());
		PageHelper.startPage(page,pageSize);
		ScheduleJobExample scheduleJobExample = new ScheduleJobExample();
		scheduleJobExample.createCriteria().andBeanNameLike(beanName);
		List<ScheduleJob> scheduleJobs = scheduleJobMapper.selectByExample(scheduleJobExample);
		PageInfo pageInfo = new PageInfo<>(scheduleJobs);
		return pageInfo;
	}


	@Override
	@Transactional(rollbackFor = Exception.class)
	public void save(ScheduleJob scheduleJob) {
		scheduleJob.setCreateTime(new Date());
		scheduleJob.setStatus(Byte.parseByte(Constant.NORMAL+""));
		scheduleJobMapper.insertSelective(scheduleJob);
        ScheduleUtils.createScheduleJob(scheduler, scheduleJob);
    }

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void update(ScheduleJob scheduleJob) {
        ScheduleUtils.updateScheduleJob(scheduler, scheduleJob);
		scheduleJobMapper.updateByPrimaryKeySelective(scheduleJob);
    }

	@Override
	@Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> jobIds) {
    	for(Long jobId : jobIds){
    		ScheduleUtils.deleteScheduleJob(scheduler, jobId);
    	}

    	//删除数据
		scheduleJobExtandMapper.deleteBatch(jobIds);
	}

	@Override
    public int updateBatch(List<Long>  jobIds, int status){
    	return scheduleJobExtandMapper.updateBatch(jobIds,status);
    }

	@Override
	@Transactional(rollbackFor = Exception.class)
    public void run(List<Long> jobIds) {
    	for(Long jobId : jobIds){
    		ScheduleUtils.run(scheduler, scheduleJobMapper.selectByPrimaryKey(jobId));
    	}
    }

	@Override
	@Transactional(rollbackFor = Exception.class)
    public void pause(List<Long> jobIds) {
        for(Long jobId : jobIds){
    		ScheduleUtils.pauseJob(scheduler, jobId);
    	}

    	updateBatch(jobIds, Constant.PAUSE);
    }

	@Override
	@Transactional(rollbackFor = Exception.class)
    public void resume(List<Long> jobIds) {
    	for(Long jobId : jobIds){
    		ScheduleUtils.resumeJob(scheduler, jobId);
    	}

    	updateBatch(jobIds, Constant.NORMAL);
    }

}

```
6.ScheduleJobLogServiceImpl.java
```java
package com.example.dynamicschedule.service.impl;

import com.example.dynamicschedule.bean.ScheduleJobLog;
import com.example.dynamicschedule.bean.ScheduleJobLogExample;
import com.example.dynamicschedule.dao.ScheduleJobLogMapper;
import com.example.dynamicschedule.service.ScheduleJobLogService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service("scheduleJobLogService")
public class ScheduleJobLogServiceImpl implements ScheduleJobLogService {


	@Autowired
	private ScheduleJobLogMapper scheduleJobLogMapper;

	@Override
	public PageInfo queryPage(Map<String, Object> params) {
		Long jobId = Long.parseLong(params.get("jobId").toString());
		Integer page = Integer.parseInt(params.get("page").toString());
		Integer pageSize = Integer.parseInt(params.get("pageSize").toString());

		PageHelper.startPage(page,pageSize);
		ScheduleJobLogExample scheduleJobLogExample = new ScheduleJobLogExample();
		scheduleJobLogExample.createCriteria().andJobIdEqualTo(jobId);
		List<ScheduleJobLog> scheduleJobLogs = scheduleJobLogMapper.selectByExample(scheduleJobLogExample);
		PageInfo pageInfo = new PageInfo<>(scheduleJobLogs);
		return pageInfo;
	}

}

```
**task**目录下：
7.TestTask.java
```java
package com.example.dynamicschedule.task;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * 测试定时任务(演示Demo，可删除)
 *
 * testTask为spring bean的名称
 *
 */
@Component("testTask")
@Slf4j
public class TestTask {

	public void test(String params){
		log.info("我是带参数的test方法，正在被执行，参数为：" + params);

		try {
			Thread.sleep(1000L);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public void test2(){
		log.info("我是不带参数的test2方法，正在被执行");
	}
}

```

8.TestInterfaceTask.java
```java
package com.example.dynamicschedule.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("test2task")
@Slf4j
public class TestInterfaceTask {

    public void consoleLog(){
        log.info("通过测试接口 来控制定时任务");
    }
}

```

# 第三步 测试
### 1、测试添加
![在这里插入图片描述](https://img-blog.csdnimg.cn/20190329083617587.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2JlbG9uZ2h1YW5nMTU3NDA1,size_16,color_FFFFFF,t_70)
创建成功 后台日志：
```java
Creating a new SqlSession
Registering transaction synchronization for SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@23e1c137]
JDBC Connection [com.alibaba.druid.proxy.jdbc.ConnectionProxyImpl@286090c] will be managed by Spring
==>  Preparing: insert into schedule_job ( bean_name, method_name, cron_expression, status, remark, create_time ) values ( ?, ?, ?, ?, ?, ? ) 
==> Parameters: test2task(String), consoleLog(String), 0/6 * * * * ?(String), 0(Byte), 添加测试(String), 2019-01-02 09:47:58.141(Timestamp)
<==    Updates: 1
Releasing transactional SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@23e1c137]
Transaction synchronization committing SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@23e1c137]
Transaction synchronization deregistering SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@23e1c137]
Transaction synchronization closing SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@23e1c137]
2019-01-02 09:48:00.033  INFO 3200 --- [eduler_Worker-1] c.e.d.utils.ScheduleJobUtils             : 任务准备执行，任务ID：7
2019-01-02 09:48:00.047  INFO 3200 --- [pool-2-thread-1] c.e.d.task.TestInterfaceTask             : 通过测试接口 来控制定时任务
2019-01-02 09:48:00.048  INFO 3200 --- [eduler_Worker-1] c.e.d.utils.ScheduleJobUtils             : 任务执行完毕，任务ID：7  总共耗时：15毫秒
```
### 2、测试更新
![在这里插入图片描述](https://img-blog.csdnimg.cn/20190329083631916.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2JlbG9uZ2h1YW5nMTU3NDA1,size_16,color_FFFFFF,t_70)
更新成功 后台日志：
```java
Creating a new SqlSession
Registering transaction synchronization for SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@24cbeb4d]
JDBC Connection [com.alibaba.druid.proxy.jdbc.ConnectionProxyImpl@286090c] will be managed by Spring
==>  Preparing: update schedule_job SET bean_name = ?, method_name = ?, cron_expression = ?, status = ?, remark = ? where job_id = ? 
==> Parameters: test2task(String), consoleLog(String), 0/10 * * * * ?(String), 0(Byte), 更新测试(String), 7(Long)
<==    Updates: 1
Releasing transactional SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@24cbeb4d]
Transaction synchronization committing SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@24cbeb4d]
Transaction synchronization deregistering SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@24cbeb4d]
Transaction synchronization closing SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@24cbeb4d]
```

### 3、测试删除
![在这里插入图片描述](https://img-blog.csdnimg.cn/20190329083646291.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2JlbG9uZ2h1YW5nMTU3NDA1,size_16,color_FFFFFF,t_70)
删除成功 后台日志：
```java
Creating a new SqlSession
Registering transaction synchronization for SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@5e717c5a]
JDBC Connection [com.alibaba.druid.proxy.jdbc.ConnectionProxyImpl@286090c] will be managed by Spring
==>  Preparing: delete from schedule_job where job_id in ( ? ) 
==> Parameters: 6(Long)
<==    Updates: 0
Releasing transactional SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@5e717c5a]
Transaction synchronization committing SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@5e717c5a]
Transaction synchronization deregistering SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@5e717c5a]
Transaction synchronization closing SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@5e717c5a]
```
**ScheduleJobController**类中还有列表、批量删除，启动定时器，暂停定时器，激活定时器等等方法在本文中不再一一测试，请读者自行测试。


### 附录：
附录内容为 （4.由项目的**resources**的**generator**配置文件，通过pom下配置的工具**mybatis-generator-maven-plugin**生成） 的内容补充：对应生成文件，以及该配置文件
1.ScheduleJobMapper.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.dynamicschedule.dao.ScheduleJobMapper">
  <resultMap id="BaseResultMap" type="com.example.dynamicschedule.bean.ScheduleJob">
    <id column="job_id" jdbcType="BIGINT" property="jobId" />
    <result column="bean_name" jdbcType="VARCHAR" property="beanName" />
    <result column="method_name" jdbcType="VARCHAR" property="methodName" />
    <result column="params" jdbcType="VARCHAR" property="params" />
    <result column="cron_expression" jdbcType="VARCHAR" property="cronExpression" />
    <result column="status" jdbcType="TINYINT" property="status" />
    <result column="remark" jdbcType="VARCHAR" property="remark" />
    <result column="create_time" jdbcType="TIMESTAMP" property="createTime" />
  </resultMap>
  <sql id="Example_Where_Clause">
    <where>
      <foreach collection="oredCriteria" item="criteria" separator="or">
        <if test="criteria.valid">
          <trim prefix="(" prefixOverrides="and" suffix=")">
            <foreach collection="criteria.criteria" item="criterion">
              <choose>
                <when test="criterion.noValue">
                  and ${criterion.condition}
                </when>
                <when test="criterion.singleValue">
                  and ${criterion.condition} #{criterion.value}
                </when>
                <when test="criterion.betweenValue">
                  and ${criterion.condition} #{criterion.value} and #{criterion.secondValue}
                </when>
                <when test="criterion.listValue">
                  and ${criterion.condition}
                  <foreach close=")" collection="criterion.value" item="listItem" open="(" separator=",">
                    #{listItem}
                  </foreach>
                </when>
              </choose>
            </foreach>
          </trim>
        </if>
      </foreach>
    </where>
  </sql>
  <sql id="Update_By_Example_Where_Clause">
    <where>
      <foreach collection="example.oredCriteria" item="criteria" separator="or">
        <if test="criteria.valid">
          <trim prefix="(" prefixOverrides="and" suffix=")">
            <foreach collection="criteria.criteria" item="criterion">
              <choose>
                <when test="criterion.noValue">
                  and ${criterion.condition}
                </when>
                <when test="criterion.singleValue">
                  and ${criterion.condition} #{criterion.value}
                </when>
                <when test="criterion.betweenValue">
                  and ${criterion.condition} #{criterion.value} and #{criterion.secondValue}
                </when>
                <when test="criterion.listValue">
                  and ${criterion.condition}
                  <foreach close=")" collection="criterion.value" item="listItem" open="(" separator=",">
                    #{listItem}
                  </foreach>
                </when>
              </choose>
            </foreach>
          </trim>
        </if>
      </foreach>
    </where>
  </sql>
  <sql id="Base_Column_List">
    job_id, bean_name, method_name, params, cron_expression, status, remark, create_time
  </sql>
  <select id="selectByExample" parameterType="com.example.dynamicschedule.bean.ScheduleJobExample" resultMap="BaseResultMap">
    select
    <if test="distinct">
      distinct
    </if>
    <include refid="Base_Column_List" />
    from schedule_job
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
    <if test="orderByClause != null">
      order by ${orderByClause}
    </if>
  </select>
  <select id="selectByPrimaryKey" parameterType="java.lang.Long" resultMap="BaseResultMap">
    select 
    <include refid="Base_Column_List" />
    from schedule_job
    where job_id = #{jobId,jdbcType=BIGINT}
  </select>
  <delete id="deleteByPrimaryKey" parameterType="java.lang.Long">
    delete from schedule_job
    where job_id = #{jobId,jdbcType=BIGINT}
  </delete>
  <delete id="deleteByExample" parameterType="com.example.dynamicschedule.bean.ScheduleJobExample">
    delete from schedule_job
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
  </delete>
  <insert id="insert" keyColumn="job_id" keyProperty="jobId" parameterType="com.example.dynamicschedule.bean.ScheduleJob" useGeneratedKeys="true">
    insert into schedule_job (bean_name, method_name, params, 
      cron_expression, status, remark, 
      create_time)
    values (#{beanName,jdbcType=VARCHAR}, #{methodName,jdbcType=VARCHAR}, #{params,jdbcType=VARCHAR}, 
      #{cronExpression,jdbcType=VARCHAR}, #{status,jdbcType=TINYINT}, #{remark,jdbcType=VARCHAR}, 
      #{createTime,jdbcType=TIMESTAMP})
  </insert>
  <insert id="insertSelective" keyColumn="job_id" keyProperty="jobId" parameterType="com.example.dynamicschedule.bean.ScheduleJob" useGeneratedKeys="true">
    insert into schedule_job
    <trim prefix="(" suffix=")" suffixOverrides=",">
      <if test="beanName != null">
        bean_name,
      </if>
      <if test="methodName != null">
        method_name,
      </if>
      <if test="params != null">
        params,
      </if>
      <if test="cronExpression != null">
        cron_expression,
      </if>
      <if test="status != null">
        status,
      </if>
      <if test="remark != null">
        remark,
      </if>
      <if test="createTime != null">
        create_time,
      </if>
    </trim>
    <trim prefix="values (" suffix=")" suffixOverrides=",">
      <if test="beanName != null">
        #{beanName,jdbcType=VARCHAR},
      </if>
      <if test="methodName != null">
        #{methodName,jdbcType=VARCHAR},
      </if>
      <if test="params != null">
        #{params,jdbcType=VARCHAR},
      </if>
      <if test="cronExpression != null">
        #{cronExpression,jdbcType=VARCHAR},
      </if>
      <if test="status != null">
        #{status,jdbcType=TINYINT},
      </if>
      <if test="remark != null">
        #{remark,jdbcType=VARCHAR},
      </if>
      <if test="createTime != null">
        #{createTime,jdbcType=TIMESTAMP},
      </if>
    </trim>
  </insert>
  <select id="countByExample" parameterType="com.example.dynamicschedule.bean.ScheduleJobExample" resultType="java.lang.Long">
    select count(*) from schedule_job
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
  </select>
  <update id="updateByExampleSelective" parameterType="map">
    update schedule_job
    <set>
      <if test="record.jobId != null">
        job_id = #{record.jobId,jdbcType=BIGINT},
      </if>
      <if test="record.beanName != null">
        bean_name = #{record.beanName,jdbcType=VARCHAR},
      </if>
      <if test="record.methodName != null">
        method_name = #{record.methodName,jdbcType=VARCHAR},
      </if>
      <if test="record.params != null">
        params = #{record.params,jdbcType=VARCHAR},
      </if>
      <if test="record.cronExpression != null">
        cron_expression = #{record.cronExpression,jdbcType=VARCHAR},
      </if>
      <if test="record.status != null">
        status = #{record.status,jdbcType=TINYINT},
      </if>
      <if test="record.remark != null">
        remark = #{record.remark,jdbcType=VARCHAR},
      </if>
      <if test="record.createTime != null">
        create_time = #{record.createTime,jdbcType=TIMESTAMP},
      </if>
    </set>
    <if test="_parameter != null">
      <include refid="Update_By_Example_Where_Clause" />
    </if>
  </update>
  <update id="updateByExample" parameterType="map">
    update schedule_job
    set job_id = #{record.jobId,jdbcType=BIGINT},
      bean_name = #{record.beanName,jdbcType=VARCHAR},
      method_name = #{record.methodName,jdbcType=VARCHAR},
      params = #{record.params,jdbcType=VARCHAR},
      cron_expression = #{record.cronExpression,jdbcType=VARCHAR},
      status = #{record.status,jdbcType=TINYINT},
      remark = #{record.remark,jdbcType=VARCHAR},
      create_time = #{record.createTime,jdbcType=TIMESTAMP}
    <if test="_parameter != null">
      <include refid="Update_By_Example_Where_Clause" />
    </if>
  </update>
  <update id="updateByPrimaryKeySelective" parameterType="com.example.dynamicschedule.bean.ScheduleJob">
    update schedule_job
    <set>
      <if test="beanName != null">
        bean_name = #{beanName,jdbcType=VARCHAR},
      </if>
      <if test="methodName != null">
        method_name = #{methodName,jdbcType=VARCHAR},
      </if>
      <if test="params != null">
        params = #{params,jdbcType=VARCHAR},
      </if>
      <if test="cronExpression != null">
        cron_expression = #{cronExpression,jdbcType=VARCHAR},
      </if>
      <if test="status != null">
        status = #{status,jdbcType=TINYINT},
      </if>
      <if test="remark != null">
        remark = #{remark,jdbcType=VARCHAR},
      </if>
      <if test="createTime != null">
        create_time = #{createTime,jdbcType=TIMESTAMP},
      </if>
    </set>
    where job_id = #{jobId,jdbcType=BIGINT}
  </update>
  <update id="updateByPrimaryKey" parameterType="com.example.dynamicschedule.bean.ScheduleJob">
    update schedule_job
    set bean_name = #{beanName,jdbcType=VARCHAR},
      method_name = #{methodName,jdbcType=VARCHAR},
      params = #{params,jdbcType=VARCHAR},
      cron_expression = #{cronExpression,jdbcType=VARCHAR},
      status = #{status,jdbcType=TINYINT},
      remark = #{remark,jdbcType=VARCHAR},
      create_time = #{createTime,jdbcType=TIMESTAMP}
    where job_id = #{jobId,jdbcType=BIGINT}
  </update>
</mapper>
```
2.ScheduleJobLogMapper.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.dynamicschedule.dao.ScheduleJobLogMapper">
  <resultMap id="BaseResultMap" type="com.example.dynamicschedule.bean.ScheduleJobLog">
    <id column="log_id" jdbcType="BIGINT" property="logId" />
    <result column="job_id" jdbcType="BIGINT" property="jobId" />
    <result column="bean_name" jdbcType="VARCHAR" property="beanName" />
    <result column="method_name" jdbcType="VARCHAR" property="methodName" />
    <result column="params" jdbcType="VARCHAR" property="params" />
    <result column="status" jdbcType="TINYINT" property="status" />
    <result column="error" jdbcType="VARCHAR" property="error" />
    <result column="times" jdbcType="INTEGER" property="times" />
    <result column="create_time" jdbcType="TIMESTAMP" property="createTime" />
  </resultMap>
  <sql id="Example_Where_Clause">
    <where>
      <foreach collection="oredCriteria" item="criteria" separator="or">
        <if test="criteria.valid">
          <trim prefix="(" prefixOverrides="and" suffix=")">
            <foreach collection="criteria.criteria" item="criterion">
              <choose>
                <when test="criterion.noValue">
                  and ${criterion.condition}
                </when>
                <when test="criterion.singleValue">
                  and ${criterion.condition} #{criterion.value}
                </when>
                <when test="criterion.betweenValue">
                  and ${criterion.condition} #{criterion.value} and #{criterion.secondValue}
                </when>
                <when test="criterion.listValue">
                  and ${criterion.condition}
                  <foreach close=")" collection="criterion.value" item="listItem" open="(" separator=",">
                    #{listItem}
                  </foreach>
                </when>
              </choose>
            </foreach>
          </trim>
        </if>
      </foreach>
    </where>
  </sql>
  <sql id="Update_By_Example_Where_Clause">
    <where>
      <foreach collection="example.oredCriteria" item="criteria" separator="or">
        <if test="criteria.valid">
          <trim prefix="(" prefixOverrides="and" suffix=")">
            <foreach collection="criteria.criteria" item="criterion">
              <choose>
                <when test="criterion.noValue">
                  and ${criterion.condition}
                </when>
                <when test="criterion.singleValue">
                  and ${criterion.condition} #{criterion.value}
                </when>
                <when test="criterion.betweenValue">
                  and ${criterion.condition} #{criterion.value} and #{criterion.secondValue}
                </when>
                <when test="criterion.listValue">
                  and ${criterion.condition}
                  <foreach close=")" collection="criterion.value" item="listItem" open="(" separator=",">
                    #{listItem}
                  </foreach>
                </when>
              </choose>
            </foreach>
          </trim>
        </if>
      </foreach>
    </where>
  </sql>
  <sql id="Base_Column_List">
    log_id, job_id, bean_name, method_name, params, status, error, times, create_time
  </sql>
  <select id="selectByExample" parameterType="com.example.dynamicschedule.bean.ScheduleJobLogExample" resultMap="BaseResultMap">
    select
    <if test="distinct">
      distinct
    </if>
    <include refid="Base_Column_List" />
    from schedule_job_log
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
    <if test="orderByClause != null">
      order by ${orderByClause}
    </if>
  </select>
  <select id="selectByPrimaryKey" parameterType="java.lang.Long" resultMap="BaseResultMap">
    select 
    <include refid="Base_Column_List" />
    from schedule_job_log
    where log_id = #{logId,jdbcType=BIGINT}
  </select>
  <delete id="deleteByPrimaryKey" parameterType="java.lang.Long">
    delete from schedule_job_log
    where log_id = #{logId,jdbcType=BIGINT}
  </delete>
  <delete id="deleteByExample" parameterType="com.example.dynamicschedule.bean.ScheduleJobLogExample">
    delete from schedule_job_log
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
  </delete>
  <insert id="insert" keyColumn="log_id" keyProperty="logId" parameterType="com.example.dynamicschedule.bean.ScheduleJobLog" useGeneratedKeys="true">
    insert into schedule_job_log (job_id, bean_name, method_name, 
      params, status, error, 
      times, create_time)
    values (#{jobId,jdbcType=BIGINT}, #{beanName,jdbcType=VARCHAR}, #{methodName,jdbcType=VARCHAR}, 
      #{params,jdbcType=VARCHAR}, #{status,jdbcType=TINYINT}, #{error,jdbcType=VARCHAR}, 
      #{times,jdbcType=INTEGER}, #{createTime,jdbcType=TIMESTAMP})
  </insert>
  <insert id="insertSelective" keyColumn="log_id" keyProperty="logId" parameterType="com.example.dynamicschedule.bean.ScheduleJobLog" useGeneratedKeys="true">
    insert into schedule_job_log
    <trim prefix="(" suffix=")" suffixOverrides=",">
      <if test="jobId != null">
        job_id,
      </if>
      <if test="beanName != null">
        bean_name,
      </if>
      <if test="methodName != null">
        method_name,
      </if>
      <if test="params != null">
        params,
      </if>
      <if test="status != null">
        status,
      </if>
      <if test="error != null">
        error,
      </if>
      <if test="times != null">
        times,
      </if>
      <if test="createTime != null">
        create_time,
      </if>
    </trim>
    <trim prefix="values (" suffix=")" suffixOverrides=",">
      <if test="jobId != null">
        #{jobId,jdbcType=BIGINT},
      </if>
      <if test="beanName != null">
        #{beanName,jdbcType=VARCHAR},
      </if>
      <if test="methodName != null">
        #{methodName,jdbcType=VARCHAR},
      </if>
      <if test="params != null">
        #{params,jdbcType=VARCHAR},
      </if>
      <if test="status != null">
        #{status,jdbcType=TINYINT},
      </if>
      <if test="error != null">
        #{error,jdbcType=VARCHAR},
      </if>
      <if test="times != null">
        #{times,jdbcType=INTEGER},
      </if>
      <if test="createTime != null">
        #{createTime,jdbcType=TIMESTAMP},
      </if>
    </trim>
  </insert>
  <select id="countByExample" parameterType="com.example.dynamicschedule.bean.ScheduleJobLogExample" resultType="java.lang.Long">
    select count(*) from schedule_job_log
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
  </select>
  <update id="updateByExampleSelective" parameterType="map">
    update schedule_job_log
    <set>
      <if test="record.logId != null">
        log_id = #{record.logId,jdbcType=BIGINT},
      </if>
      <if test="record.jobId != null">
        job_id = #{record.jobId,jdbcType=BIGINT},
      </if>
      <if test="record.beanName != null">
        bean_name = #{record.beanName,jdbcType=VARCHAR},
      </if>
      <if test="record.methodName != null">
        method_name = #{record.methodName,jdbcType=VARCHAR},
      </if>
      <if test="record.params != null">
        params = #{record.params,jdbcType=VARCHAR},
      </if>
      <if test="record.status != null">
        status = #{record.status,jdbcType=TINYINT},
      </if>
      <if test="record.error != null">
        error = #{record.error,jdbcType=VARCHAR},
      </if>
      <if test="record.times != null">
        times = #{record.times,jdbcType=INTEGER},
      </if>
      <if test="record.createTime != null">
        create_time = #{record.createTime,jdbcType=TIMESTAMP},
      </if>
    </set>
    <if test="_parameter != null">
      <include refid="Update_By_Example_Where_Clause" />
    </if>
  </update>
  <update id="updateByExample" parameterType="map">
    update schedule_job_log
    set log_id = #{record.logId,jdbcType=BIGINT},
      job_id = #{record.jobId,jdbcType=BIGINT},
      bean_name = #{record.beanName,jdbcType=VARCHAR},
      method_name = #{record.methodName,jdbcType=VARCHAR},
      params = #{record.params,jdbcType=VARCHAR},
      status = #{record.status,jdbcType=TINYINT},
      error = #{record.error,jdbcType=VARCHAR},
      times = #{record.times,jdbcType=INTEGER},
      create_time = #{record.createTime,jdbcType=TIMESTAMP}
    <if test="_parameter != null">
      <include refid="Update_By_Example_Where_Clause" />
    </if>
  </update>
  <update id="updateByPrimaryKeySelective" parameterType="com.example.dynamicschedule.bean.ScheduleJobLog">
    update schedule_job_log
    <set>
      <if test="jobId != null">
        job_id = #{jobId,jdbcType=BIGINT},
      </if>
      <if test="beanName != null">
        bean_name = #{beanName,jdbcType=VARCHAR},
      </if>
      <if test="methodName != null">
        method_name = #{methodName,jdbcType=VARCHAR},
      </if>
      <if test="params != null">
        params = #{params,jdbcType=VARCHAR},
      </if>
      <if test="status != null">
        status = #{status,jdbcType=TINYINT},
      </if>
      <if test="error != null">
        error = #{error,jdbcType=VARCHAR},
      </if>
      <if test="times != null">
        times = #{times,jdbcType=INTEGER},
      </if>
      <if test="createTime != null">
        create_time = #{createTime,jdbcType=TIMESTAMP},
      </if>
    </set>
    where log_id = #{logId,jdbcType=BIGINT}
  </update>
  <update id="updateByPrimaryKey" parameterType="com.example.dynamicschedule.bean.ScheduleJobLog">
    update schedule_job_log
    set job_id = #{jobId,jdbcType=BIGINT},
      bean_name = #{beanName,jdbcType=VARCHAR},
      method_name = #{methodName,jdbcType=VARCHAR},
      params = #{params,jdbcType=VARCHAR},
      status = #{status,jdbcType=TINYINT},
      error = #{error,jdbcType=VARCHAR},
      times = #{times,jdbcType=INTEGER},
      create_time = #{createTime,jdbcType=TIMESTAMP}
    where log_id = #{logId,jdbcType=BIGINT}
  </update>
</mapper>
```
3.ScheduleJobMapper.java
```java
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.dynamicschedule.dao.ScheduleJobLogMapper">
  <resultMap id="BaseResultMap" type="com.example.dynamicschedule.bean.ScheduleJobLog">
    <id column="log_id" jdbcType="BIGINT" property="logId" />
    <result column="job_id" jdbcType="BIGINT" property="jobId" />
    <result column="bean_name" jdbcType="VARCHAR" property="beanName" />
    <result column="method_name" jdbcType="VARCHAR" property="methodName" />
    <result column="params" jdbcType="VARCHAR" property="params" />
    <result column="status" jdbcType="TINYINT" property="status" />
    <result column="error" jdbcType="VARCHAR" property="error" />
    <result column="times" jdbcType="INTEGER" property="times" />
    <result column="create_time" jdbcType="TIMESTAMP" property="createTime" />
  </resultMap>
  <sql id="Example_Where_Clause">
    <where>
      <foreach collection="oredCriteria" item="criteria" separator="or">
        <if test="criteria.valid">
          <trim prefix="(" prefixOverrides="and" suffix=")">
            <foreach collection="criteria.criteria" item="criterion">
              <choose>
                <when test="criterion.noValue">
                  and ${criterion.condition}
                </when>
                <when test="criterion.singleValue">
                  and ${criterion.condition} #{criterion.value}
                </when>
                <when test="criterion.betweenValue">
                  and ${criterion.condition} #{criterion.value} and #{criterion.secondValue}
                </when>
                <when test="criterion.listValue">
                  and ${criterion.condition}
                  <foreach close=")" collection="criterion.value" item="listItem" open="(" separator=",">
                    #{listItem}
                  </foreach>
                </when>
              </choose>
            </foreach>
          </trim>
        </if>
      </foreach>
    </where>
  </sql>
  <sql id="Update_By_Example_Where_Clause">
    <where>
      <foreach collection="example.oredCriteria" item="criteria" separator="or">
        <if test="criteria.valid">
          <trim prefix="(" prefixOverrides="and" suffix=")">
            <foreach collection="criteria.criteria" item="criterion">
              <choose>
                <when test="criterion.noValue">
                  and ${criterion.condition}
                </when>
                <when test="criterion.singleValue">
                  and ${criterion.condition} #{criterion.value}
                </when>
                <when test="criterion.betweenValue">
                  and ${criterion.condition} #{criterion.value} and #{criterion.secondValue}
                </when>
                <when test="criterion.listValue">
                  and ${criterion.condition}
                  <foreach close=")" collection="criterion.value" item="listItem" open="(" separator=",">
                    #{listItem}
                  </foreach>
                </when>
              </choose>
            </foreach>
          </trim>
        </if>
      </foreach>
    </where>
  </sql>
  <sql id="Base_Column_List">
    log_id, job_id, bean_name, method_name, params, status, error, times, create_time
  </sql>
  <select id="selectByExample" parameterType="com.example.dynamicschedule.bean.ScheduleJobLogExample" resultMap="BaseResultMap">
    select
    <if test="distinct">
      distinct
    </if>
    <include refid="Base_Column_List" />
    from schedule_job_log
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
    <if test="orderByClause != null">
      order by ${orderByClause}
    </if>
  </select>
  <select id="selectByPrimaryKey" parameterType="java.lang.Long" resultMap="BaseResultMap">
    select 
    <include refid="Base_Column_List" />
    from schedule_job_log
    where log_id = #{logId,jdbcType=BIGINT}
  </select>
  <delete id="deleteByPrimaryKey" parameterType="java.lang.Long">
    delete from schedule_job_log
    where log_id = #{logId,jdbcType=BIGINT}
  </delete>
  <delete id="deleteByExample" parameterType="com.example.dynamicschedule.bean.ScheduleJobLogExample">
    delete from schedule_job_log
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
  </delete>
  <insert id="insert" keyColumn="log_id" keyProperty="logId" parameterType="com.example.dynamicschedule.bean.ScheduleJobLog" useGeneratedKeys="true">
    insert into schedule_job_log (job_id, bean_name, method_name, 
      params, status, error, 
      times, create_time)
    values (#{jobId,jdbcType=BIGINT}, #{beanName,jdbcType=VARCHAR}, #{methodName,jdbcType=VARCHAR}, 
      #{params,jdbcType=VARCHAR}, #{status,jdbcType=TINYINT}, #{error,jdbcType=VARCHAR}, 
      #{times,jdbcType=INTEGER}, #{createTime,jdbcType=TIMESTAMP})
  </insert>
  <insert id="insertSelective" keyColumn="log_id" keyProperty="logId" parameterType="com.example.dynamicschedule.bean.ScheduleJobLog" useGeneratedKeys="true">
    insert into schedule_job_log
    <trim prefix="(" suffix=")" suffixOverrides=",">
      <if test="jobId != null">
        job_id,
      </if>
      <if test="beanName != null">
        bean_name,
      </if>
      <if test="methodName != null">
        method_name,
      </if>
      <if test="params != null">
        params,
      </if>
      <if test="status != null">
        status,
      </if>
      <if test="error != null">
        error,
      </if>
      <if test="times != null">
        times,
      </if>
      <if test="createTime != null">
        create_time,
      </if>
    </trim>
    <trim prefix="values (" suffix=")" suffixOverrides=",">
      <if test="jobId != null">
        #{jobId,jdbcType=BIGINT},
      </if>
      <if test="beanName != null">
        #{beanName,jdbcType=VARCHAR},
      </if>
      <if test="methodName != null">
        #{methodName,jdbcType=VARCHAR},
      </if>
      <if test="params != null">
        #{params,jdbcType=VARCHAR},
      </if>
      <if test="status != null">
        #{status,jdbcType=TINYINT},
      </if>
      <if test="error != null">
        #{error,jdbcType=VARCHAR},
      </if>
      <if test="times != null">
        #{times,jdbcType=INTEGER},
      </if>
      <if test="createTime != null">
        #{createTime,jdbcType=TIMESTAMP},
      </if>
    </trim>
  </insert>
  <select id="countByExample" parameterType="com.example.dynamicschedule.bean.ScheduleJobLogExample" resultType="java.lang.Long">
    select count(*) from schedule_job_log
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
  </select>
  <update id="updateByExampleSelective" parameterType="map">
    update schedule_job_log
    <set>
      <if test="record.logId != null">
        log_id = #{record.logId,jdbcType=BIGINT},
      </if>
      <if test="record.jobId != null">
        job_id = #{record.jobId,jdbcType=BIGINT},
      </if>
      <if test="record.beanName != null">
        bean_name = #{record.beanName,jdbcType=VARCHAR},
      </if>
      <if test="record.methodName != null">
        method_name = #{record.methodName,jdbcType=VARCHAR},
      </if>
      <if test="record.params != null">
        params = #{record.params,jdbcType=VARCHAR},
      </if>
      <if test="record.status != null">
        status = #{record.status,jdbcType=TINYINT},
      </if>
      <if test="record.error != null">
        error = #{record.error,jdbcType=VARCHAR},
      </if>
      <if test="record.times != null">
        times = #{record.times,jdbcType=INTEGER},
      </if>
      <if test="record.createTime != null">
        create_time = #{record.createTime,jdbcType=TIMESTAMP},
      </if>
    </set>
    <if test="_parameter != null">
      <include refid="Update_By_Example_Where_Clause" />
    </if>
  </update>
  <update id="updateByExample" parameterType="map">
    update schedule_job_log
    set log_id = #{record.logId,jdbcType=BIGINT},
      job_id = #{record.jobId,jdbcType=BIGINT},
      bean_name = #{record.beanName,jdbcType=VARCHAR},
      method_name = #{record.methodName,jdbcType=VARCHAR},
      params = #{record.params,jdbcType=VARCHAR},
      status = #{record.status,jdbcType=TINYINT},
      error = #{record.error,jdbcType=VARCHAR},
      times = #{record.times,jdbcType=INTEGER},
      create_time = #{record.createTime,jdbcType=TIMESTAMP}
    <if test="_parameter != null">
      <include refid="Update_By_Example_Where_Clause" />
    </if>
  </update>
  <update id="updateByPrimaryKeySelective" parameterType="com.example.dynamicschedule.bean.ScheduleJobLog">
    update schedule_job_log
    <set>
      <if test="jobId != null">
        job_id = #{jobId,jdbcType=BIGINT},
      </if>
      <if test="beanName != null">
        bean_name = #{beanName,jdbcType=VARCHAR},
      </if>
      <if test="methodName != null">
        method_name = #{methodName,jdbcType=VARCHAR},
      </if>
      <if test="params != null">
        params = #{params,jdbcType=VARCHAR},
      </if>
      <if test="status != null">
        status = #{status,jdbcType=TINYINT},
      </if>
      <if test="error != null">
        error = #{error,jdbcType=VARCHAR},
      </if>
      <if test="times != null">
        times = #{times,jdbcType=INTEGER},
      </if>
      <if test="createTime != null">
        create_time = #{createTime,jdbcType=TIMESTAMP},
      </if>
    </set>
    where log_id = #{logId,jdbcType=BIGINT}
  </update>
  <update id="updateByPrimaryKey" parameterType="com.example.dynamicschedule.bean.ScheduleJobLog">
    update schedule_job_log
    set job_id = #{jobId,jdbcType=BIGINT},
      bean_name = #{beanName,jdbcType=VARCHAR},
      method_name = #{methodName,jdbcType=VARCHAR},
      params = #{params,jdbcType=VARCHAR},
      status = #{status,jdbcType=TINYINT},
      error = #{error,jdbcType=VARCHAR},
      times = #{times,jdbcType=INTEGER},
      create_time = #{createTime,jdbcType=TIMESTAMP}
    where log_id = #{logId,jdbcType=BIGINT}
  </update>
</mapper>
```
4.ScheduleJobLogMapper.java
```java
package com.example.dynamicschedule.dao;

import com.example.dynamicschedule.bean.ScheduleJobLog;
import com.example.dynamicschedule.bean.ScheduleJobLogExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ScheduleJobLogMapper {
    long countByExample(ScheduleJobLogExample example);

    int deleteByExample(ScheduleJobLogExample example);

    int deleteByPrimaryKey(Long logId);

    int insert(ScheduleJobLog record);

    int insertSelective(ScheduleJobLog record);

    List<ScheduleJobLog> selectByExample(ScheduleJobLogExample example);

    ScheduleJobLog selectByPrimaryKey(Long logId);

    int updateByExampleSelective(@Param("record") ScheduleJobLog record, @Param("example") ScheduleJobLogExample example);

    int updateByExample(@Param("record") ScheduleJobLog record, @Param("example") ScheduleJobLogExample example);

    int updateByPrimaryKeySelective(ScheduleJobLog record);

    int updateByPrimaryKey(ScheduleJobLog record);
}
```
5.mybatis_generator_config.xml
```xml
jdbc.driverLocation=D:\\mavenLocal\\mysql\\mysql-connector-java\\5.1.43\\mysql-connector-java-5.1.43.jar
# 该驱动会导致 XXByPrimaryKey 生成失败
jdbc.driverClass=com.mysql.jdbc.Driver
jdbc.connectionURL=jdbc:mysql://47.99.200.71:3306/test?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&tinyInt1isBit=false
jdbc.userId=root
jdbc.password=123456

# defaultModelType
# 1，conditional：类似hierarchical；
# 2，flat：所有内容（主键，blob）等全部生成在一个对象中； default
# 3，hierarchical：主键生成一个XXKey对象(key class)，Blob等单独生成一个对象，其他简单属性在一个对象中(record class)
context.defaultModelType=flat
# targetRuntime:
# 1，MyBatis3：默认的值，生成基于MyBatis3.x以上版本的内容，包括XXXBySample；
# 2，MyBatis3Simple：类似MyBatis3，只是不生成XXXBySample；
context.targetRuntime=MyBatis3

# 相关表的配置
table.name=schedule_job_log
table.domainObjectName=ScheduleJobLog

java.model.generator.targetPackage=com.example.dynamicschedule.bean
java.client.generator.targetPackage=com.example.dynamicschedule.dao
sql.map.generator.targetPackage=mappers

table.enableInsert=true
table.enableDeleteByPrimaryKey=true
table.enableUpdateByPrimaryKey=true
table.enableSelectByPrimaryKey=true

table.enableDeleteByExample=true
table.enableUpdateByExample=true
table.enableSelectByExample=true
table.enableCountByExample=true
# column:主键的列名
table.generatedKey.column=log_id
# sqlStatement：要生成的selectKey语句，有以下可选项：
# Cloudscape:相当于selectKey的SQL为： VALUES IDENTITY_VAL_LOCAL()
# DB2       :相当于selectKey的SQL为： VALUES IDENTITY_VAL_LOCAL()
# DB2_MF    :相当于selectKey的SQL为：SELECT IDENTITY_VAL_LOCAL() FROM SYSIBM.SYSDUMMY1
# Derby      :相当于selectKey的SQL为：VALUES IDENTITY_VAL_LOCAL()
# HSQLDB      :相当于selectKey的SQL为：CALL IDENTITY()
# Informix  :相当于selectKey的SQL为：select dbinfo('sqlca.sqlerrd1') from systables where tabid=1
# MySql      :相当于selectKey的SQL为：SELECT LAST_INSERT_ID()
# SqlServer :相当于selectKey的SQL为：SELECT SCOPE_IDENTITY()
# SYBASE      :相当于selectKey的SQL为：SELECT @@IDENTITY
# JDBC      :相当于在生成的insert元素上添加useGeneratedKeys="true"和keyProperty属性
table.generatedKey.sqlStatement=JDBC


# 实体类生成的位置
# Model模型生成器,用来生成含有主键key的类，记录类 以及查询Example类
# targetPackage     指定生成的model生成所在的包名
# targetProject     指定在该项目下所在的路径
java.model.generator.targetProject=src/main/java

# *Mapper.xml 文件的位置
# Mapper映射文件生成所在的目录 为每一个数据库的表生成对应的SqlMap文件
sql.map.generator.targetProject=src/main/resources

# Mapper 接口文件的位置
# 客户端代码，生成易于使用的针对Model对象和XML配置文件 的代码
# type="ANNOTATEDMAPPER",生成Java Model 和基于注解的Mapper对象
# type="MIXEDMAPPER",生成基于注解的Java Model 和相应的Mapper对象
# type="XMLMAPPER",生成SQLMap XML文件和独立的Mapper接口
java.client.generator.targetProject=src/main/java
java.client.generator.type=XMLMAPPER

```
6.generator.properties
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE generatorConfiguration
        PUBLIC "-//mybatis.org//DTD MyBatis Generator Configuration 1.0//EN"
        "http://mybatis.org/dtd/mybatis-generator-config_1_0.dtd">
<!-- 配置生成器 -->
<generatorConfiguration>
    <!-- 可以用于加载配置项或者配置文件，在整个配置文件中就可以使用${propertyKey}的方式来引用配置项
    resource：配置资源加载地址，使用resource，MBG从classpath开始找，比如com/myproject/generatorConfig.properties
    url：配置资源加载地质，使用URL的方式，比如file:///C:/myfolder/generatorConfig.properties.
    注意，两个属性只能选址一个;
    另外，如果使用了mybatis-generator-maven-plugin，那么在pom.xml中定义的properties都可以直接在generatorConfig.xml中使用
    <properties resource="" url="" />
    -->
    <!-- 在MBG工作的时候，需要额外加载的依赖包
     location属性指明加载 jar/zip包的全路径-->
    <properties resource="generator/generator.properties"></properties>
    <!-- 本地数据库驱动程序jar包的全路径 -->
    <!--<classPathEntry location="${jdbc.driverLocation}"/>-->
    <!--
    context:生成一组对象的环境
    id:必选，上下文id，用于在生成错误时提示
    defaultModelType:指定生成对象的样式
        1，conditional：类似hierarchical；
        2，flat：所有内容（主键，blob）等全部生成在一个对象中；
        3，hierarchical：主键生成一个XXKey对象(key class)，Blob等单独生成一个对象，其他简单属性在一个对象中(record class)
    targetRuntime:
        1，MyBatis3：默认的值，生成基于MyBatis3.x以上版本的内容，包括XXXBySample；
        2，MyBatis3Simple：类似MyBatis3，只是不生成XXXBySample；
    introspectedColumnImpl：类全限定名，用于扩展MBG
    -->
    <context id="context" defaultModelType="${context.defaultModelType}" targetRuntime="${context.targetRuntime}" >
        <!-- 自动识别数据库关键字，默认false，如果设置为true，根据SqlReservedWords中定义的关键字列表；
        一般保留默认值，遇到数据库关键字（Java关键字），使用columnOverride覆盖
        -->
        <property name="autoDelimitKeywords" value="false" />
        <!-- 生成的Java文件的编码 -->
        <property name="javaFileEncoding" value="UTF-8" />
        <!-- 格式化java代码 -->
        <property name="javaFormatter" value="org.mybatis.generator.api.dom.DefaultJavaFormatter" />
        <!-- 格式化XML代码 -->
        <property name="xmlFormatter" value="org.mybatis.generator.api.dom.DefaultXmlFormatter" />

        <!-- beginningDelimiter和endingDelimiter：指明数据库的用于标记数据库对象名的符号，比如ORACLE就是双引号，MYSQL默认是`反引号； -->
        <property name="beginningDelimiter" value="`"/>
        <property name="endingDelimiter" value="`"/>


        <!--实体类增加序列化-->
        <plugin type="org.mybatis.generator.plugins.SerializablePlugin" ></plugin>

        <commentGenerator>
            <!-- 是否去除自动生成的注释 true：是 ： false:否 -->
            <property name="suppressAllComments" value="true"/>
            <!-- 这个元素用来去除指定生成的注释中是否包含生成的日期 false:表示保护 -->
            <!-- 如果生成日期，会造成即使修改一个字段，整个实体类所有属性都会发生变化，不利于版本控制，所以设置为true -->
            <property name="suppressDate" value="true" />
        </commentGenerator>
        <!-- 数据库的相关配置 -->
        <jdbcConnection driverClass="${jdbc.driverClass}" connectionURL="${jdbc.connectionURL}" userId="${jdbc.userId}"
                        password="${jdbc.password}" />
        <!-- 非必需，类型处理器，在数据库类型和java类型之间的转换控制-->
        <javaTypeResolver>
            <property name="forceBigDecimals" value="false" />
        </javaTypeResolver>

        <!-- 实体类生成的位置 -->
        <!-- Model模型生成器,用来生成含有主键key的类，记录类 以及查询Example类
          targetPackage     指定生成的model生成所在的包名
          targetProject     指定在该项目下所在的路径
      -->
        <javaModelGenerator targetPackage="${java.model.generator.targetPackage}"
                            targetProject="${java.model.generator.targetProject}">
            <!--  for MyBatis3/MyBatis3Simple
              自动为每一个生成的类创建一个构造方法，构造方法包含了所有的field；而不是使用setter；
            -->
            <property name="constructorBased" value="false"/>
            <!-- 是否允许子包，即targetPackage.schemaName.tableName -->
            <property name="enableSubPackages" value="false" />
            <!-- 建立的Model对象是否 不可改变  即生成的Model对象不会有 setter方法，只有构造方法 -->
            <property name="immutable" value="false"/>
            <!-- 给Model添加一个父类 -->
            <!--<property name="rootClass" value="com.foo.louis.Hello"/>-->
            <!-- 是否对类CHAR类型的列的数据进行trim操作 -->
            <property name="trimStrings" value="true" />
        </javaModelGenerator>

        <!-- *Mapper.xml 文件的位置 -->
        <!--Mapper映射文件生成所在的目录 为每一个数据库的表生成对应的SqlMap文件 -->
        <sqlMapGenerator targetPackage="${sql.map.generator.targetPackage}"
                         targetProject="${sql.map.generator.targetProject}">
            <property name="enableSubPackages" value="false" />
        </sqlMapGenerator>

        <!-- Mapper 接口文件的位置 -->
        <!-- 客户端代码，生成易于使用的针对Model对象和XML配置文件 的代码
               type="ANNOTATEDMAPPER",生成Java Model 和基于注解的Mapper对象
               type="MIXEDMAPPER",生成基于注解的Java Model 和相应的Mapper对象
               type="XMLMAPPER",生成SQLMap XML文件和独立的Mapper接口
       -->
        <javaClientGenerator targetPackage="${java.client.generator.targetPackage}"
                             targetProject="${java.client.generator.targetProject}"
                             type="${java.client.generator.type}">
            <property name="enableSubPackages" value="false" />
        </javaClientGenerator>

        <!-- 相关表的配置 -->
        <table
                tableName="${table.name}"
                domainObjectName="${table.domainObjectName}"
                enableInsert="${table.enableInsert}"
                enableDeleteByPrimaryKey="${table.enableDeleteByPrimaryKey}"
                enableUpdateByPrimaryKey="${table.enableUpdateByPrimaryKey}"
                enableSelectByPrimaryKey="${table.enableSelectByPrimaryKey}"

                enableDeleteByExample="${table.enableDeleteByExample}"
                enableUpdateByExample="${table.enableUpdateByExample}"
                enableSelectByExample="${table.enableSelectByExample}"
                enableCountByExample="${table.enableCountByExample}"
        >

        <!-- generatedKey用于生成生成主键的方法，
          如果设置了该元素，MBG会在生成的<insert>元素中生成一条正确的<selectKey>元素，该元素可选
          column:主键的列名；
          sqlStatement：要生成的selectKey语句，有以下可选项：
              Cloudscape:相当于selectKey的SQL为： VALUES IDENTITY_VAL_LOCAL()
              DB2       :相当于selectKey的SQL为： VALUES IDENTITY_VAL_LOCAL()
              DB2_MF    :相当于selectKey的SQL为：SELECT IDENTITY_VAL_LOCAL() FROM SYSIBM.SYSDUMMY1
              Derby      :相当于selectKey的SQL为：VALUES IDENTITY_VAL_LOCAL()
              HSQLDB      :相当于selectKey的SQL为：CALL IDENTITY()
              Informix  :相当于selectKey的SQL为：select dbinfo('sqlca.sqlerrd1') from systables where tabid=1
              MySql      :相当于selectKey的SQL为：SELECT LAST_INSERT_ID()
              SqlServer :相当于selectKey的SQL为：SELECT SCOPE_IDENTITY()
              SYBASE      :相当于selectKey的SQL为：SELECT @@IDENTITY
              JDBC      :相当于在生成的insert元素上添加useGeneratedKeys="true"和keyProperty属性
      <generatedKey column="" sqlStatement=""/>
       -->
            <generatedKey column="${table.generatedKey.column}" sqlStatement="${table.generatedKey.sqlStatement}" identity="true" />
        </table>
    </context>
</generatorConfiguration>
```
