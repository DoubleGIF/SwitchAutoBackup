package com.auto.Util;


import com.auto.Util.enums.ConnectType;
import com.auto.Util.enums.DeviceType;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 抽象设备类
 */
public class Device {

    public String NO_PAGE_CMD = "screen-length 0 temporary";
    public String DISPLAY_CURRENT_CONFIGURATION = "display current-configuration";
    public String SUPER_PASSWORD_CMD = "super ";

    public static final int TIMEOUT_MS = 30000; // 30秒超时
    public static final int BUFFER_SIZE = 8192;

    /**
     * 定义设备的设备类型
     * HUAWEI, CISCO
     */
    public DeviceType DEVICE_TYPER = null;

    /**
     * 定义设备的连接类型
     * TELNET,SSH
     */
    public ConnectType CONNECT_TYPE = null;

    /**
     * 设备的IPv4地址
     */
    public String host = null;

    /**
     * 设备的远程连接端口
     */
    public int ssh_port = 22;
    public int telnet_port = 23;

    /**
     * 设备的账户名
     */
    public String account = null;

    /**
     * 设备的密码
     */
    public String password = null;

    /**
     * 设备的超级密码
     * 如果是huawei设备 代表super密码
     * 如果是cisco设备 代表enable密码
     */
    public String superPassword = null;

    /**
     * Super密码的级别
     */
    public int superLevel = 0;


    //ssh连接客户端
    SSHClient sshClient = null;
    //telnet连接客户端
    Socket telnetClient = null;

    //设备的输入/输出流
    InputStream inputStream = null;
    OutputStream outputStream = null;

    /**
     * 跟设备创建连接
     */
    public boolean createConnection(){
        //标识设备是否连接成功
        boolean flag = false;
        //1.判断是SSH连接还是TELNET
        if (CONNECT_TYPE == ConnectType.SSH){
            //2.创建SSH的连接
            try {
                sshClient = new SSHClient();
                // 忽略主机密钥验证（测试环境可用，生产环境建议替换为安全的验证方式）
                sshClient.addHostKeyVerifier(new PromiscuousVerifier());
                // 连接设备（IP+端口）
                sshClient.connect(host, ssh_port);
                // 密码认证
                sshClient.authPassword(account, password);
                flag = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else if (CONNECT_TYPE == ConnectType.TELNET){
            //3.创建telnet的连接
            flag = createTelnetConnection();
        }

        return flag;
    }

    /**
     * 子类自行实现该方法
     */
    public boolean createTelnetConnection(){
        boolean isLoginSeccess = false;
        try {
            telnetClient = new Socket(host, telnet_port);
            //拿到输入/输出流
            outputStream = telnetClient.getOutputStream();
            inputStream = telnetClient.getInputStream();
            //设置连接超时时间
            telnetClient.setSoTimeout(TIMEOUT_MS);
            //记录是否登录成功
            isLoginSeccess = loginDevice();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return isLoginSeccess;
    }


    public boolean loginDevice() throws IOException {

        return false;
    }

    /**
     * 执行实参 并得到设备输出结果
     * @param command 命令
     * @return 执行结果
     */
    public void exec(String... command){
        if (CONNECT_TYPE == ConnectType.SSH){
            sshExex(command);
        } else if (CONNECT_TYPE == ConnectType.TELNET){
            telnetExec(command);
        }
    }

    /**
     * 子类重写
     */
    public void telnetExec(String... command){
        for (int i = 0; i < command.length; i++) {
            try {
                sendCMD(outputStream,command[i]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 判断设备返回的内容中是否包含特定的字符串
     * @param content 设备返回的内容
     * @param str 特定的字符串
     * @param count 传入的字符
     * @param outputStream 传入流
     * @return 是否检测成功
     * @throws IOException
     */
    public boolean sendCMD(String content,String str, String count,OutputStream outputStream) throws IOException {
        if (content.contains(str)){
            outputStream.write(count.getBytes(StandardCharsets.UTF_8));
            outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            // 等待指令执行完成（根据设备响应速度调整）
            try {
                TimeUnit.MILLISECONDS.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    public void sendCMD(OutputStream outputStream,String count) throws IOException {
        if (outputStream == null){
            throw new NullPointerException();
        }
        outputStream.write(count.getBytes(StandardCharsets.UTF_8));
        outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        // 等待指令执行完成（根据设备响应速度调整）
        try {
            TimeUnit.MILLISECONDS.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * SSH调用命令
     */
    public BufferedReader sshExex(String... command){
        BufferedReader reader = null;
        Session session = null;
        Session.Shell shell = null;
        try {
            // 1. 开启会话和交互式Shell
            session = sshClient.startSession();
            shell = session.startShell();
            // 2. 初始化连接流
            outputStream = shell.getOutputStream();
            inputStream = shell.getInputStream();
            reader = new BufferedReader(
                    new InputStreamReader(inputStream, "UTF-8"));
            // 3. 注入命令
            for (int i = 0; i < command.length; i++) {
                sendCMD(outputStream,command[i]);
            }

        } catch (ConnectionException e) {
            e.printStackTrace();
        } catch (TransportException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                shell.close();
            } catch (TransportException e) {
                e.printStackTrace();
            } catch (ConnectionException e) {
                e.printStackTrace();
            }
            try {
                session.close();
            } catch (TransportException e) {
                e.printStackTrace();
            } catch (ConnectionException e) {
                e.printStackTrace();
            }
        }
        return reader;
    }


    /**
     * 调用该方法拉取设备的配置到本地
     */
    public void backupConfig() throws ConnectionException {
        //创建连接对象
        boolean connectionStatus = createConnection();
        //如果设备没有连接成功
        if (!connectionStatus){
            //直接结束该方法
            throw new ConnectionException("设备连接失败 : " + host);
        }
        //传入命令
        exec(getNO_PAGE_CMD(),getDISPLAY_CURRENT_CONFIGURATION());
        try {
            //将 inputStream 流中的内容存储到 StringBuilder 中
            StringBuilder stringBuilder = printlnStringBuilder();
            saveToFile(stringBuilder.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //释放资源
        close();
    }

    /**
     * 获取不分页的命令
     * 默认返回华为的，子类需要重写
     * @return 不分页的命令
     */
    public String getNO_PAGE_CMD(){
        return NO_PAGE_CMD;
    }

    /**
     * 获取设备所有的配置的命令
     * 默认返回华为的，子类需要重写
     * @return 不分页的命令
     */
    public String getDISPLAY_CURRENT_CONFIGURATION(){
        return DISPLAY_CURRENT_CONFIGURATION;
    }

    /**
     * 获取设备执行super的命令
     * 默认返回华为的，子类需要重写
     * @return super的命令
     */
    public String getSUPER_PASSWORD_CMD() {
        return SUPER_PASSWORD_CMD;
    }


    /**
     * 释放资源
     */
    public void close(){
        if (inputStream != null){
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (outputStream != null){
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (telnetClient != null){
            try {
                telnetClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (sshClient != null){
            try {
                sshClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    /**
     * 输出 inputStream 流中的内容
     * @throws IOException
     */
    public StringBuilder printlnStringBuilder() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder stringBuilder = new StringBuilder();
        char[] buffer = new char[BUFFER_SIZE];
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
            if (reader.ready()) {
                int len = reader.read(buffer);
                if (len > 0) {
                    stringBuilder.append(buffer, 0, len);
                    String content = stringBuilder.toString();
                    if (content.contains("return") && content.endsWith(">") &&DEVICE_TYPER == DeviceType.HUAWEI)break;
                    if ( content.contains("end")  && content.endsWith("#") && DEVICE_TYPER == DeviceType.CISCO)break;
                }
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return stringBuilder;
    }


    /**
     * 保存配置到文件
     */
    private void saveToFile(String count) throws IOException {
        String deviceName = getLastLine(count);
        String fileName = deviceName.replaceAll("[<>#]", "");
        if ("".equals(fileName) || fileName.length() <= 0){
            fileName = "null";
        }

        // 1. 生成当日时间戳（yyyyMMdd），复用为目录名和文件名的日期后缀
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 2. 获取备份根目录（优先使用UI设置的系统配置目录）
        String backupRoot = getBackupRootDirectory();

        // 3. 构建多级目标目录路径：备份根目录/当日日期
        Path backupDir = Paths.get(backupRoot, timestamp);
        String outputPath;
        try {
            // 4. 自动创建多级目录（目录已存在则无操作，不会抛异常，核心方法）
            Files.createDirectories(backupDir);
            // 5. 拼接文件名：fileName_host_timestamp.txt
            String fileFullName = String.format("%s_%s.txt", fileName, host);
            // 6. 拼接完整文件路径：目录路径 + 文件名（resolve自动处理系统分隔符）
            if (fileFullName.contains(":")){
                fileFullName = fileFullName.replaceAll(":","");
            }
            outputPath = backupDir.resolve(fileFullName).toString();
        } catch (IOException e) {
            // 捕获目录创建失败的异常（如权限不足），按需处理
            throw new RuntimeException("创建备份目录失败，请检查目录权限", e);
        }

        Path path = Paths.get(outputPath);

        // 添加文件头信息
        String header = String.format(
                "# Switch Configuration Backup%n" +
                        "# Host: %s%n" +
                        "# Date: %s%n" +
                        "# Device: %s%n" +
                        "# ========================================%n%n",
                host,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                deviceName
        );

        Files.writeString(path, header + count, StandardCharsets.UTF_8);
        System.out.println("配置已保存到: " + path.toAbsolutePath());
    }

    /**
     * 获取备份根目录
     * 优先使用UI配置的目录，如果没有则使用默认的backup目录
     */
    private String getBackupRootDirectory() {
        // 方法1：通过系统属性获取（从UI设置的）
        String backupDir = System.getProperty("backup.dir");
        if (backupDir != null && !backupDir.trim().isEmpty()) {
            return backupDir;
        }

        // 方法2：通过静态方法获取（如果DeviceManagerUI类可用）
        try {
            Class<?> uiClass = Class.forName("com.auto.Ui.DeviceManagerUI");
            java.lang.reflect.Method getMethod = uiClass.getMethod("getGlobalBackupDir");
            String globalDir = (String) getMethod.invoke(null);
            if (globalDir != null && !globalDir.trim().isEmpty()) {
                return globalDir;
            }
        } catch (Exception e) {
            // UI类可能不存在或方法调用失败，使用默认目录
        }

        // 方法3：使用项目根目录下的backup文件夹作为默认
        String projectRoot = System.getProperty("user.dir");
        return Paths.get(projectRoot, "backup").toString();
    }

    /**
     * 获取多行字符串的最后一行，兼容所有换行符，处理边界情况
     * @param content 原始多行字符串
     * @return 最后一行内容，无有效内容则返回空字符串""
     */
    public static String getLastLine(String content) {
        // 先处理空字符串，直接返回空
        if (content == null || content.isEmpty()) {
            return "";
        }
        // 找到最后一个换行符\n的索引
        int lastNewlineIndex = content.lastIndexOf('\n');
        // 没有换行符，说明是单行，返回原字符串（可加trim()按需去除首尾空格）
        if (lastNewlineIndex == -1) {
            return content;
        }
        // 截取换行符后到末尾的子串，即为最后一行
        String lastLine = content.substring(lastNewlineIndex + 1);
        // 可选：如果需要去除最后一行的首尾空白（比如换行/空格/制表符），加这行
        // lastLine = lastLine.trim();
        return lastLine;
    }

}
