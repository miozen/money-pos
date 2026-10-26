# MoneyPOS REL-1 main 自动正式发布实施契约

> 状态：本地实施与验证完成，等待首次已验收的 `main` 合并进行远程验收；确认日期：2026-10-26；适用分支：`dev`。

## 目标

当已验收的变更合并并推送至 `main` 时，自动生成一个正式 Windows 安装包版本：补丁版本递增、构建成功后将版本记录提交回 `main`、创建 Git 标签、创建 GitHub Release，并将 EXE 作为 Release 下载附件。

## 冻结决定

- `main` 仅承载可发布版本；`dev` 仍是日常开发分支。此工作流不会自动把 `dev` 合并到 `main`。
- 每个符合条件的 `main` 推送自动递增稳定 SemVer 的补丁位，例如 `1.0.12` 到 `1.0.13`。`minor`/`major` 版本仍须在合并前由发布人显式修改版本号。
- 版本唯一来源为 `money-pos-web/package.json`；`package-lock.json` 根版本必须与之相同。Maven 在发布构建中继续接收同一个 `-Drevision` 值。
- 仅在 EXE 已构建成功后，工作流才提交 `chore(release): vX.Y.Z`、推送 `vX.Y.Z` 标签并创建 GitHub Release。
- 版本提交不会再次发布；手动 `workflow_dispatch` 仅允许在 `main` 上运行，并同样递增一个补丁版本。
- 安装包作为 GitHub Release 附件 `WanXiangPOS-Setup-vX.Y.Z.exe`，同时保留同名版本的 Actions Artifact。

## 工作流边界

- 不修改 POS、数据库、Cloudflare、会员同步或打包内容；只调整 CI/CD、版本一致性校验与发布元数据。
- 发布构建仍使用 Windows、Java 17、Node 18、Maven 打包、`npm ci`、Vite 和 Electron Builder。
- GitHub Actions 需要仓库 `contents: write` 权限；若 `main` 分支保护禁止 `github-actions[bot]` 推送，工作流必须失败并由仓库管理员为该机器人授予允许的写入方式，不能绕过保护。
- 当构建、推送版本提交、推送标签或创建 Release 的任一步失败时，不把失败构建标记为正式 Release。

## 验收

1. 版本校验拒绝 package 与 lock 文件根版本不一致的提交。
2. 工作流的 `main` 推送触发、递增补丁、构建、版本提交、标签、Release 附件和 Artifact 名称均可由静态审查验证；GitHub Actions 的实际 YAML 解析与运行由首次正式发布验收。
3. 不在本轮合并 `main`、不触发 GitHub Actions、不创建标签或 Release；首次真实发布由未来一次已验收的 `main` 合并验证。

## 已完成的本地验证

- `money-pos-web`：NVM Node `v22.23.4` 下 `npm run release:check` 通过，确认当前 `package.json` 与 lockfile 根版本均为 `1.0.12`；`npm run build` 通过。
- `money-pos`：`mvn -q package -DskipTests`、`scripts/test-architecture-scan.sh`、`scripts/architecture-scan.sh --check-new` 通过。
- `git diff --check` 通过。前端构建仍出现既有的 Browserslist、CSS 解析、动态导入和大 chunk 告警，但没有阻断构建。
- 未在本地模拟 Windows Runner，也未实际调用 GitHub Release API；未来首次合并 `main` 时，应确认 Actions 对 `main` 的版本提交权限与 `contents: write` 权限可用。
