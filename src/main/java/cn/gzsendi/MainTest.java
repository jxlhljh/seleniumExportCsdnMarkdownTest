package cn.gzsendi;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class MainTest {
	public static void main(String[] args) throws InterruptedException {
		WebDriver webDriver = null;
		try {
			//1.设置chromedirver 的存放位置
			//System.getProperties().setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver"); linux
			System.getProperties().setProperty("webdriver.chrome.driver", "C:/software/seleniumtest/chromedriver.exe"); //windows
			ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--disable-blink-features=AutomationControlled");//滑块验证码不生效的解决
			chromeOptions.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});//chrome正受到自动测试软件的控制的提示去除
			webDriver = new ChromeDriver(chromeOptions);
			//最大化浏览器窗口
			webDriver.manage().window().maximize();
			//2.模拟打开www.csdn.net页面进行测试
			String url = "https://www.csdn.net/";
			webDriver.get(url);
			Thread.sleep(5000l);
			System.out.println(webDriver.getTitle());
		} catch (Exception e) {
			System.out.println(e.getClass());
			e.printStackTrace();
		} finally {
			if(webDriver!=null) webDriver.close();
			if(webDriver!=null) webDriver.quit();
		}
	}
}
