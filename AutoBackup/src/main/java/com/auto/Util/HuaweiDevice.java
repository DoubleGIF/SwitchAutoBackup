package com.auto.Util;


import java.io.*;
import java.nio.charset.StandardCharsets;

public class HuaweiDevice extends Device{

    public String NO_PAGE_CMD = "screen-length 0 temporary";
    public String DISPLAY_CURRENT_CONFIGURATION = "display current-configuration";
    public String SUPER_PASSWORD_CMD = "super ";


    @Override
    public String getNO_PAGE_CMD() {
        return NO_PAGE_CMD;
    }

    @Override
    public String getDISPLAY_CURRENT_CONFIGURATION() {
        return DISPLAY_CURRENT_CONFIGURATION;
    }

    @Override
    public String getSUPER_PASSWORD_CMD() {
        return SUPER_PASSWORD_CMD;
    }

    /**
     * 登录设备
     * @return
     * @throws IOException
     */
    @Override
    public boolean loginDevice() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        char[] buffer = new char[BUFFER_SIZE];
        long startTime = System.currentTimeMillis();
        StringBuilder stringBuilder = new StringBuilder();
        boolean flag = false;
        while (System.currentTimeMillis() - startTime < TIMEOUT_MS) {
            if (reader.ready()) {
                int len = reader.read(buffer);
                if (len > 0) {
                    stringBuilder.append(buffer, 0, len);
                    String content = stringBuilder.toString();
                    if (content.contains(">")) {
                        //登录成功
                        flag = true;
                        //判断是否需要执行super命令
                        if(!"".equals(superPassword)){
                            exec(SUPER_PASSWORD_CMD + " " +superLevel,superPassword);
                        }

                        break;
                    }
                    //判断是否需要输入账户/密码
                    sendCMD(content, "Password:", password, outputStream);
                    sendCMD(content, "Username:", account, outputStream);
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
        return flag;
    }

}
