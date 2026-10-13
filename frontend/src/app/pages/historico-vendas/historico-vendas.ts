import { ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import { MatFormFieldModule, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule, MatSelect, MatOption } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { VendaResposta } from '../../models/venda';
import { VendaService } from '../../services/venda.service';
import { ProdutoService } from '../../services/produto.service';
import { Periodo, PeriodoService } from '../../services/periodo.service';
import { PeriodoDialog } from '../../shared/periodo-dialog/periodo-dialog';

interface GrupoCliente {
  clienteId: number;
  nomeCliente: string;
  totalGasto: number;
  totalPendente: number;
  totalCredito: number;
  totalPago: number;
  vendas: VendaResposta[];
}

// ── Diálogo de edição de pagamento ────────────────────────────────────────────
@Component({
  selector: 'app-pagamento-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule],
  templateUrl: './pagamento-dialog.html',
})
export class PagamentoDialog {
  formaPagamento: string;
  valorPago: number;
  erro = '';

  formas = [
    { valor: 'DINHEIRO', label: 'Dinheiro' },
    { valor: 'PIX', label: 'PIX' },
    { valor: 'CARTAO_CREDITO', label: 'Cartão de Crédito' },
    { valor: 'CARTAO_DEBITO', label: 'Cartão de Débito' },
    { valor: 'VOUCHER', label: 'Voucher' },
    { valor: 'PENDENTE', label: 'Deixar Pendente' }
  ];

  constructor(
    private dialogRef: MatDialogRef<PagamentoDialog>,
    private vendaService: VendaService,
    private cdr: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) public data: { venda: VendaResposta; total: number }
  ) {
    this.formaPagamento = data.venda.formaPagamento ?? '';
    this.valorPago = data.venda.valorPago ?? 0;
  }

  salvar() {
    const valorFinal = this.formaPagamento === 'PENDENTE' ? 0 : this.valorPago;
    this.vendaService.atualizarPagamento(this.data.venda.id, {
      formaPagamento: this.formaPagamento,
      valorPago: valorFinal,
    }).subscribe({
      next: () => this.dialogRef.close({ formaPagamento: this.formaPagamento, valorPago: valorFinal }),
      error: () => { this.erro = 'Erro ao salvar pagamento.'; }
    });
    this.cdr.detectChanges();
  }

  cancelar() { this.dialogRef.close(null); }
}

// ── Diálogo de pagamento geral do cliente ────────────────────────────────────
@Component({
  selector: 'app-pagamento-total-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule],
  templateUrl: './pagamento-total-dialog.html',
})
export class PagamentoTotalDialog {
  formaPagamento = '';
  valorPago: number;
  erro = '';

  formas = [
    { valor: 'DINHEIRO', label: 'Dinheiro' },
    { valor: 'PIX', label: 'PIX' },
    { valor: 'CARTAO_CREDITO', label: 'Cartão de Crédito' },
    { valor: 'CARTAO_DEBITO', label: 'Cartão de Débito' },
    { valor: 'VOUCHER', label: 'Voucher' },
    { valor: 'PENDENTE', label: 'Deixar Pendente' },
  ];

  constructor(
    private dialogRef: MatDialogRef<PagamentoTotalDialog>,
    @Inject(MAT_DIALOG_DATA) public data: { nomeCliente: string; totalPendente: number }
  ) {
    this.valorPago = data.totalPendente;
  }

  confirmar() {
    const valorFinal = this.formaPagamento === 'PENDENTE' ? 0 : this.valorPago;
    this.dialogRef.close({ formaPagamento: this.formaPagamento, valorPago: valorFinal });
  }

  cancelar() { this.dialogRef.close(null); }
}

// ── Página de histórico ───────────────────────────────────────────────────────
@Component({
  selector: 'app-historico-vendas',
  imports: [
    CommonModule,
    DatePipe,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatExpansionModule,
    MatDialogModule,
    RouterLink,
    MatFormField,
    MatSelect,
    MatLabel,
    MatFormFieldModule,
    MatSelectModule,
    MatTooltipModule,
    MatAutocompleteModule,
    MatInputModule,
    FormsModule,
  ],
  templateUrl: './historico-vendas.html',
  styleUrl: './historico-vendas.css',
})
export class HistoricoVendas implements OnInit {
  grupos: GrupoCliente[] = [];
  carregando = true;
  colunasItens = ['produto', 'quantidade', 'precoUnitario', 'subtotal'];

  periodo: Periodo | null = null;
  filtroAno = 0;
  filtroStatus = '';
  filtroCliente = '';

  get periodoTexto(): string {
    return this.periodo
      ? `${PeriodoService.formatar(this.periodo.inicio)} a ${PeriodoService.formatar(this.periodo.fim)}`
      : '';
  }

  get anos(): { valor: number; label: string }[] {
    const set = new Set<number>();
    for (const g of this.grupos) {
      for (const v of g.vendas) {
        set.add(new Date(v.data_venda).getFullYear());
      }
    }
    const anoAtual = new Date().getFullYear();
    if (!set.has(anoAtual)) set.add(anoAtual);
    return [
      { valor: 0, label: 'Todos os anos' },
      ...Array.from(set).sort().map(a => ({ valor: a, label: String(a) })),
    ];
  }

  get clientesSugeridos(): string[] {
    const termo = this.filtroCliente.toLowerCase().trim();
    return this.grupos
      .map(g => g.nomeCliente)
      .filter(nome => !termo || nome.toLowerCase().includes(termo));
  }

  get gruposFiltrados(): GrupoCliente[] {
    return this.grupos
      .filter(grupo => {
        if (!this.filtroCliente.trim()) return true;
        return grupo.nomeCliente.toLowerCase().includes(this.filtroCliente.toLowerCase().trim());
      })
      .map(grupo => {
        let vendas = grupo.vendas;

        if (this.filtroAno) {
          vendas = vendas.filter(v => new Date(v.data_venda).getFullYear() === this.filtroAno);
        }

        if (!vendas.length) return null;

        let totalGasto = 0, totalPago = 0;

        for (const v of vendas) {
          const tot = this.totalVenda(v);
          const pago = v.valorPago ?? 0;

          totalGasto += tot;
          totalPago += pago;
        }
        const totalPendente = Math.max(0, totalGasto - totalPago);
        const totalCredito = Math.max(0, totalPago - totalGasto);

        if (this.filtroStatus === 'PENDENTE' && !(totalPago < totalGasto)) return null;
        if (this.filtroStatus === 'PAGO' && !(totalPago === totalGasto)) return null;
        if (this.filtroStatus === 'CREDITO' && totalCredito <= 0) return null;

        return {
          ...grupo,
          vendas: vendas,
          totalGasto,
          totalPago,
          totalPendente,
          totalCredito
        };
      })
      .filter((g): g is GrupoCliente => g !== null);
  }

  limparFiltros() {
    this.filtroAno = 0;
    this.filtroStatus = '';
    this.filtroCliente = '';
  }

  constructor(
    private vendaService: VendaService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef,
    private periodoService: PeriodoService,
    private router: Router
  ) { }

  ngOnInit() {
    this.abrirContexto(true);
  }

  /** Tela de contexto: pede o periodo antes de listar. Na abertura, cancelar volta para a tela inicial. */
  abrirContexto(abertura = false) {
    this.periodoService.obter('historico').subscribe(salvo => {
      const ref = this.dialog.open(PeriodoDialog, {
        width: '520px',
        disableClose: true,
        data: { titulo: 'Contexto do histórico de vendas', periodo: this.periodo ?? salvo },
      });
      ref.afterClosed().subscribe(escolhido => {
        if (!escolhido) {
          if (abertura) this.router.navigate(['/home']);
          return;
        }
        this.periodo = escolhido;
        this.periodoService.salvar('historico', escolhido).subscribe();
        this.carregar();
      });
    });
  }

  carregar() {
    if (!this.periodo) return;
    this.carregando = true;
    this.cdr.detectChanges();
    this.vendaService.listarTodas(this.periodo.inicio, this.periodo.fim).subscribe({
      next: vendas => {
        this.grupos = this.agruparPorCliente(vendas);
        this.carregando = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.grupos = [];
        this.carregando = false;
        this.cdr.detectChanges();
      }
    });
  }

  private agruparPorCliente(vendas: VendaResposta[]): GrupoCliente[] {
    const mapa = new Map<number, GrupoCliente>();
    for (const venda of vendas) {
      const id = venda.cliente.id;
      if (!mapa.has(id)) {
        mapa.set(id, {
          clienteId: id,
          nomeCliente: venda.cliente.pessoa.nome,
          totalGasto: 0,
          totalPendente: 0,
          totalCredito: 0,
          totalPago: 0,

          vendas: [],
        });
      }
      const grupo = mapa.get(id)!;
      grupo.vendas.push(venda);
      grupo.totalGasto += this.totalVenda(venda);
      grupo.totalPago += venda.valorPago ?? 0;
      if (venda.formaPagamento === 'PENDENTE' || (venda.valorPago ?? 0) < this.totalVenda(venda)) {
        grupo.totalPendente += (this.totalVenda(venda) - (venda.valorPago ?? 0));
      }
    }

    for (const grupo of mapa.values()) {
      if (grupo.totalPago > grupo.totalGasto) {
        grupo.totalCredito = Math.max(0, grupo.totalPago - grupo.totalGasto);
      }

    }

    return Array.from(mapa.values())
      .sort((a, b) => b.totalGasto - a.totalGasto);
  }

  totalVenda(venda: VendaResposta): number {
    return venda.itens?.reduce((acc, i) => acc + i.quantidade * +i.precoUnitario, 0) ?? 0;
  }

  labelPagamento(forma: string): string {
    const mapa: Record<string, string> = {
      DINHEIRO: 'Dinheiro',
      PIX: 'PIX',
      CARTAO_CREDITO: 'Cartão de Crédito',
      CARTAO_DEBITO: 'Cartão de Débito',
      VOUCHER: 'Voucher',
      PENDENTE: 'Pendente'
    };
    return mapa[forma] ?? forma ?? '—';
  }

  abrirPagamentoTotal(grupo: GrupoCliente) {
    const ref = this.dialog.open(PagamentoTotalDialog, {
      width: '420px',
      data: { nomeCliente: grupo.nomeCliente, totalPendente: grupo.totalPendente }
    });
    ref.afterClosed().subscribe((resultado: { formaPagamento: string; valorPago: number } | null) => {
      if (!resultado) return;

      const { formaPagamento, valorPago } = resultado;
      const pendentes = [...grupo.vendas]
        .filter(v => (v.valorPago ?? 0) < this.totalVenda(v))
        .sort((a, b) => a.id - b.id);

      if (!pendentes.length) return;

      // Distribui o valor pago nas vendas pendentes (mais antigas primeiro)
      let restante = valorPago;
      const atualizacoes: { id: number; valorPago: number }[] = [];

      for (const v of pendentes) {
        if (restante <= 0) break;
        const jaAdiantado = v.valorPago ?? 0;
        const faltava = this.totalVenda(v) - jaAdiantado;
        const aplicar = Math.min(restante, faltava);
        atualizacoes.push({ id: v.id, valorPago: jaAdiantado + aplicar });
        restante -= aplicar;
      }

      // Se sobrou valor (pagamento a mais), aplica como crédito na última venda atualizada
      if (restante > 0 && atualizacoes.length > 0) {
        atualizacoes[atualizacoes.length - 1].valorPago += restante;
      }

      forkJoin(
        atualizacoes.map(a => this.vendaService.atualizarPagamento(a.id, { formaPagamento, valorPago: a.valorPago }))
      ).subscribe({
        next: () => {
          this.snackBar.open('Pagamento registrado!', 'Fechar', { duration: 3000 });
          this.carregar();
        },
        error: () => this.snackBar.open('Erro ao registrar pagamento.', 'Fechar', { duration: 3000 })
      });
    });
  }

  // No historico-vendas.ts, dentro da classe HistoricoVendas

  abrirEdicaoItens(venda: VendaResposta) {
    const ref = this.dialog.open(EdicaoItensDialog, {
      width: '600px',
      data: { venda }
    });

    ref.afterClosed().subscribe(confirmado => {
      if (confirmado) {
        this.snackBar.open('Itens da venda atualizados!', 'Fechar', { duration: 3000 });
        this.carregar(); // Recarrega a lista para atualizar totais e saldos
      }
    });
  }
}

// ── Diálogo de edição de itens da venda ──────────────────────────────────────
@Component({
  selector: 'app-edicao-itens-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatTableModule,
    MatIconModule,
    MatDialogContent,
    MatDialogActions,
    MatOption,
    MatAutocompleteModule
],
  templateUrl: './editar-pedido-dialog.html',
  styleUrl: './historico-vendas.css',
})
export class EdicaoItensDialog implements OnInit {
  selecionarProduto(item: any, produtoSelecionado: any) {
    item.produto.id = produtoSelecionado.id;
    item.produto.nome = produtoSelecionado.nome;
    item.precoUnitario = produtoSelecionado.preco;
  }
  produtosFiltrados: any;

  filtrarProdutos(item: any) {
    const termo = item.produto.nome.toLowerCase();
    this.produtosFiltrados = this.todosProdutos.filter(p =>
      p.nome.toLowerCase().includes(termo)
    );
  }
  itensEdicao: any[];
  todosProdutos: any[] = [];

  constructor(
    private dialogRef: MatDialogRef<EdicaoItensDialog>,
    private vendaService: VendaService,
    private produtoService: ProdutoService,
    private cdr: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) public data: { venda: VendaResposta }
  ) {
    // Criamos uma cópia profunda para não alterar a tela de fundo antes de salvar
    this.itensEdicao = data.venda.itens.map(i => ({ ...i }));
  }
  ngOnInit(): void {
    this.produtoService.listar().subscribe({
      next: produtos => {
        this.todosProdutos = produtos;
      }
    });
  }

  remover(item: any) {
    console.log('Removendo item:', item);

    this.itensEdicao = this.itensEdicao.filter(i => {
      const idParaRemover = item.produto?.id ?? item.produtoId;
      const idAtual = i.produto?.id ?? i.produtoId;
      return idAtual !== idParaRemover;
    });
    this.cdr.detectChanges();
  }

  calcularNovoTotal() {
    return this.itensEdicao.reduce((acc, i) => acc + (i.quantidade * i.precoUnitario), 0);
  }

  salvar() {
    // Aqui chamamos o serviço para atualizar os itens
    const payload = {
      produtos: this.itensEdicao.map(i => ({
        produtoId: i.produto.id,
        quantidade: i.quantidade
      }))
    };

    console.log('Payload para atualização:', payload);

    this.vendaService.atualizarItens(this.data.venda.id, payload).subscribe({
      next: () => this.dialogRef.close(true),
      error: (err) => alert('Erro ao atualizar itens da venda.' + err)
    });
  }

  cancelar() { this.dialogRef.close(null); }
}