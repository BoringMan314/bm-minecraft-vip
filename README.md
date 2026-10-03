# [B.M] Minecraft VIP

[![Paper](https://img.shields.io/badge/Paper-26.3-2D2D2D)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![GitHub](https://img.shields.io/badge/GitHub-bm--minecraft--vip-181717?logo=github)](https://github.com/BoringMan314/bm-minecraft-vip)
[![GitHub all releases](https://img.shields.io/github/downloads/BoringMan314/bm-minecraft-vip/total)](https://github.com/BoringMan314/bm-minecraft-vip/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

適用於 **Minecraft Paper 26.3** 的 VIP 插件：提供可自訂外觀、耐久、附魔與期限的裝備，支援範圍挖掘、VIP 禮包及首次加入的新手禮包。

*适用于 **Minecraft Paper 26.3** 的 VIP 插件：提供自定义限时装备、范围挖掘、VIP 礼包与新手礼包。*<br>
*Minecraft Paper 26.3 向け：カスタム装備、期限付きアイテム、範囲採掘、VIP ギフトと初参加ギフトを提供します。*<br>
*A **Minecraft Paper 26.3** plugin for custom timed equipment, area mining, VIP gift packs, and first-join starter packs.*

> **說明**：關閉功能後會停止範圍挖掘與禮包取出；已開始的期限仍會計時，到期物品會消失。

---

## 目錄

- [功能](#功能)
- [系統需求](#系統需求)
- [安裝方式](#安裝方式)
- [指令與權限](#指令與權限)
- [設定檔](#設定檔)
- [本機開發與測試](#本機開發與測試)
- [技術概要](#技術概要)
- [專案結構](#專案結構)
- [版本與多語系](#版本與多語系)
- [資料與隱私說明](#資料與隱私說明)
- [維護者：更新 GitHub 與發行版本](#維護者更新-github-與發行版本)
- [授權](#授權)
- [問題與建議](#問題與建議)

---

## 功能

- **自訂裝備**：設定物品主體、顯示外觀、附魔、耐久與使用期限。
- **範圍挖掘**：支援依挖掘面的一層範圍，或沿玩家面向往前的一層範圍；可限制單次破壞方塊數。
- **裝備期限**：支援永久、天數或指定日期；工具首次挖掘、套裝穿上後開始計時。
- **VIP 禮包**：依編號組合裝備，支援束口袋與界伏盒；內容取完或到期後禮包消失。
- **新手禮包**：首次進入伺服器時發放，可包含一般物品、附魔裝備與自訂 VIP 裝備。
- **管理指令**：開關功能、重新載入、查詢狀態，並指定玩家發放裝備或禮包。

---

## 系統需求

- **Paper 26.3** 伺服器。
- **Java 25**。

---

## 安裝方式

### 從 GitHub Releases 安裝

若 [GitHub Releases](https://github.com/BoringMan314/bm-minecraft-vip/releases) 已提供 JAR，請選擇所需語系下載；尚無發行檔時，可依下方步驟自行建置。

1. 停止伺服器，將所選 JAR 放入 `plugins/` 資料夾。
2. 啟動 **Paper 26.3** 伺服器，確認控制台顯示插件已啟用。
3. 在 `plugins/bm-minecraft-vip/` 調整設定；指令與設定項目請見下方說明。

> 同一插件只安裝一份語系 JAR；更新時請移除舊版 JAR。

### 從原始碼建置

1. 點選本頁綠色 **Code** → **Download ZIP** 解壓，或執行 `git clone https://github.com/BoringMan314/bm-minecraft-vip.git`。
2. 依 [本機開發與測試](#本機開發與測試) 準備 JDK 與 Maven，執行建置。
3. 從本機 `dist/` 選取 `bm-minecraft-vip_26.3_0.0.1-<語系>.jar`，依上方步驟安裝。

---

## 指令與權限

| 指令 | 說明 | 權限 |
|------|------|------|
| `/bm-minecraft-vip 0/1` | 關閉／開啟功能 | `.admin` |
| `/bm-minecraft-vip reload` | 重新載入設定與語系 | `.admin` |
| `/bm-minecraft-vip info` | 顯示插件版本 | `.admin` |
| `/bm-minecraft-vip status` | 顯示開關、裝備與禮包數量 | `.admin` |
| `/bm-minecraft-vip give item <編號> [玩家]` | 發放指定裝備 | `.admin` |
| `/bm-minecraft-vip give box <編號> [玩家]` | 發放 VIP 或新手禮包 | `.admin` |

`bm-minecraft-vip.use` 預設所有玩家可用；`bm-minecraft-vip.admin` 預設 OP 可用。發放時省略玩家代表自己；控制台必須指定線上玩家。

---

## 設定檔

| 檔案 | 用途 |
|------|------|
| [`config.yml`](src/main/resources/config.yml) | 功能開關、管理限制與 `max-area-blocks`（預設 `125`，範圍 `1`–`4096`）。 |
| [`config-item.yml`](src/main/resources/config-item.yml) | 裝備編號、材質、外觀、耐久、附魔、期限與範圍挖掘設定。 |
| [`config-box.yml`](src/main/resources/config-box.yml) | VIP 禮包外觀、期限與裝備編號清單。 |
| [`config-join.yml`](src/main/resources/config-join.yml) | 新手禮包開關、內容與期限。 |
| [`lang/`](src/main/resources/lang/) | 指令訊息、物品說明與控制台文字。 |

裝備編號請加引號，例如 `"0001"`。期限可使用 `permanent`、`30d` 或 `2026/10/10`；指定日期依台北時間計算。詳細欄位與範例請見各設定檔內的註解，修改後執行 `/bm-minecraft-vip reload`。

---

## 本機開發與測試

**Windows / PowerShell：**

1. 準備 **JDK 25**：放在 `.tools/<JDK 資料夾>/`，或安裝至可由 Windows `JavaSoft\JDK\25` 登錄項目辨識的位置。
2. 準備 **Maven**：放在 `.tools/apache-maven-3.9.11/`，或讓 `mvn.cmd` 可從 `PATH` 執行。首次建置需連線下載依賴。
3. 在專案根目錄執行：

```powershell
.\build.bat --no-pause
```

建置完成後，`dist/` 會產生 `zh_TW`、`zh_CN`、`ja_JP`、`en_US` 四份語系 JAR。直接執行 `build.bat` 會在結束時暫停；`--no-pause` 適合終端與自動化使用。

修改 [`src/main/java/`](src/main/java/) 或 [`src/main/resources/`](src/main/resources/) 後，重新建置並更換測試伺服器的 JAR，重新啟動伺服器，驗證 [功能](#功能) 及 [指令與權限](#指令與權限) 中的操作。`dist/`、`target/` 與 `.tools/` 由 [`.gitignore`](.gitignore) 排除，不會隨原始碼上傳。

---

## 技術概要

- **核心實作**：由裝備目錄、物品資料、事件監聽、新手禮包及期限處理類別分工；排程定期檢查已載入物品的期限。
- **指令與權限**：由 [`plugin.yml`](src/main/resources/plugin.yml) 宣告，實際管理限制由指令處理程式與設定共同決定。
- **建置與語系**：使用 [`pom.xml`](pom.xml) 定義依賴，由 [`build.bat`](build.bat) 依語系逐次執行 Maven 建置。

---

## 專案結構

| 路徑 | 說明 |
|------|------|
| [`pom.xml`](pom.xml) | 插件版本、Java 版本、Paper API 依賴與 Maven 建置設定 |
| [`build.bat`](build.bat) | Windows 四語系 JAR 建置腳本 |
| [`src/main/java/bm.minecraft.vip/`](src/main/java/bm.minecraft.vip/) | 插件主類別與功能實作 |
| [`src/main/resources/plugin.yml`](src/main/resources/plugin.yml) | 插件資訊、指令與權限宣告 |
| [`src/main/resources/config.yml`](src/main/resources/config.yml) | 功能與管理設定 |
| [`src/main/resources/active-language.yml`](src/main/resources/active-language.yml) | 建置時套用的預設語系 |
| [`src/main/resources/lang/`](src/main/resources/lang/) | 四種語系的訊息 |
| [`.gitignore`](.gitignore) | 本機工具、暫存與建置產物的排除規則 |

---

## 版本與多語系

- **插件版本**：目前為 `26.3_0.0.1`，建置設定見 [`pom.xml`](pom.xml)。
- **目標 API**：Paper `26.3`；實際依賴版本見 `pom.xml` 的 `paper.version`。
- **預設語系**：`zh_TW`，由 Maven 的 `default.language` 設定。
- **內建語系**：`zh_TW`、`zh_CN`、`ja_JP`、`en_US`（路徑為 `src/main/resources/lang/<語系>.yml`）。
- **語系選擇**：建置腳本將不同預設語系分別打包為 JAR；執行時依 `active-language.yml` 載入對應語系。

---

## 資料與隱私說明

裝備種類、期限與禮包取出進度儲存在物品或方塊的持久資料容器（PDC）；新手禮包領取標記儲存在玩家 PDC，供伺服器本機判斷是否已領取。

---

## 維護者：更新 GitHub 與發行版本

### 更新至 GitHub

在專案根目錄執行：

```powershell
git add README.md
git commit -m "V26.3_0.0.1"
git push origin main
```

### 準備發行檔

1. 確認 [`pom.xml`](pom.xml)、[`build.bat`](build.bat) 與 [`plugin.yml`](src/main/resources/plugin.yml) 中的版本設定一致。
2. 執行 `build.bat --no-pause`，並在測試伺服器驗證功能及語系顯示。
3. 在 [GitHub Releases](https://github.com/BoringMan314/bm-minecraft-vip/releases) 建立對應版本，附上本機 `dist/` 中的四份語系 JAR 與更新說明。

---

## 授權

本專案以 [MIT License](LICENSE) 授權。

---

## 問題與建議

歡迎透過 [GitHub Issues](https://github.com/BoringMan314/bm-minecraft-vip/issues) 回報錯誤或提出改善建議。回報時請一併提供 Paper 版本、Java 版本、插件版本、**語系**及重現步驟；若有錯誤，請附上相關設定與錯誤日誌。
