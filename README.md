# SwitchAutoBackup<br>
基于JDK11、flatlaf、sshj、commons-net、制作的交换机自动备份软件。作用：将华为/思科交换机的配置文件保存到本地。<br>
事情起因：由于工作需要，园区内有大量的交换机（华为、思科、华三）需要对他们做定期的备份，之前一直都在用CRT记录会话<br>
的方式将配置文件拉到本地电脑中，但是始终无法全自动化进行。园区里面的网管系统也不支持这个功能，所以做了一个程序实现。<br>
<br>
软件的UI界面：<br>
<img width="800" height="600" alt="image" src="https://github.com/user-attachments/assets/318b9080-a40a-42cc-b86b-9e48d05aa793" /><br>
<br>
使用方法：<br>
1.添加设备信息到程序中<br>
```-主机IP：地址要求网络可达即可<br>```
    -用户名：为交换机远程的账户（如启用的密码登录，则留空即可）<br>
    -密码：交换机远程账户的密码<br>
    -设备类型：这里我将设备分成2类华为系和思科系列，不再做具体的细分，根据命令格式选择即可<br>
      · 使用display 的设备为华为系列 选择 HUAWEI<br>
      · 使用show 的设备为思科系列 选择 CISCO<br>
  -Enable/Super密码：设备的二级密码，这个根据上面选择的设备类型不同会有不同的逻辑，具体逻辑如下：<br>
    · 如果选择的 HUAWEI ，则该密码为进入设备后输入的 Super 密码<br>
    · 如果选择的 CISCO ，则该密码为特权模式的密码<br>
  -SSH端口：SSH远程所使用的端口号<br>
  -TELNET端口：TELNET远程所使用的端口号<br>
  -Super级别：只有 HUAWEI 系交换机需要 值 0 - 15 ，根据设备配置对应填写即可，不知道留空即可<br>
<img width="800" height="600" alt="image" src="https://github.com/user-attachments/assets/ef284e7b-d973-46fa-b8c9-e7c1db3e89f5" /><br>
<br>
2.添加完成设备后选中该设备点击左上角的备份配置即可启动备份流程<br>
  -如需要一次性全部备份，可以点击其中一台设备然后按快捷键 Alt + A 全选设备，然后点击备份配置即可<br>
  -备份完成后的保存的路径为：当前文件目录的 backup/日期/xxxxx.txt<br>
<img width="800" height="600" alt="image" src="https://github.com/user-attachments/assets/22be9988-bc3e-4271-99d2-1fb9f094d950" /><br>
备份文件所在的文件夹<br>
<img width="627" height="255" alt="image" src="https://github.com/user-attachments/assets/282580f9-fb66-4d2a-955c-5816449ca258" /><br>
<img width="640" height="208" alt="image" src="https://github.com/user-attachments/assets/0aaf496a-a7b8-47e2-83ba-ef794bd9b848" /><br>
<img width="616" height="181" alt="image" src="https://github.com/user-attachments/assets/fb66c78c-9f6e-4d10-b897-6e6693db51d5" /><br>
<br>
3.备份完成后的文件打开如下所示<br>
<img width="887" height="693" alt="image" src="https://github.com/user-attachments/assets/7b7ca994-656d-4e20-ab21-ab6cecbf4032" /><br>
<br>
4.程序基础信息设备<br>
  -线程池大小：同时并发处理的设备数量，值越大，并发处理的设备就越多（没有做限制，保持默认即可）<br>
  -super级别：默认全局super级别，当华为系的设备填写了enable/Super密码，同时没有在添加设备处指定super级别，则会调用这里的super级别<br>
  -备份文件保存目录：指定备份的配置文件存储在哪个目录下<br>
  -定时备份策略：设置定时执行备份任务，自行探索即可<br>
  -最后配置完成后记得点击保存<br>
<img width="800" height="600" alt="image" src="https://github.com/user-attachments/assets/5592ab1a-8b00-4eae-a113-7bd6c27e1e71" /><br>









