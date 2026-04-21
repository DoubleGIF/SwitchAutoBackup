package com.auto.Ui;

import com.auto.Util.*;
import com.auto.Util.enums.ConnectType;
import com.auto.Util.enums.DeviceType;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 由于不太会写UI界面所以这个类由Ai完成
 */

public class DeviceManagerUI extends JFrame {

    private Gson gson = new Gson();
    private static final String CONFIG_FILE_PATH = "config.json";
    private static final String SYSTEM_CONFIG_PATH = "system.json";

    // 全局备份目录，供Device类使用
    private static String globalBackupDir = "backup";

    //默认华为交换机super Level级别
    private static int superLevel = 0;

    // 定时任务相关
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledBackupTask;
    private Timer checkTimer; // 用于检查一次性任务

    // UI组件
    private JTable deviceTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private JProgressBar progressBar;
    private JButton backupBtn;
    private JButton addBtn;
    private JButton editBtn;
    private JButton deleteBtn;
    private JButton refreshBtn;
    private JButton clearLogBtn;
    private JButton systemConfigBtn;

    private int success = 0;
    private int failure = 0;


    /**
     * 初始化界面外观和风格
     */
    static {
        //加载 FlatLightLaf UI
        FlatLightLaf.setup();
        try {
            UIManager.setLookAndFeel(new FlatMacLightLaf());
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF");
        }
    }

    // 设备列表
    private List<DeviceConfig> deviceConfigs = new ArrayList<>();

    // 线程池 - 使用 volatile 确保可见性
    private volatile ExecutorService executorService;
    private int currentThreadPoolSize = 10; // 当前线程池大小

    public DeviceManagerUI() {
        // 先加载系统配置
        loadSystemConfigFromFile();
        // 初始化线程池
        initThreadPool();
        initUI();
        loadDeviceConfigs();
        refreshDeviceTable();
        // 启动定时任务调度器
        initScheduler();
        // 加载并启动定时备份任务
        loadAndStartScheduleTask();
        //优雅关闭线程池
        setupCloseHandler();
    }

    /**
     * 从文件加载系统配置
     */
    private void loadSystemConfigFromFile() {
        try {
            File configFile = new File(SYSTEM_CONFIG_PATH);
            if (configFile.exists()) {
                String jsonStr = readFileToJson(SYSTEM_CONFIG_PATH);
                JsonObject config = gson.fromJson(jsonStr, JsonObject.class);
                //加载线程池大小
                if (config.has("threadCount")) {
                    currentThreadPoolSize = config.get("threadCount").getAsInt();
                }
                // 加载备份目录
                if (config.has("backupDir")) {
                    String backupDir = config.get("backupDir").getAsString();
                    if (backupDir != null && !backupDir.trim().isEmpty()) {
                        setGlobalBackupDir(backupDir);
                    }
                }

                if (config.has("superLevel")) {
                    int superLevel = config.get("superLevel").getAsInt();
                    setSuperLevel(superLevel);
                }
            }
        } catch (Exception e) {
            addLog("加载系统配置失败: " + e.getMessage());
            currentThreadPoolSize = 10; // 默认10个线程
        }
    }

    /**
     * 初始化线程池
     */
    private void initThreadPool() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        executorService = Executors.newFixedThreadPool(currentThreadPoolSize);
        addLog("线程池已初始化，大小: " + currentThreadPoolSize);
    }

    /**
     * 初始化UI界面
     */
    private void initUI() {
        setTitle("懒得备份系统");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 添加工具栏
        mainPanel.add(createToolBar(), BorderLayout.NORTH);

        // 添加设备列表面板
        mainPanel.add(createDeviceListPanel(), BorderLayout.CENTER);

        // 添加日志面板
        mainPanel.add(createLogPanel(), BorderLayout.SOUTH);

        add(mainPanel);
    }

    /**
     * 创建工具栏
     */
    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        backupBtn = new JButton("备份配置");
        backupBtn.addActionListener(e -> backupSelectedDevices());

        addBtn = new JButton("添加设备");
        addBtn.addActionListener(e -> showDeviceDialog(null));

        editBtn = new JButton("编辑设备");
        editBtn.addActionListener(e -> editSelectedDevice());

        deleteBtn = new JButton("删除设备");
        deleteBtn.addActionListener(e -> deleteSelectedDevices());

        refreshBtn = new JButton("刷新");
        refreshBtn.addActionListener(e -> {
            loadDeviceConfigs();
            refreshDeviceTable();
            addLog("设备列表已刷新");
        });

        clearLogBtn = new JButton("清空日志");
        clearLogBtn.addActionListener(e -> logArea.setText(""));


        systemConfigBtn = new JButton("系统配置");
        systemConfigBtn.addActionListener(e -> showSystemConfigDialog());

        toolBar.add(backupBtn);
        toolBar.addSeparator();
        toolBar.add(addBtn);
        toolBar.add(editBtn);
        toolBar.add(deleteBtn);
        toolBar.addSeparator();
        toolBar.add(refreshBtn);
        toolBar.add(clearLogBtn);
        toolBar.addSeparator();
        toolBar.add(systemConfigBtn);

        return toolBar;
    }

    /**
     * 创建设备列表面板
     */
    private JPanel createDeviceListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("设备列表"));

        // 创建表格模型
        String[] columns = {"主机IP", "用户名", "设备类型", "连接类型", "SSH端口", "TELNET端口", "状态"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        deviceTable = new JTable(tableModel);
        deviceTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        deviceTable.getTableHeader().setReorderingAllowed(false);

        // 设置列宽
        deviceTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        deviceTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        deviceTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        deviceTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        deviceTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        deviceTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        deviceTable.getColumnModel().getColumn(6).setPreferredWidth(80);

        JScrollPane scrollPane = new JScrollPane(deviceTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        // 添加进度条
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        panel.add(progressBar, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * 创建日志面板
     */
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("操作日志"));
        panel.setPreferredSize(new Dimension(panel.getWidth(), 200));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 加载设备配置
     */
    private void loadDeviceConfigs() {
        deviceConfigs.clear();
        try {
            String jsonStr = readFileToJson(CONFIG_FILE_PATH);
            JsonObject device = gson.fromJson(jsonStr, JsonObject.class);
            JsonArray devicesJsonArray = device.getAsJsonArray("devices");

            for (int i = 0; i < devicesJsonArray.size(); i++) {
                JsonObject asJsonObject = devicesJsonArray.get(i).getAsJsonObject();
                DeviceConfig config = new DeviceConfig();
                config.host = asJsonObject.get("host").getAsString();
                config.username = asJsonObject.get("username").getAsString();
                config.password = asJsonObject.get("password").getAsString();
                config.deviceType = asJsonObject.get("deviceType").getAsString();
                config.enPassword = asJsonObject.get("enPassword").getAsString();

                // 读取连接类型，默认为TELNET
                if (asJsonObject.has("connectionType")) {
                    config.connectionType = asJsonObject.get("connectionType").getAsString();
                } else {
                    config.connectionType = "TELNET";
                }

                // 读取端口配置，使用默认值
                if (asJsonObject.has("sshPort")) {
                    config.sshPort = asJsonObject.get("sshPort").getAsInt();
                } else {
                    config.sshPort = 22;
                }

                if (asJsonObject.has("telnetPort")) {
                    config.telnetPort = asJsonObject.get("telnetPort").getAsInt();
                } else {
                    config.telnetPort = 23;
                }

                if (asJsonObject.has("superLevel")) {
                    config.superLevel = asJsonObject.get("superLevel").getAsInt();
                }

                deviceConfigs.add(config);
            }
            addLog("成功加载 " + deviceConfigs.size() + " 个设备配置");
        } catch (IOException e) {
            addLog("读取配置文件失败: " + e.getMessage());
            // 如果配置文件不存在，创建一个示例配置
            createExampleConfig();
        } catch (Exception e) {
            addLog("解析配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 创建示例配置文件
     */
    private void createExampleConfig() {
        try {
            JsonObject root = new JsonObject();
            JsonArray devicesArray = new JsonArray();

            // 示例设备1 - CISCO SSH
            JsonObject device1 = new JsonObject();
            device1.addProperty("host", "192.168.1.1");
            device1.addProperty("username", "admin");
            device1.addProperty("password", "password123");
            device1.addProperty("deviceType", "CISCO");
            device1.addProperty("enPassword", "enable123");
            device1.addProperty("connectionType", "SSH");
            device1.addProperty("sshPort", 22);
            device1.addProperty("telnetPort", 23);
            devicesArray.add(device1);

            // 示例设备2 - HUAWEI TELNET
            JsonObject device2 = new JsonObject();
            device2.addProperty("host", "192.168.1.2");
            device2.addProperty("username", "admin");
            device2.addProperty("password", "password456");
            device2.addProperty("deviceType", "HUAWEI");
            device2.addProperty("enPassword", "super123");
            device2.addProperty("connectionType", "TELNET");
            device2.addProperty("sshPort", 22);
            device2.addProperty("telnetPort", 23);
            devicesArray.add(device2);

            root.add("devices", devicesArray);

            try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH)) {
                writer.write(gson.toJson(root));
            }
            addLog("已创建示例配置文件: " + CONFIG_FILE_PATH);
            loadDeviceConfigs(); // 重新加载
        } catch (IOException e) {
            addLog("创建示例配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 刷新设备表格
     */
    private void refreshDeviceTable() {
        tableModel.setRowCount(0);
        for (DeviceConfig config : deviceConfigs) {
            Object[] row = {
                    config.host,
                    config.username,
                    config.deviceType,
                    config.connectionType,
                    config.sshPort,
                    config.telnetPort,
                    "未备份"
            };
            tableModel.addRow(row);
        }
    }

    /**
     * 备份选中的设备
     */
    private void backupSelectedDevices() {
        int[] selectedRows = deviceTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "请至少选择一个设备", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

//        int result = JOptionPane.showConfirmDialog(this,
//                "确定要备份选中的 " + selectedRows.length + " 个设备吗？",
//                "确认备份",
//                JOptionPane.YES_NO_OPTION);
//
//        if (result != JOptionPane.YES_OPTION) {
//            return;
//        }

        success = 0;
        failure = 0;
        progressBar.setMaximum(selectedRows.length);
        progressBar.setValue(0);
        backupBtn.setEnabled(false);

        for (int i = 0; i < selectedRows.length; i++) {
            int row = selectedRows[i];
            DeviceConfig config = deviceConfigs.get(row);
            final int currentProgress = i + 1;

            executorService.submit(() -> {
                backupDevice(config, row, currentProgress);
            });
        }
    }

    /**
     * 备份单个设备
     */
    private void backupDevice(DeviceConfig config, int row, int progress) {
        updateTableStatus(row, "备份中...");
        Device device = null;
        if ("HUAWEI".equalsIgnoreCase(config.deviceType)) {
            device = new HuaweiDevice();
        } else if ("CISCO".equalsIgnoreCase(config.deviceType)) {
            device = new CiscoDevice();
        } else {
            addLog("✗ 不支持的设备类型: " + config.deviceType + " - " + config.host);
            updateTableStatus(row, "失败");
            SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
            return;
        }

        // 设置设备连接参数
        device.host = config.host;
        device.password = config.password;
        device.account = config.username;
        device.superPassword = config.enPassword;
        if (config.superLevel != 0){
            device.superLevel = config.superLevel;
        }else {
            device.superLevel = superLevel;
        }


        // 设置连接类型
        if ("SSH".equalsIgnoreCase(config.connectionType)) {
            device.CONNECT_TYPE = ConnectType.SSH;
            device.ssh_port = config.sshPort;
        } else {
            device.CONNECT_TYPE = ConnectType.TELNET;
            device.telnet_port = config.telnetPort;
        }

        // 设置设备类型
        if (device instanceof HuaweiDevice) {
            device.DEVICE_TYPER = DeviceType.HUAWEI;
        } else if (device instanceof CiscoDevice) {
            device.DEVICE_TYPER = DeviceType.CISCO;
        }

        try {
            device.backupConfig();
            addLog("✓ 设备备份成功: " + config.host);
            updateTableStatus(row, "成功");
            success ++;
        } catch (Exception e) {
            addLog("✗ 设备备份失败: " + config.host + " - " + e.getMessage());
            e.printStackTrace();
            updateTableStatus(row, "failure");
            failure ++;
        }


        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(progress);
            if (progress >= progressBar.getMaximum()) {
                backupBtn.setEnabled(true);
                //备份完成提示
//                JOptionPane.showMessageDialog(this, "所有设备备份完成！", "完成", JOptionPane.INFORMATION_MESSAGE);
                addLog("备份成功设备数量:" + success);
                addLog("备份失败设备数量:" + failure);
            }
        });
    }

    /**
     * 更新表格中的设备状态
     */
    private void updateTableStatus(int row, String status) {
        SwingUtilities.invokeLater(() -> {
            tableModel.setValueAt(status, row, 6);
        });
    }

    /**
     * 显示设备添加/编辑对话框
     */
    private void showDeviceDialog(DeviceConfig config) {
        boolean isEdit = config != null;
        JDialog dialog = new JDialog(this, isEdit ? "编辑设备" : "添加设备", true);
        dialog.setSize(500, 500);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 主机IP
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("主机IP:*"), gbc);

        JTextField hostField = new JTextField(20);
        if (isEdit) hostField.setText(config.host);
        gbc.gridx = 1;
        panel.add(hostField, gbc);

        // 用户名
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("用户名:"), gbc);

        JTextField usernameField = new JTextField(20);
        if (isEdit) usernameField.setText(config.username);
        gbc.gridx = 1;
        panel.add(usernameField, gbc);

        // 密码
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("密码:"), gbc);

        JPasswordField passwordField = new JPasswordField(20);
        if (isEdit) passwordField.setText(config.password);
        gbc.gridx = 1;
        panel.add(passwordField, gbc);

        // 设备类型
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("设备类型:*"), gbc);

        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"HUAWEI", "CISCO"});
        if (isEdit && "CISCO".equalsIgnoreCase(config.deviceType)) {
            typeCombo.setSelectedItem("CISCO");
        }
        gbc.gridx = 1;
        panel.add(typeCombo, gbc);

        // Enable/Super密码
        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(new JLabel("Enable/Super密码:"), gbc);

        JPasswordField enPasswordField = new JPasswordField(20);
        if (isEdit) enPasswordField.setText(config.enPassword);
        gbc.gridx = 1;
        panel.add(enPasswordField, gbc);

        // 连接类型
        gbc.gridx = 0;
        gbc.gridy = 5;
        panel.add(new JLabel("连接类型:*"), gbc);

        JComboBox<String> connectionTypeCombo = new JComboBox<>(new String[]{"TELNET", "SSH"});
        if (isEdit && "SSH".equalsIgnoreCase(config.connectionType)) {
            connectionTypeCombo.setSelectedItem("SSH");
        }
        gbc.gridx = 1;
        panel.add(connectionTypeCombo, gbc);

        // SSH端口
        gbc.gridx = 0;
        gbc.gridy = 6;
        panel.add(new JLabel("SSH端口:"), gbc);

        JTextField sshPortField = new JTextField(10);
        if (isEdit) {
            sshPortField.setText(String.valueOf(config.sshPort));
        } else {
            sshPortField.setText("22");
        }
        gbc.gridx = 1;
        panel.add(sshPortField, gbc);

        // TELNET端口
        gbc.gridx = 0;
        gbc.gridy = 7;
        panel.add(new JLabel("TELNET端口:"), gbc);

        JTextField telnetPortField = new JTextField(10);
        if (isEdit) {
            telnetPortField.setText(String.valueOf(config.telnetPort));
        } else {
            telnetPortField.setText("23");
        }
        gbc.gridx = 1;
        panel.add(telnetPortField, gbc);

        // Super级别
        gbc.gridx = 0;
        gbc.gridy = 8;
        panel.add(new JLabel("Super级别:"), gbc);

        JComboBox<String> superLevelCombo = new JComboBox<>();
        for (int i = 0; i <= 15; i++) {
            superLevelCombo.addItem(String.valueOf(i));
        }
        if (isEdit) {
            superLevelCombo.setSelectedItem(String.valueOf(config.superLevel));
        } else {
            superLevelCombo.setSelectedItem("0");
        }
        gbc.gridx = 1;
        panel.add(superLevelCombo, gbc);




        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");
        buttonPanel.add(saveBtn);
        buttonPanel.add(cancelBtn);

        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.gridwidth = 2;
        panel.add(buttonPanel, gbc);

        dialog.add(panel);

        saveBtn.addActionListener(e -> {
            String host = hostField.getText().trim();
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String deviceType = (String) typeCombo.getSelectedItem();
            String enPassword = new String(enPasswordField.getPassword());
            String connectionType = (String) connectionTypeCombo.getSelectedItem();
            int superLevel = Integer.parseInt((String) superLevelCombo.getSelectedItem());

            int sshPort;
            int telnetPort;

            try {
                sshPort = Integer.parseInt(sshPortField.getText().trim());
                telnetPort = Integer.parseInt(telnetPortField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "端口号必须是数字", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (host.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "主机IP不能为空", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (isEdit) {
                config.host = host;
                config.username = username;
                config.password = password;
                config.deviceType = deviceType;
                config.enPassword = enPassword;
                config.connectionType = connectionType;
                config.sshPort = sshPort;
                config.telnetPort = telnetPort;
                config.superLevel = superLevel;
                saveDeviceConfigs();
                refreshDeviceTable();
                addLog("设备已更新: " + host);
            } else {
                DeviceConfig newConfig = new DeviceConfig();
                newConfig.host = host;
                newConfig.username = username;
                newConfig.password = password;
                newConfig.deviceType = deviceType;
                newConfig.enPassword = enPassword;
                newConfig.connectionType = connectionType;
                newConfig.sshPort = sshPort;
                newConfig.telnetPort = telnetPort;
                config.superLevel = superLevel;
                deviceConfigs.add(newConfig);
                saveDeviceConfigs();
                refreshDeviceTable();
                addLog("设备已添加: " + host);
            }
            dialog.dispose();
        });

        cancelBtn.addActionListener(e -> dialog.dispose());
        dialog.setVisible(true);
    }

    /**
     * 显示系统配置对话框
     */
    private void showSystemConfigDialog() {
        JDialog dialog = new JDialog(this, "系统配置", true);
        dialog.setSize(600, 550);
        dialog.setLocationRelativeTo(this);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 1. 线程数配置
        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(new JLabel("线程池大小:"), gbc);

        JSpinner threadSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
        threadSpinner.setValue(currentThreadPoolSize);
        gbc.gridx = 1;
        mainPanel.add(threadSpinner, gbc);

        // 2. Super级别配置
        gbc.gridx = 0;
        gbc.gridy = 1;
        mainPanel.add(new JLabel("Super级别:"), gbc);

        JComboBox<String> superLevelCombo = new JComboBox<>(new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"});
        gbc.gridx = 1;
        mainPanel.add(superLevelCombo, gbc);

        // 3. 备份目录配置
        gbc.gridx = 0;
        gbc.gridy = 2;
        mainPanel.add(new JLabel("备份保存目录:"), gbc);

        JPanel dirPanel = new JPanel(new BorderLayout(5, 0));
        JTextField backupDirField = new JTextField();
        JButton browseBtn = new JButton("浏览");
        dirPanel.add(backupDirField, BorderLayout.CENTER);
        dirPanel.add(browseBtn, BorderLayout.EAST);
        gbc.gridx = 1;
        mainPanel.add(dirPanel, gbc);

        // 4. 定时备份配置
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        mainPanel.add(new JLabel("定时备份策略:"), gbc);

        gbc.gridy = 4;
        // 创建定时策略选择框（先不添加事件监听）
        JComboBox<String> scheduleTypeCombo = new JComboBox<>(new String[]{"从不", "每天", "每周", "每月", "一次性"});
        mainPanel.add(scheduleTypeCombo, gbc);

        // 动态配置面板
        gbc.gridy = 5;
        JPanel scheduleDetailPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        mainPanel.add(scheduleDetailPanel, gbc);

        // 每天配置
        JPanel dailyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dailyPanel.add(new JLabel("执行时间:"));
        JSpinner hourSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 23, 1));
        JSpinner minuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        dailyPanel.add(hourSpinner);
        dailyPanel.add(new JLabel("时"));
        dailyPanel.add(minuteSpinner);
        dailyPanel.add(new JLabel("分"));

        // 每周配置
        JPanel weeklyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        weeklyPanel.add(new JLabel("星期:"));
        JComboBox<String> weekCombo = new JComboBox<>(new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"});
        weeklyPanel.add(weekCombo);
        weeklyPanel.add(new JLabel("执行时间:"));
        JSpinner weekHourSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 23, 1));
        JSpinner weekMinuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        weeklyPanel.add(weekHourSpinner);
        weeklyPanel.add(new JLabel("时"));
        weeklyPanel.add(weekMinuteSpinner);
        weeklyPanel.add(new JLabel("分"));

        // 每月配置
        JPanel monthlyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        monthlyPanel.add(new JLabel("日期:"));
        JComboBox<String> dayCombo = new JComboBox<>();
        for (int i = 1; i <= 31; i++) {
            dayCombo.addItem(String.valueOf(i));
        }
        monthlyPanel.add(dayCombo);
        monthlyPanel.add(new JLabel("号 执行时间:"));
        JSpinner monthHourSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 23, 1));
        JSpinner monthMinuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        monthlyPanel.add(monthHourSpinner);
        monthlyPanel.add(new JLabel("时"));
        monthlyPanel.add(monthMinuteSpinner);
        monthlyPanel.add(new JLabel("分"));

        // 一次性配置
        JPanel oncePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        oncePanel.add(new JLabel("执行时间:"));
        JSpinner onceYearSpinner = new JSpinner(new SpinnerNumberModel(2026, 2020, 2100, 1));
        JSpinner onceMonthSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 12, 1));
        JSpinner onceDaySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 31, 1));
        JSpinner onceHourSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 23, 1));
        JSpinner onceMinuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        oncePanel.add(onceYearSpinner);
        oncePanel.add(new JLabel("年"));
        oncePanel.add(onceMonthSpinner);
        oncePanel.add(new JLabel("月"));
        oncePanel.add(onceDaySpinner);
        oncePanel.add(new JLabel("日"));
        oncePanel.add(onceHourSpinner);
        oncePanel.add(new JLabel("时"));
        oncePanel.add(onceMinuteSpinner);
        oncePanel.add(new JLabel("分"));

        // 浏览按钮事件
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                backupDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        // 先加载已保存的配置（这时还没有事件监听，不会触发面板切换）
        loadSystemConfig(threadSpinner, superLevelCombo, backupDirField, scheduleTypeCombo,
                dailyPanel, weeklyPanel, monthlyPanel, oncePanel,
                hourSpinner, minuteSpinner, weekHourSpinner, weekMinuteSpinner,
                monthHourSpinner, monthMinuteSpinner,
                onceYearSpinner, onceMonthSpinner, onceDaySpinner, onceHourSpinner, onceMinuteSpinner,
                weekCombo, dayCombo);

        // 加载完成后再添加事件监听
        scheduleTypeCombo.addActionListener(e -> {
            scheduleDetailPanel.removeAll();
            String selected = (String) scheduleTypeCombo.getSelectedItem();
            switch (selected) {
                case "每天":
                    scheduleDetailPanel.add(dailyPanel);
                    break;
                case "每周":
                    scheduleDetailPanel.add(weeklyPanel);
                    break;
                case "每月":
                    scheduleDetailPanel.add(monthlyPanel);
                    break;
                case "一次性":
                    scheduleDetailPanel.add(oncePanel);
                    break;
                default:
                    scheduleDetailPanel.add(new JLabel("未启用自动备份"));
            }
            scheduleDetailPanel.revalidate();
            scheduleDetailPanel.repaint();
        });

        // 加载完成后手动触发一次事件，显示对应的面板
        String savedType = (String) scheduleTypeCombo.getSelectedItem();
        if (savedType != null && !"从不".equals(savedType)) {
            scheduleDetailPanel.removeAll();
            switch (savedType) {
                case "每天":
                    scheduleDetailPanel.add(dailyPanel);
                    break;
                case "每周":
                    scheduleDetailPanel.add(weeklyPanel);
                    break;
                case "每月":
                    scheduleDetailPanel.add(monthlyPanel);
                    break;
                case "一次性":
                    scheduleDetailPanel.add(oncePanel);
                    break;
            }
            scheduleDetailPanel.revalidate();
            scheduleDetailPanel.repaint();
        }

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");
        JButton testBackupBtn = new JButton("立即执行一次备份");
        buttonPanel.add(saveBtn);
        buttonPanel.add(testBackupBtn);
        buttonPanel.add(cancelBtn);

        gbc.gridy = 6;
        mainPanel.add(buttonPanel, gbc);

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        dialog.add(scrollPane);

        // 保存按钮事件
        saveBtn.addActionListener(e -> {
            int threadCount = (Integer) threadSpinner.getValue();
            int superLevel = Integer.valueOf((String) superLevelCombo.getSelectedItem());
            String backupDir = backupDirField.getText().trim();
            String scheduleType = (String) scheduleTypeCombo.getSelectedItem();

            // 保存配置
            saveSystemConfig(threadCount, superLevel, backupDir, scheduleType,
                    scheduleTypeCombo, dailyPanel, weeklyPanel, monthlyPanel, oncePanel,
                    hourSpinner, minuteSpinner, weekHourSpinner, weekMinuteSpinner,
                    monthHourSpinner, monthMinuteSpinner,
                    onceYearSpinner, onceMonthSpinner, onceDaySpinner, onceHourSpinner, onceMinuteSpinner,
                    weekCombo, dayCombo);

            addLog("系统配置已保存 - 线程数:" + threadCount + ", Super级别:" + superLevel + ", 备份目录:" + backupDir + ", 备份策略:" + scheduleType);
            JOptionPane.showMessageDialog(dialog, "配置保存成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
            dialog.dispose();
        });

        // 立即备份按钮
        testBackupBtn.addActionListener(e -> {
            dialog.dispose();
            int allRows = deviceTable.getRowCount();
            if (allRows > 0) {
                deviceTable.selectAll();
                backupSelectedDevices();
            } else {
                JOptionPane.showMessageDialog(this, "没有可备份的设备", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setVisible(true);
    }

    /**
     * 获取当前线程池大小
     */
    private int getCurrentThreadPoolSize() {
        return currentThreadPoolSize;
    }

    /**
     * 更新线程池大小
     */
    private void updateThreadPoolSize(int newSize) {
        if (newSize == currentThreadPoolSize) {
            return;
        }

        currentThreadPoolSize = newSize;

        // 重新初始化线程池
        ExecutorService oldExecutor = executorService;
        executorService = Executors.newFixedThreadPool(currentThreadPoolSize);

        // 优雅关闭旧线程池
        if (oldExecutor != null && !oldExecutor.isShutdown()) {
            oldExecutor.shutdown();
        }

        addLog("线程池大小已更新为: " + currentThreadPoolSize);
    }

    /**
     * 更新备份目录
     */
    private void updateBackupDirectory(String backupDir) {
        if (backupDir != null && !backupDir.trim().isEmpty()) {
            // 设置全局备份目录
            setGlobalBackupDir(backupDir);
            addLog("备份目录已设置为: " + backupDir);
        }
    }

    /**
     * 加载系统配置到UI
     */
    private void loadSystemConfig(JSpinner threadSpinner, JComboBox<String> superLevelCombo,
                                  JTextField backupDirField, JComboBox<String> scheduleTypeCombo,
                                  JPanel dailyPanel, JPanel weeklyPanel, JPanel monthlyPanel, JPanel oncePanel,
                                  JSpinner hourSpinner, JSpinner minuteSpinner,
                                  JSpinner weekHourSpinner, JSpinner weekMinuteSpinner,
                                  JSpinner monthHourSpinner, JSpinner monthMinuteSpinner,
                                  JSpinner onceYearSpinner, JSpinner onceMonthSpinner, JSpinner onceDaySpinner,
                                  JSpinner onceHourSpinner, JSpinner onceMinuteSpinner,
                                  JComboBox<String> weekCombo, JComboBox<String> dayCombo) {
        try {
            File configFile = new File(SYSTEM_CONFIG_PATH);
            if (configFile.exists()) {
                String jsonStr = readFileToJson(SYSTEM_CONFIG_PATH);
                JsonObject config = gson.fromJson(jsonStr, JsonObject.class);

                // 加载线程数配置
                if (config.has("threadCount")) {
                    int savedThreadCount = config.get("threadCount").getAsInt();
                    threadSpinner.setValue(savedThreadCount);
                    if (currentThreadPoolSize != savedThreadCount) {
                        currentThreadPoolSize = savedThreadCount;
                        initThreadPool();
                    }
                }

                if (config.has("superLevel")) {
                    superLevelCombo.setSelectedItem(config.get("superLevel").getAsString());
                }
                if (config.has("backupDir")) {
                    backupDirField.setText(config.get("backupDir").getAsString());
                    updateBackupDirectory(config.get("backupDir").getAsString());
                }

                // 加载定时策略
                if (config.has("scheduleType")) {
                    String scheduleType = config.get("scheduleType").getAsString();
                    scheduleTypeCombo.setSelectedItem(scheduleType);

                    // 根据策略类型加载对应的时间配置
                    if ("每天".equals(scheduleType) && config.has("hour") && config.has("minute")) {
                        hourSpinner.setValue(config.get("hour").getAsInt());
                        minuteSpinner.setValue(config.get("minute").getAsInt());
                    } else if ("每周".equals(scheduleType)) {
                        if (config.has("weekDay")) {
                            weekCombo.setSelectedItem(config.get("weekDay").getAsString());
                        }
                        if (config.has("hour")) {
                            weekHourSpinner.setValue(config.get("hour").getAsInt());
                        }
                        if (config.has("minute")) {
                            weekMinuteSpinner.setValue(config.get("minute").getAsInt());
                        }
                    } else if ("每月".equals(scheduleType)) {
                        if (config.has("monthDay")) {
                            dayCombo.setSelectedItem(config.get("monthDay").getAsString());
                        }
                        if (config.has("hour")) {
                            monthHourSpinner.setValue(config.get("hour").getAsInt());
                        }
                        if (config.has("minute")) {
                            monthMinuteSpinner.setValue(config.get("minute").getAsInt());
                        }
                    } else if ("一次性".equals(scheduleType)) {
                        if (config.has("onceYear")) {
                            onceYearSpinner.setValue(config.get("onceYear").getAsInt());
                        }
                        if (config.has("onceMonth")) {
                            onceMonthSpinner.setValue(config.get("onceMonth").getAsInt());
                        }
                        if (config.has("onceDay")) {
                            onceDaySpinner.setValue(config.get("onceDay").getAsInt());
                        }
                        if (config.has("hour")) {
                            onceHourSpinner.setValue(config.get("hour").getAsInt());
                        }
                        if (config.has("minute")) {
                            onceMinuteSpinner.setValue(config.get("minute").getAsInt());
                        }
                    }
                }
            }
        } catch (Exception e) {
            addLog("加载系统配置失败: " + e.getMessage());
        }
    }

    /**
     * 保存系统配置
     */
    private void saveSystemConfig(int threadCount, int superLevel, String backupDir, String scheduleType,
                                  JComboBox<String> scheduleTypeCombo, JPanel dailyPanel, JPanel weeklyPanel,
                                  JPanel monthlyPanel, JPanel oncePanel,
                                  JSpinner hourSpinner, JSpinner minuteSpinner,
                                  JSpinner weekHourSpinner, JSpinner weekMinuteSpinner,
                                  JSpinner monthHourSpinner, JSpinner monthMinuteSpinner,
                                  JSpinner onceYearSpinner, JSpinner onceMonthSpinner, JSpinner onceDaySpinner,
                                  JSpinner onceHourSpinner, JSpinner onceMinuteSpinner,
                                  JComboBox<String> weekCombo, JComboBox<String> dayCombo) {
        try {
            JsonObject config = new JsonObject();
            config.addProperty("threadCount", threadCount);
            config.addProperty("superLevel", superLevel);
            config.addProperty("backupDir", backupDir);
            config.addProperty("scheduleType", scheduleType);

            // 保存详细的时间配置
            if ("每天".equals(scheduleType)) {
                config.addProperty("hour", (Integer) hourSpinner.getValue());
                config.addProperty("minute", (Integer) minuteSpinner.getValue());
            } else if ("每周".equals(scheduleType)) {
                config.addProperty("weekDay", (String) weekCombo.getSelectedItem());
                config.addProperty("hour", (Integer) weekHourSpinner.getValue());
                config.addProperty("minute", (Integer) weekMinuteSpinner.getValue());
            } else if ("每月".equals(scheduleType)) {
                config.addProperty("monthDay", (String) dayCombo.getSelectedItem());
                config.addProperty("hour", (Integer) monthHourSpinner.getValue());
                config.addProperty("minute", (Integer) monthMinuteSpinner.getValue());
            } else if ("一次性".equals(scheduleType)) {
                config.addProperty("onceYear", (Integer) onceYearSpinner.getValue());
                config.addProperty("onceMonth", (Integer) onceMonthSpinner.getValue());
                config.addProperty("onceDay", (Integer) onceDaySpinner.getValue());
                config.addProperty("hour", (Integer) onceHourSpinner.getValue());
                config.addProperty("minute", (Integer) onceMinuteSpinner.getValue());
            }

            try (FileWriter writer = new FileWriter(SYSTEM_CONFIG_PATH)) {
                writer.write(gson.toJson(config));
            }

            // 更新线程池
            updateThreadPoolSize(threadCount);

            // 更新备份目录
            updateBackupDirectory(backupDir);

            // 重新启动定时任务
            startScheduleTask(config, scheduleType);

        } catch (IOException e) {
            addLog("保存系统配置失败: " + e.getMessage());
        }
    }

    /**
     * 编辑选中的设备
     */
    private void editSelectedDevice() {
        int selectedRow = deviceTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "请先选择一个设备", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (deviceTable.getSelectedRowCount() > 1) {
            JOptionPane.showMessageDialog(this, "请只选择一个设备进行编辑", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DeviceConfig config = deviceConfigs.get(selectedRow);
        showDeviceDialog(config);
    }

    /**
     * 删除选中的设备
     */
    private void deleteSelectedDevices() {
        int[] selectedRows = deviceTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "请至少选择一个设备", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "确定要删除选中的 " + selectedRows.length + " 个设备吗？",
                "确认删除",
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            List<DeviceConfig> toRemove = new ArrayList<>();
            for (int row : selectedRows) {
                toRemove.add(deviceConfigs.get(row));
            }
            deviceConfigs.removeAll(toRemove);
            saveDeviceConfigs();
            refreshDeviceTable();
            addLog("已删除 " + selectedRows.length + " 个设备");
        }
    }

    /**
     * 保存设备配置到文件
     */
    private void saveDeviceConfigs() {
        try {
            JsonObject root = new JsonObject();
            JsonArray devicesArray = new JsonArray();

            for (DeviceConfig config : deviceConfigs) {
                JsonObject deviceObj = new JsonObject();
                deviceObj.addProperty("host", config.host);
                deviceObj.addProperty("username", config.username);
                deviceObj.addProperty("password", config.password);
                deviceObj.addProperty("deviceType", config.deviceType);
                deviceObj.addProperty("enPassword", config.enPassword);
                deviceObj.addProperty("connectionType", config.connectionType);
                deviceObj.addProperty("sshPort", config.sshPort);
                deviceObj.addProperty("telnetPort", config.telnetPort);
                deviceObj.addProperty("superLevel", config.superLevel);
                devicesArray.add(deviceObj);
            }

            root.add("devices", devicesArray);

            try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH)) {
                writer.write(gson.toJson(root));
            }
            addLog("配置已保存到文件");
        } catch (IOException e) {
            addLog("保存配置失败: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "保存配置失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 添加日志
     */
    private void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /**
     * 读取文件内容
     */
    private String readFileToJson(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * 设备配置类
     */
    private static class DeviceConfig {
        String host;
        String username;
        String password;
        String deviceType;
        String enPassword;
        int superLevel;
        String connectionType = "TELNET";
        int sshPort = 22;
        int telnetPort = 23;
    }

    // 在 DeviceManagerUI 类中添加关闭窗口时的处理
    private void setupCloseHandler() {
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {

                // 关闭定时任务调度器
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.shutdown();
                }
                // 关闭备份线程池
                if (executorService != null && !executorService.isShutdown()) {
                    executorService.shutdown();
                }

            }
        });
    }

    /**
     * 获取全局备份目录
     */
    public static String getGlobalBackupDir() {
        return globalBackupDir;
    }

    /**
     * 设置全局备份目录
     */
    public static void setGlobalBackupDir(String dir) {
        globalBackupDir = dir;
    }

    public static int getSuperLevel() {
        return superLevel;
    }

    public static void setSuperLevel(int superLevel) {
        DeviceManagerUI.superLevel = superLevel;
    }

    /**
     * 初始化定时任务调度器
     */
    private void initScheduler() {
        scheduler = Executors.newScheduledThreadPool(1);
    }

    /**
     * 加载并启动定时备份任务
     */
    private void loadAndStartScheduleTask() {
        try {
            File configFile = new File(SYSTEM_CONFIG_PATH);
            if (configFile.exists()) {
                String jsonStr = readFileToJson(SYSTEM_CONFIG_PATH);
                JsonObject config = gson.fromJson(jsonStr, JsonObject.class);

                if (config.has("scheduleType")) {
                    String scheduleType = config.get("scheduleType").getAsString();
                    startScheduleTask(config, scheduleType);
                }
            }
        } catch (Exception e) {
            addLog("加载定时任务失败: " + e.getMessage());
        }
    }

    /**
     * 根据配置启动定时任务
     */
    private void startScheduleTask(JsonObject config, String scheduleType) {
        // 取消现有任务
        cancelScheduleTask();

        if ("从不".equals(scheduleType)) {
            addLog("自动备份已禁用");
            return;
        }

        try {
            Runnable backupTask = () -> {
                addLog("定时备份任务开始执行...");
                // 备份所有设备
                SwingUtilities.invokeLater(() -> {
                    int allRows = deviceTable.getRowCount();
                    if (allRows > 0) {
                        deviceTable.selectAll();
                        backupSelectedDevices();
                    } else {
                        addLog("没有可备份的设备");
                    }
                });
            };

            long initialDelay = 0;
            long period = 0;
            TimeUnit unit = TimeUnit.SECONDS;

            switch (scheduleType) {
                case "每天":
                    int hour = config.get("hour").getAsInt();
                    int minute = config.get("minute").getAsInt();
                    initialDelay = calculateDelayToNextTime(hour, minute, 0);
                    period = TimeUnit.DAYS.toSeconds(1);
                    addLog(String.format("已设置每天 %02d:%02d 自动备份", hour, minute));
                    break;

                case "每周":
                    String weekDay = config.get("weekDay").getAsString();
                    int weekHour = config.get("hour").getAsInt();
                    int weekMinute = config.get("minute").getAsInt();
                    int targetDayOfWeek = getDayOfWeekValue(weekDay);
                    initialDelay = calculateDelayToNextWeek(targetDayOfWeek, weekHour, weekMinute, 0);
                    period = TimeUnit.DAYS.toSeconds(7);
                    addLog(String.format("已设置每周%s %02d:%02d 自动备份", weekDay, weekHour, weekMinute));
                    break;

                case "每月":
                    String monthDay = config.get("monthDay").getAsString();
                    int monthHour = config.get("hour").getAsInt();
                    int monthMinute = config.get("minute").getAsInt();
                    int targetDay = Integer.parseInt(monthDay);
                    initialDelay = calculateDelayToNextMonth(targetDay, monthHour, monthMinute, 0);
                    period = TimeUnit.DAYS.toSeconds(30); // 近似30天
                    addLog(String.format("已设置每月%s号 %02d:%02d 自动备份", monthDay, monthHour, monthMinute));
                    break;

                case "一次性":
                    int onceYear = config.get("onceYear").getAsInt();
                    int onceMonth = config.get("onceMonth").getAsInt();
                    int onceDay = config.get("onceDay").getAsInt();
                    int onceHour = config.get("hour").getAsInt();
                    int onceMinute = config.get("minute").getAsInt();
                    initialDelay = calculateDelayToSpecificTime(onceYear, onceMonth, onceDay, onceHour, onceMinute, 0);
                    period = 0; // 一次性任务，不重复
                    addLog(String.format("已设置一次性备份于 %d年%d月%d日 %02d:%02d", onceYear, onceMonth, onceDay, onceHour, onceMinute));
                    break;
            }

            if (initialDelay < 0) {
                addLog("警告：设定的备份时间已过期，请重新设置");
                return;
            }

            if (period > 0) {
                // 周期性任务
                scheduledBackupTask = scheduler.scheduleAtFixedRate(backupTask, initialDelay, period, TimeUnit.SECONDS);
            } else if (period == 0 && initialDelay > 0) {
                // 一次性任务
                scheduledBackupTask = scheduler.schedule(backupTask, initialDelay, TimeUnit.SECONDS);
            }

        } catch (Exception e) {
            addLog("启动定时任务失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 取消定时任务
     */
    private void cancelScheduleTask() {
        if (scheduledBackupTask != null && !scheduledBackupTask.isDone()) {
            scheduledBackupTask.cancel(false);
            addLog("已取消原有的定时备份任务");
        }
    }

    /**
     * 计算到下一个指定时间的延迟（秒）
     */
    private long calculateDelayToNextTime(int targetHour, int targetMinute, int targetSecond) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(),
                targetHour, targetMinute, targetSecond);

        if (target.isBefore(now)) {
            target = target.plusDays(1);
        }

        return Duration.between(now, target).getSeconds();
    }

    /**
     * 计算到下一个指定星期几的延迟
     */
    private long calculateDelayToNextWeek(int targetDayOfWeek, int targetHour, int targetMinute, int targetSecond) {
        LocalDateTime now = LocalDateTime.now();
        int currentDayOfWeek = now.getDayOfWeek().getValue();

        int daysToAdd = targetDayOfWeek - currentDayOfWeek;
        if (daysToAdd <= 0) {
            daysToAdd += 7;
        }

        LocalDateTime target = now.plusDays(daysToAdd)
                .withHour(targetHour)
                .withMinute(targetMinute)
                .withSecond(targetSecond)
                .withNano(0);

        return Duration.between(now, target).getSeconds();
    }

    /**
     * 计算到下一个指定日期的延迟（每月）
     */
    private long calculateDelayToNextMonth(int targetDay, int targetHour, int targetMinute, int targetSecond) {
        LocalDateTime now = LocalDateTime.now();

        // 获取目标日期，处理月份天数不足的情况
        int year = now.getYear();
        int month = now.getMonthValue();
        int maxDayOfMonth = getMaxDayOfMonth(year, month);

        // 如果目标日期大于当月最大天数，使用当月最后一天
        int actualTargetDay = Math.min(targetDay, maxDayOfMonth);

        LocalDateTime target = LocalDateTime.of(year, month, actualTargetDay,
                targetHour, targetMinute, targetSecond);

        if (target.isBefore(now)) {
            // 推迟到下个月
            year = now.getYear();
            month = now.getMonthValue() + 1;
            if (month > 12) {
                month = 1;
                year++;
            }
            maxDayOfMonth = getMaxDayOfMonth(year, month);
            actualTargetDay = Math.min(targetDay, maxDayOfMonth);
            target = LocalDateTime.of(year, month, actualTargetDay,
                    targetHour, targetMinute, targetSecond);
        }

        return Duration.between(now, target).getSeconds();
    }

    /**
     * 获取指定年月的最大天数
     */
    private int getMaxDayOfMonth(int year, int month) {
        switch (month) {
            case 2:
                // 判断闰年
                if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) {
                    return 29;
                } else {
                    return 28;
                }
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    /**
     * 计算到指定具体时间的延迟
     */
    private long calculateDelayToSpecificTime(int year, int month, int day, int hour, int minute, int second) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = LocalDateTime.of(year, month, day, hour, minute, second);

        if (target.isBefore(now)) {
            return -1; // 时间已过期
        }

        return Duration.between(now, target).getSeconds();
    }

    /**
     * 获取星期几对应的数字（周一=1，周日=7）
     */
    private int getDayOfWeekValue(String weekDay) {
        switch (weekDay) {
            case "周一": return 1;
            case "周二": return 2;
            case "周三": return 3;
            case "周四": return 4;
            case "周五": return 5;
            case "周六": return 6;
            case "周日": return 7;
            default: return 1;
        }
    }


}