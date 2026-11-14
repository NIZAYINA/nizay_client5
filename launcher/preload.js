const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('nizay', {
  load: () => ipcRenderer.invoke('state:load'),
  save: (s) => ipcRenderer.invoke('state:save', s),
  addOffline: (n) => ipcRenderer.invoke('acc:addOffline', n),
  removeAccount: (n) => ipcRenderer.invoke('acc:remove', n),
  selectAccount: (n) => ipcRenderer.invoke('acc:select', n),
  launch: () => ipcRenderer.invoke('game:launch'),
  openFolder: () => ipcRenderer.invoke('game:folder'),
  openLink: (url) => ipcRenderer.invoke('link:open', url),
  minimize: () => ipcRenderer.send('win:min'),
  close: () => ipcRenderer.send('win:close'),
  onLog: (cb) => ipcRenderer.on('game:log', (_, m) => cb(m)),
  onProgress: (cb) => ipcRenderer.on('game:progress', (_, p) => cb(p)),
  onStatus: (cb) => ipcRenderer.on('game:status', (_, m) => cb(m)),
  onError: (cb) => ipcRenderer.on('game:error', (_, m) => cb(m)),
  onState: (cb) => ipcRenderer.on('game:state', (_, s) => cb(s)),
});
