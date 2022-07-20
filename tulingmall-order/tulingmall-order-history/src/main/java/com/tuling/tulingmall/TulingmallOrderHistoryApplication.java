package com.tuling.tulingmall;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;

//不使用ShardingSphere。SpringBootConfiguration会往Spring中注入ShardingDataSource
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class})
//@SpringBootApplication
@EnableFeignClients
public class TulingmallOrderHistoryApplication {

	private static ApplicationContext applicationContext;

	public static void main(String[] args) {
		applicationContext = SpringApplication.run(TulingmallOrderHistoryApplication.class, args);
	}

	public static <T> T getBean(String beanName) {
		return applicationContext != null ? (T) applicationContext.getBean(beanName) : null;
	}


}
