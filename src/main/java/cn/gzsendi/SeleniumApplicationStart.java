package cn.gzsendi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import cn.gzsendi.modules.selenium.service.ExportCsdnMarkdownService;

/**
 * 此SpringBoot工程只引用了spring-boot-starter，执行完main方法程序就结束，也不占用端口
 */
@SpringBootApplication
public class SeleniumApplicationStart {

	public static void main(String[] args) throws Exception {
		
		ConfigurableApplicationContext ctx = SpringApplication.run(SeleniumApplicationStart.class, args);
		ExportCsdnMarkdownService exportService = ctx.getBean(ExportCsdnMarkdownService.class);
		exportService.exportStart();
	}

}
