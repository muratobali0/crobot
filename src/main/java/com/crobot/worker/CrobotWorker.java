package com.crobot.worker;

import com.crobot.dto.DocumentDTO;
import com.crobot.dto.SettingDTO;
import com.crobot.dto.SettingPoolDTO;
import com.crobot.http.CaptchaRequestDTO;
import com.crobot.http.CaptchaResponseDTO;
import com.crobot.http.HttpClientUtil;
import com.crobot.http.ResponseContent;
import com.crobot.page.DefinitionType;
import com.crobot.page.SettingPoolStatus;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CrobotWorker {
    long start;
    private WebDriver driver;
    private String solvedCaptcha;
    private String serverUrl;
    private String userName;
    private String password;
    private Integer fairDuration = 20;

    public CrobotWorker(String serverUrl, String userName, String password) {
        this.serverUrl = serverUrl;
        this.userName = userName;
        this.password = password;
        log.info("Created CrobotWorker.");
    }

//    public static void main(String[] args) throws InterruptedException, IOException {
//        CrobotWorker crobotWorker = new CrobotWorker();
//        crobotWorker.start();
//    }

    public void start() throws InterruptedException, IOException {
        start = System.currentTimeMillis();
        log.info("Crobot Worker Started.");
        AppProperties.getInstance().init();

        String downloadPath = AppProperties.getInstance().getProperty("file.download.path");
        System.setProperty("webdriver.chrome.driver", AppProperties.getInstance().getProperty("webdriver.chrome.driver"));

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--disable-notifications");
        HashMap<String, Object> chromePrefs = new HashMap<>();
        chromePrefs.put("profile.default_content_settings.popups", 0);
        chromePrefs.put("download.default_directory", downloadPath);
        chromeOptions.setExperimentalOption("prefs", chromePrefs);
        chromeOptions.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);

        driver = new ChromeDriver(chromeOptions);
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

        if (responseContent != null && responseContent.getResponseCode() == HttpStatus.SC_OK) {
            CaptchaResponseDTO captchaResponseDTO = gson.fromJson(responseContent.getContent(), CaptchaResponseDTO.class);
            if (captchaResponseDTO.getResult() != null) {
                result = captchaResponseDTO.getResult().toLowerCase();
            } else {
                log.error("Captcha response result is null!!");
            }
            log.debug("Captcha result: " + captchaResponseDTO.getResult());
        } else {
            log.error("Error while calling captcha service.");
            if (responseContent != null) {
                log.error(responseContent.getContent());
            } else {
                log.error("Error while calling captcha service. Response is null.");
            }
        }

        return result;
    }

    /**
     * @return
     */
    private SettingDTO getSettings() {
        SettingDTO result = null;
        String settingUrl = serverUrl + "/v1/api/setting";
        String authString = this.userName + ":" + this.password;
        String authStringEnc = new String(Base64.getEncoder().encode(authString.getBytes()));

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Basic " + authStringEnc);
        ResponseContent responseContent = HttpClientUtil.getInstance().sendHttpGet(settingUrl, headers, RequestConfig.DEFAULT, 1, 1);

        if (responseContent != null && responseContent.getResponseCode() == HttpStatus.SC_OK) {
            Gson gson = new Gson();
            result = gson.fromJson(responseContent.getContent(), SettingDTO.class);
        } else {
            log.error("Error while getting settings!");
        }
        return result;
    }

    /**
     * @return
     */
    private SettingPoolDTO fetchOneFromPool() {
        SettingPoolDTO result = null;
        String poolUrl = serverUrl + "/v1/api/pool/fetchone";
        String authString = this.userName + ":" + this.password;
        String authStringEnc = new String(Base64.getEncoder().encode(authString.getBytes()));

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Basic " + authStringEnc);

        ResponseContent responseContent = HttpClientUtil.getInstance().sendHttpGet(poolUrl, headers, RequestConfig.DEFAULT, 1, 1);

        if (responseContent != null && responseContent.getResponseCode() == HttpStatus.SC_OK) {
            Gson gson = new Gson();
            result = gson.fromJson(responseContent.getContent(), SettingPoolDTO.class);
        } else {
            log.error("Error while getting settings!");
        }
        return result;
    }

    private void updatePool(SettingPoolDTO settingPoolDTO) {
        String poolUrl = serverUrl + "/v1/api/pool/update";
        String authString = this.userName + ":" + this.password;
        String authStringEnc = new String(Base64.getEncoder().encode(authString.getBytes()));

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Basic " + authStringEnc);

        Gson gson = new Gson();
        String jsonSettingPoolDTO = gson.toJson(settingPoolDTO);
        ResponseContent responseContent = HttpClientUtil.getInstance().sendHttpPost(poolUrl, jsonSettingPoolDTO, headers, RequestConfig.DEFAULT, 1, 1);

        if (responseContent != null && responseContent.getResponseCode() == HttpStatus.SC_OK) {
            log.info("Setting Pool updated.");

        } else {
            log.error("Error while getting settings!");
        }
    }

    private void sendDocument(DocumentDTO documentDTO) {
        String poolUrl = serverUrl + "/v1/api/add/document";
        String authString = this.userName + ":" + this.password;
        String authStringEnc = new String(Base64.getEncoder().encode(authString.getBytes()));

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Basic " + authStringEnc);

        Gson gson = new Gson();
        String jsonSettingPoolDTO = gson.toJson(documentDTO);
        ResponseContent responseContent = HttpClientUtil.getInstance().sendHttpPost(poolUrl, jsonSettingPoolDTO, headers, RequestConfig.DEFAULT, 1, 1);

        if (responseContent != null && responseContent.getResponseCode() == HttpStatus.SC_OK) {
            log.info("Document sent.");

        } else {
            log.error("Error while sending document!");
        }
    }


    /**
     * @throws InterruptedException
     * @throws IOException
     */
    private void startLoop() throws InterruptedException, IOException {
        SettingDTO settingDTO = getSettings();
        if (settingDTO == null || settingDTO.getWebPageUrl() == null) {
            log.error("Could not found settings..");
            return;
        }
        String fileSavePath = AppProperties.getInstance().getProperty("file.save.path");

        this.fairDuration = settingDTO.getFairDuration() != null ? settingDTO.getFairDuration() : 20;
        driver.get(settingDTO.getWebPageUrl());
        TimeUnit.SECONDS.sleep(this.fairDuration);

        // NEED LOOP

        SettingPoolDTO settingPoolDTO = fetchOneFromPool();
        if (settingPoolDTO == null) {
            log.error("There no SettingPool record!");
            return;
        }

        String daire = settingPoolDTO.getDefinitionType();
        int selection = settingPoolDTO.getOrderNumber();
        int verdictYear = settingPoolDTO.getYear();
        int verdictNoStart = settingPoolDTO.getVerdictNoStart();
        int verdictNoEnd = settingPoolDTO.getVerdictNoEnd();

        boolean firstRun = true;
        int recordSize = 37;
        int difference = verdictNoEnd % recordSize;
        int currentNumber = verdictNoStart;
        while (currentNumber <= verdictNoEnd + difference) {
            try {
                startProcess(daire, selection, verdictYear + "", currentNumber + "", (currentNumber + recordSize) + "", firstRun, fileSavePath);
                firstRun = false;
            } catch (Exception e) {
                log.error("Exception", e);
            }
            currentNumber = currentNumber + recordSize;
        }

        settingPoolDTO.setStatus(SettingPoolStatus.PROCESSED.name());
        updatePool(settingPoolDTO);


//        List<Integer> selectionList = new ArrayList<>();
//        List<String> kararYilList = AppProperties.getInstance().getPropertyAsStringList("web.page.karar.yil");
//
//        int daire = Integer.parseInt(AppProperties.getInstance().getProperty("web.page.daire"));
//        if (daire == DaireType.KURUL.getValue()) {
//            selectionList = AppProperties.getInstance().getPropertyAsIntegerList("web.page.daire.kurul");
//        } else if (daire == DaireType.CEZA.getValue()) {
//            selectionList = AppProperties.getInstance().getPropertyAsIntegerList("web.page.daire.ceza");
//        } else if (daire == DaireType.HUKUK.getValue()) {
//            selectionList = AppProperties.getInstance().getPropertyAsIntegerList("web.page.daire.hukuk");
//        }
//
//        int recordSize = 35;
//        int difference = endNumber % recordSize;
//        //String solvedCaptcha = null;
//        boolean firstRun = true;
//        for (Integer selection : selectionList) {
//            boolean selectionChanged = true;
//            for (String kararYil : kararYilList) {
//                boolean yearChanged = true;
//                int currentNumber = startNumber;
//                while (currentNumber <= endNumber + difference) {
//                    startProcess(daire, selection - 1, kararYil, currentNumber + "", (currentNumber + recordSize) + "", yearChanged, selectionChanged, firstRun, fileSavePath);
//                    yearChanged = false;
//                    selectionChanged = false;
//                    firstRun = false;
//                    try {
//                        startProcess(daire, selection - 1, kararYil, currentNumber + "", (currentNumber + recordSize) + "", yearChanged, selectionChanged, firstRun, fileSavePath);
//                    } catch (Exception e) {
//                        log.error("Error while processing.. Daire: " + daire
//                                + " Selection: " + selection
//                                + " Karar Yil: " + kararYil
//                                + " Karar Start No: " + currentNumber);
//                        log.error("Exception", e);
//                    }
//                    currentNumber = currentNumber + recordSize;
//                }
//            }
//        }


    }

    /**
     * @throws Exception
     */
    private void startProcess(String daire, int selection, String selectedYear, String startNumber, String endNumber, boolean firstRun, String fileSavePath) throws
            IOException, InterruptedException {
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


        if (DefinitionType.KURUL.name().equals(daire)) {
            WebElement kurullar = driver.findElement(By.cssSelector("#aramaForm\\:kurulCombo"));
            kurullar.click();

            List<WebElement> kurullarList = driver.findElements(By.cssSelector("#aramaForm\\:kurulCombo_panel li.ui-selectcheckboxmenu-item"));
            kurullarList.clear();
//            if (selection != 0)
//                kurullarList.get(selection - 1).click();
            TimeUnit.MILLISECONDS.sleep(100);
            kurullarList.get(selection).click();
            kurullar.click();
        } else if (DefinitionType.CEZA_DAIRESI.name().equals(daire)) {
            WebElement cezaDaire = driver.findElement(By.cssSelector("#aramaForm\\:cezaDaireCombo"));
            cezaDaire.click();

            List<WebElement> cezaDaireList = driver.findElements(By.cssSelector("#aramaForm\\:cezaDaireCombo_panel li.ui-selectcheckboxmenu-item"));
            cezaDaireList.clear();
//            if (selection != 0)
//                cezaDaireList.get(selection - 1).click();
            TimeUnit.MILLISECONDS.sleep(100);
            cezaDaireList.get(selection).click();
            cezaDaire.click();
        } else if (DefinitionType.HUKUK_DAIRESI.name().equals(daire)) {
            WebElement hukukDaire = driver.findElement(By.cssSelector("#aramaForm\\:hukukDaireCombo"));
            hukukDaire.click();

            List<WebElement> hukukDaireList = driver.findElements(By.cssSelector("#aramaForm\\:hukukDaireCombo_panel li.ui-selectcheckboxmenu-item"));
            hukukDaireList.clear();
//            if (selection != 0)
//                hukukDaireList.get(selection - 1).click();
            TimeUnit.MILLISECONDS.sleep(100);
            hukukDaireList.get(selection).click();
            hukukDaire.click();
        }


        WebElement kararYili = driver.findElement(By.cssSelector("#aramaForm\\:karaYilInput"));
        kararYili.clear();
        TimeUnit.MILLISECONDS.sleep(100);
        kararYili.sendKeys(selectedYear);
        jsExcecutor.executeScript("$(arguments[0]).change();", kararYili);
        TimeUnit.SECONDS.sleep(1);


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

            kararYili = driver.findElement(By.cssSelector("#aramaForm\\:karaYilInput"));
            kararYili.clear();
            TimeUnit.MILLISECONDS.sleep(100);
            kararYili.sendKeys(selectedYear);
            jsExcecutor.executeScript("$(arguments[0]).change();", kararYili);
            TimeUnit.SECONDS.sleep(1);


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

                    DocumentDTO documentDTO = new DocumentDTO();
                    documentDTO.setData(contentStr.getBytes(StandardCharsets.UTF_8));
                    documentDTO.setDocumentName(fileName);
                    documentDTO.setVerdictYear(Integer.parseInt(selectedYear));
                    documentDTO.setDefinitionType(daire);
                    sendDocument(documentDTO);

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





