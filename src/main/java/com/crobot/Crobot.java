package com.crobot;

import com.crobot.http.CaptchaRequestDTO;
import com.crobot.http.CaptchaResponseDTO;
import com.crobot.http.HttpClientUtil;
import com.crobot.http.ResponseContent;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.io.FileHandler;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Crobot {
    long start;
    private WebDriver driver;

    public static void main(String[] args) {
        Crobot crobot = new Crobot();
        crobot.start();
    }

    private static Tesseract getTesseract() {
        Tesseract instance = new Tesseract();
        //instance.setDatapath("/usr/local/Cellar/tesseract/4.0.0/share/tessdata");
        instance.setLanguage("eng");
        instance.setHocr(true);
        return instance;
    }

    private void start() {
        start = System.currentTimeMillis();
        System.setProperty("webdriver.chrome.driver", "C:\\opt\\chrome\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-notifications");

        String downloadFilepath = "C:\\Users\\mobali\\Downloads\\karar";
        HashMap<String, Object> chromePrefs = new HashMap<String, Object>();
        chromePrefs.put("profile.default_content_settings.popups", 0);
        chromePrefs.put("download.default_directory", downloadFilepath);
        options.setExperimentalOption("prefs", chromePrefs);
        DesiredCapabilities cap = DesiredCapabilities.chrome();
        cap.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
        cap.setCapability(ChromeOptions.CAPABILITY, options);

        driver = new ChromeDriver(cap);
        driver.manage().window().maximize();

        try {
            //startInternal();
            readCaptcha();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.close();
            driver.quit();
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("elapsed time in sec: " + (elapsed / 1000));
        System.exit(0);
    }

    private void readCaptcha() throws InterruptedException, IOException, TesseractException {
        String webPage = "https://karararama.yargitay.gov.tr/YargitayBilgiBankasiIstemciWeb";
        driver.get(webPage);
        Thread.sleep(5000);
        File captchaSrc = driver.findElement(By.id("aramaForm:cptImg")).getScreenshotAs(OutputType.FILE);
        String path = System.getProperty("user.dir") + "/captcha.png";
        FileHandler.copy(captchaSrc, new File(path));

//        ITesseract tesseract = new Tesseract();//getTesseract();
//        //tesseract.setDatapath("tessdata");
//        tesseract.setLanguage("captcha");
//        //tesseract.setTessVariable("user_defined_dpi", "300");
//        File file = new File(path);
//        String result = tesseract.doOCR(file);
//        System.out.println("======================");
//        System.out.println("Tessaract OCR Result: " + result);

        //User service
        String server_url = "https://api.apitruecaptcha.org/one/gettext";
        byte[] fileContent = FileUtils.readFileToByteArray(new File(path));
        String encodedImgData = Base64.getEncoder().encodeToString(fileContent);
        String data = encodedImgData.replace("data:image/png;base64,", "");

        CaptchaRequestDTO captchaRequestDTO = new CaptchaRequestDTO();
        captchaRequestDTO.setUserid("captcham");
        captchaRequestDTO.setApikey("RGUhNVQqlKDd7rmH4kkq");
        captchaRequestDTO.setData(data);

        Gson gson = new Gson();
        String jsonCaptchaRequest = gson.toJson(captchaRequestDTO);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-type", "application/json");

        ResponseContent responseContent = HttpClientUtil.getInstance().sendHttpPost(server_url, jsonCaptchaRequest, headers, RequestConfig.DEFAULT, 1, 1);

        if (responseContent != null || responseContent.getResponseCode() == HttpStatus.SC_OK) {
            CaptchaResponseDTO captchaResponseDTO = gson.fromJson(responseContent.getContent(), CaptchaResponseDTO.class);
            log.info("Result: "+ captchaResponseDTO.getResult());
            log.info("Result: "+ captchaResponseDTO.getResult().toLowerCase());
        } else {
            //error
        }


    }

    private void startInternal() throws Exception {
        String path = "https://karararama.yargitay.gov.tr/YargitayBilgiBankasiIstemciWeb";

        driver.get(path);

        Thread.sleep(20000);

        JavascriptExecutor js = (JavascriptExecutor) driver;

        WebElement detayliAramaLink = driver.findElement(By.cssSelector("#aramaForm\\:detayliAramaCl"));
        detayliAramaLink.click();

        Thread.sleep(1000);

        WebElement kurullar = driver.findElement(By.cssSelector("#aramaForm\\:kurulCombo"));
        kurullar.click();

        List<WebElement> kurullarList = driver.findElements(By.cssSelector("#aramaForm\\:kurulCombo_panel li.ui-selectcheckboxmenu-item"));
        log.info("kurullar: " + kurullarList.size());
        kurullarList.get(0).click();
        kurullar.click();

        WebElement kararYili = driver.findElement(By.cssSelector("#aramaForm\\:karaYilInput"));

        kararYili.sendKeys("2010");
        js.executeScript("$(arguments[0]).change();", kararYili);
        Thread.sleep(1000);

        WebElement ilkKararNo = driver.findElement(By.cssSelector("#aramaForm\\:ilkKararNoInput"));
        ilkKararNo.click();
        Thread.sleep(1000);

        ilkKararNo = driver.findElement(By.cssSelector("#aramaForm\\:ilkKararNoInput"));
        ilkKararNo.click();
        ilkKararNo.clear();
        Thread.sleep(1000);
        ilkKararNo.sendKeys("1");
        js.executeScript("$(arguments[0]).change();", ilkKararNo);
        Thread.sleep(1000);

        WebElement sonKararNo = driver.findElement(By.cssSelector("#aramaForm\\:sonKararNoInput"));
        sonKararNo.click();
        Thread.sleep(1000);

        sonKararNo = driver.findElement(By.cssSelector("#aramaForm\\:sonKararNoInput"));
        sonKararNo.clear();
        sonKararNo.sendKeys("40");
        js.executeScript("$(arguments[0]).change();", sonKararNo);
        Thread.sleep(1000);

        WebElement ara = driver.findElement(By.cssSelector("#aramaForm\\:detayliAraCommandButton"));
        ara.click();
        Thread.sleep(5000);

        List<WebElement> sonucButtonList = driver.findElements(By.cssSelector("button[id$='rowbtn']"));
        if (!sonucButtonList.isEmpty()) {
            WebElement button = sonucButtonList.get(0);
            button.click();
            Thread.sleep(3000);

            WebElement pdfLink = driver.findElement(By.cssSelector("#aramaForm\\:pdfOlusturCmd > img"));
            if (pdfLink != null) {
                pdfLink.click();
                Thread.sleep(1000);

                while (true) {
                    WebElement sonrakiEvrakLink = driver.findElement(By.cssSelector("#aramaForm\\:sonrakiEvrakCmd"));
                    if (sonrakiEvrakLink == null) {
                        break;
                    }

                    sonrakiEvrakLink.click();
                    Thread.sleep(2000);

                    pdfLink = driver.findElement(By.cssSelector("#aramaForm\\:pdfOlusturCmd > img"));
                    pdfLink.click();
                    Thread.sleep(1000);
                }
            }
        }

        Thread.sleep(20000);
    }
}





