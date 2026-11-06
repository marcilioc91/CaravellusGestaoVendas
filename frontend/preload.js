// Ponte entre o Angular e o processo principal: expoe so o necessario para exportar PDF
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('caravellus', {
  escolherDestinoPdf: (nomeSugerido) => ipcRenderer.invoke('pdf:escolher-destino', nomeSugerido),
  gerarPdf: () => ipcRenderer.invoke('pdf:gerar'),
  abrirUltimoPdf: () => ipcRenderer.invoke('pdf:abrir-ultimo'),
});
