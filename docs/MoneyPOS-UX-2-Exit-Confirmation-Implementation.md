# MoneyPOS UX-2.1：桌面端确认退出实施记录

> 实施日期：2026-09-23；源码基线：`dev`。范围仅为主 POS 窗口的用户主动关闭确认，不改变 POS 业务状态或 Java 生命周期。

## 退出路径审查

实施前已对 Electron 源与前端代码搜索 `app.quit()`、`app.exit()`、`mainWindow.close()/destroy()`、`process.exit()`、IPC、菜单和退出文案。

| 路径 | 原有行为 | 分类 | UX-2.1 处理 |
| --- | --- | --- | --- |
| 主 POS 窗口 `closed` | 直接 `app.quit()` | 用户关闭后导致的应用退出 | 移除直接退出；改为 `close` 事件原生确认。 |
| `window-all-closed` | 非 macOS 调用 `app.quit()` | 窗口已全部关闭后的兜底退出 | 保持原逻辑；正常主 POS 关闭已先被拦截。 |
| `before-quit` | 设置 `isQuitting`，强制停止 Java 子进程 | 已确认的应用内部退出收尾 | 保持不变。 |
| Admin `close` | 清理临时 session 后调用自身 `close()` | Admin 会话关闭 | 保持；不调用 `app.quit()`。 |
| 客显窗口 | 没有 close/closed 退出处理 | 单窗口关闭 | 保持；关闭客显不退出应用。 |
| `app.exit()`、`process.exit()`、主窗口 `close/destroy()`、退出 IPC/菜单 | 未发现 | 不存在的主动入口 | 无需处理。 |

Windows 关机/注销、任务管理器强杀、崩溃和断电不属于本切片的确认框范围。

## 实现

`money-pos-web/main.cjs` 新增应用级状态：

- `isAppExitApproved`：用户已明确确认退出；后续窗口关闭不再询问。
- `isExitConfirmationShowing`：确认框正在显示，拦截连续关闭或 `Alt+F4` 的重复 Dialog。
- 既有 `isQuitting`：在确认后、调用 `app.quit()` 前设置，供全部窗口关闭和 Java 生命周期收尾使用。

主 POS 的 `close` 未获批准时调用 `preventDefault()`，随后展示主进程原生 `dialog.showMessageBox`：

- `继续收银`：第一个按钮，`defaultId: 0`、`cancelId: 0`，因此 Esc/取消等价于继续收银。
- `确认退出`：设置应用级批准状态，再统一调用 `app.quit()`。

确认文案固定为：

> 确认退出万象收银？未结算购物车不会自动保存；已挂单订单保留在本机。

Java 停止仍仅发生在 `app.quit()` 后的既有 `before-quit`，不会在主窗口的 `close` 事件中执行。取消时不改变窗口、renderer、客显、Admin 或 Java 状态。

## 非目标

不实现自动挂单、购物车保存/恢复、renderer 与主进程购物车同步，或任何 POS 订单/会员/权限业务改动。

## 验收清单

在 Windows 打包版中验证：标题栏关闭、Alt+F4、任务栏关闭、连续关闭、取消后状态保持、确认后全部窗口和 Java 子进程退出、Admin 单独关闭、客显单独关闭。系统关机等强制退出不要求弹框。
