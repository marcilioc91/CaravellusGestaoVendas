import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { FormsModule } from '@angular/forms';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonModule } from '@angular/material/button';
import { Router } from "@angular/router";
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CadastroModal } from '../cadastro-modal/cadastro-modal';
import { RecuperarSenhaDialog } from './recuperar-senha-dialog';
import { TrocarSenhaDialog, TrocarSenhaDados } from './trocar-senha-dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpErrorResponse } from '@angular/common/http';
import { TemaToggle } from '../../shared/tema-toggle/tema-toggle';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatDialogModule,
    TemaToggle
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  conta = {
    login: '',
    senha: ''
  }

  constructor(
    private auth: AuthService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) { }

  login() {
    if (!this.conta.login || !this.conta.senha) {
      return;
    }
    this.auth.login(this.conta).subscribe({
      next: (usuario: any) => {
        if (usuario.trocarSenha) {
          this.exigirTrocaSenha(usuario);
          return;
        }
        this.entrar(usuario);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 0) {
          this.snackBar.open('Erro de conexão. Tente novamente.', 'Fechar', { duration: 3000 });
        } else if (err.status === 401) {
          this.snackBar.open('Login ou senha inválidos.', 'Fechar', { duration: 3000 });
        } else {
          this.snackBar.open('Erro inesperado. Tente novamente.', 'Fechar', { duration: 3000 });
        }
      }
    });
  }

  private entrar(usuario: any) {
    this.auth.setUsuarioLogado(usuario);
    this.router.navigate(['/home']);
    this.snackBar.open(`Login realizado com sucesso! Bem-vindo, ${usuario.pessoa?.nome || usuario.usuarioLogin}!`, 'Fechar', { duration: 3000 });
  }

  // Primeiro acesso com senha padrão: só entra no sistema depois de definir uma nova senha
  private exigirTrocaSenha(usuario: any) {
    const dados: TrocarSenhaDados = {
      login: usuario.usuarioLogin,
      senhaAtual: this.conta.senha,
      nome: usuario.pessoa?.nome || usuario.usuarioLogin,
    };
    const dialogRef = this.dialog.open(TrocarSenhaDialog, { width: '420px', disableClose: true, data: dados });

    dialogRef.afterClosed().subscribe(atualizado => {
      this.conta.senha = '';
      this.cdr.markForCheck();
      if (atualizado) {
        this.snackBar.open('Senha alterada com sucesso!', 'Fechar', { duration: 3000 });
        this.entrar(atualizado);
      } else {
        this.snackBar.open('É necessário trocar a senha para acessar o sistema.', 'Fechar', { duration: 4000 });
      }
    });
  }

  abrirCadastro() {
    const dialogRef = this.dialog.open(CadastroModal, {
      width: '400px',
      data: { modo: 'usuario' }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Cadastro realizado com sucesso!', 'Fechar', {
          duration: 3000,
        });
      }
    });
  }

  abrirRecuperacaoSenha() {
    const dialogRef = this.dialog.open(RecuperarSenhaDialog, { width: '420px' });

    dialogRef.afterClosed().subscribe(redefinida => {
      if (redefinida) {
        this.conta.senha = '';
        this.cdr.markForCheck();
        this.snackBar.open('Senha redefinida! Entre com a nova senha.', 'Fechar', { duration: 4000 });
      }
    });
  }

  hide = true;

  togglePassword() {
    this.hide = !this.hide;
  }
}
