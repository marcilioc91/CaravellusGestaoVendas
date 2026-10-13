import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Title } from '@angular/platform-browser';
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

  constructor(
    private vendaService: VendaService,
    private periodoService: PeriodoService,
    private dialog: MatDialog,
    private title: Title,
    private router: Router,
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

  imprimir() {
    window.print();
  }

  /** Mesmo caminho da impressão: no diálogo, escolher "Salvar como PDF". */
  exportarPdf() {
    const anterior = this.title.getTitle();
    this.title.setTitle(`Relatorio de Inventario ${this.periodoTexto.replace(/\//g, '-')}`);
    window.print();
    this.title.setTitle(anterior);
  }

  private carregar() {
    this.carregando = true;
    this.cdRef.detectChanges();
    this.vendaService.getRelatorioInventario(this.periodo!.inicio, this.periodo!.fim).subscribe({
      next: (dados) => {
        this.itens = dados;

        this.totalReceita = this.itens.reduce((s, i) => s + (i.totalReceita ?? 0), 0);
        this.totalCusto = this.itens.reduce((s, i) => s + (i.totalCusto ?? 0), 0);
        this.totalLucro = this.itens.reduce((s, i) => s + (i.lucro ?? 0), 0);

        this.carregando = false;
        this.cdRef.detectChanges();
      },
      error: (err) => {
        console.error('Erro ao carregar relatório de inventário:', err);
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
