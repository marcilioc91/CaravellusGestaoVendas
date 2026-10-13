import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../services/auth.service';
import { TemaToggle } from '../../shared/tema-toggle/tema-toggle';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatIconModule, TemaToggle],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  nomeUsuario: string = '';
  isAdmin: boolean = false;

  constructor(private router: Router, private auth: AuthService) {
    const usuario = this.auth.getUsuarioLogado();
    this.nomeUsuario = usuario?.pessoa?.nome ?? usuario?.usuarioLogin ?? '';
    this.isAdmin = this.auth.isAdmin();
  }

  irParaCadastroCliente() {
    this.router.navigate(['/clientes']);
  }

  irParaCadastroProduto() {
    this.router.navigate(['/produto-form']);
  }

  irParaCadastroCategoria() {
    this.router.navigate(['/categorias']);
  }

  irParaNovaVenda() {
    this.router.navigate(['/vendas']);
  }

  irParaHistoricoVendas() {
    this.router.navigate(['/historico-vendas']);
  }

  irParaRelatorioInventario() {
    this.router.navigate(['/relatorio-inventario']);
  }

  irParaAuditoria() {
    this.router.navigate(['/auditoria']);
  }

  irParaGerenciarUsuarios() {
    this.router.navigate(['/gerenciar-usuarios']);
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
