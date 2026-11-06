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

function nomeArquivoValido(nome: string): string {
  return nome.replace(/[\\/:*?"<>|]/g, '-').replace(/\s+/g, ' ').trim();
}

