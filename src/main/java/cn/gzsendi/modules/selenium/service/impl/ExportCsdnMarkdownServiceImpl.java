package cn.gzsendi.modules.selenium.service.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cn.gzsendi.modules.framework.utils.DownloadImageHttpclientUtils;
import cn.gzsendi.modules.selenium.service.ExportCsdnMarkdownService;

@Service
public class ExportCsdnMarkdownServiceImpl implements ExportCsdnMarkdownService{
	
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Value("${exportDir}")
	private String exportDir;
	
	@Value("${downImageAndGenerateNewFile:false}")
	private Boolean downImageAndGenerateNewFile;
	
	@Value("${csdnUserName}")
	private String csdnUserName;
	
	@Value("${csdnPwd}")
	private String csdnPwd;
	
	@Override
	public void exportStart() throws Exception {
		
		//0.开始进行CSDN文章的自动化导出操作
		logger.info("csdnMarkdown exportStart....");
		
		WebDriver webDriver = null;
		Scanner in = new Scanner(System.in);
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
			Thread.sleep(3000l);
			logger.info(webDriver.getTitle());
			
			//3.手工登陆CSDN,需要人工介入一下（滑块滑动，暂时没自动化处理）
			loginWaitMeOperate(webDriver,in);
			
			//4.现在已经登陆成功了，打开内容管理的首页
			String myPage = "https://mp.csdn.net/mp_blog/manage/article";
			webDriver.get(myPage);
			Thread.sleep(3000l);
			logger.info(webDriver.getTitle());
			
			//4.有个调研的div挡住了内容，不处理会报错，因此用js控制一下不显示
		    ((JavascriptExecutor)webDriver).executeScript("document.getElementById(\"nps-box\").style.display=\"none\";");
		    Thread.sleep(2000l);
			
			//5.从第一页文章开始进行处理，直到最后一页
			exportAllCsdnArticles(webDriver,in);
			logger.info("all completed.");
			
		} catch (Exception e) {
			logger.error("errror",e);
		} finally {
			if(webDriver!=null) webDriver.quit();
			in.close();
		}
		
		//关闭image下载工具类的连接池
		DownloadImageHttpclientUtils.closeConnectionPool();
		logger.info("csdnMarkdown exportEnd.");
		
	}
	
	private void exportAllCsdnArticles(WebDriver webDriver,Scanner in) throws Exception{
		
		String mainHandle = webDriver.getWindowHandle();
		
		while(true){

			//1.读取第一页的文章
			List<WebElement> list = webDriver.findElements(By.cssSelector("#view-containe > div > div > div.article_manage_list > div:nth-child(2) > div.article-list-item-mp"));
			for(WebElement element : list){
				
				WebElement aElem = element.findElement(By.tagName("a"));//a标签
				logger.info(aElem.getAttribute("href"));
				String articleTitle = aElem.getText();//文章标题
				
				//找到每一篇文章的编辑按钮
				WebElement editButton = element.findElement(By.linkText("编辑"));
				//点击编辑按钮，会新开一个浏览器窗口
				editButton.click();
				
				//点击编辑后只有两个窗口，通过窗口的句柄是否等于默认窗口判断是否切换
				Set<String> Handles = webDriver.getWindowHandles();
				for (String hand : Handles) {
					if (!hand.equals(mainHandle)) {
						logger.info("-----------------" + hand);
						webDriver.switchTo().window(hand);
						break;
					}
				}
				Thread.sleep(5000l);
				
				//解析内容并下载到本地
				logger.info("export Article: {}" , articleTitle);
				//先发送ctrl+A进行选择
			    WebElement preEle = webDriver.findElement(By.cssSelector("body > div.app.app--light > div.layout > div.layout__panel.flex.flex--row > div > div.layout__panel.flex.flex--row > div.layout__panel.layout__panel--editor > div.editor > pre > div:nth-child(1)"));
			    preEle.sendKeys(Keys.CONTROL,"a");
			    Thread.sleep(2000);
			    //再发送ctrl+C进行复制
			    preEle.sendKeys(Keys.CONTROL,"c");
			    Thread.sleep(2000);

			    // 获取剪贴板中的内容
			    //由于找不到直接获取剪切版的内容的方法，采用曲线救国的方法，先打开http://www.ku51.net/ox2str/网站，然后将复制好的内容粘贴到这个网站的一个文本域中，再取出来
			    webDriver.get("http://www.ku51.net/ox2str/");
			    Thread.sleep(2000);
			    WebElement inputstr = webDriver.findElement(By.id("inputstr"));
			    inputstr.sendKeys(Keys.CONTROL,"v");//先ctrl+v粘贴，然后取出内容出来
			    Thread.sleep(2000);
			    //通过执行Js代码获取刚才复制进去的markdown文本内容
			    String textStr = ((JavascriptExecutor)webDriver).executeScript("return document.getElementById(\"inputstr\").value;").toString();
			    
			    //将内容写入markdown本地文件,放在csdnArticles目录下
			    FileUtils.write(new File(exportDir + "/csdnArticles/" + articleTitle + ".md" ), textStr, StandardCharsets.UTF_8.name());
			    
			    //上面写入的markdown文件的图片地址还是csdn网址，如果我们也希望将图片本地存储的话，就进一步处理下载图片，并替换markdown中关于图片链接的部分，重新生成新的文件
			    //放在localArticles目录下
			    if(downImageAndGenerateNewFile){
			    	downImageAndGenerateNewFile(articleTitle,textStr);
			    }
			    
			    //关掉当前打开的文章的窗口
				webDriver.close();
				
				//最后切换回内容管理的主页面，继续进行下一篇文章的处理
				webDriver.switchTo().window(mainHandle);
				
			}
			
			//定位下一页的右箭头按钮,看看还有没有下一页的数据，如果disabled=true说明没有下一页了
			WebElement nextPage = webDriver.findElement(By.cssSelector("#view-containe > div > div > div:nth-child(4) > div > button.btn-next"));
			if("true".equals(nextPage.getAttribute("disabled"))){
				break;
			}
			
			//点击到下一页进行处理
			nextPage.click();
			Thread.sleep(5000l);
			
		}
		
	}
	
	//重新下载图片到本地并生成本地markdown格式，图片引用本地的图片，并重新生成新的文件
	private void downImageAndGenerateNewFile(String articleTitle,String textStr) throws IOException{
		
		String localArticlesDir = exportDir + "/localArticles";
		String localFileDir = localArticlesDir + "/" + articleTitle + ".md";
		String assetsDir = localFileDir + "/assets";
		File pathDir = new File(assetsDir);
		if(!pathDir.exists()){
			pathDir.mkdirs();
		}
		
		//提取出Image中的图片文件名称
		String tempImagePattern = "!\\[(.*)\\]\\(https://img-blog.csdnimg.cn/(\\w+)\\.png.*\\)";
		Pattern r = Pattern.compile(tempImagePattern);
		Matcher m = r.matcher(textStr);
		
		//找出所有带csdn地址的图片下载到本地
		while(m.find()){
			String imageName = m.group(2);
			String imageUrl = "https://img-blog.csdnimg.cn/"+imageName+".png";
			String fileName = imageName + ".png";
			DownloadImageHttpclientUtils.downloadPicture(imageUrl, fileName, assetsDir);
		}
		
		//将文章内容的带csdn路径的图片替换成本地图片的assets目录
		textStr = textStr.replaceAll("https://img-blog.csdnimg.cn", "assets");
		
		//最后将新的带本地路径的markdown内容重新写到新的文件
		FileUtils.write(new File(localFileDir+ "/" + articleTitle + ".md"), textStr, StandardCharsets.UTF_8.name());
		
	}
	
	//登录操作，有一点点的手工介入
	private void loginWaitMeOperate(WebDriver webDriver,Scanner in) throws Exception{
		
		//找到登录/注册的链接，点击一下
		WebElement linkTextLoginAndRegister = webDriver.findElement(By.linkText("登录/注册"));
		linkTextLoginAndRegister.click();
		Thread.sleep(5000l);
		
		//最新版本的csdn使用了iframe进行登陆表单的显示，因为先找到iframe，然后切换到iframe后才能继续
		WebElement iframeElement = webDriver.findElement(By.cssSelector("iframe[name=passport_iframe]"));
		webDriver.switchTo().frame(iframeElement);
		Thread.sleep(2000l);
		
		//找到密码登陆，点击一下
		WebElement pwdLoginText = webDriver.findElement(By.cssSelector("div.login-box-tabs > div.login-box-tabs-items > span:nth-child(4)"));
		pwdLoginText.click();
		Thread.sleep(2000l);
		
		//找到用户名，输入csdn账号
		WebElement usernameInput = webDriver.findElement(By.cssSelector("div.base-input > input[autocomplete=\"username\"]"));
		usernameInput.sendKeys(csdnUserName);
		Thread.sleep(2000l);
		
		//找到密码，填写
		WebElement passwordInput = webDriver.findElement(By.cssSelector("div.base-input > input[autocomplete=\"current-password\"]"));
		passwordInput.sendKeys(csdnPwd);
		passwordInput.sendKeys(Keys.ENTER);//直接输入回车
		Thread.sleep(2000l);
		
		//滑块的还没有进行自动化处理，先手工介入拖动滑块.循环判断有没有人工登陆好，有的话程序就继续
		while(true){
			
			//检查登陆/注册的这个元素是不是还在，不存在的话说明已经登陆成功
			try {
				webDriver.findElement(By.linkText("登录/注册"));
				Thread.sleep(3000l);
				logger.info("wait login......");
			} catch (NoSuchElementException e) {
				//找不到这个远素说明登陆成功了，退出循环
				break;
			}
			
			/**下面注解的是通过人工输入yes来决定登陆成功与否*/
			/*System.out.println("Do you has operate finish, input yes or no ?");
            if(in.hasNextLine()){
            	if("yes".equals(in.nextLine())){
            		break;
            	}
            }else{
            	Thread.sleep(3000l);
            }*/
        }
		
		logger.info("login success. now do other things.");
		
	}

}
