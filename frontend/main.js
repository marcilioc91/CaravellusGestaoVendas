const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const fs = require('fs');
const { verificarAtualizacao } = require('./atualizacao');

let javaProcess = null;

// Encerra o backend e espera o processo sair (no maximo 5 s).
// O instalador da atualizacao substitui app.jar e java.exe: com o Java vivo, os arquivos ficam travados.
function pararBackend() {
  return new Promise((resolve) => {
    if (!javaProcess || javaProcess.exitCode !== null) return resolve();
    const limite = setTimeout(resolve, 5000);
    javaProcess.once('exit', () => {
      clearTimeout(limite);
      resolve();
    });
    javaProcess.kill();
  });
}

function startBackend() {
  const resourcesPath = app.isPackaged 
    ? process.resourcesPath 
    : path.join(__dirname, 'resources');

  const jarPath = path.join(resourcesPath, 'app.jar');
  
  const javaExec = path.join(resourcesPath, 'jre-min', 'bin', 'java.exe');

  console.log(`Tentando iniciar backend em: ${jarPath}`);
  console.log(`Usando Java em: ${javaExec}`);

  if (fs.existsSync(jarPath) && fs.existsSync(javaExec)) {
    // --enable-native-access: o driver do Firebird embarcado (JNA) carrega DLLs nativas (evita aviso/bloqueio no Java 25+)
    javaProcess = spawn(javaExec, ['--enable-native-access=ALL-UNNAMED', '-jar', jarPath]);

    javaProcess.stdout.on('data', (data) => console.log(`Java: ${data}`));
    javaProcess.stderr.on('data', (data) => console.error(`Java Error: ${data}`));
  } else {
    console.error("ERRO: Arquivos de backend ou JRE não encontrados!");
    console.error("Caminho JAR:", jarPath);
    console.error("Caminho JRE:", javaExec);
  }
}

let destinoPdf = null; // escolhido na janela "Salvar como"
let ultimoPdf = null;  // ultimo PDF gerado (botao "Abrir" da mensagem)

function registrarExportacaoPdf() {
  ipcMain.handle('pdf:escolher-destino', async (event, nomeSugerido) => {
    const win = BrowserWindow.fromWebContents(event.sender);
    const { canceled, filePath } = await dialog.showSaveDialog(win, {
      title: 'Exportar PDF',
      defaultPath: path.join(app.getPath('documents'), `${nomeSugerido}.pdf`),
      filters: [{ name: 'PDF', extensions: ['pdf'] }],
    });
    destinoPdf = canceled ? null : filePath;
    return !!destinoPdf;
  });

  ipcMain.handle('pdf:gerar', async (event) => {
    if (!destinoPdf) throw new Error('Nenhum destino escolhido.');
    // Usa o CSS de @media print e o tamanho de @page, como no window.print()
    const dados = await event.sender.printToPDF({ preferCSSPageSize: true, printBackground: true });
    await fs.promises.writeFile(destinoPdf, dados);
    ultimoPdf = destinoPdf;
    destinoPdf = null;
    return ultimoPdf;
  });

  ipcMain.handle('pdf:abrir-ultimo', () => (ultimoPdf ? shell.openPath(ultimoPdf) : ''));
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1200,
    height: 800,
    icon: path.join(__dirname, 'src/assets/logoPC.ico'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js'),
    }
  });

  win.loadFile(path.join(__dirname, 'dist/caravellus-frontend/browser/index.html'));
  return win;
}

app.whenReady().then(() => {
  startBackend();
  registrarExportacaoPdf();
  const win = createWindow();

  // Espera a tela carregar para o alerta nao aparecer sobre uma janela em branco
  win.webContents.once('did-finish-load', () => verificarAtualizacao(win, pararBackend));
});

app.on('window-all-closed', async () => {
  await pararBackend(); // antes do quit: o instalador da atualizacao roda logo em seguida
  if (process.platform !== 'darwin') {
    app.quit();
  }
});