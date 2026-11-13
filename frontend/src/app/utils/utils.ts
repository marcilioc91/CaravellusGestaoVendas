import { Produto } from '../models/produto';

export function formatarTelefone(tel?: string): string {
  if (!tel) return '—';

  const v = String(tel).replace(/\D/g, '');

  if (v.length === 11) {
    return `(${v.slice(0, 2)}) ${v.slice(2, 7)}-${v.slice(7)}`;
  }

  if (v.length === 10) {
    return `(${v.slice(0, 2)}) ${v.slice(2, 6)}-${v.slice(6)}`;
  }

  return '—';
}

export function cpfValido(cpf: string): boolean {
  const n = cpf.replace(/\D/g, '');
  if (n.length !== 11 || /^(\d)\1+$/.test(n)) return false;
  let soma = 0;
  for (let i = 0; i < 9; i++) soma += +n[i] * (10 - i);
  let d1 = 11 - (soma % 11);
  if (d1 >= 10) d1 = 0;
  if (d1 !== +n[9]) return false;
  soma = 0;
  for (let i = 0; i < 10; i++) soma += +n[i] * (11 - i);
  let d2 = 11 - (soma % 11);
  if (d2 >= 10) d2 = 0;
  return d2 === +n[10];
}

export function emailValido(email: string): boolean {
  return /^[\w+.%-]+@[\w.-]+\.[a-zA-Z]{2,}$/.test(email);
}

/** Tamanho mínimo de toda senha nova; o backend aplica a mesma regra (ValidacaoUtil.SENHA_TAMANHO_MINIMO) */
export const SENHA_TAMANHO_MINIMO = 6;

export function senhaValida(senha?: string | null): boolean {
  return !!senha?.trim() && senha.length >= SENHA_TAMANHO_MINIMO;
}

/** Formato padrão das imagens de produto (4:3, o mesmo dos cartões do catálogo) */
export const IMAGEM_PRODUTO_LARGURA = 800;
export const IMAGEM_PRODUTO_ALTURA = 600;

/**
 * Converte qualquer imagem para o tamanho padrão: preenche todo o quadro e corta o que sobrar,
 * mantendo o centro; grava em JPEG (fundo branco onde era transparente)
 */
export async function reduzirImagem(
  arquivo: File,
  largura = IMAGEM_PRODUTO_LARGURA,
  altura = IMAGEM_PRODUTO_ALTURA
): Promise<Blob> {
  const bitmap = await createImageBitmap(arquivo);
  const escala = Math.max(largura / bitmap.width, altura / bitmap.height);
  const larguraFinal = bitmap.width * escala;
  const alturaFinal = bitmap.height * escala;
  const canvas = document.createElement('canvas');
  canvas.width = largura;
  canvas.height = altura;
  const ctx = canvas.getContext('2d')!;
  ctx.fillStyle = '#fff';
  ctx.fillRect(0, 0, largura, altura);
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(bitmap, (largura - larguraFinal) / 2, (altura - alturaFinal) / 2, larguraFinal, alturaFinal);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob(b => (b ? resolve(b) : reject(new Error('Falha ao converter a imagem.'))), 'image/jpeg', 0.85)
  );
}

export function normalizarTexto(texto?: string | null): string {
  return (texto ?? '').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase().trim();
}

export interface OpcoesImpressao {
  titulo?: string;
  antes?: () => void;
  depois?: () => void;
}

export function imprimirPagina(opcoes: OpcoesImpressao = {}): void {
    const tituloAnterior = document.title;
    if (opcoes.titulo)
      document.title = nomeArquivoValido(opcoes.titulo);
    try {
      opcoes.antes?.();
      window.print();
    } finally {
      opcoes.depois?.();
      document.title = tituloAnterior;
    }
  }

/** Funcoes expostas pelo preload.js (so existem dentro do Electron) */
export interface PonteElectron {
  escolherDestinoPdf(nomeSugerido: string): Promise<boolean>;
  gerarPdf(): Promise<string>;
  abrirUltimoPdf(): Promise<string>;

  verificarAtualizacao(): Promise<void>;
  baixarAtualizacao(): Promise<void>;
  instalarAtualizacaoAgora(): Promise<void>;
  /** Os "ao..." devolvem a funcao que remove o ouvinte */
  aoAtualizacaoDisponivel(callback: (dados: { nova: string; atual: string }) => void): () => void;
  aoProgressoAtualizacao(callback: (percentual: number) => void): () => void;
  aoAtualizacaoBaixada(callback: (dados: { nova: string }) => void): () => void;
  aoErroAtualizacao(callback: () => void): () => void;
}

declare global {
  interface Window {
    caravellus?: PonteElectron;
  }
}

export type ResultadoPdf =
  | { status: 'salvo'; caminho: string }
  | { status: 'cancelado' }
  | { status: 'navegador' };

export interface OpcoesExportacao extends OpcoesImpressao {
  titulo: string;
  /** Chamado depois que o usuario escolhe onde salvar: hora de mostrar a barra */
  aoIniciar?: () => void;
}

/**
 * Exporta a tela em PDF. No Electron: "Salvar como" + geracao com progresso.
 * No navegador (ng serve): cai no dialogo de impressao, como antes.
 */
export async function salvarPdf(opcoes: OpcoesExportacao): Promise<ResultadoPdf> {
  const ponte = window.caravellus;
  if (!ponte) {
    imprimirPagina(opcoes);
    return { status: 'navegador' };
  }

  if (!(await ponte.escolherDestinoPdf(nomeArquivoValido(opcoes.titulo))))
    return { status: 'cancelado' };

  opcoes.aoIniciar?.();
  try {
    opcoes.antes?.();
    await esperarPintura(); // a barra e o bloco de impressao precisam estar na tela
    const caminho = await ponte.gerarPdf();
    return { status: 'salvo', caminho };
  } finally {
    opcoes.depois?.();
  }
}

export function abrirUltimoPdf(): void {
  window.caravellus?.abrirUltimoPdf();
}

/** Espera o navegador desenhar a tela (dois quadros) */
function esperarPintura(): Promise<void> {
  return new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
}

function nomeArquivoValido(nome: string): string {
  return nome.replace(/[\\/:*?"<>|]/g, '-').replace(/\s+/g, ' ').trim();
}

/** Produtos de uma categoria (ou "Todas" / "Sem categoria") */
export interface GrupoCategoria {
  nome: string;
  produtos: Produto[];
}

/**
 * Agrupa por categoria, em ordem alfabética, com "Sem categoria" no fim.
 * O primeiro grupo é sempre "Todas" (todos os produtos), usado como opção padrão do filtro.
 * Usado nas telas de vendas e de produtos.
 */
export function agruparPorCategoria(produtos: Produto[]): GrupoCategoria[] {
  const mapa = new Map<number, GrupoCategoria>();
  const semCategoria: Produto[] = [];
  for (const p of produtos) {
    const cat = p.categoria;
    if (!cat?.id) {
      semCategoria.push(p);
      continue;
    }
    if (!mapa.has(cat.id)) mapa.set(cat.id, { nome: cat.nome, produtos: [] });
    mapa.get(cat.id)!.produtos.push(p);
  }
  const grupos = [...mapa.values()].sort((a, b) => a.nome.localeCompare(b.nome));
  if (semCategoria.length > 0) grupos.push({ nome: 'Sem categoria', produtos: semCategoria });
  return [{ nome: 'Todas', produtos: grupos.flatMap(g => g.produtos) }, ...grupos];
}

/** "Todas": uma seção por categoria; categoria escolhida no filtro: só a seção dela */
export function secoesDoFiltro(grupos: GrupoCategoria[], selecionado: GrupoCategoria | null): GrupoCategoria[] {
  if (!selecionado) return [];
  return selecionado === grupos[0] ? grupos.slice(1) : [selecionado];
}
