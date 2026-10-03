# bm-new

這是 B.M. Minecraft 插件的新專案規格與範本來源，不是可直接發布的插件。

建立新插件時，先複製 `templates/` 的內容，再將所有 `${...}` 佔位字替換為實際值。

## 固定專案結構

```text
bm-minecraft-<功能>/
├─ .editorconfig
├─ .gitignore
├─ pom.xml
├─ build.bat
└─ src/main/
   ├─ java/bm.minecraft.<功能>/
   │  └─ BmMinecraft<功能>Plugin.java
   └─ resources/
      ├─ config.yml
      ├─ active-language.yml
      ├─ plugin.yml
      └─ lang/
         ├─ zh_TW.yml
         ├─ zh_CN.yml
         ├─ ja_JP.yml
         └─ en_US.yml
```

## 必守規格

1. 專案、artifactId、Plugin 名稱、權限前綴皆為 `bm-minecraft-<功能>`。
2. Java package 使用單一實體目錄：`src/main/java/bm.minecraft.<功能>/`；package 宣告同為 `bm.minecraft.<功能>`。
3. 主類別一律命名 `BmMinecraft<功能>Plugin`，例如 `BmMinecraftMoreExpPlugin`。
4. 玩家可見文字一律放在 `lang/*.yml`，不可硬編碼在 Java 或 `config.yml`。
5. `config.yml` 僅放行為設定；`active-language.yml` 僅放建置時選定的語系；語系檔根層只放三個 prefix，文字放入 `messages:`。
6. `plugin.yml` 的說明、指令說明、權限說明一律使用繁體中文；欄位順序依範本。
7. 權限一律使用 `bm-minecraft-<功能>.<動作>`，例如 `.use`、`.admin`，不得使用舊縮寫或不同品牌名稱。
8. 使用 Java 25、Paper API `26.3.build.5-alpha`、UTF-8、四空白縮排與 LF 換行；僅 `build.bat` 使用 CRLF。
9. `build.bat` 必須建置 `zh_TW`、`zh_CN`、`ja_JP`、`en_US` 四份 JAR。
10. 不追蹤 `.tools/`、`.build/`、`target/`、`out/`、`dist/` 或編譯產物。

## 主類別排列

主類別依此順序安排：類別註解、常數、欄位、`onEnable`、`onDisable`、公開存取器／重新載入方法、私有註冊方法、私有輔助方法。指令需以 `PluginCommand` 明確註冊；`plugin.yml` 遺漏指令時丟出 `IllegalStateException`。

## 建立與驗證

1. 複製 `templates/` 到新專案根目錄。
2. 取代所有 `${...}`，包括套件、主類別、指令、權限和顯示名稱。
3. 補齊四份語系檔的相同訊息鍵。
4. 執行 `build.bat --no-pause`。
5. 確認四個 JAR 都產生在 `dist/`，且 `.gitignore` 使其不被 Git 追蹤。
