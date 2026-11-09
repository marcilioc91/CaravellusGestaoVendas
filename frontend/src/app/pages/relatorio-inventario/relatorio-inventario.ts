import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { VendaService } from '../../services/venda.service';
import { RelatorioInventarioItem } from '../../models/venda';
import { Periodo, PeriodoService } from '../../services/periodo.service';
import { PeriodoDialog } from '../../shared/periodo-dialog/periodo-dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { abrirUltimoPdf, imprimirPagina, salvarPdf } from '../../utils/utils';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { RelatorioGraficos } from './relatorio-graficos';

type Visao = 'tabela' | 'graficos';
const CHAVE_VISAO = 'caravellus.relatorio.visao';

@Component({
  selector: 'app-relatorio-inventario',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatCardModule,
    MatDialogModule,
    MatProgressBarModule,
    MatButtonToggleModule,
    RelatorioGraficos,
  ],
  templateUrl: './relatorio-inventario.html',
  styleUrl: './relatorio-inventario.css',
})
export class RelatorioInventario implements OnInit {
  itens: RelatorioInventarioItem[] = [];
  carregando = true;
  periodo: Periodo | null = null;
  colunas = ['nomeCategoria', 'nomeProduto', 'quantidadeVendida', 'totalReceita', 'totalCusto', 'lucro'];
  totalReceita = 0;
  totalCusto = 0;
  totalLucro = 0;
  exportandoPdf = false;
  /** Tabela (relatório completo) ou gráficos; a escolha fica salva neste computador */
  visao: Visao = lerVisao();

  constructor(
    private vendaService: VendaService,
    private periodoService: PeriodoService,
    private dialog: MatDialog,
    private router: Router,
    private snackBar: MatSnackBar,
    private cdRef: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.abrirContexto(true);
  }

  get periodoTexto(): string {
    return this.periodo
      ? `${PeriodoService.formatar(this.periodo.inicio)} a ${PeriodoService.formatar(this.periodo.fim)}`
      : '';
  }

  /** Tela de contexto. Na abertura, cancelar volta para a tela inicial. */
  abrirContexto(abertura = false) {
    this.periodoService.obter('relatorio').subscribe(salvo => {
      const ref = this.dialog.open(PeriodoDialog, {
        width: '520px',
        disableClose: true,
        data: { titulo: 'Contexto do relatório', periodo: this.periodo ?? salvo },
      });
      ref.afterClosed().subscribe(escolhido => {
        if (!escolhido) {
          if (abertura) this.voltar();
          return;
        }
        this.periodo = escolhido;
        this.periodoService.salvar('relatorio', escolhido).subscribe();
        this.carregar();
      });
    });
  }

  trocarVisao(visao: Visao) {
    this.visao = visao;
    try {
      localStorage.setItem(CHAVE_VISAO, visao);
    } catch {
      // Sem acesso ao armazenamento local: a escolha vale só até fechar a tela
    }
  }

  imprimir() {
    imprimirPagina();
  }

  /** No Electron: "Salvar como" + barra de progresso + mensagem de sucesso */
  async exportarPdf() {
    try {
      const resultado = await salvarPdf({
        titulo: `Desempenho de Vendas ${this.periodoTexto}`,
        aoIniciar: () => {
          this.exportandoPdf = true;
          this.cdRef.detectChanges();
        },
        depois: () => {
          this.exportandoPdf = false;
          this.cdRef.detectChanges();
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

  private carregar() {
    this.carregando = true;
    this.cdRef.detectChanges();
    this.vendaService.getRelatorioInventario(this.periodo!.inicio, this.periodo!.fim).subscribe({
      next: (dados) => {
        // Mais vendidos primeiro; empate: ordem alfabética do produto
        this.itens = [...dados].sort((a, b) =>
          (b.quantidadeVendida ?? 0) - (a.quantidadeVendida ?? 0) || a.nomeProduto.localeCompare(b.nomeProduto));

        this.totalReceita = this.itens.reduce((s, i) => s + (i.totalReceita ?? 0), 0);
        this.totalCusto = this.itens.reduce((s, i) => s + (i.totalCusto ?? 0), 0);
        this.totalLucro = this.itens.reduce((s, i) => s + (i.lucro ?? 0), 0);

        this.carregando = false;
        this.cdRef.detectChanges();
      },
      error: (err) => {
        console.error('Erro ao carregar o desempenho de vendas:', err);
        this.itens = [];
        this.carregando = false;
        this.cdRef.detectChanges();
      },
    });
  }

  voltar() {
    this.router.navigate(['/home']);
  }
  trackByProduto(index: number, item: RelatorioInventarioItem) {
    return item.nomeProduto;
  }
}

function lerVisao(): Visao {
  try {
    return localStorage.getItem(CHAVE_VISAO) === 'graficos' ? 'graficos' : 'tabela';
  } catch {
    return 'tabela';
  }
}
