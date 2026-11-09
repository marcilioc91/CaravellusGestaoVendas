import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { AuditoriaService } from '../../services/auditoria.service';
import { AuditoriaLog } from '../../models/auditoria';

interface UsuarioOpcao {
  id: number;
  nome: string;
}

@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
  ],
  templateUrl: './auditoria.html',
  styleUrl: './auditoria.css',
})
export class Auditoria implements OnInit {
  todosLogs: AuditoriaLog[] = [];
  logsFiltrados: AuditoriaLog[] = [];
  logsExibidos: AuditoriaLog[] = [];
  usuarios: UsuarioOpcao[] = [];
  usuarioSelecionado: number | null = null;
  dataInicio: string = '';
  dataFim: string = '';
  carregando = true;

  readonly itensPorPagina = 10;
  paginaAtual = 0;
  totalPaginas = 0;

  colunas = ['dataHora', 'usuarioNome', 'tipoOperacao', 'descricao'];

  constructor(
    private auditoriaService: AuditoriaService,
    private router: Router,
    private cdRef: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.auditoriaService.listar().subscribe({
      next: (dados) => {
        this.todosLogs = dados;
        this.logsFiltrados = dados;
        this.extrairUsuarios(dados);
        this.carregando = false;
        this.atualizarPagina();
        this.cdRef.detectChanges();
      },
      error: () => {
        this.carregando = false;
      },
    });
  }

  private extrairUsuarios(logs: AuditoriaLog[]) {
    const mapa = new Map<number, string>();
    logs.forEach((l) => {
      if (l.usuarioId && !mapa.has(l.usuarioId)) {
        mapa.set(l.usuarioId, l.usuarioNome);
      }
    });
    this.usuarios = Array.from(mapa.entries()).map(([id, nome]) => ({ id, nome }));
  }

  filtrar() {
    let resultado = [...this.todosLogs];

    if (this.usuarioSelecionado) {
      resultado = resultado.filter((l) => l.usuarioId === this.usuarioSelecionado);
    }

    if (this.dataInicio) {
      const inicio = new Date(this.dataInicio + 'T00:00:00');
      resultado = resultado.filter((l) => new Date(l.dataHora) >= inicio);
    }

    if (this.dataFim) {
      const fim = new Date(this.dataFim + 'T23:59:59');
      resultado = resultado.filter((l) => new Date(l.dataHora) <= fim);
    }

    this.logsFiltrados = resultado;
    this.paginaAtual = 0;
    this.atualizarPagina();
  }

  limparFiltro() {
    this.usuarioSelecionado = null;
    this.dataInicio = '';
    this.dataFim = '';
    this.logsFiltrados = this.todosLogs;
    this.paginaAtual = 0;
    this.atualizarPagina();
  }

  private atualizarPagina() {
    const inicio = this.paginaAtual * this.itensPorPagina;
    this.logsExibidos = this.logsFiltrados.slice(inicio, inicio + this.itensPorPagina);
    this.totalPaginas = Math.ceil(this.logsFiltrados.length / this.itensPorPagina);
    this.cdRef.detectChanges();
  }

  paginaAnterior() {
    if (this.paginaAtual > 0) {
      this.paginaAtual--;
      this.atualizarPagina();
    }
  }

  proximaPagina() {
    if (this.paginaAtual < this.totalPaginas - 1) {
      this.paginaAtual++;
      this.atualizarPagina();
    }
  }

  temFiltroAtivo(): boolean {
    return !!this.usuarioSelecionado || !!this.dataInicio || !!this.dataFim;
  }

  labelTipo(tipo: string): string {
    const mapa: Record<string, string> = {
      VENDA: 'Venda',
      EXCLUSAO_VENDA: 'Excl. Venda',
      ALTERACAO_PAGAMENTO_VENDA: 'Alt. Pagamento',
      ALTERACAO_ITENS_VENDA: 'Alt. Itens Venda',
      INCLUSAO_PRODUTO: 'Incl. Produto',
      ALTERACAO_PRODUTO: 'Alt. Produto',
      EXCLUSAO_PRODUTO: 'Excl. Produto',
      INCLUSAO_CLIENTE: 'Incl. Cliente',
      ALTERACAO_CLIENTE: 'Alt. Cliente',
      EXCLUSAO_CLIENTE: 'Excl. Cliente',
      INCLUSAO_CATEGORIA: 'Incl. Categoria',
      ALTERACAO_CATEGORIA: 'Alt. Categoria',
      EXCLUSAO_CATEGORIA: 'Excl. Categoria',
      SOLICITACAO_RECUPERACAO_SENHA: 'Pedido Rec. Senha',
      RECUPERACAO_SENHA: 'Rec. Senha',
      TROCA_SENHA: 'Troca Senha',
      PROMOCAO_CLIENTE_USUARIO: 'Cliente → Usuário',
      IMPORTACAO_CLIENTES: 'Import. Clientes',
    };
    return mapa[tipo] ?? tipo;
  }

  corTipo(tipo: string): string {
    if (tipo === 'VENDA') return 'venda';
    if (tipo.startsWith('INCLUSAO') || tipo === 'PROMOCAO_CLIENTE_USUARIO' || tipo === 'IMPORTACAO_CLIENTES') return 'inclusao';
    if (tipo.startsWith('ALTERACAO')) return 'alteracao';
    if (tipo.startsWith('EXCLUSAO')) return 'exclusao';
    return '';
  }

  voltar() {
    this.router.navigate(['/home']);
  }
}
