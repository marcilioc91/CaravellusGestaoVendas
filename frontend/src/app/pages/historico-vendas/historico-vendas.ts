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
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, forkJoin } from 'rxjs';
import { VendaResposta } from '../../models/venda';
import { VendaService } from '../../services/venda.service';
import { ProdutoService } from '../../services/produto.service';
import { Periodo, PeriodoService } from '../../services/periodo.service';
import { PeriodoDialog } from '../../shared/periodo-dialog/periodo-dialog';
import { abrirUltimoPdf, imprimirPagina, normalizarTexto, salvarPdf } from '../../utils/utils';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialog } from '../clientes/clientes';
import { ValorMonetarioDirective } from '../../shared/valor-monetario/valor-monetario.directive';

interface GrupoCliente {
  clienteId: number;
  nomeCliente: string;
  totalGasto: number;
  totalPendente: number;
  totalCredito: number;
  totalPago: number;
  vendas: VendaResposta[];
}

// ── Diálogo de pagamento geral do cliente ────────────────────────────────────
@Component({
  selector: 'app-pagamento-total-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule, ValorMonetarioDirective],
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
    MatProgressBarModule,
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
  imprimindo = false;
  exportandoPdf = false;
  isAdmin = false;

  get periodoTexto(): string {
    return this.periodo
      ? `${PeriodoService.formatar(this.periodo.inicio)} a ${PeriodoService.formatar(this.periodo.fim)}`
      : '';
  }

  // Calculados so quando os dados ou um filtro mudam (aplicarFiltros), nao a cada ciclo de tela
  anos: { valor: number; label: string }[] = [];
  clientesSugeridos: string[] = [];
  gruposFiltrados: GrupoCliente[] = [];

  private filtroCliente$ = new Subject<void>();
  private totais = new Map<number, number>();

  private calcularAnos() {
    const set = new Set<number>([new Date().getFullYear()]);
    for (const g of this.grupos) {
      for (const v of g.vendas) set.add(new Date(v.data_venda).getFullYear());
    }
    this.anos = [
      { valor: 0, label: 'Todos os anos' },
      ...Array.from(set).sort().map(a => ({ valor: a, label: String(a) })),
    ];
  }

  onFiltroClienteChange(valor: string) {
    this.filtroCliente = valor ?? '';
    this.filtroCliente$.next();
  }

  /** Recalcula a lista exibida; chamado so quando um filtro ou os dados mudam */
  aplicarFiltros() {
    const termo = normalizarTexto(this.filtroCliente);

    this.clientesSugeridos = this.grupos
      .map(g => g.nomeCliente)
      .filter(nome => !termo || normalizarTexto(nome).includes(termo));

    this.gruposFiltrados = this.grupos
      .filter(grupo => !termo || normalizarTexto(grupo.nomeCliente).includes(termo))
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
    this.aplicarFiltros();
  }

  /** No Electron: "Salvar como" + barra de progresso + mensagem de sucesso */
  async exportarPdf() {
    try {
      const resultado = await salvarPdf({
        titulo: this.tituloArquivo,
        aoIniciar: () => {
          this.exportandoPdf = true;
          this.cdr.detectChanges();
        },
        antes: () => {
          this.imprimindo = true;
          this.cdr.detectChanges();
        },
        depois: () => {
          this.imprimindo = false;
          this.exportandoPdf = false;
          this.cdr.detectChanges();
        },
      });

      if (resultado.status === 'salvo') {
        this.snackBar
          .open('PDF exportado com sucesso!', 'Abrir', { duration: 6000 })
          .onAction()
          .subscribe(() => abrirUltimoPdf());
      }
    } catch {
      this.snackBar.open('Erro ao exportar o PDF. Se o arquivo estiver aberto, feche-o e tente de novo.', 'Fechar', { duration: 5000 });
    }
  }

  imprimir() {
    this.imprimirComBloco();
  }

  /** O bloco de impressao so existe enquanto o dialogo esta aberto */
  private imprimirComBloco(titulo?: string) {
    imprimirPagina({
      titulo,
      antes: () => {
        this.imprimindo = true;
        this.cdr.detectChanges(); // o bloco precisa estar no DOM antes do window.print()
      },
      depois: () => {
        this.imprimindo = false;
      },
    });
  }

  private readonly rotulosStatus: Record<string, string> = { PENDENTE: 'Pendente', PAGO: 'Quitado', CREDITO: 'Crédito' };

  get filtrosTexto(): string {
    const partes = [this.periodoTexto];
    if (this.filtroCliente.trim()) partes.push(`Cliente: ${this.filtroCliente.trim()}`);
    if (this.filtroAno) partes.push(`Ano: ${this.filtroAno}`);
    if (this.filtroStatus) partes.push(`Status: ${this.rotulosStatus[this.filtroStatus]}`);
    return partes.join(' · ');
  }

  private get tituloArquivo(): string {
    const partes = ['Historico de Vendas', this.periodoTexto];
    if (this.filtroCliente.trim()) partes.push(this.filtroCliente.trim());
    if (this.filtroAno) partes.push(String(this.filtroAno));
    if (this.filtroStatus) partes.push(this.rotulosStatus[this.filtroStatus]);
    return partes.join(' ');
  }

  get totalGeral(): number {
    return this.gruposFiltrados.reduce((acc, g) => acc + g.totalGasto, 0);
  }

  constructor(
    private vendaService: VendaService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef,
    private periodoService: PeriodoService,
    private router: Router,
    private auth: AuthService
  ) { }

  ngOnInit() {
    // Espera o usuario parar de digitar antes de filtrar
    this.filtroCliente$.pipe(debounceTime(250)).subscribe(() => {
      this.aplicarFiltros();
      this.cdr.detectChanges();
    });
    this.isAdmin = this.auth.isAdmin();
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
        this.totais.clear(); // os itens podem ter mudado (edicao de pedido, novo preco)
        this.grupos = this.agruparPorCliente(vendas);
        this.calcularAnos();
        this.aplicarFiltros();
        this.carregando = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.grupos = [];
        this.aplicarFiltros();
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
      // Mais antiga primeiro; data_venda vem em ISO ("2026-11-06T14:30:00"), entao a ordem de texto = ordem de data
      grupo.vendas.sort((a, b) => a.data_venda.localeCompare(b.data_venda));
    }

    return Array.from(mapa.values())
      .sort((a, b) => b.totalGasto - a.totalGasto);
  }

  /** Total com cache: o reduce dos itens roda uma vez por venda, nao a cada ciclo */
  totalVenda(venda: VendaResposta): number {
    let total = this.totais.get(venda.id);
    if (total === undefined) {
      total = venda.itens?.reduce((acc, i) => acc + i.quantidade * +i.precoUnitario, 0) ?? 0;
      this.totais.set(venda.id, total);
    }
    return total;
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

  /** Exclui a venda (so admin); o backend devolve os itens ao estoque */
  excluirVenda(venda: VendaResposta) {
    const pago = venda.valorPago ?? 0;
    const avisoPagamento = pago > 0
      ? `<br><br><strong>Atenção:</strong> esta venda tem ${pago.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })} pagos, que deixarão de constar no histórico.`
      : '';

    const ref = this.dialog.open(ConfirmDialog, {
      width: '420px',
      data: {
        titulo: 'Excluir venda',
        mensagem: `Deseja excluir a venda #${venda.id} de "${venda.cliente.pessoa.nome}"? Os itens voltarão ao estoque.${avisoPagamento}`,
      },
      panelClass: 'confirm-dialog-panel',
    });

    ref.afterClosed().subscribe((confirmado: boolean) => {
      if (!confirmado) return;
      this.vendaService.excluir(venda.id).subscribe({
        next: () => {
          this.snackBar.open(`Venda #${venda.id} excluída. Itens devolvidos ao estoque.`, 'Fechar', { duration: 4000 });
          this.carregar();
        },
        error: (err) => {
          const mensagem = err.status === 403 ? 'Apenas administradores podem excluir vendas.' : 'Erro ao excluir venda.';
          this.snackBar.open(mensagem, 'Fechar', { duration: 4000 });
        },
      });
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
    const termo = normalizarTexto(item.produto.nome);
    this.produtosFiltrados = this.todosProdutos.filter(p =>
      normalizarTexto(p.nome).includes(termo)
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

    this.vendaService.atualizarItens(this.data.venda.id, payload).subscribe({
      next: () => this.dialogRef.close(true),
      error: (err) => alert(typeof err?.error === 'string' && err.error ? err.error : 'Erro ao atualizar itens da venda.')
    });
  }

  cancelar() { this.dialogRef.close(null); }
}