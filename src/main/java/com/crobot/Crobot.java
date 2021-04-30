package com.crobot;

import com.crobot.http.CaptchaRequestDTO;
import com.crobot.http.CaptchaResponseDTO;
import com.crobot.http.HttpClientUtil;
import com.crobot.http.ResponseContent;
import com.crobot.page.DaireType;
import com.crobot.util.AppProperties;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
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
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Crobot {
    long start;
    private WebDriver driver;
    private String solvedCaptcha;

    public static void main(String[] args) throws InterruptedException, IOException {
        Crobot crobot = new Crobot();
        crobot.start();
    }

    private void start() throws InterruptedException, IOException {
        start = System.currentTimeMillis();
        log.info("CROBOT Started.");
        AppProperties.getInstance().init();

        String downloadPath = AppProperties.getInstance().getProperty("file.download.path");
        System.setProperty("webdriver.chrome.driver", AppProperties.getInstance().getProperty("webdriver.chrome.driver"));

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-notifications");
        HashMap<String, Object> chromePrefs = new HashMap<>();
        chromePrefs.put("profile.default_content_settings.popups", 0);
        chromePrefs.put("download.default_directory", downloadPath);
        options.setExperimentalOption("prefs", chromePrefs);
        DesiredCapabilities cap = DesiredCapabilities.chrome();
        cap.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
        cap.setCapability(ChromeOptions.CAPABILITY, options);

        driver = new ChromeDriver(cap);
        driver.manage().window().maximize();

        try {
            startLoop();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            driver.close();
            driver.quit();
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("elapsed time in sec: " + (elapsed / 1000));
        System.exit(0);
    }

    /**
     * Call captcha service to solve captcha.
     *
     * @param path
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private String solveCaptcha(String path) throws InterruptedException, IOException {
        String result = null;
        String server_url = AppProperties.getInstance().getProperty("captcha.service.url");
        byte[] fileContent = FileUtils.readFileToByteArray(new File(path));
        String encodedImgData = Base64.getEncoder().encodeToString(fileContent);
        String data = encodedImgData.replace("data:image/png;base64,", "");

        CaptchaRequestDTO captchaRequestDTO = new CaptchaRequestDTO();
        captchaRequestDTO.setUserid(AppProperties.getInstance().getProperty("captcha.service.userid"));
        captchaRequestDTO.setApikey(AppProperties.getInstance().getProperty("captcha.service.apikey"));
        captchaRequestDTO.setData(data);

        Gson gson = new Gson();
        String jsonCaptchaRequest = gson.toJson(captchaRequestDTO);

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-type", "application/json");

        ResponseContent responseContent = HttpClientUtil.getInstance().sendHttpPost(server_url, jsonCaptchaRequest, headers, RequestConfig.DEFAULT, 1, 1);

        if (responseContent != null || responseContent.getResponseCode() == HttpStatus.SC_OK) {
            CaptchaResponseDTO captchaResponseDTO = gson.fromJson(responseContent.getContent(), CaptchaResponseDTO.class);
            if (captchaResponseDTO.getResult() != null) {
                result = captchaResponseDTO.getResult().toLowerCase();
            } else {
                log.error("Captcha response result is null!!");
            }
            log.debug("Captcha result: " + captchaResponseDTO.getResult());
        } else {
            log.error("Error while calling captcha service.");
        }

        return result;
    }

    private void startLoop() throws InterruptedException, IOException {
        driver.get(AppProperties.getInstance().getProperty("web.page.url"));
        TimeUnit.SECONDS.sleep(5);

        List<Integer> selectionList = new ArrayList<>();
        List<String> kararYilList = AppProperties.getInstance().getPropertyAsStringList("web.page.karar.yil");
        int startNumber = AppProperties.getInstance().getPropertyAsInt("web.page.karar.no.start");
        int endNumber = AppProperties.getInstance().getPropertyAsInt("web.page.karar.no.end");
        String fileSavePath = AppProperties.getInstance().getProperty("file.save.path");

        int daire = Integer.parseInt(AppProperties.getInstance().getProperty("web.page.daire"));
        if (daire == DaireType.KURUL.getValue()) {
            selectionList = AppProperties.getInstance().getPropertyAsIntegerList("web.page.daire.kurul");
        } else if (daire == DaireType.CEZA.getValue()) {
            selectionList = AppProperties.getInstance().getPropertyAsIntegerList("web.page.daire.ceza");
        } else if (daire == DaireType.HUKUK.getValue()) {
            selectionList = AppProperties.getInstance().getPropertyAsIntegerList("web.page.daire.hukuk");
        }

        int recordSize = 35;
        int difference = endNumber % recordSize;
        String solvedCaptcha = null;
        boolean firstRun = true;
        for (Integer selection : selectionList) {
            boolean selectionChanged = true;
            for (String kararYil : kararYilList) {
                boolean yearChanged = true;
                int currentNumber = startNumber;
                while (currentNumber <= endNumber + difference) {
                    startProcess(daire, selection - 1, kararYil, currentNumber + "", (currentNumber + recordSize) + "", yearChanged, selectionChanged, firstRun, fileSavePath);
                    yearChanged = false;
                    selectionChanged = false;
                    firstRun = false;
//                    try {
//                        startProcess(daire, selection - 1, kararYil, currentNumber + "", (currentNumber + recordSize) + "");
//                    } catch (Exception e) {
//                        log.error("Error while processing.. Daire: " + daire
//                                + " Selection: " + selection
//                                + " Karar Yil: " + kararYil
//                                + " Karar Start No: " + currentNumber);
//                        log.error("Exception", e);
//                    }
                    currentNumber = currentNumber + recordSize;
                }
            }
        }


    }

    /**
     * @throws Exception
     */
    private void startProcess(int daire, int selection, String selectedYear, String startNumber, String endNumber, boolean yearChanged, boolean selectionChanged, boolean firstRun, String fileSavePath) throws IOException, InterruptedException {
        JavascriptExecutor jsExcecutor = (JavascriptExecutor) driver;

        String path = System.getProperty("user.dir") + "/captcha.png";
        if (solvedCaptcha == null) {
            for (int i = 0; i < 100; i++) {
                File captchaSrc = driver.findElement(By.id("aramaForm:cptImg")).getScreenshotAs(OutputType.FILE);
                FileHandler.copy(captchaSrc, new File(path));
                solvedCaptcha = solveCaptcha(path);
                if (solvedCaptcha != null)
                    break;
                log.error("Captcha did no solved. Waiting 10 seconds..");
                TimeUnit.SECONDS.sleep(10);
            }
        }

        if (firstRun) {
            WebElement guvenlikKodu = driver.findElement(By.cssSelector("#aramaForm\\:guvenlikKodu"));
            guvenlikKodu.sendKeys(solvedCaptcha);
            jsExcecutor.executeScript("$(arguments[0]).change();", guvenlikKodu);
            TimeUnit.SECONDS.sleep(1);

            WebElement detayliAramaLink = driver.findElement(By.cssSelector("#aramaForm\\:detayliAramaCl"));
            detayliAramaLink.click();
            TimeUnit.SECONDS.sleep(1);
        }

        if (selectionChanged) {
            if (daire == DaireType.KURUL.getValue()) {
                WebElement kurullar = driver.findElement(By.cssSelector("#aramaForm\\:kurulCombo"));
                kurullar.click();

                List<WebElement> kurullarList = driver.findElements(By.cssSelector("#aramaForm\\:kurulCombo_panel li.ui-selectcheckboxmenu-item"));
                if (selection != 0)
                    kurullarList.get(selection - 1).click();
                TimeUnit.MILLISECONDS.sleep(100);
                kurullarList.get(selection).click();
                kurullar.click();
            } else if (daire == DaireType.CEZA.getValue()) {
                WebElement cezaDaire = driver.findElement(By.cssSelector("#aramaForm\\:cezaDaireCombo"));
                cezaDaire.click();

                List<WebElement> cezaDaireList = driver.findElements(By.cssSelector("#aramaForm\\:cezaDaireCombo_panel li.ui-selectcheckboxmenu-item"));
                if (selection != 0)
                    cezaDaireList.get(selection - 1).click();
                TimeUnit.MILLISECONDS.sleep(100);
                cezaDaireList.get(selection).click();
                cezaDaire.click();
            } else if (daire == DaireType.HUKUK.getValue()) {
                WebElement hukukDaire = driver.findElement(By.cssSelector("#aramaForm\\:hukukDaireCombo"));
                hukukDaire.click();

                List<WebElement> hukukDaireList = driver.findElements(By.cssSelector("#aramaForm\\:hukukDaireCombo_panel li.ui-selectcheckboxmenu-item"));
                if (selection != 0)
                    hukukDaireList.get(selection - 1).click();
                TimeUnit.MILLISECONDS.sleep(100);
                hukukDaireList.get(selection).click();
                hukukDaire.click();
            }
        }


        if (yearChanged) {
            WebElement kararYili = driver.findElement(By.cssSelector("#aramaForm\\:karaYilInput"));
            kararYili.clear();
            TimeUnit.MILLISECONDS.sleep(100);
            kararYili.sendKeys(selectedYear);
            jsExcecutor.executeScript("$(arguments[0]).change();", kararYili);
            TimeUnit.SECONDS.sleep(1);
        }

        WebElement ilkKararNo = driver.findElement(By.cssSelector("#aramaForm\\:ilkKararNoInput"));
        ilkKararNo.click();
        TimeUnit.SECONDS.sleep(1);

        ilkKararNo = driver.findElement(By.cssSelector("#aramaForm\\:ilkKararNoInput"));
        ilkKararNo.click();
        ilkKararNo.clear();
        TimeUnit.MILLISECONDS.sleep(100);
        ilkKararNo.sendKeys(startNumber);
        jsExcecutor.executeScript("$(arguments[0]).change();", ilkKararNo);
        TimeUnit.SECONDS.sleep(1);

        WebElement sonKararNo = driver.findElement(By.cssSelector("#aramaForm\\:sonKararNoInput"));
        sonKararNo.click();
        TimeUnit.SECONDS.sleep(1);

        sonKararNo = driver.findElement(By.cssSelector("#aramaForm\\:sonKararNoInput"));
        sonKararNo.clear();
        TimeUnit.MILLISECONDS.sleep(100);
        sonKararNo.sendKeys(endNumber);
        jsExcecutor.executeScript("$(arguments[0]).change();", sonKararNo);
        TimeUnit.SECONDS.sleep(1);

        WebElement ara = driver.findElement(By.cssSelector("#aramaForm\\:detayliAraCommandButton"));
        ara.click();
        TimeUnit.SECONDS.sleep(5);

        boolean isCaptchaError = driver.findElement(By.cssSelector("#aramaForm\\:messages")).getText().contains("Güvenlik Kodunu Kontrol Ediniz!");
        if (isCaptchaError) {
            for (int i = 0; i < 100; i++) {
                File captchaSrc = driver.findElement(By.id("aramaForm:cptImg")).getScreenshotAs(OutputType.FILE);
                FileHandler.copy(captchaSrc, new File(path));
                solvedCaptcha = solveCaptcha(path);
                if (solvedCaptcha != null)
                    break;
                log.error("Captcha did no solved. Waiting 10 seconds..");
                TimeUnit.SECONDS.sleep(10);
            }

            WebElement guvenlikKodu = driver.findElement(By.cssSelector("#aramaForm\\:guvenlikKodu"));
            guvenlikKodu.clear();
            TimeUnit.SECONDS.sleep(1);
            guvenlikKodu.sendKeys(solvedCaptcha);
            jsExcecutor.executeScript("$(arguments[0]).change();", guvenlikKodu);
            TimeUnit.SECONDS.sleep(1);

            ara = driver.findElement(By.cssSelector("#aramaForm\\:detayliAraCommandButton"));
            ara.click();
            TimeUnit.SECONDS.sleep(3);
        }

        boolean isSearchParamError = driver.findElement(By.cssSelector("#aramaForm\\:messages")).getText().contains("Aranacak Kavram Boş Olamaz!");
        if (isSearchParamError) {
            if (yearChanged) {
                WebElement kararYili = driver.findElement(By.cssSelector("#aramaForm\\:karaYilInput"));
                kararYili.clear();
                TimeUnit.MILLISECONDS.sleep(100);
                kararYili.sendKeys(selectedYear);
                jsExcecutor.executeScript("$(arguments[0]).change();", kararYili);
                TimeUnit.SECONDS.sleep(1);
            }

            ilkKararNo = driver.findElement(By.cssSelector("#aramaForm\\:ilkKararNoInput"));
            ilkKararNo.click();
            TimeUnit.SECONDS.sleep(1);

            ilkKararNo = driver.findElement(By.cssSelector("#aramaForm\\:ilkKararNoInput"));
            ilkKararNo.click();
            ilkKararNo.clear();
            TimeUnit.MILLISECONDS.sleep(100);
            ilkKararNo.sendKeys(startNumber);
            jsExcecutor.executeScript("$(arguments[0]).change();", ilkKararNo);
            TimeUnit.SECONDS.sleep(1);

            sonKararNo = driver.findElement(By.cssSelector("#aramaForm\\:sonKararNoInput"));
            sonKararNo.click();
            TimeUnit.SECONDS.sleep(1);

            sonKararNo = driver.findElement(By.cssSelector("#aramaForm\\:sonKararNoInput"));
            sonKararNo.clear();
            TimeUnit.MILLISECONDS.sleep(100);
            sonKararNo.sendKeys(endNumber);
            jsExcecutor.executeScript("$(arguments[0]).change();", sonKararNo);
            TimeUnit.SECONDS.sleep(1);

            ara = driver.findElement(By.cssSelector("#aramaForm\\:detayliAraCommandButton"));
            ara.click();
            TimeUnit.SECONDS.sleep(5);
        }

        List<WebElement> sonucButtonList = driver.findElements(By.cssSelector("button[id$='rowbtn']"));
        if (!sonucButtonList.isEmpty()) {
            WebElement button = sonucButtonList.get(0);
            button.click();
            TimeUnit.SECONDS.sleep(3);
            int controlCount = 1;

            WebElement pdfLink = driver.findElement(By.cssSelector("#aramaForm\\:pdfOlusturCmd > img"));
            if (pdfLink != null) {
                pdfLink.click();
                TimeUnit.SECONDS.sleep(1);

                while (controlCount < 35) {
                    controlCount++;
                    WebElement sonrakiEvrakLink;
                    try {
                        //sonrakiEvrakLink = driver.findElement(By.cssSelector("#aramaForm\\:sonrakiEvrakCmd"));
                        sonrakiEvrakLink = driver.findElement(By.cssSelector("#aramaForm\\:sonrakiEvrakLabel"));
                    } catch (Exception e) {
                        log.error("Error with sonrakiEvrakLink");
                        log.error("Error Detail", e);
                        sonrakiEvrakLink = null;

                        List<WebElement> dialogCloseButtons = driver.findElements(By.className("ui-dialog-titlebar-close"));
                        for (WebElement closeButton : dialogCloseButtons) {
                            try {
                                closeButton.click();
                            } catch (Exception e1) {
                                log.error("Error with dialog close button");
                                log.error("Error Detail", e1);
                                //do nothing..
                            }
                        }
                    }

                    if (sonrakiEvrakLink == null || sonrakiEvrakLink.getAttribute("class").contains("disabled")) {
                        break;
                    }

                    sonrakiEvrakLink.click();
                    TimeUnit.SECONDS.sleep(2);

                    pdfLink = driver.findElement(By.cssSelector("#aramaForm\\:pdfOlusturCmd > img"));
                    pdfLink.click();
                    TimeUnit.SECONDS.sleep(1);

                    WebElement content = driver.findElement(By.cssSelector("#aramaForm\\:karakIcerikPanel"));
                    String contentStr = content.getText();
                    String[] contentStrLines = contentStr.split("\\R", 2);
                    String fileName = contentStrLines[0];
                    fileName = fileName.substring(0, Math.min(fileName.length(), 250));

                    writeToFile(fileSavePath + "\\\\" + sanitizeFilename(fileName) + ".txt", contentStr);

                }
            }
            //driver.findElement(By.xpath("//a[@class='ui-dialog-titlebar-close']/a")).click();

            List<WebElement> dialogCloseButtons = driver.findElements(By.className("ui-dialog-titlebar-close"));
            for (WebElement closeButton : dialogCloseButtons) {
                try {
                    closeButton.click();
                } catch (Exception e) {
                    //do nothing..
                }
            }
            // driver.findElement(By.className("ui-dialog-titlebar-close")).click();
        }
        TimeUnit.SECONDS.sleep(AppProperties.getInstance().getPropertyAsInt("web.page.request.fair.duration"));

    }

    /**
     * @param file
     * @param content
     */
    private void writeToFile(String file, String content) {
        FileWriter myWriter = null;
        try {
            myWriter = new FileWriter(file);
            myWriter.write(content);

        } catch (IOException e) {
            log.error("Error while writing to file!");
            log.error("Error Detail:", e);
        } finally {
            try {
                myWriter.close();
            } catch (IOException e) {
                log.error("Error while closing file!");
                log.error("Error Detail:", e);
            }
        }
    }

    /**
     * @param inputName
     * @return
     */
    private String sanitizeFilename(String inputName) {
        return inputName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }
}





