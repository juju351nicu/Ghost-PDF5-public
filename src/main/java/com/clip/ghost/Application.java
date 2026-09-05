package com.clip.ghost;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ghost-PDF5のSpring Boot起動クラス。
 */
@SpringBootApplication
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
