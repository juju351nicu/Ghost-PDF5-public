package com.clip.ghost;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ghost-PDF5のSpring Boot起動クラス。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Application {
	/**
	 * アプリケーションを起動する。
	 *
	 * @param args 起動引数
	 */
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
