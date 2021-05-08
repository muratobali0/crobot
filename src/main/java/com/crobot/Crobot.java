package com.crobot;

import com.crobot.gui.CrobotGUI;
import com.crobot.util.AppProperties;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;

@Slf4j
public class Crobot {
    /**
     * Starting point of the application
     *
     * @param args
     */
    public static void main(final String[] args) {
        AppProperties.getInstance().init();

        SwingUtilities.invokeLater(() -> {
            log.debug("Starting application");
            try {
                UIManager.setLookAndFeel(AppProperties.getInstance().getLookAndFeel());
            } catch (Exception e) {
                log.error("Error while starting the application", e);
                e.printStackTrace();
            }

            final CrobotGUI crobotGUI = new CrobotGUI("Crobot");
            crobotGUI.pack();
            crobotGUI.setSize(960, 660);
            crobotGUI.setLocationRelativeTo(null);
            crobotGUI.setVisible(true);
        });


    }
}