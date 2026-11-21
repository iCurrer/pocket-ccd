# 内测激活（邀请码 + 服务端校验）方案 V1.0

> **状态：已实施（2026-11-11）。** 服务端已上线腾讯云，客户端已实现并通过截图回归，
> 版本 `0.9.0-beta3 / vc24`。**唯一未完成项：`pccd.fky.ltd` 的 DNS A 记录 → HTTPS 入口。**
> 实际落地与本文的差异见 §11。
>
> 关联：D-091（INTERNET 权限已加）、D-092（应用内更新，可复用其 HTTP 写法）、D-093（本方案的决策记录）。

---

## 1. 结论

**能做，工作量小（约半天），零额外成本**（复用已有腾讯云服务器 + 已有域名证书）。

核心不是"加密"，而是**服务端权威绑定表**：

```
用户首次打开 → 输入邀请码 → 服务端校验（码有效？没被别人绑过？）
            → 绑定 码↔设备指纹 → 回一个不透明 token
            → 客户端存 token → 之后永久免验证、完全离线可用
```

一句话概括安全模型：**码是入场券，绑定表是检票口，token 是已入场的手环。**
手环本身不需要防伪（它只在你自己的手机上），需要防伪的是入场券——而入场券的真伪由服务端当场判定，客户端无从伪造。

方案**刻意不做**的事（理由见第 3 节）：客户端离线验签、设备指纹防篡改、码的加密传输（HTTPS 已覆盖）。

---

## 2. 用户体验流程

| 步骤 | 界面 |
|---|---|
| 首次启动 | 暖白机身底 + 压暗衬底上浮起一张卡：「内测凭证」/ 一个码输入框 / 一颗「激活」通栏键 |
| 输入 | `KeyboardType.Ascii`、自动大写、自动忽略连字符（`pccd7k3m9qx2` 也认） |
| 成功 | 卡片淡出 → 直接进取景器（保住 D-039「打开即取景」，不额外增加一步点击） |
| 失败 | 卡片内一行朱红提示，**不弹 Toast**（Toast 在横屏/浅底上可读性差） |
| 已激活 | 永远不再出现。设置页底部多一行灰字：`内测凭证 · 2026-11-12 激活` |

**形态决策：全屏遮罩卡，不是 `Dialog`。** 视觉上是弹窗（压暗底 + 浮起面板 + 圆角阴影），实现上是 `AppRoot` 顶部的分支返回。
理由：`Dialog` 会起独立 window，与拍摄页的 `requestedOrientation = SENSOR_LANDSCAPE` 打架（D-083 的方向是按页面设的），且 `Dialog` 排在 Activity 之上会让相机权限弹窗的层级变得不可预期。全屏分支还能顺手**完全不启动相机**——省电、少一次绑定。

---

## 3. 安全模型（这一节决定方案能不能用）

### 3.1 为什么不做客户端验签

常见做法是服务端用私钥签 token、客户端内置公钥验签。**本项目不采用**，原因：

1. **它防不住真正的绕过。** 攻击者能改包的话，直接删掉 `if (!activated)` 那一行就行，验签逻辑一起删掉。
2. **它防不了的东西 HTTPS 已经防了。** 验签的实质价值是"防止伪造服务器响应"，而这正是 TLS 的工作。
3. **成本是真的。** Ed25519 在 Android 上要 API 33+（本项目 minSdk 29），退到 ECDSA P-256 则服务端要引入 `cryptography` 依赖（可能撞 PEP 668 / 无 pip 环境），或每次请求 fork 一次 `openssl`。

**内测分发的真实威胁是"APK 被转到群里、陌生人白嫖"，不是"有人逆向后破解"。** 前者靠服务端绑定表 100% 解决，后者任何客户端方案都解决不了。

### 3.2 真正的防线（按重要性排序）

| # | 措施 | 挡住什么 |
|---|---|---|
| 1 | **一码一机绑定**：首次激活时把 `码 ↔ deviceHash` 写死，第二台设备用同一个码 → `CODE_USED_UP` | 转群白嫖（**这是主防线**） |
| 2 | **码空间足够大**：8 位 Crockford Base32 ≈ 1.1×10¹²，撞码/穷举不可行 | 暴力猜码 |
| 3 | **双层限速**：Nginx `limit_req` + 应用内每 IP 每小时 20 次 | 脚本刷码 |
| 4 | **后台不下发任何明文码**：生成时一次性打印到终端/CSV，库里只存码本身（可哈希，见 4.2 注） | 库被拖走也不至于立刻全员泄漏 |
| 5 | **HTTPS 强制**（复用已有证书域名） | 中间人 |

### 3.3 边界（必须承认的部分）

- **卸载重装 = 重新激活。** token 在 `SharedPreferences`，卸载就没。内测用户须知里要写"换机/重装找我要新码"。
  - 注：`android:allowBackup` 未在 manifest 显式声明，默认 `true` → 换新机时 Auto Backup 可能把 token 一起还原过去，表现为"换机也自动激活了"。**这是漏洞但无害**（等于多给了一台），要不要堵见第 7 节决策 B。
- **不联网就完全无法吊销。** 已发出的 token 无法远程作废。可加"启动时静默复核"（Phase 2 可选），但复核失败绝不能阻塞——否则服务器一挂，所有内测用户拍不了照。
- **deviceHash 可被 root 用户改写**（改 `Settings.Secure.ANDROID_ID` 或直接改 pref）。接受。

---

## 4. 服务端

### 4.1 技术选型：Python 3 标准库，零第三方依赖

单文件 `server/app.py`，用 `http.server.ThreadingHTTPServer` + `sqlite3` + `json` + `secrets`。

**为什么不 FastAPI / Go / Node**：`pip` 在国内云主机上常撞 PEP 668（externally-managed-environment）、Go/Node 未必装了。标准库版本 `python3 app.py` 就能跑，`systemd` 一条命令托管，200 行以内看得完。内测规模（几百次激活，峰值个位数 QPS）标准库绰绰有余。

### 4.2 数据表

```sql
CREATE TABLE codes (
  code        TEXT PRIMARY KEY,      -- 存原码（见下方注）
  batch       TEXT NOT NULL,
  note        TEXT,                  -- "小红书第一批""给XX"
  max_devices INTEGER NOT NULL DEFAULT 1,
  revoked     INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL
);

CREATE TABLE bindings (
  code       TEXT NOT NULL,
  device     TEXT NOT NULL,          -- 32 hex
  token      TEXT NOT NULL UNIQUE,   -- 32 hex, secrets.token_hex(16)
  model      TEXT,                   -- PJZ110
  app_ver    TEXT,                   -- 0.9.0-beta3
  ip         TEXT,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (code, device)
);

CREATE TABLE attempts (               -- 限速用；也可纯内存，重启清零无所谓
  ip TEXT, ts INTEGER
);
```

> **注（存原码 vs 存哈希）**：存哈希更安全，但主人需要能"查这个码是谁的"、"这个码失效了换成哪个"。折中：**主表存 `sha256(code)`，另存一份明文 CSV 在服务器 `/root/batches/*.csv`（600 权限）**。这样库被拖走不可直接冒用，台账又还在。**提案默认按这个做**，若嫌麻烦可改存原码。

### 4.3 接口

公共（走 HTTPS，Nginx 反代到 `127.0.0.1:8710`）：

```
POST /pccd/activate
  {"code":"PCCD-7K3M-9QX2", "device":"a1b2...", "model":"PJZ110", "ver":"0.9.0-beta3"}
  200 {"ok":true, "token":"...", "activated_at":1762900000}
  4xx {"ok":false, "err":"INVALID_CODE"}      // 码不存在 / 格式错 —— 同一个错误码，不区分
      {"ok":false, "err":"CODE_USED_UP"}      // 已被别的设备绑满
      {"ok":false, "err":"REVOKED"}           // 手动作废
      {"ok":false, "err":"RATE_LIMITED"}
```

管理（`bind 127.0.0.1` + `Authorization: Bearer <admin_key>`，外网访问不到）：

```
POST /pccd/admin/gen     {"batch":"b1","count":50,"max_devices":1,"note":"小红书"}  → 明文 CSV
POST /pccd/admin/revoke  {"code":"..."} 或 {"token":"..."}
GET  /pccd/admin/stats   → 各批次已发/已激活/未激活
```

> **错误码设计要点**：`INVALID_CODE` 与"码不存在"必须是同一个，否则等于给攻击者一个"这个码存在"的探测器，把 10¹² 的搜索空间降到几百。

### 4.4 部署

```nginx
# 已有 fky.ltd 的 443 server 块里追加，不动 DNS
location /pccd/ {
    limit_req zone=pccd burst=5 nodelay;
    proxy_pass http://127.0.0.1:8710/;
    proxy_set_header X-Real-IP $remote_addr;
}
```

`/etc/nginx/nginx.conf` 加 `limit_req_zone $binary_remote_addr zone=pccd:1m rate=10r/m;`

```
/etc/systemd/system/pccd-auth.service
  ExecStart=/usr/bin/python3 /opt/pccd-auth/app.py --port 8710 --db /var/lib/pccd/auth.db
  Restart=always
  User=pccd  (nobody 亦可，只读 /opt、读写 /var/lib)
```

运维三条：
1. **只监听 `127.0.0.1:8710`**，腾讯云安全组**不需要新开任何端口**，全部走已有 443。
2. **每天 `sqlite3 auth.db ".backup /var/backups/pccd-$(date +%F).db"`**（cron）。绑定表丢了 = 所有内测用户要重新激活。
3. 日志 `journalctl -u pccd-auth`，同时把每条激活落 `bindings` 表即可当台账查。

---

## 5. 客户端改动

### 5.1 新增文件

| 文件 | 内容 |
|---|---|
| `auth/DeviceId.kt` | `sha256(ANDROID_ID + Build.FINGERPRINT + packageName).take(32)` |
| `auth/ActivationStore.kt` | SharedPreferences（`beta_activation`）：`token` / `code` / `activated_at` / `device` |
| `auth/ActivationClient.kt` | `HttpURLConnection` POST，8s connect + 8s read 超时；错误映射为密封类 `ActivateResult`（写法照抄 `update/UpdateChecker.kt`，**不引入 OkHttp**） |
| `ui/auth/ActivationScreen.kt` | 全屏遮罩卡：压暗底（`veil` 反向，黑 32%）+ `consolePanel` 卡 + 标题/说明/输入框/通栏键/错误行/折叠"这是什么" |

### 5.2 改动文件

| 文件 | 改什么 |
|---|---|
| `ui/AppRoot.kt` | `PocketCcdTheme {` 之后**第一件事**：`if (!activated) { ActivationScreen(...); return@PocketCcdTheme }`。必须在所有副作用（相机绑定、权限请求、`PhotoStore` 构造、`LaunchedEffect`）之前 |
| `ui/settings/SettingsScreen.kt` | 页脚加一行 `内测凭证 · yyyy-MM-dd 激活` |
| `app/build.gradle.kts` | `versionCode = 24` / `versionName = "0.9.0-beta3"`（应用内更新要求 code 递增，D-092） |
| `AndroidManifest.xml` | **不用改**。`INTERNET` 已有（D-091）；ExoPlayer 之类都没有，也不需要 `usesCleartextTraffic`（默认 false 正好） |

### 5.3 三个技术陷阱（真实会踩）

1. **方向**。激活分支里要显式 `LaunchedEffect { requestedOrientation = PORTRAIT }`，激活成功后恢复 `UNSPECIFIED`。否则：从横屏状态的拍摄页→杀进程→冷启动，窗口可能先以横屏起来，激活卡横躺（违反第 13 条 UI 红线"不要让人歪头看"）。
2. **别让网络跑主线程**。`withContext(Dispatchers.IO)`；激活键按下后立刻 `enabled = false` + 转圈，防连点。
3. **`BuildConfig.DEBUG` 逃逸**。`if (BuildConfig.DEBUG) return activated = true`，方便自己调试。**安全**：release 用 debug 签名但 `BuildConfig.DEBUG == false`（D-091 已确认），不会泄漏到内测包。

### 5.4 状态机

```
Unknown(冷启动读 pref) ─┬─ 有 token → Activated（不再联网）
                        └─ 无 token → Locked
Locked ──输入码──> Submitting ──ok──> Activated（存 pref，淡出）
                             ├─INVALID_CODE / CODE_USED_UP / REVOKED → Locked + 错误文案
                             ├─超时/无网 → Locked + "网络不通，检查后重试"（**不清空已输入的码**）
                             └─5xx → Locked + "服务器忙，稍后再试"
```

---

## 6. 码的形态与分发

格式：`PCCD-XXXX-XXXX`，字符集 **Crockford Base32**（`0123456789ABCDEFGHJKMNPQRSTVWXYZ`，去掉 I/L/O/U，避免 1/I、0/O 抄错）。

输入容错：自动大写、剥掉所有非字母数字、`0↔O`、`1↔I`、`L↔1` 归一化（Crockford 标准解码）。这样用户在聊天里看到 `PCCD-7K3M-9QX2` 手打十次错一次的问题基本消失。

分发建议：**首批 30 个**（20 发人，10 备用），按批次记录 `batch` + `note`（发给谁），CSV 台账留在服务器。若以后要发到公开渠道，再加一步"扫码/深链 `pccd://activate?code=...`"，Phase 2。

---

## 7. 待主人确认（4 项）

| # | 决策 | 选项 | 我的建议 |
|---|---|---|---|
| **A** | 一码几台设备 | ① 1 台（严格） ② 2 台（宽） | **① 1 台**。你自己那批单独生成一组 `max_devices=3` 的 master 码；换机直接发新码 |
| **B** | token 有效期 | ① 永久（离线永久可用） ② 90 天 + 到期需联网续 | **① 永久**。相机 App 必须能离线拍；②的收益只是"理论上可吊销"，而吊销还得等用户联网，不值 |
| **C** | 是否堵 Auto Backup（换机自动激活） | ① 堵（`dataExtractionRules` 排除 pref） ② 不堵 | **② 不堵**。内测阶段"多给一台"不是损失，改 manifest 反而引入风险 |
| **D** | 激活页形态 | ① 全屏遮罩卡 ② `Dialog` | **①**，理由见第 2 节（方向 + 相机生命周期） |

## 8. 需要主人提供（服务端）

1. 腾讯云服务器 **公网 IP**、**SSH 用户**、**密钥文件路径/passphrase**（其他对话里有，本会话读不到历史，麻烦贴一次）
2. 系统与 **Python 版本**（`python3 -V`；要 ≥ 3.7）
3. 是否已有 **Nginx** 与 **fky.ltd 的 443 证书**（有则可直接加 `location /pccd/`；没有我给你换个域名/路径）
4. 该 443 上**已经跑了什么**（避免 `location` 顺序撞车）
5. 是否允许新建 **systemd 服务 + 系统用户 + `/opt/pccd-auth` 目录**（按铁律，未获明确允许我不动服务器）

## 9. 落地顺序（预计 4–5 小时）

| 步 | 内容 | 时长 |
|---|---|---|
| 1 | 服务端 `app.py` + 本地跑通（含生成 5 个码、激活、复用码被拒、限速） | 60 min |
| 2 | 客户端 4 个文件 + `AppRoot` 一行分支 | 90 min |
| 3 | 装到真机验证 6 个场景（首次/错码/复用码/断网/杀进程重启/横竖屏） | 40 min |
| 4 | 上传服务器 + Nginx + systemd + 外网 curl 验证 | 40 min |
| 5 | 出 `0.9.0-beta3` release 包 + 生成 30 个码 + 台账 | 30 min |

## 10. 已知风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 服务器宕机 | 新用户激活不了（老用户不受影响） | 激活页明确"网络不通"文案 + 备用方案：主人手动发 token（Phase 2 后台加 `/admin/manual_token`） |
| 数据库丢失 | 全员需重新激活 | 每日备份 + CSV 台账 |
| 码被截图传播 | 只有第一个抢到的人能用 | 设计如此（一码一机）；发现后后台 `revoke` |
| 内测用户嫌麻烦 | 差评 | 卡片上写明"一次激活，之后不用再管"；码可粘贴 |

---

## 11. 实施结果（2026-11-11）

### 11.1 已落地的部分

| 层 | 内容 | 状态 |
|---|---|---|
| 服务端 | `server/app.py`，Python 3 标准库零依赖 | 已上线 |
| 服务端 | systemd `pccd-auth.service`，用户 `pccd`，`ProtectSystem=strict` | 已启用 |
| 服务端 | Nginx `pccd.fky.ltd.conf`，只暴露 `/pccd/`，其余 404 | 已生效 |
| 服务端 | 每日 04:15 热备份 `/var/backups/pccd/`，保留 14 份 | 已跑通 |
| 服务端 | 首批 33 个码（30 单人 + 3 master） | 已生成 |
| 客户端 | `auth/` 四个文件 + `ui/auth/ActivationScreen.kt` | 已完成 |
| 客户端 | `AppRoot` 闸门、设置页"内测凭证"行、版本 24/beta3 | 已完成 |
| 验证 | 服务端 9 场景端到端（含外网 80 反代）+ 单测 + 3 张截图 | 已通过 |

### 11.2 部署事实（与本文 §4.4 的差异）

- 服务器：`101.34.239.213`，Ubuntu 24.04.4 / Python 3.12.3 / Nginx 1.28.1（**宝塔面板托管**）。
- **没有动全局 `nginx.conf`**：本方案原打算加 `limit_req_zone`，实际改由应用层内存限速
  （每 IP 每小时 20 次）承担 —— 少改一处宝塔托管的全局配置，风险更低，效果等价。
- 站点配置写成**独立 vhost 文件** `/www/server/panel/vhost/nginx/pccd.fky.ltd.conf`，
  不改 `fky.ltd.conf`（宝塔重存站点会覆盖它）。
- 管理接口全部走 `X-Admin-Key`，且因为后端只监听 `127.0.0.1`，**外网根本到不了** `/admin/*`
  （Nginx 只反代 `/pccd/`，`/pccd/admin/*` 确实会被转发，但 `pccd.fky.ltd` 只对
  主人生效的 key 开放 —— 若要更严，可把 `admin` 路径在 Nginx 层直接 `return 404`）。

### 11.3 四个决策的实际取值

按本文第 7 节的建议执行（主人回复"这个功能先做吧"时未逐条否决）：

- **A = 1 台/码**（另生成 3 个 `max_devices=3` 的 master 码给主人自己）
- **B = token 永久**（离线永久可用）
- **C = 不堵 Auto Backup**（换机还原等于多送一台，内测阶段可接受）
- **D = 全屏遮罩卡**

若要改成 90 天续期或严格 1 台（含备份不还原），改动点集中在 `ActivationStore` 与
`AndroidManifest` 的 `dataExtractionRules`，不影响服务端。

### 11.4 HTTPS 入口（已打通，2026-11-11）

打通前的事实，留作排障对照：

| 事实 | 当时的值 |
|---|---|
| `fky.ltd` 解析 | `101.132.107.88`（**阿里云旧节点**） |
| `omaster.fky.ltd` | Cloudflare 代理，回源已失效 |
| `pccd.fky.ltd` | **无记录**（NXDOMAIN） |
| 本机 `fky.ltd` 证书 | 2026-07-12 已过期 |
| NS | `yahir.ns.cloudflare.com` / `tani.ns.cloudflare.com` |

**实际做法：灰云直连 + 宝塔自带 acme_v2，没有装任何新软件。**

1. Cloudflare 加 `pccd` → A → `101.34.239.213`，**代理状态选灰云（DNS only）**。
   不选橙云的理由：CF 的 SSL 加密模式是 zone 级设置，为让 `pccd` 回源到 80 而把全局
   改成 Flexible，会连带改变 `omaster.fky.ltd` 的回源行为。
2. 证书用面板自带的 `/www/server/panel/class/acme_v2.py`（不是 acme.sh、不是 certbot）：

```bash
cd /www/server/panel
/www/server/panel/pyenv/bin/python3 class/acme_v2.py \
  --domain pccd.fky.ltd --type http --path /www/wwwroot/pccd.fky.ltd
```

   HTTP-01 验证由 vhost 的 `location ^~ /.well-known/acme-challenge/` 提供，
   落盘在 `vhost/letsencrypt/pccd.fky.ltd/`；该模块不写站点配置（无 `SetSSL` 调用）。
3. 证书复制到面板口径目录 `vhost/cert/pccd.fky.ltd/`（nginx 引用这里），
   私钥 `chmod 600`。
4. vhost 改为双 server：80 只留 ACME 验证 + `301` 到 https；443 承担反代。

**两个坑：**

- 只有 `listen 80` 时，443 上落下的是**宝塔默认自签证书**（`subject` 空）。所以
  DNS 生效后 `https://` 依旧连不上，且客户端报错与"域名没解析"完全一样。
  **DNS 和 443 vhost 必须一起做完才算通。**
- 证书要**两个目录各一份**：`letsencrypt/`（acme 落盘）与 `cert/`（nginx 引用）。
  只放一处会出现"面板看不到证书"或"续期后线上还是旧证书"。

**续期**：`/opt/pccd-auth/renew_cert.sh` + `/etc/cron.d/pccd-cert-renew`（每日 03:30）。
面板的自动续签遍历**站点列表**，本 vhost 是手工独立文件、未注册成面板站点，面板不管它
—— 那会 90 天后静默失效，症状是"某天起所有新用户都激活不了"且无报错。脚本用
`openssl -checkend 2592000` 判断（剩余 <30 天才申请，一年最多 4 次），续期后强制
复制到 `cert/`、修私钥权限、`nginx -s reload`。

**验证结果（真实公网请求）：**

| 检查 | 结果 |
|---|---|
| `dig @8.8.8.8 pccd.fky.ltd` | `101.34.239.213` |
| `https://pccd.fky.ltd/pccd/health` | `200`，`ssl_verify_result=0`（链被信任） |
| `http://pccd.fky.ltd/pccd/health` | `301` 到 https |
| 真实激活（首码） | 返回 token，`again=false` |
| 同码换设备 | `CODE_USED_UP` |
| 公网 `/pccd/admin/stats` | `404`（管理接口只走 SSH） |
| 证书 | `CN=pccd.fky.ltd`，Let's Encrypt `YR1`，到期 **2027-02-09** |

### 11.5 日常运维命令

```bash
# 服务状态 / 日志
systemctl status pccd-auth
journalctl -u pccd-auth -f

# 台账（各批次发放与激活数）
su -s /bin/sh pccd -c "/usr/bin/python3 /opt/pccd-auth/app.py --db /var/lib/pccd/auth.db --stats"

# 再发一批码（在服务器上）
su -s /bin/sh pccd -c "/usr/bin/python3 /opt/pccd-auth/app.py --db /var/lib/pccd/auth.db \
  --gen 20 --batch b2 --note 可回滚"

# 换机补救：释放旧设备（码本身仍有效）
curl -s -X POST http://127.0.0.1:8710/admin/unbind -H "X-Admin-Key: $(cat /var/lib/pccd/admin.key)" \
  -H 'Content-Type: application/json' -d '{"code":"PCCD-XXXX-XXXX"}'

# 作废一个码
curl -s -X POST http://127.0.0.1:8710/admin/revoke -H "X-Admin-Key: $(cat /var/lib/pccd/admin.key)" \
  -H 'Content-Type: application/json' -d '{"code":"PCCD-XXXX-XXXX"}'
```
