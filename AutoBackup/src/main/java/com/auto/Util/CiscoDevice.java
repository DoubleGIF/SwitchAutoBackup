package com.auto.Util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CiscoDevice extends Device{

    public String NO_PAGE_CMD = "terminal length 0";

    public String DISPLAY_CURRENT_CONFIGURATION = "show running-config";


    @Override
    public String getNO_PAGE_CMD() {
        return NO_PAGE_CMD;
    }

    @Override
    public String getDISPLAY_CURRENT_CONFIGURATION() {
        return DISPLAY_CURRENT_CONFIGURATION;
    }

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
                    if (content.contains("#")){
                        flag = true;
                        break;
                    }
                    if (content.contains(">")) {
                        //思科还需要输入enable密码
                        if (superPassword != null && superPassword.length() > 0){
                            sendCMD(outputStream,"enable");
                            sendCMD(outputStream,superPassword);
                            continue;
                        }
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
