import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { LicencaService, LicencaStatus } from '../../services/licenca.service';

// ── Ativação do sistema: o cliente envia o código da máquina e informa a chave recebida ──
@Component({
  selector: 'app-ativacao',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ativacao.html',
  styleUrl: './ativacao.css',
})
export class Ativacao implements OnInit {
  status: LicencaStatus | null = null;
  carregando = true;
  chave = '';
  enviando = false;
  erro = '';

  constructor(
    private licenca: LicencaService,
    private router: Router,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.licenca.status().subscribe({
      next: s => {
        this.status = s;
        this.carregando = false;
        this.cdr.detectChanges();
      },
      error: (err: HttpErrorResponse) => {
        this.carregando = false;
        this.falha(err, 'Não foi possível consultar a licença.');
      },
    });
  }

  copiarCodigo() {
    const codigo = this.status?.codigoMaquina;
    if (!codigo) return;
    navigator.clipboard.writeText(codigo).then(
      () => this.snackBar.open('Código da máquina copiado.', 'OK', { duration: 3000 }),
      () => this.snackBar.open('Não foi possível copiar. Selecione o código e copie manualmente.', 'OK', { duration: 4000 })
    );
  }

  ativar() {
    if (!this.chave.trim()) return;
    this.enviar(this.chave);
  }

  /** Arquivo de licença (privatekey.lic) enviado pelo fornecedor: o backend extrai a chave do conteúdo */
  importarArquivo(evento: Event) {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = ''; // permite escolher o mesmo arquivo de novo depois de um erro
    if (!arquivo || this.enviando) return;
    if (arquivo.size > 64 * 1024) {
      this.erro = 'Este arquivo não é um arquivo de licença do Caravellus.';
      this.cdr.detectChanges();
      return;
    }
    arquivo.text().then(
      conteudo => this.enviar(conteudo),
      () => {
        this.erro = 'Não foi possível ler o arquivo selecionado.';
        this.cdr.detectChanges();
      }
    );
  }

  private enviar(conteudo: string) {
    if (this.enviando) return;
    this.enviando = true;
    this.erro = '';
    this.cdr.detectChanges();
    this.licenca.ativar(conteudo).subscribe({
      next: s => {
        this.enviando = false;
        this.snackBar.open(`Sistema ativado para ${s.cliente}.`, 'OK', { duration: 4000 });
        this.router.navigate(['/']);
      },
      error: (err: HttpErrorResponse) => this.falha(err, 'Não foi possível ativar o sistema.'),
    });
  }

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
