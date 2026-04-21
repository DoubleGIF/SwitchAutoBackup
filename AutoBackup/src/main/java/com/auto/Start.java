package com.auto;

import com.auto.Ui.DeviceManagerUI;
import javax.swing.*;


public class Start {


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DeviceManagerUI ui = new DeviceManagerUI();
            ui.setVisible(true);
        });
    }
}
