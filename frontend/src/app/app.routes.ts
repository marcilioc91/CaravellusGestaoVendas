import { Routes } from '@angular/router';
import { adminGuard } from './guards/auth.guard';
import { loginGuard } from './guards/login.guard';
import { ativacaoGuard, licencaGuard } from './guards/licenca.guard';

export const routes: Routes = [
  {
    path: 'ativacao',
    canActivate: [ativacaoGuard],
    loadComponent: () =>
      import('./pages/ativacao/ativacao').then(m => m.Ativacao),
  },
  // Todas as demais telas exigem o sistema ativado (licença)
  {
    path: '',
    canActivateChild: [licencaGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/login/login').then(m => m.Login),
      },
      {
        path: 'home',
        canActivate: [loginGuard],
        loadComponent: () =>
          import('./pages/home/home').then(m => m.Home),
      },
      {
        path: 'clientes',
        canActivate: [loginGuard],
        loadComponent: () =>
          import('./pages/clientes/clientes').then(m => m.Clientes),
      },
      {
        path: 'produtos',
        canActivate: [loginGuard],
        loadComponent: () =>
          import('./pages/produtos/produtos').then(m => m.Produtos),
      },
      {
        path: 'produto-form',
        canActivate: [loginGuard],
        loadComponent: () =>
          import('./pages/produto-form/produto-form').then(m => m.ProdutoForm),
      },
      {
        path: 'categorias',
        canActivate: [loginGuard],
        loadComponent: () =>
          import('./pages/categorias/categorias').then(m => m.Categorias),
      },
      {
        path: 'vendas',
        canActivate: [loginGuard],
        loadComponent: () =>
          import('./pages/vendas/vendas').then(m => m.Vendas),
      },
      {
        path: 'historico-vendas',
        canActivate: [loginGuard],
        loadComponent: () =>
          import('./pages/historico-vendas/historico-vendas').then(m => m.HistoricoVendas),
      },
      {
        path: 'relatorio-inventario',
        canActivate: [loginGuard, adminGuard],
        loadComponent: () =>
          import('./pages/relatorio-inventario/relatorio-inventario').then(m => m.RelatorioInventario),
      },
      {
        path: 'auditoria',
        canActivate: [loginGuard],
        loadComponent: () =>
          import('./pages/auditoria/auditoria').then(m => m.Auditoria),
      },
      {
        path: 'eventos',
        canActivate: [loginGuard, adminGuard],
        loadComponent: () =>
          import('./pages/eventos/eventos').then(m => m.Eventos),
      },
      {
        path: 'gerenciar-usuarios',
        canActivate: [loginGuard, adminGuard],
        loadComponent: () =>
          import('./pages/gerenciar-usuarios/gerenciar-usuarios').then(m => m.GerenciarUsuarios),
      },
    ],
  },
];
