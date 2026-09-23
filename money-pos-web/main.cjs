const { app, BrowserWindow, screen, Menu, dialog, ipcMain } = require('electron');
const path = require('path');
const { spawn, exec } = require('child_process');
const http = require('http');

const isPackaged = app.isPackaged;
const APP_ROOT = isPackaged ? path.dirname(app.getPath('exe')) : path.join(__dirname, '..');
const RESOURCES_DIR = isPackaged ? process.resourcesPath : __dirname;

const JAVA_BIN = isPackaged ? path.join(APP_ROOT, 'jre', 'bin', 'vana-java.exe') : 'java';
const JAR_PATH = isPackaged ? path.join(RESOURCES_DIR, 'backend', 'vana-pos.jar') : path.join(__dirname, '../qk-money-app/money-app-biz/target/money-pos.jar');

let mainWindow;
let guestWindow;
let adminWindow;
let backendProcess;
let isQuitting = false;
let isAppExitApproved = false;
let isExitConfirmationShowing = false;

const POS_PRELOAD = path.join(__dirname, 'preload.cjs');

function frontendUrl(route) {
    const indexPath = isPackaged ? `file://${path.join(__dirname, 'dist', 'index.html')}` : 'http://localhost:1520/money-pos';
    return `${indexPath}#${route}`;
}

async function requestAppExitConfirmation() {
    if (isAppExitApproved || isExitConfirmationShowing || !mainWindow || mainWindow.isDestroyed()) return;

    isExitConfirmationShowing = true;
    try {
        const { response } = await dialog.showMessageBox(mainWindow, {
            type: 'warning',
            title: '确认退出',
            message: '确认退出万象收银？未结算购物车不会自动保存；已挂单订单保留在本机。',
            buttons: ['继续收银', '确认退出'],
            defaultId: 0,
            cancelId: 0,
            noLink: true
        });

        if (response === 1) {
            isAppExitApproved = true;
            isQuitting = true;
            app.quit();
        }
    } catch (error) {
        console.error('[VanaPOS] 显示退出确认框失败', error);
    } finally {
        isExitConfirmationShowing = false;
    }
}

function openAdminWindow() {
    if (adminWindow && !adminWindow.isDestroyed()) {
        if (adminWindow.isMinimized()) adminWindow.restore();
        adminWindow.focus();
        return;
    }

    const partition = `money-admin-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    adminWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        minWidth: 1024,
        minHeight: 640,
        title: '万象收银 - 后台管理',
        webPreferences: {
            preload: POS_PRELOAD,
            partition,
            nodeIntegration: false,
            contextIsolation: true,
            webSecurity: false
        },
        autoHideMenuBar: true
    });

    let storageCleared = false;
    let clearingStorage = false;
    adminWindow.on('close', (event) => {
        if (isQuitting || storageCleared) return;
        event.preventDefault();
        if (clearingStorage) return;
        clearingStorage = true;
        const closingWindow = adminWindow;
        closingWindow.webContents.session.clearStorageData()
            .catch((error) => console.error('[VanaPOS] 清理后台认证状态失败', error))
            .finally(() => {
                storageCleared = true;
                clearingStorage = false;
                if (!closingWindow.isDestroyed()) closingWindow.close();
            });
    });
    adminWindow.on('closed', () => { adminWindow = undefined; });
    adminWindow.loadURL(frontendUrl('/login?entry=admin'));
}

ipcMain.handle('money-pos:open-admin', (event) => {
    if (!mainWindow || event.sender !== mainWindow.webContents) {
        throw new Error('后台窗口只能由主收银窗口打开');
    }
    openAdminWindow();
});

function createWindows() {
    const displays = screen.getAllDisplays();
    const primaryDisplay = screen.getPrimaryDisplay(); // 🌟 修复嗅探：获取真正的系统主屏

    mainWindow = new BrowserWindow({
        width: 1280, height: 800,
        title: "万象收银", show: false,
        webPreferences: { preload: POS_PRELOAD, nodeIntegration: false, contextIsolation: true, webSecurity: false },
        autoHideMenuBar: true
    });

    mainWindow.loadURL(frontendUrl('/pos'));

    mainWindow.once('ready-to-show', () => {
        mainWindow.show();
        mainWindow.maximize();
    });

    // 主 POS 是唯一需要确认的用户主动退出入口；Admin/客显不触发此流程。
    mainWindow.on('close', (event) => {
        if (isAppExitApproved || isQuitting) return;
        event.preventDefault();
        requestAppExitConfirmation();
    });

    mainWindow.on('closed', () => {
        mainWindow = undefined;
    });

    // 🌟 修复嗅探：只要 ID 和系统主屏不一样，就是副屏（完美兼容负坐标、左右颠倒布局）
    const externalDisplay = displays.find(d => d.id !== primaryDisplay.id);

    if (externalDisplay) {
        guestWindow = new BrowserWindow({
            // 1. 初始化时只给基本坐标和无边框，绝不在这里混用 fullscreen/kiosk
            x: externalDisplay.bounds.x,
            y: externalDisplay.bounds.y,
            width: externalDisplay.bounds.width,
            height: externalDisplay.bounds.height,
            frame: false,
            show: false,
            skipTaskbar: true, // 仅隐藏任务栏图标

            title: "万象收银-客显屏",
            webPreferences: { nodeIntegration: false, contextIsolation: true, webSecurity: false }
        });

        guestWindow.setMenu(null);

        // 🌟 核心提权：突破 floating 限制，使用 screen-saver 级别，绝对压制 Windows 任务栏
        guestWindow.setAlwaysOnTop(true, 'screen-saver');

        guestWindow.loadURL(frontendUrl('/guest'));

        guestWindow.once('ready-to-show', () => {
            // 🌟 核心防线：确保物理边界在渲染引擎中 100% 锁定
            guestWindow.setBounds(externalDisplay.bounds);

            // 🌟 核心防线：静默显示，绝对不抢主收银台的扫码枪焦点！
            guestWindow.showInactive();

            // 🌟 核心防线：在窗口完全可见且失去焦点的状态下，霸道夺取全屏 (Kiosk)
            guestWindow.setKiosk(true);

            // 兜底操作：强制提到 z-index 最顶层
            guestWindow.moveTop();
        });
    }
}

function startBackend() {
    console.log("🚀 [VanaPOS总控] 正在拉起专属 Java 引擎...");
    backendProcess = spawn(JAVA_BIN, ['-jar', JAR_PATH, `--app.home=${APP_ROOT}`], { cwd: APP_ROOT });
    backendProcess.stdout.on('data', (data) => console.log(`[VanaPOS] ${data.toString().trim()}`));
    backendProcess.stderr.on('data', (data) => console.error(`[VanaPOS Error] ${data.toString().trim()}`));
    checkHealth();
}

function checkHealth() {
    const req = http.get('http://127.0.0.1:9101/money-pos/actuator/health', (res) => {
        if (res.statusCode === 200) {
            createWindows();
        } else {
            setTimeout(checkHealth, 1500);
        }
    });
    req.on('error', () => setTimeout(checkHealth, 1500));
    req.end();
}

app.whenReady().then(() => {
    Menu.setApplicationMenu(null);
    startBackend();
});

// 🌟 核心修复：在应用真正退出前，无条件强杀 Java 引擎
app.on('before-quit', () => {
    isQuitting = true;
    if (backendProcess) {
        console.log("🛑 [VanaPOS总控] 正在执行强行停机序列...");
        if (process.platform === 'win32') {
            // Windows 下连带子线程一起强杀
            exec(`taskkill /pid ${backendProcess.pid} /t /f`, (err) => {});
        } else {
            backendProcess.kill('SIGKILL');
        }
    }
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});
