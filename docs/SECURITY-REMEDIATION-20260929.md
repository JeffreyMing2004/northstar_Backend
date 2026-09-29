# NorthStar 后端安全修复说明

- **对应报告**：《NorthStar 渗透测试报告-20260929》（NS-01 ~ NS-15）
- **修复范围**：`northstar_backend`（Spring Boot 4.1.1 / Java 17）
- **验证方式**：`./gradlew test` —— 127 个用例全绿（其中 38 个为本次新增的回归用例）
- **遗留项**：需运维 / 产品侧配合的条目集中列在 §4，**不含**在本仓库代码内

> ⚠️ 部署前必读：本次改动引入了启动期安全自检。**生产必须先设置
> `SPRING_PROFILES_ACTIVE=prod`**，否则新版本会拒绝启动（这是刻意设计的 fail-fast，
> 见 §2.1）。

---

## 一、处置总览

| 编号 | 问题 | 等级 | 处置 | 落点 |
| --- | --- | --- | --- | --- |
| NS-01 | JWT 默认密钥可伪造管理员 | 严重 | ✅ 已修复 | `SecurityStartupValidator` / `JwtFilter` / `JwtUtil` |
| NS-02 | 数据层暴露公网 | 严重 | 🔧 代码侧已加固，**安全组与口令轮换需运维** | `application-prod.properties` / `.env.example` |
| NS-03 | 重置验证码可爆破 | 高危 | ✅ 已修复 | `EmailService` / `AuthGuard` / `AuthService` |
| NS-04 | 内测校验可绕过 | 高危 | ✅ 已修复（默认值翻转） | `application.properties` / `BetaWhitelistService` |
| NS-05 | 桥接密钥沿用默认值 | 高危 | ✅ 已修复 + 可选 IP 白名单 | `SecurityStartupValidator` / `ServerStatsController` |
| NS-06 | 缺安全头与 Cookie 属性 | 高危 | 🟡 API 侧已补齐，**静态站需 nginx 侧配置** | `SecurityHeadersFilter` |
| NS-07 | 生产 CORS 含 localhost | 中危 | ✅ 已修复 | `SecurityConfig` / `application*.properties` |
| NS-08 | 登录/发信无 IP 限流 | 中危 | ✅ 已修复 | `AuthGuard` / `RateLimitService` |
| NS-09 | 限流取到的不是真实 IP | 中危 | ✅ 已修复（**需 nginx 开 realip**） | `ClientIp` / `RateLimitService` |
| NS-10 | 注册无密码强度校验 | 中危 | ✅ 已修复（统一策略） | `PasswordPolicy` |
| NS-11 | 账号与白名单可枚举 | 中危 | ✅ 后端已去自增化 + 限流 | `StatsService` / `BetaController` |
| NS-12 | 生产用 Hibernate 自动改表 | 中危 | ✅ 已修复（prod=validate） | `application-prod.properties` |
| NS-13 | 令牌存 localStorage | 低危 | 🟡 后端已具备吊销能力，**存储方案待前端决策** | `TokenRevocationService` |
| NS-14 | 无鉴权接口消耗出口带宽 | 低危 | ✅ 已修复（限流） | `MinecraftAvatarController` |
| NS-15 | 公开仓库暴露内部架构 | 信息 | 🔧 **需运维**（转私有 / 清理部署配置） | — |
| 附加 | **本地配置被打进 jar**（报告未覆盖，疑似 NS-01/NS-02 的真正成因） | 严重 | ✅ 已修复 | `build.gradle` |

---

## 二、重点修复说明

### 2.1 NS-01：密钥与提权链路

**报告结论**：`application-local.properties` 里的开发密钥可读 → 结合
`spring.profiles.active=${SPRING_PROFILES_ACTIVE:local}` 兜底 → 生产沿用同一密钥 →
用该密钥签发的令牌可通过 `JwtFilter` 验签，且 `AdminAccessService.isAdmin()` 直接采信
令牌里的 `username` 声明，于是「用户名 = 提权开关」。

**本次改动**

| 层面 | 改动 |
| --- | --- |
| 判权来源 | `JwtFilter` 不再使用令牌里的 `username`：以 `sub`（userId）查 `users` 表，用数据库中的用户名判管理员。伪造令牌写谁的名字都换不到 `ROLE_ADMIN` |
| 密钥强度 | 新增 `SecurityStartupValidator`：`jwt.secret` 缺失、仍为未解析占位符、不足 32 字节、或命中已知泄露值（含本次报告里的那串）→ **启动失败**（带修复指引） |
| 默认值 | `application.properties` 中 `northstar.bridge.secret` 移除 `local-bridge-secret` 兜底；`spring.data.redis.host` 的硬编码 IP 默认值改为 `localhost`（生产值移入 prod profile） |
| 开发兜底 | 「local profile + 远程数据库 + `ddl-auto=update`」组合直接拒绝启动；本机开发需在（被 gitignore 的）`application-local.properties` 或 `.env` 里显式声明 `northstar.security.allow-dev-profile-on-remote-db=true` |
| 令牌吊销 | 新增 `TokenRevocationService`：重置密码后按「用户 × 签发时间」作废该账号全部存量令牌，新令牌 `iat` 顺延 1 秒避免误伤自身 |

**回归用例**：`JwtFilterTest`（6 例）、`SecurityStartupValidatorTest`（10 例）。

### 2.2 NS-03 / NS-08 / NS-10：认证链路防爆破

| 接口 | 修复前 | 修复后 |
| --- | --- | --- |
| `POST /api/auth/login` | 无限流、无失败计数 | 按 IP 30 次/5 分钟；按账号 10 次失败/15 分钟；失败后渐进延迟（上限 1s）；成功清零；账号不存在时也跑一次 BCrypt 抹平时间差 |
| `POST /api/auth/send-code` | 冷却只按邮箱，换邮箱即绕过 | 追加按 IP 5 次/10 分钟 |
| `POST /api/auth/forgot-password/lookup` | 无限流，回显用户名 + 游戏 ID | 按 IP 10 次/10 分钟 |
| `POST /api/auth/reset-password` | 无限流、验证码无失败计数 | 按 IP 10 次/10 分钟；同一验证码最多错 5 次即作废；成功后吊销存量令牌 |
| 验证码生成 | `ThreadLocalRandom`，TTL 5 分钟 | `SecureRandom`，TTL 120 秒（可配） |
| 密码策略 | 注册零校验，重置仅 6 位 | 统一 `PasswordPolicy`：8–72 位、必须同时含字母与数字、不允许空格 |

限流计数在 Redis（`RateLimitService`），多实例共享、重启不清零；**Redis 故障时放行**
并记 WARN——限流组件不能变成登录不可用的单点故障。

**回归用例**：`AuthGuardTest`（5 例）、`RateLimitServiceTest`（5 例）、`PasswordPolicyTest`（7 例）、
`EmailServiceTest`（验证码作废 2 例新增）、`AuthServiceTest`（弱口令 / 吊销 2 例新增）。

> ✅ **前端已同步**（`northstar_frontend`）：新增 `src/utils/password.js` 作为与
> `PasswordPolicy` 一一对应的唯一实现（8-72 位、含字母和数字、不含空格），
> 注册页与忘记密码页都改用它校验并展示同一条提示文案。
> 登录页**保留不校验长度**——存量账号可能是加严之前的短口令，前端拦下来会让他们连
> 提交按钮都点不了。响应拦截器同时补齐了 429 / `Retry-After` 的统一提示。

### 2.3 NS-04：内测校验严格模式

`northstar.verify.require-mc-id` 默认值由 `false` 改为 `true`：白名单条目未绑定游戏 ID 时
一律判为「未通过」，不再「任意游戏 ID 放行」。

上线前必须确认历史条目已回填 `mcId`（后台「白名单对账」接口
`POST /api/admin/beta/whitelist/sync-accounts` 会按账号重建），否则会误伤老玩家。
过渡期如需临时放宽，可显式设 `VERIFY_REQUIRE_MC_ID=false` 并在报告中标记为已知例外。

### 2.4 NS-05：桥接接口

- 共享令牌无默认值，缺失/过弱 → 启动失败；比较仍用 `MessageDigest.isEqual`（恒定时间）。
- 新增可选来源 IP 白名单 `northstar.bridge.allowed-ips`（单个 IP 或 IPv4 CIDR，逗号分隔）；
  留空时启动会打 WARN 提醒。

### 2.5 NS-06 / NS-07：传输层与跨域

- 新增 `SecurityHeadersFilter`：`X-Content-Type-Options`、`X-Frame-Options`、
  `Referrer-Policy`、`Permissions-Policy`、`CSP`、`Cross-Origin-Opener-Policy`，
  并在 HTTPS 请求上下发 HSTS（可配 max-age）；可选 `force-https` 由后端做 308 跳转。
- 自动为后端下发的任何 `Set-Cookie` 补 `Secure`（HTTPS 时）与 `SameSite=Lax`。
- CORS 的 `allowedHeaders` 由 `*` 收敛为白名单，并暴露 `Retry-After` 供 429 提示；
  允许来源的默认值只剩 `https://northstar.mingpixel.net`，localhost 仅存在于开发 profile。

> 静态前端由 OpenResty 直出，**不经过本后端**——静态站的 CSP / HSTS / Cookie 属性
> 仍需在 nginx 侧配置，见 §4.2。

### 2.6 NS-09 / NS-11 / NS-14：来源识别与公开接口

- `ClientIp` 统一解析来源 IP：**优先 `getRemoteAddr()`**（框架在
  `forward-headers-strategy=framework` 下已按反代覆盖过的 `X-Forwarded-For` 还原），
  只有它仍是回环地址才退回代理头；并且只接受合法 IP 字面量，
  杜绝「用任意字符串当限流键」把计数器字典打爆或跨节点绕过。
- `/api/beta/check` 增加按 IP 限流（30 次/分钟）。
- `/api/minecraft/avatar/{playerId}` 增加按 IP 限流（60 次/分钟）——该接口每次未命中缓存
  都会外呼 Mojang 并写 Redis。
- `/api/profile/{playerId}` 默认**不再接受自增主键**（前端只传用户名或游戏 ID）；
  如确有依赖可用 `PUBLIC_NUMERIC_ID_LOOKUP=true` 恢复。

### 2.7 NS-12：生产不再自动改表

新增 `application-prod.properties`（随产物分发、不含任何口令）：

| 项 | 生产取值 |
| --- | --- |
| `spring.jpa.hibernate.ddl-auto` | `validate`（可用 `DDL_AUTO` 覆盖） |
| CORS | 仅正式域名 |
| 内测校验 | 严格模式 |
| 数据库 / Redis 地址 | 支持 `DB_URL` / `REDIS_HOST` 覆盖，默认回落现网地址，便于迁内网 |

> 若 `validate` 首次启动报字段不匹配（MySQL 5.7 与实体类型的细微差异），
> 先临时 `DDL_AUTO=none` 放行，在维护窗口内对齐 schema，别退回 `update`。

### 2.8 附加项：本地配置不再进构建产物

`application-local.properties` 虽被 `.gitignore` 排除，但 Gradle 的 `processResources`
仍会把它**打进 jar**。这意味着「本机开发的数据库口令、JWT 密钥随包上生产」，
恰好能解释报告里的现象：生产库地址、`useSSL=false`、`ddl-auto=update` 全部来自开发文件。

`build.gradle` 已排除 `application-local.properties` / `*-local.properties` / `.env`，
且**只排除打包**——本机 IDE 与 `bootRun` 的 classpath 不受影响。

验证结果：

```
$ jar tf build/libs/northstar_backend-0.0.1-SNAPSHOT.jar | grep properties
BOOT-INF/classes/application-prod.properties
BOOT-INF/classes/application.properties
```

---

## 三、改动文件清单

**新增**

```
src/main/java/.../config/AuthRateLimitProperties.java     认证限流策略配置
src/main/java/.../config/SecurityHeadersFilter.java       安全响应头 + Cookie 加固 + 可选强制 HTTPS
src/main/java/.../config/SecurityStartupValidator.java    启动期密钥/配置自检（fail fast）
src/main/java/.../service/AuthGuard.java                  认证接口闸门（限流 + 失败计数 + 渐进延迟）
src/main/java/.../service/RateLimitService.java           Redis 固定窗口计数器（故障即放行）
src/main/java/.../service/TokenRevocationService.java     JWT 吊销（用户 × 签发时间）
src/main/java/.../support/ClientIp.java                   来源 IP 唯一解析入口
src/main/java/.../support/PasswordPolicy.java             密码强度唯一策略
src/main/java/.../support/RateLimitExceededException.java 限流异常（映射 429）
src/main/resources/application-prod.properties            生产 profile
.env.example                                              环境变量模板（无真实口令）
docs/SECURITY-REMEDIATION-20260929.md                     本文档
```

**修改**

```
security/JwtFilter.java                 身份改由 DB 反查；吊销令牌回 401
security/JwtUtil.java                   新增 tryParse；支持推送 iat
service/AuthService.java                登录限流、统一密码策略、重置即吊销、防枚举时间差
service/EmailService.java               SecureRandom、TTL 120s、错误 5 次作废
service/BetaWhitelistService.java       限流迁 Redis；require-mc-id 语义更新
service/StatsService.java               自增 ID 查询纳入开关
controller/AuthController.java          传来源 IP；限流回 429 + Retry-After
controller/BetaController.java          统一 ClientIp；/check 限流
controller/MinecraftAvatarController.java  按 IP 限流
controller/StatsController.java         自增 ID 查询开关
controller/ServerStatsController.java   桥接来源 IP 白名单
config/SecurityConfig.java              CORS 头白名单、暴露 Retry-After、注册配置类
resources/application.properties        桥接密钥去默认值、require-mc-id=true、Redis 默认地址、
                                        CORS 收敛、新增安全与限流配置项
build.gradle                            构建产物排除本地配置
```

**测试**

```
新增：JwtFilterTest、SecurityStartupValidatorTest、RateLimitServiceTest、
      AuthGuardTest、PasswordPolicyTest、ClientIpTest
更新：AuthServiceTest、EmailServiceTest、BetaVerifyContractTest、
      BetaWhitelistServiceTest、application-test.properties
```

**本机文件（被 gitignore）**

```
.env                        开发用环境变量：口令改由此注入；新增开发专用 JWT / 桥接令牌
application-local.properties 清空真实口令，仅保留开发便利配置
```

---

## 四、仍需运维 / 产品侧执行

### 4.1 P0：凭据轮换与网络收敛（报告 NS-01 / NS-02）

| # | 动作 | 说明 |
| --- | --- | --- |
| 1 | 生成新 `JWT_SECRET`（`openssl rand -base64 48`）注入生产环境 | 轮换后所有在线玩家需重新登录（存量令牌验签失败） |
| 2 | 生产设置 `SPRING_PROFILES_ACTIVE=prod` | **本次版本的前置条件**，否则拒绝启动 |
| 3 | 轮换 MySQL `web` 口令；删除 `web@%`，改建内网来源账号 | 报告实测 `web@%` 可任意来源登录 |
| 4 | 轮换 Redis 口令；开启 `protected-mode`，`rename-command` 禁用 `FLUSHALL`/`CONFIG` 等 | |
| 5 | 轮换 SMTP 授权码；轮换桥接令牌并同步更新 `northstar-web-bridge` 的 `config.yml` | 桥接令牌必须两端一致 |
| 6 | 安全组把 3306 / 6379 收敛为仅内网 | 有条件的话把数据层迁内网，JDBC 再启用 `sslMode=REQUIRED` |
| 7 | MySQL 5.7.44 升级到 8.0+（已 EOL） | 升级后再考虑引入 Flyway 做版本化迁移 |

### 4.2 P1：反代与 CDN（报告 NS-06 / NS-09）

> 可直接落地的版本已写进前端仓库：`northstar_frontend/deploy/openresty/northstar.mingpixel.net.conf`
> 与 `deploy/README.md` §3。**`real_ip_header` 这段是硬前提**——加固后来源 IP 以
> `request.getRemoteAddr()` 为准（由 `forward-headers-strategy=framework` 从反代覆盖过的
> XFF 还原），漏配时它等于 CF 边缘节点 IP，登录/发码的按 IP 限流会退化成全站共用一份配额，
> 正常玩家大面积 429。

```nginx
# 1) 还原 Cloudflare 真实 IP —— 否则限流按 CF 边缘 IP 计数，整群玩家共享同一个桶
set_real_ip_from 173.245.48.0/20;   # 余下 CF 网段见官方 ip-ranges 列表
# ...（完整 CF IPv4/IPv6 段）
real_ip_header CF-Connecting-IP;
real_ip_recursive on;               # 别写 0.0.0.0/0，否则 CF-Connecting-IP 可被伪造

# 2) 覆盖而不是追加 X-Forwarded-For，客户端无法伪造来源
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Real-IP $remote_addr;

# 3) 静态站安全头（后端只管 /api）
add_header Strict-Transport-Security "max-age=2592000; includeSubDomains" always;
add_header X-Content-Type-Options nosniff always;
add_header X-Frame-Options DENY always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://mc-heads.net; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'" always;

# 4) 80 -> 443（Cloudflare 侧同时开 Always Use HTTPS）
```

`p_uv_id` 这类 Cookie 若由 nginx / 统计脚本下发，同样补齐 `Secure; SameSite=Lax`。

### 4.3 P2：产品与前端

| # | 事项 | 说明 |
| --- | --- | --- |
| 1 | ~~前端注册/重置表单同步 8 位 + 字母数字规则~~ | ✅ 已完成（`northstar_frontend` 的 `src/utils/password.js` + 注册/重置/登录三页） |
| 2 | 令牌存储方案评估 | 后端已支持吊销；若要迁 HttpOnly Cookie，需要前后端一起改（CSRF 策略也要跟上） |
| 3 | `/api/beta/check`、`/api/profile` 的展示层去标识化 | 后端已限流 + 去自增 ID；前端已把 429 与「未找到」分开提示。进一步可改成统一文案 |
| 4 | 仓库可见性 | 两个公开仓库建议转私有或至少移出部署配置与接口文档 |
| 5 | 补齐历史 `mcId` 后再全量启用严格模式 | 用后台对账接口先跑一遍，确认无「未绑定」残留 |
| 6 | 前端站内文档口径 | ✅ 已完成：`docs.json` / FAQ 里编造的「重置邮件 + 点击链接、30 分钟有效期」已改为与实现一致的「6 位验证码、2 分钟有效、错 5 次作废」 |

---

## 五、遗留风险与未覆盖项

1. **每请求一次用户查询**：`JwtFilter` 现在按 userId 查库判权，换来的是「令牌里的用户名不再
   可信」。当前量级无压力；若后续 QPS 上来，可加 Caffeine 短 TTL 缓存（注意改角色后要有失效路径）。
2. **限流精度依赖反代**：`ClientIp` 已不再直接信 `X-Forwarded-For` 首段，但若反代没有用
   `$remote_addr` 覆盖该头，仍有被伪造的空间——§4.2 的 nginx 配置是配套前提。
3. **Redis 故障时限流失效**（刻意选择的可用性优先）。若安全要求更高，可改为「Redis 不可用
   时对登录只放行弱限流」，但不建议直接拒绝。
4. **`ddl-auto=validate` 首次切换**可能因 5.7 与实体类型差异报错，需按 §2.7 处理。
5. **未覆盖**：微信/第三方登录链路、MC 客户端 Mod 本体、后台批量导入的边界条件、
   同主机其它服务（报告 §8 局限说明）。
6. **`.env` 仍在本机保存着尚未轮换的真实口令**——轮换完成后请同步更新，并确认它不在
   任何备份 / 同步目录里。
