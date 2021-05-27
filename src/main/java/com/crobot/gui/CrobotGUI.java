package com.crobot.gui;

import com.crobot.http.HttpClientUtil;
import com.crobot.http.ResponseContent;
import com.crobot.util.AppProperties;
import com.crobot.util.PatternUtil;
import com.crobot.worker.CrobotWorker;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CrobotGUI extends JFrame {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    TrayIcon trayIcon;
    SystemTray tray;
    private JPanel panelMain;
    private JPanel panelMainHeader;
    private JLabel labelHeader;
    private JPanel panelMainBody;
    private JPanel panelMainFooter;
    private JButton buttonStart;
    private JButton buttonStop;
    private JButton buttonClose;
    private JLabel labelWorking;
    private JTextField textFieldServerAddress;
    private JTextField textFieldUsername;
    private JPasswordField passwordFieldPassword;
    private JLabel labelServerAddress;
    private JLabel labelUsername;
    private JLabel labelPassword;
    private JButton buttonTestConnection;
    private JLabel labelHelpServerAddress;
    private JScrollPane jScrollPaneStaus;
    private JTextArea textAreaStatus;
    private JLabel labelDownloadDir;
    private JTextField textFieldDownloadDir;
    private JButton buttonDownloadDir;
    private JCheckBox checkBoxDownload;
    private JLabel labelSaveAsTxt;
    private JTextField textFieldSaveTxtDir;
    private JButton buttonSaveTxt;
    private JCheckBox checkBoxSaveTxt;
    private JLabel labelVersion;
    private String serverUrl;
    private String userName;
    private String password;
    private String downloadDir;
    private boolean isDownload;
    private String saveTxtDir;
    private boolean isSaveTxt;
    private GetDocumentsTask getDocumentsTask;

    /**
     * Constructor for CrobotGUI
     *
     * @param title
     * @throws HeadlessException
     */
    public CrobotGUI(String title) throws HeadlessException {
        super(title);
        setContentPane(panelMain);
        initSystemTray();
        initCloseOperation();
        initActions();
        textAreaStatus.append("Welcome to Crobot.");
        //setExtendedState(Frame.ICONIFIED);
        setFrameIcon();
        autoStartWorker();
    }

    private void autoStartWorker() {
        boolean autostart = AppProperties.getInstance().getPropertyAsBoolean("crobot.worker.auto.start");
        if (autostart) {
            int waitDuration = AppProperties.getInstance().getPropertyAsInt("crobot.worker.auto.after");
            try {
                TimeUnit.SECONDS.sleep(waitDuration);
            } catch (InterruptedException e) {
                log.error("Auto start wait error", e);
            }

            textFieldServerAddress.setText(AppProperties.getInstance().getProperty("cserver.connection.address"));
            textFieldUsername.setText(AppProperties.getInstance().getProperty("cserver.connection.username"));
            passwordFieldPassword.setText(AppProperties.getInstance().getProperty("cserver.connection.password"));

            startProcess();
        }
    }

    /**
     *
     */
    private void initActions() {
        this.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                textFieldServerAddress.requestFocus();
            }
        });

        buttonClose.addActionListener(new ExitAction());

        buttonDownloadDir.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Directory For PDF");
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int returnValue = fileChooser.showOpenDialog(panelMain);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    textFieldDownloadDir.setText(fileChooser.getSelectedFile().getPath());
                }
            }
        });

        buttonSaveTxt.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Directory For TXT");
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int returnValue = fileChooser.showOpenDialog(panelMain);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    textFieldSaveTxtDir.setText(fileChooser.getSelectedFile().getPath());
                }
            }
        });

        buttonStart.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                startProcess();
            }
        });
        buttonStop.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                stopProcess();
            }
        });

        buttonTestConnection.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                testServerConnection(false);
            }
        });
        textFieldServerAddress.setText("http://localhost:8090");
        textFieldUsername.setText("crobot");
        passwordFieldPassword.setText("secret");
    }

    private void startProcess() {
        this.isDownload = checkBoxDownload.isSelected();
        this.downloadDir = textFieldDownloadDir.getText();
        if (isDownload && !isDirectory(this.downloadDir))
            return;

        this.isSaveTxt = checkBoxSaveTxt.isSelected();
        this.saveTxtDir = textFieldSaveTxtDir.getText();
        if (isSaveTxt && !isDirectory(this.saveTxtDir))
            return;

        textAreaStatus.append("\nChecking server connection..");
        boolean isTestOK = testServerConnection(true);
        if (isTestOK) {
            textAreaStatus.append("\nServer connection is OK.");
            textAreaStatus.append("\nStarting the process..");
            this.serverUrl = textFieldServerAddress.getText();
            this.userName = textFieldUsername.getText();
            this.password = new String(passwordFieldPassword.getPassword());

            labelWorking.setIcon(new ImageIcon(getClass().getResource("/icons/blocks-working.gif")));
            buttonStart.setEnabled(false);
            (getDocumentsTask = new GetDocumentsTask()).execute();
            buttonStart.setEnabled(true);
        } else {
            textAreaStatus.append("\nPlease check server connection!");
        }
    }

    /**
     * Stops to getting documents.
     */
    private void stopProcess() {
        if (getDocumentsTask != null) {
            getDocumentsTask.cancel(true);
        }
        labelWorking.setIcon(new ImageIcon(getClass().getResource("/icons/blocks.png")));
        buttonStart.setEnabled(true);
        log.debug("GetDocumentsTask is stopped.");
    }

    /**
     * Tests server connection
     */
    private boolean testServerConnection(boolean silent) {
        String server_url = textFieldServerAddress.getText();
        String server_test_url = server_url + "/v1/api/hello"; //Ex: "http://localhost:8090/v1/api/hello";

        String authString = textFieldUsername.getText() + ":" + new String(passwordFieldPassword.getPassword());
        String authStringEnc = new String(Base64.getEncoder().encode(authString.getBytes()));

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Basic " + authStringEnc);
        ResponseContent responseContent = HttpClientUtil.getInstance().sendHttpGet(server_test_url, headers, RequestConfig.DEFAULT, 1, 1);

        if (silent && responseContent.getResponseCode() == HttpStatus.SC_OK) {
            return true;
        }

        if (responseContent.getResponseCode() == HttpStatus.SC_OK) {
            JOptionPane.showMessageDialog(this,
                    ResourceBundle.getBundle("messages").getString("app.server_connection_ok.message"),
                    ResourceBundle.getBundle("messages").getString("app.server_connection_ok.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } else {
            StringBuilder responseError = new StringBuilder("");
            if (responseContent != null) {
                responseError.append("\nHttp Status Code: ");
                responseError.append(responseContent.getResponseCode());
            }
            JOptionPane.showMessageDialog(this,
                    ResourceBundle.getBundle("messages").getString("app.server_connection_failure.message") + responseError.toString(),
                    ResourceBundle.getBundle("messages").getString("app.server_connection_failure.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    /**
     * @param dirPath
     */
    private boolean isDirectory(String dirPath) {
        Path file = new File(dirPath).toPath();
        if (!Files.isDirectory(file)) {
            JOptionPane.showMessageDialog(this, "Invalid directory\n" + dirPath,
                    "Invalid Directory",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Sets frame icon
     */
    private void setFrameIcon() {
        try {
            this.setIconImage(ImageIO.read(getClass().getResource("/icons/logo-icon.png")));
        } catch (IOException e) {
            log.debug("Error while loading icon file logo-icon.png", e);
        }
    }

    /**
     * Initialize window close operation
     */
    private void initCloseOperation() {
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        Action closeAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                JFrame frame = (JFrame) e.getSource();
                //TODO: Add close operations
                log.info("Closing: " + frame.getTitle());
            }
        };

        CloseListener closeListener = new CloseListener(ResourceBundle.getBundle("messages").getString("app.confirm.exit.message"),
                ResourceBundle.getBundle("messages").getString("app.confirm.exit.title"), closeAction);
        this.addWindowListener(closeListener);
    }

    /**
     * Initializes system tray menu and properties.
     */
    private void initSystemTray() {
        if (SystemTray.isSupported()) {
            log.debug("System tray is supported");
            tray = SystemTray.getSystemTray();

            Image image = null;

            try {
                image = ImageIO.read(getClass().getResource("/icons/logo-icon.png"));
            } catch (IOException e) {
                log.debug("Error while loading icon file logo-icon.png", e);
            }

            ActionListener exitListener = new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setVisible(true);
                    setExtendedState(JFrame.NORMAL);
                    int dialogResult = JOptionPane.showConfirmDialog(panelMain, ResourceBundle.getBundle("messages").getString("app.confirm.exit.message"),
                            ResourceBundle.getBundle("messages").getString("app.confirm.exit.title"), JOptionPane.YES_NO_OPTION);
                    if (dialogResult == JOptionPane.YES_OPTION) {
                        log.info("Exiting..");
                        System.exit(0);
                    }
                }
            };

            PopupMenu popup = new PopupMenu();
            MenuItem defaultItem = new MenuItem(ResourceBundle.getBundle("messages").getString("tray.menu.exit"));
            defaultItem.addActionListener(exitListener);
            popup.add(defaultItem);
            defaultItem = new MenuItem(ResourceBundle.getBundle("messages").getString("tray.menu.open"));
            defaultItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setVisible(true);
                    setExtendedState(JFrame.NORMAL);
                }
            });
            popup.add(defaultItem);
            trayIcon = new TrayIcon(image, "Crobot", popup);
            trayIcon.setImageAutoSize(true);
        } else {
            log.error("System tray is not supported");
        }

        addWindowStateListener(new WindowStateListener() {
            public void windowStateChanged(WindowEvent e) {
                if (e.getNewState() == ICONIFIED) {
                    try {
                        tray.add(trayIcon);
                        setVisible(false);
                        log.debug("Added to SystemTray");
                    } catch (AWTException ex) {
                        log.error("Unable to add to SystemTray", ex);
                    }
                }
                if (e.getNewState() == 7) {
                    try {
                        tray.add(trayIcon);
                        setVisible(false);
                        log.debug("Added to SystemTray");
                    } catch (AWTException ex) {
                        log.error("Unable to add to SystemTray", ex);
                    }
                }
                if (e.getNewState() == MAXIMIZED_BOTH) {
                    tray.remove(trayIcon);
                    setVisible(true);
                    log.debug("SystemTray icon removed");
                }
                if (e.getNewState() == NORMAL) {
                    tray.remove(trayIcon);
                    setVisible(true);
                    log.debug("SystemTray icon removed");
                }
            }
        });

        try {
            setIconImage(ImageIO.read(getClass().getResource("/icons/logo-icon.png")));
        } catch (IOException e) {
            log.debug("Unable to read icon", e);
        }
        setVisible(true);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        panelMain = new JPanel();
        panelMain.setLayout(new BorderLayout(0, 0));
        panelMainHeader = new JPanel();
        panelMainHeader.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panelMain.add(panelMainHeader, BorderLayout.NORTH);
        labelHeader = new JLabel();
        labelHeader.setBackground(new Color(-10789208));
        Font labelHeaderFont = this.$$$getFont$$$(null, Font.BOLD, 20, labelHeader.getFont());
        if (labelHeaderFont != null) labelHeader.setFont(labelHeaderFont);
        labelHeader.setHorizontalAlignment(0);
        labelHeader.setHorizontalTextPosition(0);
        labelHeader.setText("Crobot");
        panelMainHeader.add(labelHeader);
        panelMainBody = new JPanel();
        panelMainBody.setLayout(new FormLayout("fill:d:noGrow,left:4dlu:noGrow,fill:d:grow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow", "center:d:noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:d:grow"));
        panelMain.add(panelMainBody, BorderLayout.CENTER);
        panelMainBody.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Settings", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        labelServerAddress = new JLabel();
        labelServerAddress.setText("Server Address");
        CellConstraints cc = new CellConstraints();
        panelMainBody.add(labelServerAddress, cc.xy(1, 1));
        labelUsername = new JLabel();
        labelUsername.setText("Username");
        panelMainBody.add(labelUsername, cc.xy(1, 5));
        textFieldServerAddress = new JTextField();
        panelMainBody.add(textFieldServerAddress, cc.xy(3, 1, CellConstraints.FILL, CellConstraints.DEFAULT));
        textFieldUsername = new JTextField();
        panelMainBody.add(textFieldUsername, cc.xy(3, 5, CellConstraints.FILL, CellConstraints.DEFAULT));
        passwordFieldPassword = new JPasswordField();
        panelMainBody.add(passwordFieldPassword, cc.xy(3, 7, CellConstraints.FILL, CellConstraints.DEFAULT));
        labelPassword = new JLabel();
        labelPassword.setText("Password");
        panelMainBody.add(labelPassword, cc.xy(1, 7));
        labelHelpServerAddress = new JLabel();
        labelHelpServerAddress.setText("Ex: http://localhost:8090");
        panelMainBody.add(labelHelpServerAddress, cc.xyw(3, 3, 5));
        jScrollPaneStaus = new JScrollPane();
        jScrollPaneStaus.setEnabled(true);
        panelMainBody.add(jScrollPaneStaus, cc.xyw(1, 15, 7, CellConstraints.FILL, CellConstraints.FILL));
        jScrollPaneStaus.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Status", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        textAreaStatus = new JTextArea();
        textAreaStatus.setEditable(false);
        jScrollPaneStaus.setViewportView(textAreaStatus);
        labelDownloadDir = new JLabel();
        labelDownloadDir.setText("Download Dir");
        panelMainBody.add(labelDownloadDir, cc.xy(1, 11));
        textFieldDownloadDir = new JTextField();
        panelMainBody.add(textFieldDownloadDir, cc.xy(3, 11, CellConstraints.FILL, CellConstraints.DEFAULT));
        buttonDownloadDir = new JButton();
        buttonDownloadDir.setIcon(new ImageIcon(getClass().getResource("/icons/folder-16.png")));
        buttonDownloadDir.setText("");
        panelMainBody.add(buttonDownloadDir, cc.xy(5, 11));
        checkBoxDownload = new JCheckBox();
        checkBoxDownload.setText("Download Pdf");
        panelMainBody.add(checkBoxDownload, cc.xy(7, 11));
        labelSaveAsTxt = new JLabel();
        labelSaveAsTxt.setText("Text Dir");
        panelMainBody.add(labelSaveAsTxt, cc.xy(1, 13));
        textFieldSaveTxtDir = new JTextField();
        panelMainBody.add(textFieldSaveTxtDir, cc.xy(3, 13, CellConstraints.FILL, CellConstraints.DEFAULT));
        buttonSaveTxt = new JButton();
        buttonSaveTxt.setIcon(new ImageIcon(getClass().getResource("/icons/folder-16.png")));
        buttonSaveTxt.setText("");
        panelMainBody.add(buttonSaveTxt, cc.xy(5, 13));
        checkBoxSaveTxt = new JCheckBox();
        checkBoxSaveTxt.setText("Save As Txt");
        panelMainBody.add(checkBoxSaveTxt, cc.xy(7, 13));
        buttonTestConnection = new JButton();
        buttonTestConnection.setText("Test Connection");
        panelMainBody.add(buttonTestConnection, cc.xy(3, 9));
        panelMainFooter = new JPanel();
        panelMainFooter.setLayout(new FormLayout("fill:d:grow,left:4dlu:noGrow,fill:d:grow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow", "center:d:noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow"));
        panelMain.add(panelMainFooter, BorderLayout.SOUTH);
        buttonStart = new JButton();
        buttonStart.setText("Start");
        panelMainFooter.add(buttonStart, cc.xy(5, 1));
        buttonStop = new JButton();
        buttonStop.setText("Stop");
        panelMainFooter.add(buttonStop, cc.xy(7, 1));
        labelWorking = new JLabel();
        labelWorking.setIcon(new ImageIcon(getClass().getResource("/icons/blocks.png")));
        labelWorking.setText("");
        panelMainFooter.add(labelWorking, cc.xy(1, 1));
        buttonClose = new JButton();
        buttonClose.setText("Close");
        panelMainFooter.add(buttonClose, cc.xy(9, 1));
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        panelMainFooter.add(spacer1, cc.xywh(11, 1, 1, 3, CellConstraints.DEFAULT, CellConstraints.FILL));
        labelVersion = new JLabel();
        labelVersion.setHorizontalAlignment(0);
        labelVersion.setHorizontalTextPosition(0);
        labelVersion.setText("Version 1.0");
        panelMainFooter.add(labelVersion, cc.xyw(1, 3, 9, CellConstraints.CENTER, CellConstraints.DEFAULT));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panelMain;
    }

    /**
     * Starts to getting documents.
     */
    private class GetDocumentsTask extends SwingWorker<Void, Void> {
        @Override
        protected Void doInBackground() {
            textAreaStatus.append("\nPlease wait while processing..");
            CrobotWorker crobotWorker = new CrobotWorker(serverUrl, userName, password, downloadDir, isDownload, saveTxtDir, isSaveTxt);
            try {
                crobotWorker.start();
            } catch (InterruptedException e) {
                textAreaStatus.append("\nError while starting the process! Please check logs.");
                log.error(e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                textAreaStatus.append("\nError while starting the process! Please check logs.");
                log.error(e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void done() {
            labelWorking.setIcon(new ImageIcon(getClass().getResource("/icons/blocks.png")));
            textAreaStatus.append("\nProcess finished!");
            log.debug("GetDocumentsTask finished.");
        }
    }

}
