// Atualiza a versao do Caravellus em todos os arquivos que a guardam.
// Uso (na raiz do projeto): node ferramentas/atualizar-versao.js 1.4.1
//
// Arquivos alterados:
//   frontend/package.json       -> "version" (o rodape e o nome do instalador leem daqui)
//   frontend/package-lock.json  -> "version" do pacote raiz (2 ocorrencias)
//   README.md / README.pt-BR.md -> linha "Current version" / "Versao atual"
//
// Edita so as linhas da versao, por expressao regular: mantem formatacao, CRLF e UTF-8 sem BOM.
// Recusa versao menor que a atual (o electron-updater nunca ofereceria a atualizacao).

const fs = require('fs');
const path = require('path');

const RAIZ = path.resolve(__dirname, '..');
const SEMVER = /^(\d+)\.(\d+)\.(\d+)$/;

function falhar(mensagem) {
  console.error(`ERRO: ${mensagem}`);
  process.exit(1);
}

function comparar(a, b) {
  const pa = a.match(SEMVER).slice(1).map(Number);
  const pb = b.match(SEMVER).slice(1).map(Number);
  for (let i = 0; i < 3; i++) {
    if (pa[i] !== pb[i]) return pa[i] - pb[i];
  }
  return 0;
}

const nova = (process.argv[2] || '').trim().replace(/^v/i, '');
if (!SEMVER.test(nova)) {
  falhar(`versao invalida "${process.argv[2] ?? ''}". Use o formato X.Y.Z, por exemplo 1.4.1.`);
}

// Cada alvo: arquivo, regex com o valor atual no grupo 2, e quantas ocorrencias devem existir
const ALVOS = [
  {
    arquivo: 'frontend/package.json',
    regex: /("name": "caravellus-frontend",\r?\n\s*"version": ")([^"]+)(")/g,
    ocorrencias: 1,
  },
  {
    arquivo: 'frontend/package-lock.json',
    regex: /("name": "caravellus-frontend",\r?\n\s*"version": ")([^"]+)(")/g,
    ocorrencias: 2,
  },
  {
    arquivo: 'README.md',
    regex: /^(Current version: \*\*)([^*]+)(\*\*)/gm,
    ocorrencias: 1,
  },
  {
    arquivo: 'README.pt-BR.md',
    regex: /^(Versão atual: \*\*)([^*]+)(\*\*)/gm,
    ocorrencias: 1,
  },
];

// 1. Le tudo e valida antes de gravar qualquer arquivo
const atual = JSON.parse(fs.readFileSync(path.join(RAIZ, 'frontend/package.json'), 'utf8')).version;
if (SEMVER.test(atual) && comparar(nova, atual) < 0) {
  falhar(`a versao ${nova} e menor que a atual (${atual}).`);
}

const alteracoes = ALVOS.map((alvo) => {
  const caminho = path.join(RAIZ, alvo.arquivo);
  const texto = fs.readFileSync(caminho, 'utf8');
  const encontradas = [...texto.matchAll(alvo.regex)].length;
  if (encontradas !== alvo.ocorrencias) {
    falhar(`${alvo.arquivo}: esperava ${alvo.ocorrencias} ocorrencia(s) da versao, achei ${encontradas}.`);
  }
  const novoTexto = texto.replace(alvo.regex, (_, antes, _valor, depois) => antes + nova + depois);
  return { caminho, arquivo: alvo.arquivo, texto, novoTexto };
});

// 2. Grava so os que mudaram
for (const a of alteracoes) {
  if (a.novoTexto === a.texto) continue;
  fs.writeFileSync(a.caminho, a.novoTexto, 'utf8');
  console.log(`  ${a.arquivo}`);
}

console.log(atual === nova ? `Versao mantida: ${nova}` : `Versao atualizada: ${atual} -> ${nova}`);
