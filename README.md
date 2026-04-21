# SwitchAutoBackup
基于 JDK11、flatlaf、sshj、commons-net 制作的交换机自动备份软件。
作用：将华为/思科交换机的配置文件保存到本地。

事情起因：
由于工作需要，园区内有大量的交换机（华为、思科、华三）需要定期备份。
之前一直使用 CRT 记录会话的方式将配置文件拉到本地电脑，但无法实现全自动化。
园区网管系统也不支持该功能，因此开发了此程序。

---

## 软件的 UI 界面
<img width="800" height="600" alt="image" src="https://github.com/user-attachments/assets/318b9080-a40a-42cc-b86b-9e48d05aa793" />

---

## 使用方法

### 1. 添加设备信息到程序中
- 主机IP：地址要求网络可达即可
- 用户名：交换机远程账户（如启用密码登录，则留空）
- 密码：交换机远程账户密码
- 设备类型：分为华为系和思科系列
  - 使用 `display` 的设备 → 选择 HUAWEI
  - 使用 `show` 的设备 → 选择 CISCO
- Enable/Super 密码：设备二级密码
  - HUAWEI：Super 密码
  - CISCO：特权模式密码
- SSH 端口：SSH 远程使用的端口号
- TELNET 端口：TELNET 远程使用的端口号
- Super 级别：仅华为交换机需要（0-15），不清楚可留空

<img width="800" height="600" alt="image" src="https://github.com/user-attachments/assets/ef284e7b-d973-46fa-b8c9-e7c1db3e89f3" />

---

### 2. 执行备份
添加完成设备后，选中设备点击左上角 **备份配置** 即可启动备份。

- 如需一次性全部备份：选中一台设备 → 按 `Alt + A` 全选 → 点击备份配置
- 备份文件默认保存路径：当前程序目录下  
  `backup/日期/设备IP.txt`

<img width="800" height="600" alt="image" src="https://github.com/user-attachments/assets/22be9988-bc3e-4271-99d2-1fb9f094d950" />

备份文件所在文件夹：

<img width="627" height="255" alt="image" src="https://github.com/user-attachments/assets/282580f9-f66c-4d2a-955c-5816449ca258" />
<img width="640" height="208" alt="image" src="https://github.com/user-attachments/assets/0aaf496a-a7b8-47e2-83ba-ef794bd9b848" />
<img width="616" height="181" alt="image" src="https://github.com/user-attachments/assets/fb66c78c-9f6e-4d10-b897-6e6693db51d5" />

---

### 3. 备份完成后的文件效果
<img width="887" height="693" alt="image" src="https://github.com/user-attachments/assets/7b7ca994-656d-4e21-ab21-ab6cecbf4032" />

---

### 4. 程序基础信息配置
- 线程池大小：同时并发处理的设备数量，值越大并发越多（保持默认即可）
- Super 级别：默认全局 Super 级别。华为设备若填写了 Super 密码但未指定级别，将使用此处配置
- 备份文件保存目录：自定义配置文件存储路径
- 定时备份策略：可设置自动定时备份
- 配置完成后务必点击 **保存**

<img width="800" height="600" alt="image" src="https://github.com/user-attachments/assets/5592ab1a-8b00-4eae-a113-7bd6c27e1e71" />
