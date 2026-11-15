import { Component, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../services/auth.service';
import { TemaToggle } from '../../shared/tema-toggle/tema-toggle';
import { RodapeSistema } from '../../shared/rodape-sistema/rodape-sistema';
import { Logo } from '../../shared/logo/logo';
import { ehAtalhoAlt } from '../../shared/atalho-teclado';

interface Operacao {
  icone: string;
  titulo: string;
  descricao: string;
  rota: string;
  somenteAdmin?: boolean;
  /** Letra do atalho Alt+letra nesta tela */
  atalho?: string;
}

interface Secao {
  titulo: string;
  operacoes: Operacao[];
}

const NOVA_VENDA: Operacao = {
  icone: 'point_of_sale', titulo: 'Nova Venda', descricao: 'Registrar uma venda para um cliente', rota: '/vendas',
  atalho: 'N',
};
const HISTORICO: Operacao = {
  icone: 'receipt_long', titulo: 'Histórico de Vendas', descricao: 'Pagamentos, pendências e pedidos', rota: '/historico-vendas',
  atalho: 'H',
};
const CLIENTES: Operacao = {
  icone: 'person_add', titulo: 'Clientes', descricao: 'Gerenciar clientes e usuários', rota: '/clientes',
};
const PRODUTOS: Operacao = {
  icone: 'inventory_2', titulo: 'Produtos', descricao: 'Gerenciar produtos e estoque', rota: '/produto-form',
};
const CATEGORIAS: Operacao = {
  icone: 'category', titulo: 'Categorias', descricao: 'Agrupar produtos por categoria', rota: '/categorias',
};
const DESEMPENHO: Operacao = {
  icone: 'bar_chart', titulo: 'Desempenho de Vendas', descricao: 'Desempenho de vendas: mais vendidos, receita, custo e lucro', rota: '/relatorio-inventario',
  somenteAdmin: true,
};
const AUDITORIA: Operacao = {
  icone: 'manage_search', titulo: 'Auditoria', descricao: 'Histórico de operações por usuário', rota: '/auditoria',
};
const USUARIOS: Operacao = {
  icone: 'manage_accounts', titulo: 'Gerenciar Usuários', descricao: 'Perfis de acesso e senhas', rota: '/gerenciar-usuarios',
  somenteAdmin: true,
};
const EVENTOS: Operacao = {
  icone: 'event', titulo: 'Eventos', descricao: 'Acampamentos, conferências e outros eventos', rota: '/eventos',
  somenteAdmin: true,
};

const SECOES: Secao[] = [
  { titulo: 'Movimentações', operacoes: [NOVA_VENDA, HISTORICO] },
  { titulo: 'Cadastros', operacoes: [EVENTOS, CLIENTES, CATEGORIAS, PRODUTOS] },
  { titulo: 'Relatórios e Controle', operacoes: [DESEMPENHO, AUDITORIA] },
  { titulo: 'Administração', operacoes: [USUARIOS] },
];

/**
 * Tela inicial: menu lateral com todas as operações (agrupadas por seção) e, no painel,
 * as duas operações do caixa em destaque mais atalhos. Tudo cabe na janela, sem rolagem.
 * Operações de ADMIN só aparecem para ADMIN (o backend também bloqueia).
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TemaToggle, RodapeSistema, Logo],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  readonly nomeUsuario: string;
  readonly primeiroNome: string;
  /** Evento escolhido no login */
  readonly nomeEvento: string;
  readonly secoes: Secao[];
  readonly destaques = [NOVA_VENDA, HISTORICO];
  readonly atalhos: Operacao[];
  /** Animação de entrada: só logo após o login (não ao voltar de outra tela) */
  readonly entrada: boolean;

  constructor(private router: Router, private auth: AuthService, private dialog: MatDialog) {
    const usuario = this.auth.getUsuarioLogado();
    this.nomeUsuario = usuario?.pessoa?.nome ?? usuario?.usuarioLogin ?? '';
    this.primeiroNome = this.formatarPrimeiroNome(this.nomeUsuario);
    this.nomeEvento = this.auth.getEventoAtual()?.nome ?? '';

    const admin = this.auth.isAdmin();
    const permitida = (o: Operacao) => admin || !o.somenteAdmin;
    this.secoes = SECOES
      .map(s => ({ ...s, operacoes: s.operacoes.filter(permitida) }))
      .filter(s => s.operacoes.length > 0);
    this.atalhos = [CLIENTES, PRODUTOS, admin ? DESEMPENHO : CATEGORIAS, AUDITORIA];

    const reduzirMovimento = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    this.entrada = history.state?.aposLogin === true && !reduzirMovimento;
  }

  ir(operacao: Operacao) {
    this.router.navigate([operacao.rota]);
  }

  /** Dica do botão: "Alt+N" quando a operação tem atalho */
  dicaAtalho(operacao: Operacao): string {
    return operacao.atalho ? 'Alt+' + operacao.atalho : '';
  }

  /** Atalhos da tela: Alt+N = Nova Venda, Alt+H = Histórico de Vendas */
  @HostListener('document:keydown', ['$event'])
  aoTeclar(event: KeyboardEvent) {
    if (this.dialog.openDialogs.length > 0) return;
    const operacao = this.destaques.find(o => o.atalho && ehAtalhoAlt(event, o.atalho));
    if (!operacao) return;
    event.preventDefault();
    this.ir(operacao);
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/']);
  }

  /** "MARCILIO CABRAL" -> "Marcilio" */
  private formatarPrimeiroNome(nome: string): string {
    const primeiro = nome.trim().split(/\s+/)[0] ?? '';
    return primeiro ? primeiro.charAt(0).toUpperCase() + primeiro.slice(1).toLowerCase() : '';
  }
}
