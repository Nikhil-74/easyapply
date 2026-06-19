package com.easyapply;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.easyapply.config.AiProperties;
import com.easyapply.config.DataProperties;
import com.easyapply.config.MailProperties;
import com.easyapply.config.SeleniumProperties;
import com.easyapply.config.UserProperties;

@SpringBootApplication
@EnableConfigurationProperties({
		UserProperties.class,
		MailProperties.class,
		DataProperties.class,
		SeleniumProperties.class,
		AiProperties.class
})
public class EasyapplyApplication {

	public static void main(String[] args) {
		SpringApplication.run(EasyapplyApplication.class, args);
	}
}
