import { ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { AuthService } from '../../services/auth.service';
import { Usuario } from '../../models/usuario';
import { SENHA_TAMANHO_MINIMO, senhaValida } from '../../utils/utils';
import { ConfirmDialog } from '../clientes/clientes';

// ── Diálogo de reset de senha ─────────────────────────────────────────────────
@Component({
  selector: 'app-reset-senha-dialog',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatDialogModule, MatIconModule],
  templateUrl: './reset-senha-dialog.html',
  styleUrl: './gerenciar-usuarios.css',
})
export class ResetSenhaDialog {
  novaSenha = '';
  ocultarSenha = true;
  erro = '';
  senhaMinima = SENHA_TAMANHO_MINIMO;

  constructor(
    private dialogRef: MatDialogRef<ResetSenhaDialog>,
    @Inject(MAT_DIALOG_DATA) public data: { nomeUsuario: string }
  ) {}

  confirmar() {
    if (!this.novaSenha.trim()) {
      this.erro = 'Informe a nova senha.';
      return;
    }
    if (!senhaValida(this.novaSenha)) {
      this.erro = `A senha deve ter no mínimo ${SENHA_TAMANHO_MINIMO} caracteres.`;
      return;
    }
    this.dialogRef.close(this.novaSenha);
  }

  fechar() { this.dialogRef.close(); }
}

// ── Página de gerenciar usuários ──────────────────────────────────────────────
@Component({
  selector: 'app-gerenciar-usuarios',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDialogModule,
  ],
  templateUrl: './gerenciar-usuarios.html',
  styleUrl: './gerenciar-usuarios.css',
})
export class GerenciarUsuarios implements OnInit {
  usuarios: Usuario[] = [];
  carregando = true;
  adminId: number | null = null;
  colunas = ['nome', 'login', 'email', 'perfil', 'acao'];

  constructor(
    private auth: AuthService,
    private router: Router,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private cdRef: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.adminId = this.auth.getUsuarioLogado()?.id ?? null;
    this.auth.listarUsuarios().subscribe({
      next: (dados) => {
        this.usuarios = dados;
        this.carregando = false;
        this.cdRef.detectChanges();
      },
      error: () => {
        this.carregando = false;
      },
    });
  }

  alternarPerfil(usuario: Usuario) {
    const novoPerfil = usuario.perfil === 'ADMIN' ? 'OPERADOR' : 'ADMIN';
    const nome = usuario.pessoa?.nome ?? usuario.usuarioLogin;
    const ref = this.dialog.open(ConfirmDialog, {
      width: '380px',
      data: { titulo: 'Alterar perfil', mensagem: `Alterar o perfil de "${nome}" para ${novoPerfil}? A sessão dele será encerrada e ele precisará entrar de novo.` },
      panelClass: 'confirm-dialog-panel'
    });
    ref.afterClosed().subscribe((confirmado: boolean) => {
      if (!confirmado) return;
      this.auth.atualizarPerfil(usuario.id!, novoPerfil).subscribe({
        next: (atualizado) => {
          usuario.perfil = atualizado.perfil;
          this.cdRef.detectChanges();
        },
        error: () => this.snackBar.open('Erro ao alterar o perfil.', 'Fechar', { duration: 3000 }),
      });
    });
  }

  abrirResetSenha(usuario: Usuario) {
    const ref = this.dialog.open(ResetSenhaDialog, {
      width: '380px',
      data: { nomeUsuario: usuario.pessoa?.nome ?? usuario.usuarioLogin },
    });
    ref.afterClosed().subscribe((novaSenha: string | undefined) => {
      if (!novaSenha) return;
      this.auth.resetSenha(usuario.id!, novaSenha).subscribe({
        next: () => this.snackBar.open('Senha redefinida com sucesso!', 'Fechar', { duration: 3000 }),
        error: () => this.snackBar.open('Erro ao redefinir senha.', 'Fechar', { duration: 3000 }),
      });
    });
  }

  voltar() {
    this.router.navigate(['/home']);
  }
}
