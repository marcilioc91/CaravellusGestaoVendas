import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../services/auth.service';
import { SENHA_TAMANHO_MINIMO, senhaValida } from '../../utils/utils';

export interface TrocarSenhaDados {
  login: string;
  senhaAtual: string;
  nome: string;
}

// ── Diálogo de troca obrigatória de senha (primeiro acesso) ───────────────────
@Component({
  selector: 'app-trocar-senha-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  templateUrl: './trocar-senha-dialog.html',
  styleUrl: './recuperar-senha-dialog.css',
})
export class TrocarSenhaDialog {
  novaSenha = '';
  confirmacaoSenha = '';
  ocultarSenha = true;
  enviando = false;
  erro = '';
  senhaValida = senhaValida;
  senhaMinima = SENHA_TAMANHO_MINIMO;

  constructor(
    private auth: AuthService,
    private dialogRef: MatDialogRef<TrocarSenhaDialog>,
    private cdr: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) public data: TrocarSenhaDados
  ) {}

  podeSalvar(): boolean {
    return senhaValida(this.novaSenha) && this.novaSenha === this.confirmacaoSenha && this.novaSenha !== this.data.senhaAtual;
  }

  salvar() {
    if (!this.podeSalvar() || this.enviando) return;
    this.enviando = true;
    this.erro = '';
    this.auth.trocarSenha(this.data.login, this.data.senhaAtual, this.novaSenha).subscribe({
      next: usuario => this.dialogRef.close(usuario),
      error: (err: HttpErrorResponse) => {
        this.enviando = false;
        this.erro = err.status === 0
          ? 'Erro de conexão. Tente novamente.'
          : (typeof err.error === 'string' && err.error ? err.error : 'Não foi possível trocar a senha.');
        this.cdr.detectChanges();
      },
    });
  }

  cancelar() { this.dialogRef.close(null); }
}
