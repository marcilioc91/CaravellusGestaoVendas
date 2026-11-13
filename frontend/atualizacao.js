const { app, ipcMain } = require('electron');
const { autoUpdater } = require('electron-updater');

function registrarAtualizacao(win, pararBackend) {
  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = true;

  const enviar = (canal, dados) => {
    if (!win.isDestroyed()) win.webContents.send(canal, dados);
  };

  autoUpdater.on('update-available', (info) =>
    enviar('atualizacao:disponivel', { nova: info.version, atual: app.getVersion() }));

  autoUpdater.on('download-progress', (p) => {
    win.setProgressBar(p.percent / 100);
    enviar('atualizacao:progresso', Math.round(p.percent));
  });

  autoUpdater.on('update-downloaded', (info) => {
    win.setProgressBar(-1);
    enviar('atualizacao:baixada', { nova: info.version });
  });

  autoUpdater.on('error', (erro) => {
    win.setProgressBar(-1);
    console.error('Atualização:', erro?.message ?? erro);
    enviar('atualizacao:erro');
  });

  ipcMain.handle('atualizacao:verificar', () => {
    if (app.isPackaged) autoUpdater.checkForUpdates().catch(() => {});
  });

  ipcMain.handle('atualizacao:baixar', () => autoUpdater.downloadUpdate().catch(() => {}));

  ipcMain.handle('atualizacao:instalar-agora', async () => {
    await pararBackend();
    autoUpdater.quitAndInstall(false, true);
  });
}

module.exports = { registrarAtualizacao };
