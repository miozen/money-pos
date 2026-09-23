const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('moneyPosDesktop', {
    openAdminWindow: () => ipcRenderer.invoke('money-pos:open-admin')
});
