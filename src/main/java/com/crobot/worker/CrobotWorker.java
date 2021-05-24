package com.crobot.worker;

import com.crobot.dto.DocumentDTO;
import com.crobot.dto.SettingDTO;
import com.crobot.dto.SettingPoolDTO;
import com.crobot.exception.*;
import com.crobot.http.CaptchaRequestDTO;
import com.crobot.http.CaptchaResponseDTO;
import com.crobot.http.HttpClientUtil;
import com.crobot.http.ResponseContent;
import com.crobot.page.DefinitionType;
import com.crobot.page.SettingPoolStatus;
import com.crobot.util.AppProperties;
import com.crobot.util.PatternUtil;
import com.crobot.util.RandomUtil;
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
import java.net.ConnectException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CrobotWorker {
    private final int maxDocumentCount = 38;
    private final int recordSize = 30;
    private WebDriver driver;
    private String solvedCaptcha;
    private String serverUrl;
    private String userName;
    private String password;
    private Integer fairDuration = 20;
    private String downloadDir;
    private boolean isDownload;
    private String saveTxtDir;
    private boolean isSaveTxt;
    private String captchaServiceUrl;
    private String captchaServiceUser;
    private String captchaServiceApiKey;

    public CrobotWorker(String serverUrl, String userName, String password, String downloadDir, boolean isDownload, String saveTxtDir, boolean isSaveTxt) {
        this.serverUrl = serverUrl;
        this.userName = userName;
        this.password = password;
        this.downloadDir = downloadDir;
        this.isDownload = isDownload;
        this.saveTxtDir = saveTxtDir;
        this.isSaveTxt = isSaveTxt;
        log.info("Created CrobotWorker.");
    }

    /**
     * Start
     *
     * @throws InterruptedException
     * @throws IOException
     */
    public void start() throws InterruptedException, IOException {
        log.info("Crobot Worker process started.");
        AppProperties.getInstance().init();
        driver = getNewDriver();
        try {
            startLoop(driver);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            driver.close();
            driver.quit();
        }
        log.info("Crobot Worker process finished.");
    }

    /**
     * @throws InterruptedException
     * @throws IOException
     */
    private void startLoop(WebDriver driver) throws InterruptedException {
        boolean downloadPdf = this.isDownload;//AppProperties.getInstance().getPropertyAsBoolean("file.download.pdf");
        boolean saveTxt = this.isSaveTxt; //AppProperties.getInstance().getPropertyAsBoolean("file.save.txt");
        String fileSavePath = this.saveTxtDir; //AppProperties.getInstance().getProperty("file.save.path");
        String preDaire = "";
        int preSelection = -1;

        SettingDTO settingDTO = getSettings();
        if (settingDTO == null || settingDTO.getWebPageUrl() == null) {
            log.error("ERROR SL001 Could not found settings..");
            return;
        }

        if(AppProperties.getInstance().getPropertyAsBoolean("captcha.service.useServerSettings")){
            if(settingDTO.getCaptchaServiceUrl() ==null
                    ||settingDTO.getCaptchaServiceUserid()==null
                    ||settingDTO.getCaptchaServiceApikey()==null){
                log.error("ERROR SL002 Could not found captcha api settings..");
                return;
            }else{
                this.captchaServiceUrl = settingDTO.getCaptchaServiceUrl();
                this.captchaServiceUser = settingDTO.getCaptchaServiceUserid();
                this.captchaServiceApiKey = settingDTO.getCaptchaServiceApikey();
            }
        }else{
            this.captchaServiceUrl = AppProperties.getInstance().getProperty("captcha.service.url");
            this.captchaServiceUser = AppProperties.getInstance().getProperty("captcha.service.userid");
            this.captchaServiceApiKey = AppProperties.getInstance().getProperty("captcha.service.apikey");
        }

        this.fairDuration = settingDTO.getFairDuration() != null ? settingDTO.getFairDuration() : 20;
        driver.get(settingDTO.getWebPageUrl());
        TimeUnit.SECONDS.sleep(5);

        //TODO: NEED CONTROLLED INFINITE LOOP
        for (int i = 0; i < 10000; i++) {
            SettingPoolDTO settingPoolDTO = fetchOneFromPool();
            if (settingPoolDTO == null) {
                log.error("ERROR SL003 There no SettingPool record!");
                return;
            }

            String daire = settingPoolDTO.getDefinitionType();
            int selection = settingPoolDTO.getOrderNumber();
            Long definitionId = settingPoolDTO.getDefinitionId();
            int verdictYear = settingPoolDTO.getYear();
            int verdictNoStart = settingPoolDTO.getVerdictNoStart();
            int verdictNoEnd = settingPoolDTO.getVerdictNoEnd();

            boolean firstRun = true;
            boolean resolveCaptcha = false;
            int difference = verdictNoEnd % recordSize;
            int currentNumber = verdictNoStart;
            while (currentNumber <= verdictNoEnd + difference) {
                try {
                    startProcess(daire, selection, definitionId, preDaire, preSelection, verdictYear + "", currentNumber + "", (currentNumber + recordSize - 1) + "", firstRun, fileSavePath, downloadPdf, saveTxt, driver, resolveCaptcha);
                    preDaire = daire;
                    preSelection = selection;
                    firstRun = false;
                    resolveCaptcha = false;
                    currentNumber = currentNumber + recordSize;
                } catch (CaptchaException e) {
                    log.error("ERROR SL004 - Captcha error. Renewing request..");
                    resolveCaptcha = true;
                } catch (ValidationException e) {
                    log.error("ERROR SL005 - Form validation error. Renewing request..");
                } catch (TooManyPagesException e) {
                    log.error("ERROR SL006 - Too many pages error. Renewing request..");
                } catch (ElementClickInterceptedException e) {
                    log.error("ERROR SL007 - Too many pages error. Renewing request..", e);
                } catch (ConnectException | WebDriverException e) {
                    log.error("ERROR SL008 - ConnectException | WebDriverException", e);
                    TimeUnit.SECONDS.sleep(3);
                    log.info("Getting new driver..");
                    preDaire = "";
                    preSelection = -1;
                    firstRun = true;
                    resolveCaptcha = true;
                    driver = getNewDriver();
                    driver.get(settingDTO.getWebPageUrl());
                } catch (NoResultException e) {
                    log.error("ERROR SL009 - No Result Exception");
                    log.error("ERROR SL009 Details - Daire: " + daire + ", Selection: " + selection + ", firstRun: " + firstRun + ", Verdict Year: " + verdictYear + ", currentNumber: " + currentNumber);
                    preDaire = daire;
                    preSelection = selection;
                    firstRun = false;
                    resolveCaptcha = false;
                    currentNumber = currentNumber + recordSize;
                } catch (DialogCloseException e) {
                    log.error("ERROR SL010 - Dialog Close Exception", e);
                    TimeUnit.SECONDS.sleep(3);
                    log.info("Getting new driver..");
                    preDaire = "";
                    preSelection = -1;
                    firstRun = true;
                    resolveCaptcha = true;
                    driver = getNewDriver();
                    driver.get(settingDTO.getWebPageUrl());
                    currentNumber = currentNumber + recordSize;
                } catch (Exception e) {
                    log.error("ERROR SL011 - Exception", e);
                    currentNumber = currentNumber + recordSize;
                }
            }

            settingPoolDTO.setStatus(SettingPoolStatus.PROCESSED.name());
            updatePool(settingPoolDTO);
        } // End of infinite loop

    }

    /**
     * @throws Exception
     */
    private void startProcess(String daire, int selection, Long definitionId, String preDaire, int preSelection,
                              String selectedYear, String startNumber, String endNumber,
                              boolean firstRun, String fileSavePath, boolean downloadPdf, boolean saveTxt,
                              WebDriver driver, boolean resolveCaptcha) throws
            IOException, InterruptedException, CaptchaException {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        String path = System.getProperty("user.dir") + "/captcha.png";
        if (solvedCaptcha == null || resolveCaptcha) {
            for (int i = 0; i < 3; i++) {
                log.info("Refreshing captcha..");
                driver.findElement(By.id("aramaForm:refreshCapcha")).click();
                TimeUnit.SECONDS.sleep(2);

                File captchaSrc = driver.findElement(By.id("aramaForm:cptImg")).getScreenshotAs(OutputType.FILE);
                FileHandler.copy(captchaSrc, new File(path));
                solvedCaptcha = solveCaptcha(path);
                if (solvedCaptcha != null)
                    break;
            }
            if (solvedCaptcha == null) {
                log.error("ERR-SP002 Captcha did not solved!");
                throw new CaptchaException();
            }
        }

        if (firstRun || resolveCaptcha) {
            sendKeys("#aramaForm\\:guvenlikKodu", solvedCaptcha, driver, jsExecutor);

            WebElement detayliAramaLink = driver.findElement(By.cssSelector("#aramaForm\\:detayliAramaCl"));
            detayliAramaLink.click();
            TimeUnit.SECONDS.sleep(1);
        }

        //Used to check if "DetayliAramaLink" is clicked.
        WebElement kurulCombo = driver.findElement(By.cssSelector("#aramaForm\\:kurulCombo"));
        if (!kurulCombo.isDisplayed()) {
            WebElement detayliAramaLink = driver.findElement(By.cssSelector("#aramaForm\\:detayliAramaCl"));
            detayliAramaLink.click();
            TimeUnit.SECONDS.sleep(1);
        }

        //Unselect previous selected combo box item.
        if (preSelection != -1) {
            if (DefinitionType.KURUL.name().equals(preDaire)) {
                WebElement kurullar = driver.findElement(By.cssSelector("#aramaForm\\:kurulCombo"));
                kurullar.click();

                List<WebElement> kurullarList = driver.findElements(By.cssSelector("#aramaForm\\:kurulCombo_panel li.ui-selectcheckboxmenu-item"));
                kurullarList.get(preSelection).click();
                kurullar.click();
            } else if (DefinitionType.CEZA_DAIRESI.name().equals(preDaire)) {
                WebElement cezaDaire = driver.findElement(By.cssSelector("#aramaForm\\:cezaDaireCombo"));
                cezaDaire.click();

                List<WebElement> cezaDaireList = driver.findElements(By.cssSelector("#aramaForm\\:cezaDaireCombo_panel li.ui-selectcheckboxmenu-item"));
                cezaDaireList.get(preSelection).click();
                cezaDaire.click();
            } else if (DefinitionType.HUKUK_DAIRESI.name().equals(preDaire)) {
                WebElement hukukDaire = driver.findElement(By.cssSelector("#aramaForm\\:hukukDaireCombo"));
                hukukDaire.click();

                List<WebElement> hukukDaireList = driver.findElements(By.cssSelector("#aramaForm\\:hukukDaireCombo_panel li.ui-selectcheckboxmenu-item"));
                hukukDaireList.get(preSelection).click();
                hukukDaire.click();
            }
            TimeUnit.SECONDS.sleep(1);
        }

        //Select new item from combo box
        if (DefinitionType.KURUL.name().equals(daire)) {
            WebElement kurullar = driver.findElement(By.cssSelector("#aramaForm\\:kurulCombo"));
            kurullar.click();

            List<WebElement> kurullarList = driver.findElements(By.cssSelector("#aramaForm\\:kurulCombo_panel li.ui-selectcheckboxmenu-item"));
            kurullarList.get(selection).click();
            kurullar.click();
        } else if (DefinitionType.CEZA_DAIRESI.name().equals(daire)) {
            WebElement cezaDaire = driver.findElement(By.cssSelector("#aramaForm\\:cezaDaireCombo"));
            cezaDaire.click();

            List<WebElement> cezaDaireList = driver.findElements(By.cssSelector("#aramaForm\\:cezaDaireCombo_panel li.ui-selectcheckboxmenu-item"));
            cezaDaireList.get(selection).click();
            cezaDaire.click();
        } else if (DefinitionType.HUKUK_DAIRESI.name().equals(daire)) {
            WebElement hukukDaire = driver.findElement(By.cssSelector("#aramaForm\\:hukukDaireCombo"));
            hukukDaire.click();

            List<WebElement> hukukDaireList = driver.findElements(By.cssSelector("#aramaForm\\:hukukDaireCombo_panel li.ui-selectcheckboxmenu-item"));
            hukukDaireList.get(selection).click();
            hukukDaire.click();
        }
        TimeUnit.SECONDS.sleep(1);

        sendKeys("#aramaForm\\:karaYilInput", selectedYear, driver, jsExecutor);
        TimeUnit.SECONDS.sleep(1);

        sendKeys("#aramaForm\\:ilkKararNoInput", startNumber, driver, jsExecutor);
        TimeUnit.SECONDS.sleep(1);

        sendKeys("#aramaForm\\:sonKararNoInput", endNumber, driver, jsExecutor);
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

            List<WebElement> pageNumbers = driver.findElements(By.className("ui-paginator-page"));
            if (pageNumbers.size() > 4)
                throw new TooManyPagesException();

            WebElement button = sonucButtonList.get(0);
            button.click();
            TimeUnit.SECONDS.sleep(3);

            for (int i = 0; i < maxDocumentCount; i++) {
                if (i != 0) {
                    WebElement sonrakiEvrakLink;
                    try {
                        sonrakiEvrakLink = driver.findElement(By.cssSelector("#aramaForm\\:sonrakiEvrakLabel"));
                    } catch (Exception e) {
                        log.error("ERROR-SP003 Error with sonrakiEvrakLink");
                        log.error("ERROR-SP003 Error Detail", e);
                        sonrakiEvrakLink = null;

                        List<WebElement> dialogCloseButtons = driver.findElements(By.className("ui-dialog-titlebar-close"));
                        log.debug("DEBUG SP004 - Got all close buttons.. Close Buttons: " + dialogCloseButtons.size());

                        for (WebElement closeButton : dialogCloseButtons) {
                            try {
                                if (closeButton.isDisplayed() && closeButton.isEnabled()) {
                                    log.debug("DEBUG SP005 - Clicking close button: " + closeButton.getText());
                                    closeButton.click();
                                    break;
                                }
                            } catch (Exception ex) {
                                log.error("ERROR SP006 - Error while closing dialog window!", ex);
                                //do nothing..
                            }
                        }

                    }

                    if (sonrakiEvrakLink == null || sonrakiEvrakLink.getAttribute("class").contains("disabled")) {
                        break;
                    }

                    try {
                        sonrakiEvrakLink.click();
                        int waitTime = RandomUtil.getRandomInteger(20, 10);
                        log.debug("Waiting " + waitTime + " seconds to click <Next> link.");
                        TimeUnit.SECONDS.sleep(waitTime);
                    } catch (Exception e) {
                        break;
                    }
                }

                if (downloadPdf) {
                    WebElement pdfLink = driver.findElement(By.cssSelector("#aramaForm\\:pdfOlusturCmd > img"));
                    pdfLink.click();
                    TimeUnit.SECONDS.sleep(1);
                }

                WebElement content = driver.findElement(By.cssSelector("#aramaForm\\:karakIcerikPanel"));
                String contentStr = content.getText();
                String[] contentStrLines = contentStr.split("\\R", 2);
                String fileName = contentStrLines[0];
                fileName = fileName.substring(0, Math.min(fileName.length(), 250));

                if (saveTxt) {
                    writeToFile(fileSavePath + "\\\\" + sanitizeFilename(fileName) + ".txt", contentStr);
                }

                //Extract Verdict and Basis Numbers
                Integer[] basis = PatternUtil.getEsas(contentStrLines[0]);
                Integer[] verdict = PatternUtil.getKarar(contentStrLines[0]);

                DocumentDTO documentDTO = new DocumentDTO();
                documentDTO.setDocumentData(contentStr);
                documentDTO.setDocumentName(fileName);
                documentDTO.setVerdictYear(Integer.parseInt(selectedYear));
                documentDTO.setDefinitionType(daire);
                documentDTO.setDefinitionId(definitionId);
                if (basis != null) {
                    documentDTO.setBasisYear(basis[0]);
                    documentDTO.setBasisNo(basis[1]);
                }
                if (verdict != null) {
                    documentDTO.setVerdictNo(verdict[1]);
                }
                sendDocument(documentDTO);
            }

            log.debug("DEBUG SP007 - Out of loop. Looking for dialog close icon..");
            List<WebElement> dialogCloseButtons = driver.findElements(By.className("ui-dialog-titlebar-close"));
            log.debug("DEBUG SP008 - Got all close buttons.. Close Buttons: " + dialogCloseButtons.size());

             for (WebElement closeButton : dialogCloseButtons) {
                try {
                    if (closeButton.isDisplayed() && closeButton.isEnabled()) {
                        log.debug("DEBUG SP009 - Clicking close button: " + closeButton.getText());
                        closeButton.click();
                        break;
                    }
                } catch (Exception e) {
                    log.error("ERROR SP010 - Error while closing dialog window!", e);
                    //do nothing..
                }
            }

            log.info("Waiting fair duration: " + fairDuration + " seconds.");
            TimeUnit.SECONDS.sleep(fairDuration);
        }

    }


    /**
     * @param elementCssSelector
     * @param value
     * @return
     * @throws InterruptedException
     */
    private void sendKeys(String elementCssSelector, String value, WebDriver webDriver, JavascriptExecutor jsExecutor) throws InterruptedException {
        WebElement element = webDriver.findElement(By.cssSelector(elementCssSelector));
        try {
            element.click();
            element.clear();
        } catch (Exception e) {
            element = webDriver.findElement(By.cssSelector(elementCssSelector));
            element.click();
            element.clear();
        }
        TimeUnit.SECONDS.sleep(1);

        try {
            if (!element.getText().isEmpty()) {
                element.click();
                element.clear();
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (Exception e) {
            element = webDriver.findElement(By.cssSelector(elementCssSelector));
            element.click();
            element.clear();
            TimeUnit.SECONDS.sleep(1);
        }

        try {
            element.click();
        } catch (Exception e) {
            element = webDriver.findElement(By.cssSelector(elementCssSelector));
            element.click();
        }
        TimeUnit.SECONDS.sleep(1);

        try {
            element.sendKeys(value);
        } catch (Exception e) {
            element = webDriver.findElement(By.cssSelector(elementCssSelector));
            element.sendKeys(value);
        }
        jsExecutor.executeScript("$(arguments[0]).change();", element);

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
     * Call captcha service to solve captcha.
     *
     * @param path
     * @return
     * @throws InterruptedException
     * @throws IOException
     */
    private String solveCaptcha(String path) throws InterruptedException, IOException {
        String result = null;
        String server_url = this.captchaServiceUrl; //AppProperties.getInstance().getProperty("captcha.service.url");
        byte[] fileContent = FileUtils.readFileToByteArray(new File(path));
        String encodedImgData = Base64.getEncoder().encodeToString(fileContent);
        String data = encodedImgData.replace("data:image/png;base64,", "");

        CaptchaRequestDTO captchaRequestDTO = new CaptchaRequestDTO();
        captchaRequestDTO.setUserid(this.captchaServiceUser);
        captchaRequestDTO.setApikey(this.captchaServiceApiKey);
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
            //log.debug("Captcha result: " + captchaResponseDTO.getResult());
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
            log.info("Document is sent.");
        } else {
            log.error("Error while sending document!");
            log.error(responseContent == null ? "ResponseContent is null!" : responseContent.toString());
        }
    }

    /**
     * Returns new driver
     *
     * @return
     */
    private WebDriver getNewDriver() {
        System.setProperty("webdriver.chrome.driver", AppProperties.getInstance().getProperty("webdriver.chrome.driver"));

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--disable-notifications");
        HashMap<String, Object> chromePrefs = new HashMap<>();
        chromePrefs.put("profile.default_content_settings.popups", 0);

        if (this.isDownload)
            chromePrefs.put("download.default_directory", this.downloadDir);

//        if (AppProperties.getInstance().getPropertyAsBoolean("file.download.pdf"))
//            chromePrefs.put("download.default_directory", AppProperties.getInstance().getProperty("file.download.path"));

        chromeOptions.setExperimentalOption("prefs", chromePrefs);
        chromeOptions.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
        chromeOptions.addArguments("--start-maximized");

        WebDriver webDriver = new ChromeDriver(chromeOptions);
        webDriver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);

        return webDriver;
    }


}





