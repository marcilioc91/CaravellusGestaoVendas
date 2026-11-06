import { ChangeDetectorRef, Component, ElementRef, QueryList, ViewChildren } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../services/auth.service';
import { SENHA_TAMANHO_MINIMO, senhaValida } from '../../utils/utils';

// ── Diálogo "Esqueci minha senha": 1) solicitar código  2) validar código  3) nova senha ──
@Component({
  selector: 'app-recuperar-senha-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './recuperar-senha-dialog.html',
  styleUrl: './recuperar-senha-dialog.css',
})
export class RecuperarSenhaDialog {
  etapa: 'solicitar' | 'codigo' | 'senha' = 'solicitar';
  identificador = '';
  /** Um dígito por caixinha do código de verificação */
  digitos = ['', '', '', '', '', ''];
  novaSenha = '';
  confirmacaoSenha = '';
  ocultarSenha = true;
  enviando = false;
  erro = '';
  aviso = '';
  senhaValida = senhaValida;
  senhaMinima = SENHA_TAMANHO_MINIMO;

  @ViewChildren('digito') private caixas!: QueryList<ElementRef<HTMLInputElement>>;

  constructor(
    private auth: AuthService,
    private dialogRef: MatDialogRef<RecuperarSenhaDialog>,
    private cdr: ChangeDetectorRef
  ) {}

  solicitar() {
    if (!this.identificador.trim() || this.enviando) return;
    this.enviando = true;
    this.erro = '';
    this.auth.solicitarRecuperacaoSenha(this.identificador.trim()).subscribe({
      next: msg => {
        this.enviando = false;
        this.aviso = msg;
        this.etapa = 'codigo';
        this.cdr.detectChanges();
        this.focarDigito(0);
      },
      error: (err: HttpErrorResponse) => this.falha(err, 'Não foi possível solicitar o código.'),
    });
  }

  validarCodigo() {
    if (!this.codigoCompleto() || this.enviando) return;
    this.enviando = true;
    this.erro = '';
    this.auth.validarCodigoRecuperacao(this.identificador.trim(), this.codigo).subscribe({
      next: () => {
        this.enviando = false;
        this.etapa = 'senha';
        this.cdr.detectChanges();
      },
      error: (err: HttpErrorResponse) => this.falha(err, 'Não foi possível validar o código.'),
    });
  }

  redefinir() {
    if (!this.podeRedefinir() || this.enviando) return;
    this.enviando = true;
    this.erro = '';
    this.auth.redefinirSenhaComCodigo(this.identificador.trim(), this.codigo, this.novaSenha).subscribe({
      next: () => this.dialogRef.close(true),
      error: (err: HttpErrorResponse) => this.falha(err, 'Não foi possível redefinir a senha.'),
    });
  }

  get codigo(): string {
    return this.digitos.join('');
  }

  codigoCompleto(): boolean {
    return /^\d{6}$/.test(this.codigo);
  }

  // ── Caixinhas do código: avança ao digitar, volta no Backspace, aceita colar o código inteiro ──
  aoDigitar(indice: number, evento: Event) {
    const caixa = evento.target as HTMLInputElement;
    const valor = caixa.value.replace(/\D/g, '');
    if (valor.length > 1) {
      // Preenchimento automático (one-time-code) ou digitação sobre caixa já preenchida
      this.preencherDigitos(valor, indice);
      return;
    }
    this.digitos[indice] = valor;
    caixa.value = valor;
    if (valor && indice < this.digitos.length - 1) this.focarDigito(indice + 1);
  }

  aoTeclar(indice: number, evento: KeyboardEvent) {
    switch (evento.key) {
      case 'Backspace':
        if (!this.digitos[indice] && indice > 0) {
          evento.preventDefault();
          this.digitos[indice - 1] = '';
          this.focarDigito(indice - 1);
        }
        break;
      case 'ArrowLeft':
        if (indice > 0) { evento.preventDefault(); this.focarDigito(indice - 1); }
        break;
      case 'ArrowRight':
        if (indice < this.digitos.length - 1) { evento.preventDefault(); this.focarDigito(indice + 1); }
        break;
      case 'Enter':
        this.validarCodigo();
        break;
    }
  }

  aoColar(indice: number, evento: ClipboardEvent) {
    evento.preventDefault();
    this.preencherDigitos(evento.clipboardData?.getData('text') ?? '', indice);
  }

  private preencherDigitos(texto: string, inicio: number) {
    const numeros = texto.replace(/\D/g, '').slice(0, this.digitos.length - inicio).split('');
    numeros.forEach((d, i) => (this.digitos[inicio + i] = d));
    // Sincroniza direto no DOM: se o valor do array não mudou, o binding [value] não atualiza a caixa
    this.caixas.forEach((caixa, i) => (caixa.nativeElement.value = this.digitos[i]));
    this.focarDigito(Math.min(inicio + numeros.length, this.digitos.length - 1));
  }

  private focarDigito(indice: number) {
    setTimeout(() => {
      const caixa = this.caixas?.get(indice)?.nativeElement;
      caixa?.focus();
      caixa?.select();
    });
  }

  podeRedefinir(): boolean {
    return senhaValida(this.novaSenha) && this.novaSenha === this.confirmacaoSenha;
  }

  voltar() {
    this.etapa = 'solicitar';
    this.digitos = ['', '', '', '', '', ''];
    this.novaSenha = '';
    this.confirmacaoSenha = '';
    this.erro = '';
  }

  fechar() { this.dialogRef.close(false); }

  private falha(err: HttpErrorResponse, padrao: string) {
    this.enviando = false;
    if (err.status === 0) {
      this.erro = 'Erro de conexão. Tente novamente.';
    } else {
      this.erro = typeof err.error === 'string' && err.error ? err.error : padrao;
    }
    this.cdr.detectChanges();
  }
}
