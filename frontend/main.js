const { app, BrowserWindow } = require('electron');
const path = require('path');
const { spawn } = require('child_process');
const fs = require('fs');

let javaProcess = null;

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

function createWindow() {
  const win = new BrowserWindow({
    width: 1200,
    height: 800,
    icon: path.join(__dirname, 'src/assets/logoPC.ico'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
    }
  });

  win.loadFile(path.join(__dirname, 'dist/caravellus-frontend/browser/index.html'));
}

app.whenReady().then(() => {
  startBackend();
  createWindow();
});

app.on('window-all-closed', () => {
  if (javaProcess) {
    javaProcess.kill();
  }
  if (process.platform !== 'darwin') {
    app.quit();
  }
});