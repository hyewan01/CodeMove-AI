package com.codemove.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.codemove.customer.web.CustomerLoginInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new CustomerLoginInterceptor())
				.addPathPatterns("/quote/**", "/move/**", "/chat/**", "/more/**", "/notifications/**");
	}
}
