const { app, dialog} = require('electron');
const { autoUpdater } = require('electron-updater');

function verificarAtualizacao(win, pararBackend) {
  // Fora do instalador (npm run electron) nao existe app-update.yml
  if (!app.isPackaged)
    return;

  autoUpdater.autoDownload = false;
  autoUpdater.autoInstallOnAppQuit = true;

  autoUpdater.on('update-available', async (info) => {
    const { response: resposta } = await dialog.showMessageBox(win, {
      type: 'info',
      title: 'Atualização disponível',
      message: `A versão (${info.version}) do Caravellus está disponível.`,
      detail:
        `Você está usando a versão (${app.getVersion()}).\r\n\r\n` +
        `O download acontece em segundo plano, e você pode continuar usando o sistema.\r\n` +
        `Os seus dados não serão apagados.`,
      buttons: ['Baixar agora', 'Depois'],
      defaultId: 0,
      cancelId: 1,
    });
    if (resposta === 0) {
      autoUpdater.downloadUpdate().catch(() => {});
    }
  });

  autoUpdater.on('download-progress', (progress) => {
    win.setProgressBar(progress.percent / 100);
  });
  
  autoUpdater.on('update-downloaded', async (info) => {
    win.setProgressBar(-1);
    const { response } = await dialog.showMessageBox(win, {
      type: 'info',
      title: 'Atualização pronta',
      message: `A versão ${info.version} foi baixada.`,
      detail: 'Ela será instalada quando você fechar o Caravellus. '
        + 'Se preferir, instale agora: o sistema fecha e abre de novo já atualizado.',
      buttons: ['Instalar ao fechar', 'Reiniciar e instalar agora'],
      defaultId: 0,
      cancelId: 0,
    });
    if (response === 1) {
      await pararBackend();
      autoUpdater.quitAndInstall(false, true);
    }
  });

  autoUpdater.on('error', (erro) => {
    win.setProgressBar(-1);
    console.error('Atualização:', erro?.message ?? erro);
  });

  autoUpdater.checkForUpdates().catch(() => {});
}

module.exports = {
  verificarAtualizacao,
};