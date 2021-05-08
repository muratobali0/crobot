package com.crobot.gui;

import com.crobot.http.HttpClientUtil;
import com.crobot.http.ResponseContent;
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
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
    private String serverUrl;
    private String userName;
    private String password;
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

        //setExtendedState(Frame.ICONIFIED);
        setFrameIcon();

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
    }

    private void startProcess() {
        boolean isTestOK = testServerConnection(true);
        if (isTestOK) {
            this.serverUrl = textFieldServerAddress.getText();
            this.userName = textFieldUsername.getText();
            this.password = new String(passwordFieldPassword.getPassword());

            labelWorking.setIcon(new ImageIcon(getClass().getResource("/icons/blocks-working.gif")));
            buttonStart.setEnabled(false);
            (getDocumentsTask = new GetDocumentsTask()).execute();
            buttonStart.setEnabled(true);
        }
    }

    /**
     * Stops to gettting documents.
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
            trayIcon = new TrayIcon(image, "DataHunter", popup);
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
        panelMainHeader.setLayout(new FormLayout("fill:d:noGrow", "center:d:noGrow"));
        panelMain.add(panelMainHeader, BorderLayout.NORTH);
        labelHeader = new JLabel();
        labelHeader.setBackground(new Color(-10789208));
        Font labelHeaderFont = this.$$$getFont$$$(null, Font.BOLD, 16, labelHeader.getFont());
        if (labelHeaderFont != null) labelHeader.setFont(labelHeaderFont);
        labelHeader.setHorizontalAlignment(0);
        labelHeader.setHorizontalTextPosition(0);
        labelHeader.setText("Crobot");
        CellConstraints cc = new CellConstraints();
        panelMainHeader.add(labelHeader, cc.xy(1, 1, CellConstraints.FILL, CellConstraints.DEFAULT));
        panelMainBody = new JPanel();
        panelMainBody.setLayout(new FormLayout("fill:d:noGrow,left:4dlu:noGrow,fill:d:grow", "center:d:noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow,top:4dlu:noGrow,center:max(d;4px):noGrow"));
        panelMain.add(panelMainBody, BorderLayout.CENTER);
        panelMainBody.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Server", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        labelServerAddress = new JLabel();
        labelServerAddress.setText("Server Address");
        panelMainBody.add(labelServerAddress, cc.xy(1, 1));
        labelUsername = new JLabel();
        labelUsername.setText("Username");
        panelMainBody.add(labelUsername, cc.xy(1, 3));
        textFieldServerAddress = new JTextField();
        panelMainBody.add(textFieldServerAddress, cc.xy(3, 1, CellConstraints.FILL, CellConstraints.DEFAULT));
        textFieldUsername = new JTextField();
        panelMainBody.add(textFieldUsername, cc.xy(3, 3, CellConstraints.FILL, CellConstraints.DEFAULT));
        passwordFieldPassword = new JPasswordField();
        panelMainBody.add(passwordFieldPassword, cc.xy(3, 5, CellConstraints.FILL, CellConstraints.DEFAULT));
        labelPassword = new JLabel();
        labelPassword.setText("Password");
        panelMainBody.add(labelPassword, cc.xy(1, 5));
        buttonTestConnection = new JButton();
        buttonTestConnection.setText("Test Connection");
        panelMainBody.add(buttonTestConnection, cc.xy(3, 7));
        panelMainFooter = new JPanel();
        panelMainFooter.setLayout(new FormLayout("fill:d:grow,left:4dlu:noGrow,fill:d:grow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow,left:4dlu:noGrow,fill:max(d;4px):noGrow", "center:d:noGrow"));
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
        panelMainFooter.add(spacer1, cc.xy(3, 1, CellConstraints.FILL, CellConstraints.DEFAULT));
        final com.intellij.uiDesigner.core.Spacer spacer2 = new com.intellij.uiDesigner.core.Spacer();
        panelMainFooter.add(spacer2, cc.xy(11, 1, CellConstraints.DEFAULT, CellConstraints.FILL));
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

            CrobotWorker crobotWorker = new CrobotWorker();
            try {
                crobotWorker.start();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void done() {
            labelWorking.setIcon(new ImageIcon(getClass().getResource("/icons/blocks.png")));
            log.debug("GetDocumentsTask finished.");
        }
    }

}
