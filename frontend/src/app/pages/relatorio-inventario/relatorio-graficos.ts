import { Component, Input } from '@angular/core';
import { CurrencyPipe, DecimalPipe, NgTemplateOutlet, PercentPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { RelatorioInventarioItem } from '../../models/venda';

/** Uma barra de um gráfico de barras horizontais ranqueadas */
interface Barra {
  nome: string;
  valor: number;
  /** Comprimento da barra: 0 a 1 em relação à maior do gráfico */
  fracao: number;
  /** Texto na ponta da barra */
  rotulo: string;
  /** Linhas extras da dica (hover/foco) */
  detalhes: string[];
}

const MAXIMO_BARRAS = 10;

/**
 * Visão em gráficos do relatório de inventário. Recebe os mesmos itens da tabela (mesmo período),
 * então os números das duas visões sempre batem. Gráficos em HTML/CSS, sem biblioteca:
 * leves para computadores fracos e funcionam offline.
 */
@Component({
  selector: 'app-relatorio-graficos',
  standalone: true,
  imports: [MatIconModule, NgTemplateOutlet],
  providers: [CurrencyPipe, DecimalPipe, PercentPipe],
  templateUrl: './relatorio-graficos.html',
  styleUrl: './relatorio-graficos.css',
})
export class RelatorioGraficos {
  receita = 0;
  lucro = 0;
  margem = 0;
  itensVendidos = 0;
  produtosVendidos = 0;
  ticketPorItem = 0;

  maisVendidos: Barra[] = [];
  maisRentaveis: Barra[] = [];
  receitaPorCategoria: Barra[] = [];
  comPrejuizo: RelatorioInventarioItem[] = [];

  constructor(private moeda: CurrencyPipe, private numero: DecimalPipe, private pct: PercentPipe) {}

  @Input({ required: true }) set itens(itens: RelatorioInventarioItem[]) {
    this.calcular(itens ?? []);
  }

  get maisDeDez(): boolean {
    return this.produtosVendidos > MAXIMO_BARRAS;
  }

  real(valor: number): string {
    return this.moeda.transform(valor, 'BRL', 'symbol', '1.2-2') ?? '';
  }

  percentual(valor: number): string {
    return this.pct.transform(valor, '1.0-1') ?? '';
  }

  private calcular(itens: RelatorioInventarioItem[]) {
    this.receita = soma(itens, i => i.totalReceita);
    this.lucro = soma(itens, i => i.lucro);
    this.margem = this.receita > 0 ? this.lucro / this.receita : 0;
    this.itensVendidos = soma(itens, i => i.quantidadeVendida);
    this.produtosVendidos = itens.length;
    this.ticketPorItem = this.itensVendidos > 0 ? this.receita / this.itensVendidos : 0;

    const margemDe = (i: RelatorioInventarioItem) => (i.totalReceita > 0 ? i.lucro / i.totalReceita : 0);

    this.maisVendidos = ranquear(
      itens.filter(i => i.quantidadeVendida > 0),
      i => i.quantidadeVendida,
      i => ({
        nome: i.nomeProduto,
        rotulo: `${this.numero.transform(i.quantidadeVendida, '1.0-0')} un.`,
        detalhes: [`Receita ${this.real(i.totalReceita)}`, i.nomeCategoria ?? 'Sem categoria'],
      }),
    );

    this.maisRentaveis = ranquear(
      itens.filter(i => i.lucro > 0),
      i => i.lucro,
      i => ({
        nome: i.nomeProduto,
        rotulo: this.real(i.lucro),
        detalhes: [`Margem ${this.percentual(margemDe(i))}`, `Receita ${this.real(i.totalReceita)} · custo ${this.real(i.totalCusto)}`],
      }),
    );

    const porCategoria = new Map<string, { receita: number; lucro: number; quantidade: number }>();
    for (const i of itens) {
      const nome = i.nomeCategoria ?? 'Sem categoria';
      const atual = porCategoria.get(nome) ?? { receita: 0, lucro: 0, quantidade: 0 };
      atual.receita += i.totalReceita ?? 0;
      atual.lucro += i.lucro ?? 0;
      atual.quantidade += i.quantidadeVendida ?? 0;
      porCategoria.set(nome, atual);
    }
    this.receitaPorCategoria = ranquear(
      [...porCategoria.entries()].filter(([, c]) => c.receita > 0),
      ([, c]) => c.receita,
      ([nome, c]) => ({
        nome,
        rotulo: `${this.real(c.receita)} · ${this.percentual(this.receita > 0 ? c.receita / this.receita : 0)}`,
        detalhes: [`Lucro ${this.real(c.lucro)}`, `${this.numero.transform(c.quantidade, '1.0-0')} itens vendidos`],
      }),
      Infinity,
    );

    this.comPrejuizo = itens.filter(i => i.lucro < 0).sort((a, b) => a.lucro - b.lucro);
  }
}

function soma(itens: RelatorioInventarioItem[], campo: (i: RelatorioInventarioItem) => number): number {
  return itens.reduce((total, i) => total + (campo(i) ?? 0), 0);
}

/** Maiores primeiro, até `limite` barras; o comprimento é relativo à maior */
function ranquear<T>(
  lista: T[],
  valor: (x: T) => number,
  montar: (x: T) => Omit<Barra, 'valor' | 'fracao'>,
  limite = MAXIMO_BARRAS,
): Barra[] {
  const ordenada = [...lista].sort((a, b) => valor(b) - valor(a)).slice(0, limite);
  const maior = ordenada.length ? valor(ordenada[0]) : 0;
  return ordenada.map(x => ({ ...montar(x), valor: valor(x), fracao: maior > 0 ? valor(x) / maior : 0 }));
}
