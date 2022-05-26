@[TOC](Java使用selenium批量导出CSDN文章markdown格式到本地)
<hr style=" border:solid; width:100px; height:1px;" color=#000000 size=1">

# 一、需求背景
现在我csdn上写的文章越来越多，但只存在csdn上，还是感觉不保险，万一哪天csdn网站出错，很多文章想找回来就难了，当前csdn上已支持单篇文章的导出，但文章太多，一篇一篇导出不太现实，因此基于这个需求，希望能把csdn上的文章的markdown内容批量导出到本地备份。
>需求：包含csdn上的文章markdown内容，以及文章里面的图片，都希望转换到本地上进行存储备份。
```
使用技术：Java、Selenium框架
```
>整体流程：
1.通过webdriver进行人工加自动结合进行csdn网址登陆
2.打开内容管理首页，可以看到你所有的文章，分页显示
3.循环处理所有页的文章进行内容提取与写文件
4.点编辑处理单个文章的内容提取，通过自动化操作ctrl+A,ctrl+C,ctrl+V获取你写的文章的markdown文本
5.通过Java将文章的markdown内容写入文件

# 二、基础Springboot工程准备（依赖、驱动等）
## 1、新建springboot工程
通过https://start.spring.io/生成springboot基础工程脚手架或拷贝一个已有的改。
## 2、引入Selenium依赖
```c
		<!-- selenium start -->
		<dependency>
		    <groupId>org.seleniumhq.selenium</groupId>
		    <artifactId>selenium-java</artifactId>
		</dependency>
		<!--selenium end-->
```
## 3.下载selenium的java驱动
```
去https://chromedriver.storage.googleapis.com/index.html找到与浏览器对应版本的驱动进行下载

我的浏览器用的chrome(102.0.5005.63)是因此下载如下Window版本
https://chromedriver.storage.googleapis.com/102.0.5005.27/chromedriver_win32.zip
```
## 4.编写代码进行环境的测试
主要驱动引入代码`System.getProperties().setProperty("webdriver.chrome.driver", "C:/software/seleniumtest/chromedriver.exe"); `
```c
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
			webDriver = new ChromeDriver(chromeOptions);
			//最大化浏览器窗口
			webDriver.manage().window().maximize();
			//2.模拟打开www.csdn.net页面进行测试
			String url = "https://www.csdn.net/";
			webDriver.get(url);
			Thread.sleep(3000l);
			System.out.println(webDriver.getTitle());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(webDriver!=null) webDriver.quit();
			if(webDriver!=null) webDriver.close();
		}
	}
}
```
# 三、正式开始通过Selenium进行csdn文章导出
## 1.webdriver相关参数设置
主要进行chrome正受到自动测试软件的控制的提示去除和滑块验证码不生效的解决，通过参数进行设置
```c
System.getProperties().setProperty("webdriver.chrome.driver", "C:/software/seleniumtest/chromedriver.exe"); //windows
ChromeOptions chromeOptions = new ChromeOptions();
chromeOptions.addArguments("--disable-blink-features=AutomationControlled");//滑块验证码不生效的解决
chromeOptions.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});//chrome正受到自动测试软件的控制的提示去除
webDriver = new ChromeDriver(chromeOptions);
```
最大化浏览器窗口
```c
//最大化浏览器窗口
webDriver.manage().window().maximize();
```
## 2. 打开csdn主页地址
这个较简单，执行webDriver.get(url)即可
```c
//2.模拟打开www.csdn.net页面进行测试
String url = "https://www.csdn.net/";
webDriver.get(url);
```
## 3. 登陆csdn
采用自动+人工结合的方式，这里我暂时没有处理滑块的自动化，后续看继续完善
>登录/注册->密码登录->输入账号和密码->点击登陆按钮->人工处理滑块的拖动
![在这里插入图片描述](https://img-blog.csdnimg.cn/a47e53e7118c450e8200209f1920e120.png)

滑块操作时循环等人工拖放滑块并登陆，通过判断有没有`登陆/注册`这个元素来判断是不是登陆成功，登陆成功后退出循环
```
	//登录操作，有一点点的手工介入
	private void loginWaitMeOperate(WebDriver webDriver,Scanner in) throws Exception{
		
		//找到登录/注册的链接，点击一下
		WebElement linkTextLoginAndRegister = webDriver.findElement(By.linkText("登录/注册"));
		linkTextLoginAndRegister.click();
		Thread.sleep(5000l);
		
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
        }
		logger.info("login success. now do other things.");
	}

}
```
## 4. 进入内容管理主页
成功登陆后，通过手工进入个人中心->内容管理，看到内容管理的地址比较固定，地址为：`https://mp.csdn.net/mp_blog/manage/article`,因此登陆后，可以通过webDriver直接访问这个地址进到内容管理主页
![在这里插入图片描述](https://img-blog.csdnimg.cn/3dbd226264f44f4ba49e76054626fa07.png)
代码如下：
```c
			//4.现在已经登陆成功了，打开内容管理的首页
			String myPage = "https://mp.csdn.net/mp_blog/manage/article";
			webDriver.get(myPage);
			Thread.sleep(3000l);
			logger.info(webDriver.getTitle());
```
## 5.接下来分页遍历所有的文章处理
主要操作最下面的下一页的按钮来进到下一页，怎么判断到了最后一页呢，通过定位查看dom，发现到最后一页时，button元素的属性`disabled=disabled(webdriver取出来为true)`,可通过这个来判断是不是到达了最后一页
![在这里插入图片描述](https://img-blog.csdnimg.cn/1d68da2c950c4de3959879a3a61d8b12.png)
![在这里插入图片描述](https://img-blog.csdnimg.cn/e7b57b51aad94b04982f51912f6cc189.png)
```c
			//定位下一页的右箭头按钮,看看还有没有下一页的数据，如果disabled=true说明没有下一页了
			WebElement nextPage = webDriver.findElement(By.cssSelector("#view-containe > div > div > div:nth-child(4) > div > button.btn-next"));
			if("true".equals(nextPage.getAttribute("disabled"))){
				break;
			}
			
			//点击到下一页进行处理
			nextPage.click();
			Thread.sleep(5000l);
```
## 6.对单篇文章详细的处理，获取markdown内容文本
通过selenium点击编辑按钮，打开文章的编辑详情，通过定位ctrl+A, ctrl+C,Ctrl+v，借助第三方网站的文本框暂时存内容，取出来处理
![在这里插入图片描述](https://img-blog.csdnimg.cn/3440820759044621b54133dfe8b33d03.png)

`ctrl+C`后，需要借助一下另一个网站的文本框，通过执行`ctrl+V`粘贴进去，最后通过
调用webdriver执行js获取出来进行后续的使用。
![在这里插入图片描述](https://img-blog.csdnimg.cn/92b800533cfd456eb26f87547cc072d3.png)
关键代码如下：
```c
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
```
## 7. 将markdown内容文本地写入文件
拿到了markdown内容文本后，就简单了，直接使用工具类将文本写入文件就可
```c
	//将内容写入markdown本地文件,放在csdnArticles目录下
	FileUtils.write(new File(exportDir + "/csdnArticles/" + articleTitle + ".md" ), textStr, StandardCharsets.UTF_8.name());
```
## 8. 处理下载图片到本地，转换成不依赖csdn图库
上面已经完成了文章的导出，但图片仍在csdn网站，可以考虑进一步处理下载到本地，这样不依赖csdn网址了
`通过正则表达式查找与替换`
![在这里插入图片描述](https://img-blog.csdnimg.cn/14feb571e63a484482cd4f1ed5506fec.png)
![在这里插入图片描述](https://img-blog.csdnimg.cn/a4fb2c524cad4094866cb5df30a9d701.png)
![在这里插入图片描述](https://img-blog.csdnimg.cn/ef6121ec2e644fd8bfebe85d8f7d4e7f.png)
`通过上面的处理后，每篇文章一个目录，里面有个assets文件目录，存放这篇文章的所有图片`
![在这里插入图片描述](https://img-blog.csdnimg.cn/d17a297cf92b4c81b967e53c80e4935e.png)
![在这里插入图片描述](https://img-blog.csdnimg.cn/4799937a51b14f76a292b7d2eb509bb4.png)

```c
	//上面写入的markdown文件的图片地址还是csdn网址，如果我们也希望将图片本地存储的话，就进一步处理下载图片，并替换markdown中关于图片链接的部分，重新生成新的文件
    //放在localArticles目录下
    if(downImageAndGenerateNewFile){
    	downImageAndGenerateNewFile(articleTitle,textStr);
    }
```

```c
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
```


# 四、其他
# 1.小问题处理
登陆到内容管理主页后，有一块小区块，会挡住我们的编辑按钮操作，会导至找不到元素，因此直接使用js操作将其隐藏（`这块东西近期csdn才有，以前没有，以后也不一定会有`）
![在这里插入图片描述](https://img-blog.csdnimg.cn/3302ee4331c147279b5a1f55758a2b97.png)
`分析出元素id为nps-box,直接隐藏`
![在这里插入图片描述](https://img-blog.csdnimg.cn/0bcff4bd5ce14a7b81964c19b1fd7ee5.png)
```
	//4.有个调研的div挡住了内容，不处理会报错，因此用js控制一下不显示
    ((JavascriptExecutor)webDriver).executeScript("document.getElementById(\"nps-box\").style.display=\"none\";");
```

# 五、源代码下载
```
github: https://github.com/jxlhljh/seleniumExportCsdnMarkdownTest.git
gitee: https://gitee.com/jxlhljh/seleniumExportCsdnMarkdownTest.git
```