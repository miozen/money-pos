# MoneyPOS：WSL 双电脑开发测试环境指南

本文用于两台 Windows + WSL 开发电脑的本地联调。每台电脑都运行一套独立的 MariaDB、后端和前端；代码通过 Git 同步，数据库数据默认**不会**在两台电脑间自动同步。

## 1. 访问地址与端口

| 服务 | 地址/端口 | 用途 |
| --- | --- | --- |
| 前端 Vite | `http://localhost:1520/#/pos` | 日常浏览器测试入口 |
| 后端 Spring Boot | `http://localhost:9101/money-pos` | API 服务 |
| 后端健康检查 | `http://localhost:9101/money-pos/actuator/health` | 确认后端、数据库是否可用 |
| MariaDB | `127.0.0.1:3306` | 本机开发数据库 |

> 本地测试均使用 `localhost`，无需将 1520、9101 或 3306 暴露到局域网。

## 2. 每台电脑首次准备

### 2.1 获取代码并进入项目

在 WSL 中将仓库克隆到 Linux 文件系统（例如 `/home/<用户名>/projects`），不要放到 `/mnt/c`，以避免文件监听和大小写问题。

```bash
git clone <仓库地址> ~/projects/money-pos
cd ~/projects/money-pos
```

本文以下命令假设项目位于：

```text
~/projects/money-pos
```

如果实际路径不同，只需替换命令中的路径即可。

### 2.2 安装基础环境

两台电脑都需要以下版本：

- JDK 17
- Maven 3.8+
- Node.js 20（推荐通过 nvm 安装）
- MariaDB Server / Client

检查版本：

```bash
java -version
mvn -version
node -v
npm -v
mariadb --version
```

Node 通过 nvm 安装时，每次在非交互式终端中使用 npm 前先执行：

```bash
source ~/.nvm/nvm.sh
nvm use 20
```

本项目当前 WSL 开发环境已验证的运行时为 `Node v20.20.2` / `npm 10.8.2`，nvm 位于 `/home/mio/.nvm/nvm.sh`。在本机执行前端命令时，请使用以下完整命令，避免因 shell 未加载 nvm 而误判 npm 未安装：

```bash
source /home/mio/.nvm/nvm.sh
nvm use 20
```

### 2.3 安装并启动 MariaDB

```bash
sudo apt update
sudo apt install -y mariadb-server mariadb-client
sudo systemctl enable --now mariadb
```

验证：

```bash
systemctl status mariadb
sudo ss -lntp | grep 3306
```

正常时 MariaDB 应监听 `3306` 端口。

### 2.4 初始化本机开发数据库

进入 MariaDB：

```bash
sudo mariadb
```

执行下面 SQL：

```sql
CREATE DATABASE money_pos
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'money_pos'@'localhost'
  IDENTIFIED BY 'money_pos_dev';

GRANT ALL PRIVILEGES ON money_pos.* TO 'money_pos'@'localhost';

FLUSH PRIVILEGES;
```

验证账户：

```bash
mariadb --protocol=TCP -h 127.0.0.1 -u money_pos -pmoney_pos_dev -e 'SELECT VERSION();'
```

> 后端的开发配置文件 `money-pos/qk-money-app/money-app-biz/src/main/resources/application-dev.yml` 已使用以上本机开发账号。若需使用不同的账号或密码，请设置 `MONEY_DB_URL`、`MONEY_DB_USERNAME`、`MONEY_DB_PASSWORD` 环境变量，不要修改生产/打包配置。

## 3. 第一次构建

### 3.1 后端

从 Maven 聚合根目录构建所有相关模块：

```bash
cd ~/projects/money-pos/money-pos
mvn -pl qk-money-app/money-app-biz -am package -DskipTests
```

不要在 `money-app-biz` 子目录单独构建；该项目有多个本地 Maven 模块，聚合构建能正确解析它们。

### 3.2 前端

```bash
cd ~/projects/money-pos/money-pos-web
source ~/.nvm/nvm.sh
nvm use 20
npm ci
```

如果 npm 默认缓存目录没有写入权限，可改用 WSL 临时缓存：

```bash
npm ci --cache /tmp/money-pos-npm-cache
```

## 4. 日常启动方式

需开启两个 WSL 终端。MariaDB 已通过 `enable --now` 配置为系统启动时自动启动；若未运行，先执行：

```bash
sudo systemctl start mariadb
```

### 4.1 启动后端（终端一）

若 Java 或资源文件有改动，先重新构建：

```bash
cd ~/projects/money-pos/money-pos
mvn -pl qk-money-app/money-app-biz -am package -DskipTests
```

再启动：

```bash
cd ~/projects/money-pos/money-pos
java -jar qk-money-app/money-app-biz/target/money-app-biz-1.0.0.jar
```

后端启动后会自动执行 Flyway 数据库迁移。首次启动日志中出现建表相关的 `Unknown table` 警告属于初始化过程；看到迁移成功、Tomcat 监听 9101 即可。

验证后端：

```bash
curl --fail http://127.0.0.1:9101/money-pos/actuator/health
```

返回 JSON 中的 `"status":"UP"` 表示后端和数据库可用。

### 4.2 启动前端（终端二）

```bash
cd ~/projects/money-pos/money-pos-web
source ~/.nvm/nvm.sh
nvm use 20
npm run dev -- --host 0.0.0.0
```

浏览器访问：<http://localhost:1520/#/pos>

前端会自动将 `/api` 请求代理到后端的 `http://localhost:9101/money-pos`，无需额外配置。

Vue、JavaScript 和 CSS 改动会自动热更新；修改 Java、YAML 或 Maven 依赖后，需要停止后端、重新执行构建并再次启动。

## 5. IntelliJ IDEA（连接 WSL）

可直接在 IDEA 中运行 `com.money.QkMoneyApplication`。开发模式已配置为连接 WSL 的外部 MariaDB，因此不会再尝试启动 Windows 专用的 `mysqld.exe`。

- Project SDK / Java：选择 WSL 内的 JDK 17。
- Maven：选择 WSL Maven。
- 工作目录：`~/projects/money-pos/money-pos`。
- 启动前确保 MariaDB 已运行。

打包版 Electron 通过 `--app.home=...` 参数启动时，仍使用随 Windows 安装包提供的内置 MariaDB；不要把该参数添加到 IDEA 的开发运行配置。

## 6. 两台电脑如何切换

1. 在电脑 A 提交并推送代码变更。
2. 在电脑 B 拉取代码：`git pull`。
3. 若 `package.json` 或 `package-lock.json` 有改动，在电脑 B 的 `money-pos-web` 执行 `npm ci`。
4. 若 `pom.xml` 有改动，在电脑 B 的 `money-pos` 执行后端聚合构建。
5. 数据库结构变更必须新增 Flyway 脚本到：
   `money-pos/qk-money-app/money-app-biz/src/main/resources/db/migration/`。
   两台电脑在下一次后端启动时都会自动升级表结构。

业务测试数据是本机独立的。需要两台电脑拥有相同测试数据时，请明确导出并导入数据：

```bash
# 导出（源电脑）
mariadb-dump -u money_pos -pmoney_pos_dev money_pos > money_pos_dev.sql

# 导入（目标电脑；会覆盖同名表的数据）
mariadb -u money_pos -pmoney_pos_dev money_pos < money_pos_dev.sql
```

导入前请确认目标库可以被覆盖；不要将包含真实客户或生产数据的备份同步到开发机。

## 7. 停止服务与常见问题

| 情况 | 处理方式 |
| --- | --- |
| 停止前端或后端 | 在对应终端按 `Ctrl+C`。 |
| `node: command not found` | 执行 `source ~/.nvm/nvm.sh && nvm use 20`。 |
| `缺失数据库引擎文件` / `mysqld.exe` | 说明运行到了旧代码或使用了打包参数；重新聚合构建后，以本指南的命令启动。 |
| 3306 无法连接 | 执行 `sudo systemctl start mariadb`，再检查账号与数据库。 |
| 9101 被占用 | 停掉旧后端进程后再启动。 |
| 1520 被占用 | 停掉旧 Vite 进程，或检查是否已有一个前端服务正在运行。 |
| 前端提示找不到 `components/crud` | 路径大小写必须使用 `components/Crud`；WSL 区分大小写。 |
