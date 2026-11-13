import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { EventoService } from '../../services/evento.service';
import { Evento } from '../../models/evento';
import { catchError, forkJoin, of } from 'rxjs';
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
import { RodapeSistema } from '../../shared/rodape-sistema/rodape-sistema';

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
    TemaToggle,
    RodapeSistema
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
  styleUrls: ['../../shared/fundo-marca/fundo-marca.css', './login.css'],
})
export class Login implements OnInit {
  conta = {
    login: '',
    senha: '',
    evento: ''
  }

  /** Eventos ativos, para escolher no login */
  eventos: Evento[] = [];
  erroEventos = '';

  constructor(
    private auth: AuthService,
    private eventoService: EventoService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) { }

  /** Abre com o último usuário e o último evento usados neste computador (o backend grava a cada login) */
  ngOnInit() {
    forkJoin({
      eventos: this.eventoService.listarAtivos(),
      ultimo: this.auth.ultimoAcesso().pipe(catchError(() => of({ usuario: '', evento: '' }))),
    }).subscribe({
      next: ({ eventos, ultimo }) => {
        this.eventos = eventos;
        this.erroEventos = eventos.length === 0 ? 'Nenhum evento ativo.' : '';
        if (!this.conta.login) {
          this.conta.login = ultimo.usuario;
        }
        if (eventos.some(e => e.codigo === ultimo.evento)) {
          this.conta.evento = ultimo.evento;
        } else if (eventos.length === 1) {
          this.conta.evento = eventos[0].codigo;
        }
        this.cdr.markForCheck();
      },
      error: () => {
        this.erroEventos = 'Não foi possível carregar os eventos.';
        this.cdr.markForCheck();
      }
    });
  }

  login() {
    if (!this.conta.login || !this.conta.senha || !this.conta.evento) {
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
        } else if (err.status === 400 && typeof err.error === 'string') {
          // Evento inexistente ou desativado depois que a tela abriu
          this.snackBar.open(err.error, 'Fechar', { duration: 4000 });
        } else {
          this.snackBar.open('Erro inesperado. Tente novamente.', 'Fechar', { duration: 3000 });
        }
      }
    });
  }

  private entrar(usuario: any) {
    this.auth.setUsuarioLogado(usuario);
    // aposLogin: liga a transição animada (app.config.ts) e a entrada da tela inicial (home.ts).
    // A saudação agora fica na própria tela inicial, por isso não há mais aviso de "login realizado".
    this.router.navigate(['/home'], { state: { aposLogin: true } });
  }

  // Primeiro acesso com senha padrão: só entra no sistema depois de definir uma nova senha
  private exigirTrocaSenha(usuario: any) {
    const dados: TrocarSenhaDados = {
      login: usuario.usuarioLogin,
      senhaAtual: this.conta.senha,
      nome: usuario.pessoa?.nome || usuario.usuarioLogin,
      evento: this.conta.evento,
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
    if (!this.conta.evento) {
      this.snackBar.open('Selecione o evento antes de se cadastrar.', 'Fechar', { duration: 3000 });
      return;
    }
    const dialogRef = this.dialog.open(CadastroModal, {
      width: '400px',
      data: { modo: 'usuario', evento: this.conta.evento }
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
