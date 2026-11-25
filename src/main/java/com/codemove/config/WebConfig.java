package com.codemove.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.codemove.admin.web.AdminLoginInterceptor;
import com.codemove.auth.KeepLoginInterceptor;
import com.codemove.auth.LoginDevices;
import com.codemove.common.FileStore;
import com.codemove.customer.social.SocialLoginProperties;
import com.codemove.customer.web.CustomerLoginInterceptor;

@Configuration
@EnableConfigurationProperties(SocialLoginProperties.class)
public class WebConfig implements WebMvcConfigurer {

	private final FileStore fileStore;
	private final LoginDevices devices;

	public WebConfig(FileStore fileStore, LoginDevices devices) {
		this.fileStore = fileStore;
		this.devices = devices;
	}

	/** 업로드 파일 (FileStore) */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler(FileStore.URL_PREFIX + "**")
				.addResourceLocations(fileStore.root().toUri().toString() + "/");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new KeepLoginInterceptor(devices)).addPathPatterns("/**")
				.excludePathPatterns("/assets/**", "/css/**", "/js/**", "/vendor/**", FileStore.URL_PREFIX + "**", "/error");
		registry.addInterceptor(new CustomerLoginInterceptor())
				.addPathPatterns("/quote/**", "/move/**", "/chat/**", "/more/**", "/notifications/**");
		registry.addInterceptor(new AdminLoginInterceptor())
				.addPathPatterns("/admin", "/admin/**");
	}
}
