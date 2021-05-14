package com.crobot.worker;

import com.crobot.dto.DocumentDTO;
import com.crobot.dto.SettingDTO;
import com.crobot.dto.SettingPoolDTO;
import com.crobot.exception.CaptchaException;
import com.crobot.exception.NoResultException;
import com.crobot.exception.ValidationException;
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
    private Integer fairDuration = 10;

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
        boolean downloadPdf = AppProperties.getInstance().getPropertyAsBoolean("file.download.pdf");
        boolean saveTxt = AppProperties.getInstance().getPropertyAsBoolean("file.save.txt");

        System.setProperty("webdriver.chrome.driver", AppProperties.getInstance().getProperty("webdriver.chrome.driver"));

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--disable-notifications");
        HashMap<String, Object> chromePrefs = new HashMap<>();
        chromePrefs.put("profile.default_content_settings.popups", 0);

        if (downloadPdf)
            chromePrefs.put("download.default_directory", AppProperties.getInstance().getProperty("file.download.path"));

        chromeOptions.setExperimentalOption("prefs", chromePrefs);
        chromeOptions.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
        chromeOptions.addArguments("--start-maximized");

        driver = new ChromeDriver(chromeOptions);
//        driver.manage().window().maximize();

        try {
            startLoop(downloadPdf, saveTxt);
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
            log.error(responseContent == null ? "ResponseContent is null!" : responseContent.toString());
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
            log.error("Error while fetching from pool!");
            log.error(responseContent == null ? "ResponseContent is null!" : responseContent.toString());
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
        headers.put("Content-type", "application/json");

        Gson gson = new Gson();
        String jsonSettingPoolDTO = gson.toJson(settingPoolDTO);
        ResponseContent responseContent = HttpClientUtil.getInstance().sendHttpPost(poolUrl, jsonSettingPoolDTO, headers, RequestConfig.DEFAULT, 1, 1);
        if (responseContent != null && responseContent.getResponseCode() == HttpStatus.SC_OK) {
            log.info("Setting Pool updated.");
        } else {
            log.error("Error while updating setting pool!");
            log.error(responseContent == null ? "ResponseContent is null!" : responseContent.toString());
        }
    }

    private void sendDocument(DocumentDTO documentDTO) {
        String poolUrl = serverUrl + "/v1/api/add/document";
        String authString = this.userName + ":" + this.password;
        String authStringEnc = new String(Base64.getEncoder().encode(authString.getBytes()));

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Basic " + authStringEnc);
        headers.put("Content-type", "application/json");

        Gson gson = new Gson();
        String jsonDocumentDTO = gson.toJson(documentDTO);
        ResponseContent responseContent = HttpClientUtil.getInstance().sendHttpPost(poolUrl, jsonDocumentDTO, headers, RequestConfig.DEFAULT, 1, 1);

        if (responseContent != null && responseContent.getResponseCode() == HttpStatus.SC_OK) {
            log.info("Document sent.");
        } else {
            log.error("Error while sending document!");
            log.error(responseContent == null ? "ResponseContent is null!" : responseContent.toString());
        }
    }


    /**
     * @throws InterruptedException
     * @throws IOException
     */
    private void startLoop(boolean downloadPdf, boolean saveTxt) throws InterruptedException, IOException {
        SettingDTO settingDTO = getSettings();
        if (settingDTO == null || settingDTO.getWebPageUrl() == null) {
            log.error("ERROR SL001 Could not found settings..");
            return;
        }
        String fileSavePath = AppProperties.getInstance().getProperty("file.save.path");

        this.fairDuration = settingDTO.getFairDuration() != null ? settingDTO.getFairDuration() : 20;
        driver.get(settingDTO.getWebPageUrl());
        TimeUnit.SECONDS.sleep(this.fairDuration);

        // TODO: NEED LOOP

        SettingPoolDTO settingPoolDTO = fetchOneFromPool();
        if (settingPoolDTO == null) {
            log.error("ERROR SL002 There no SettingPool record!");
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
                startProcess(daire, selection, verdictYear + "", currentNumber + "", (currentNumber + recordSize) + "", firstRun, fileSavePath, downloadPdf, saveTxt);
                firstRun = false;
            } catch (CaptchaException e) {
                log.error("ERROR SL003 Captcha error. Renewing request..");
                startProcess(daire, selection, verdictYear + "", currentNumber + "", (currentNumber + recordSize) + "", firstRun, fileSavePath, downloadPdf, saveTxt);
                firstRun = false;
            } catch (ValidationException e) {
                log.error("ERROR SL004 Form validation error. Renewing request..");
                startProcess(daire, selection, verdictYear + "", currentNumber + "", (currentNumber + recordSize) + "", firstRun, fileSavePath, downloadPdf, saveTxt);
                firstRun = false;
            } catch (Exception e) {
                log.error("ERROR SL005 Exception", e);
            }
            currentNumber = currentNumber + recordSize;
        }

        settingPoolDTO.setStatus(SettingPoolStatus.PROCESSED.name());
        updatePool(settingPoolDTO);


    }

    /**
     * @throws Exception
     */
    private void startProcess(String daire, int selection, String selectedYear, String startNumber, String endNumber,
                              boolean firstRun, String fileSavePath, boolean downloadPdf, boolean saveTxt) throws
            IOException, InterruptedException, CaptchaException {
        JavascriptExecutor jsExcecutor = (JavascriptExecutor) driver;
        Integer preSelection = null;

        String path = System.getProperty("user.dir") + "/captcha.png";
        if (solvedCaptcha == null) {
            for (int i = 0; i < 3; i++) {
                File captchaSrc = driver.findElement(By.id("aramaForm:cptImg")).getScreenshotAs(OutputType.FILE);
                FileHandler.copy(captchaSrc, new File(path));
                solvedCaptcha = solveCaptcha(path);
                if (solvedCaptcha != null) {
                    break;
                } else {
                    log.error("ERR-SP001 Captcha did not solved! Refreshing captcha and trying again..");
                    driver.findElement(By.id("aramaForm:refreshCapcha")).click();
                    TimeUnit.SECONDS.sleep(2);
                }
            }
            if (solvedCaptcha == null)
                log.error("ERR-SP002 Captcha did not solved!");
        }

        if (firstRun) {
            WebElement guvenlikKodu = driver.findElement(By.cssSelector("#aramaForm\\:guvenlikKodu"));
            guvenlikKodu.clear();
            guvenlikKodu.sendKeys(solvedCaptcha);
            jsExcecutor.executeScript("$(arguments[0]).change();", guvenlikKodu);
            TimeUnit.SECONDS.sleep(1);

            WebElement detayliAramaLink = driver.findElement(By.cssSelector("#aramaForm\\:detayliAramaCl"));
            detayliAramaLink.click();
            TimeUnit.SECONDS.sleep(1);
        }

        WebElement kurulCombo = driver.findElement(By.cssSelector("#aramaForm\\:kurulCombo"));
        if (!kurulCombo.isDisplayed()) {
            WebElement detayliAramaLink = driver.findElement(By.cssSelector("#aramaForm\\:detayliAramaCl"));
            detayliAramaLink.click();
            TimeUnit.SECONDS.sleep(1);
        }

        if (DefinitionType.KURUL.name().equals(daire)) {
            WebElement kurullar = driver.findElement(By.cssSelector("#aramaForm\\:kurulCombo"));
            kurullar.click();

            List<WebElement> kurullarList = driver.findElements(By.cssSelector("#aramaForm\\:kurulCombo_panel li.ui-selectcheckboxmenu-item"));

            if (preSelection != null)
                kurullarList.get(preSelection).click();
            TimeUnit.MILLISECONDS.sleep(100);
            kurullarList.get(selection).click();
            kurullar.click();
            preSelection = selection;
        } else if (DefinitionType.CEZA_DAIRESI.name().equals(daire)) {
            WebElement cezaDaire = driver.findElement(By.cssSelector("#aramaForm\\:cezaDaireCombo"));
            cezaDaire.click();

            List<WebElement> cezaDaireList = driver.findElements(By.cssSelector("#aramaForm\\:cezaDaireCombo_panel li.ui-selectcheckboxmenu-item"));

            if (preSelection != null)
                cezaDaireList.get(preSelection).click();
            TimeUnit.MILLISECONDS.sleep(100);
            cezaDaireList.get(selection).click();
            cezaDaire.click();
            preSelection = selection;
        } else if (DefinitionType.HUKUK_DAIRESI.name().equals(daire)) {
            WebElement hukukDaire = driver.findElement(By.cssSelector("#aramaForm\\:hukukDaireCombo"));
            hukukDaire.click();

            List<WebElement> hukukDaireList = driver.findElements(By.cssSelector("#aramaForm\\:hukukDaireCombo_panel li.ui-selectcheckboxmenu-item"));

            if (preSelection != null)
                hukukDaireList.get(preSelection).click();
            TimeUnit.MILLISECONDS.sleep(100);
            hukukDaireList.get(selection).click();
            hukukDaire.click();
            preSelection = selection;
        }

        sendKeys("#aramaForm\\:karaYilInput", selectedYear, driver, jsExcecutor);
        TimeUnit.SECONDS.sleep(1);

        sendKeys("#aramaForm\\:ilkKararNoInput", startNumber, driver, jsExcecutor);
        TimeUnit.SECONDS.sleep(1);

        sendKeys("#aramaForm\\:sonKararNoInput", endNumber, driver, jsExcecutor);
        TimeUnit.SECONDS.sleep(1);

        driver.findElement(By.cssSelector("label[for='aramaForm:siralamaKriteri:1']")).click();
        TimeUnit.SECONDS.sleep(1);

        WebElement ara = driver.findElement(By.cssSelector("#aramaForm\\:detayliAraCommandButton"));
        ara.click();
        TimeUnit.SECONDS.sleep(5);

        //Check form errors..
        boolean isCaptchaError = driver.findElement(By.cssSelector("#aramaForm\\:messages")).getText().contains("Güvenlik Kodunu Kontrol Ediniz!");
        if (isCaptchaError)
            throw new CaptchaException();
        boolean isSearchParamError = driver.findElement(By.cssSelector("#aramaForm\\:messages")).getText().contains("Aranacak Kavram Boş Olamaz!");
        if (isSearchParamError)
            throw new ValidationException();
        boolean isNoResult = driver.findElement(By.cssSelector("#aramaForm\\:messages")).getText().contains("Sonuç Bulunamadı!");
        if (isNoResult)
            throw new NoResultException();


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

                while (controlCount < 40) {
                    controlCount++;
                    WebElement sonrakiEvrakLink;
                    try {
                        sonrakiEvrakLink = driver.findElement(By.cssSelector("#aramaForm\\:sonrakiEvrakLabel"));
                    } catch (Exception e) {
                        log.error("ERROR-SP003 Error with sonrakiEvrakLink");
                        log.error("ERROR-SP003 Error Detail", e);
                        sonrakiEvrakLink = null;

                        List<WebElement> dialogCloseButtons = driver.findElements(By.className("ui-dialog-titlebar-close"));
                        for (WebElement closeButton : dialogCloseButtons) {
                            try {
                                closeButton.click();
                            } catch (Exception e1) {
                                log.error("ERROR-SP004 Error with dialog close button");
                                log.error("ERROR-SP004 Error Detail", e1);
                                //do nothing..
                            }
                        }
                    }

                    if (sonrakiEvrakLink == null || sonrakiEvrakLink.getAttribute("class").contains("disabled")) {
                        break;
                    }

                    try {
                        sonrakiEvrakLink.click();
                        TimeUnit.SECONDS.sleep(2);
                    } catch (Exception e) {
                        break;
                    }

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
                    documentDTO.setDocumentData(contentStr.getBytes(StandardCharsets.UTF_8));
                    documentDTO.setDocumentName(fileName);
                    documentDTO.setVerdictYear(Integer.parseInt(selectedYear));
                    documentDTO.setDefinitionType(daire);
                    sendDocument(documentDTO);

                }
            }

            List<WebElement> dialogCloseButtons = driver.findElements(By.className("ui-dialog-titlebar-close"));
            for (WebElement closeButton : dialogCloseButtons) {
                try {
                    closeButton.click();
                } catch (Exception e) {
                    //do nothing..
                }
            }
        }
        TimeUnit.SECONDS.sleep(fairDuration);

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

    /**
     * @param elementCssSelector
     * @param value
     * @return
     * @throws InterruptedException
     */
    private WebElement sendKeys(String elementCssSelector, String value, WebDriver webDriver, JavascriptExecutor jsExcecutor) throws InterruptedException {
        WebElement element = webDriver.findElement(By.cssSelector(elementCssSelector));
        try {
            element.click();
            element.clear();
        } catch (Exception e) {
            element = webDriver.findElement(By.cssSelector(elementCssSelector));
            element.click();
            element.clear();
        }
        TimeUnit.MILLISECONDS.sleep(100);
        try {
            element.click();
        } catch (Exception e) {
            element = webDriver.findElement(By.cssSelector(elementCssSelector));
            element.click();
        }
        TimeUnit.MILLISECONDS.sleep(100);
        try {
            element.sendKeys(value);
        } catch (Exception e) {
            element = webDriver.findElement(By.cssSelector(elementCssSelector));
            element.sendKeys(value);
        }
        jsExcecutor.executeScript("$(arguments[0]).change();", element);
        return element;
    }

}





