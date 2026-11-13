// Ponte entre o Angular e o processo principal: expoe so o necessario (PDF e atualizacao)
const { contextBridge, ipcRenderer } = require('electron');

// Registra um ouvinte de evento do processo principal; devolve a funcao que remove o ouvinte
const ouvir = (canal) => (callback) => {
  const handler = (_evento, dados) => callback(dados);
  ipcRenderer.on(canal, handler);
  return () => ipcRenderer.removeListener(canal, handler);
};

contextBridge.exposeInMainWorld('caravellus', {
  escolherDestinoPdf: (nomeSugerido) => ipcRenderer.invoke('pdf:escolher-destino', nomeSugerido),
  gerarPdf: () => ipcRenderer.invoke('pdf:gerar'),
  abrirUltimoPdf: () => ipcRenderer.invoke('pdf:abrir-ultimo'),

  verificarAtualizacao: () => ipcRenderer.invoke('atualizacao:verificar'),
  baixarAtualizacao: () => ipcRenderer.invoke('atualizacao:baixar'),
  instalarAtualizacaoAgora: () => ipcRenderer.invoke('atualizacao:instalar-agora'),
  aoAtualizacaoDisponivel: ouvir('atualizacao:disponivel'),
  aoProgressoAtualizacao: ouvir('atualizacao:progresso'),
  aoAtualizacaoBaixada: ouvir('atualizacao:baixada'),
  aoErroAtualizacao: ouvir('atualizacao:erro'),
});
