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
import { NgxMaskDirective, provideNgxMask } from 'ngx-mask';

// ── Ativação do sistema: no primeiro acesso o cliente pede a licença (o backend envia o pedido ao
// fornecedor por e-mail) e depois importa o arquivo de licença recebido. Sem internet: código + chave ──
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
    NgxMaskDirective
  ],
  providers: [provideNgxMask()],
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

  pedido = { nome: '', email: '', telefone: '' };
  solicitando = false;
  /** Pedido já enviado, mas o cliente quer corrigir os dados e enviar de novo */
  editandoPedido = false;

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
        this.preencherPedido();
        this.cdr.detectChanges();
      },
      error: (err: HttpErrorResponse) => {
        this.carregando = false;
        this.falha(err, 'Não foi possível consultar a licença.');
      },
    });
  }

  /** "20/10/2026 14:05" a partir de "2026-10-20 14:05:00" */
  get enviadoEm(): string {
    const valor = this.status?.solicitacao?.enviadaEm ?? '';
    const m = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/.exec(valor);
    return m ? `${m[3]}/${m[2]}/${m[1]} às ${m[4]}:${m[5]}` : valor;
  }

  editarPedido() {
    this.preencherPedido();
    this.editandoPedido = true;
  }

  solicitar() {
    if (this.solicitando || !this.pedido.nome.trim() || !this.pedido.email.trim()) return;
    this.solicitando = true;
    this.erro = '';
    this.cdr.detectChanges();
    this.licenca.solicitar(this.pedido).subscribe({
      next: s => {
        this.solicitando = false;
        this.status = s;
        this.editandoPedido = false;
        this.snackBar.open('Pedido de licença enviado. Aguarde o arquivo de licença no seu e-mail.', 'OK', { duration: 5000 });
        this.cdr.detectChanges();
      },
      error: (err: HttpErrorResponse) => {
        this.solicitando = false;
        this.falha(err, 'Não foi possível enviar o pedido de licença.');
      },
    });
  }

  private preencherPedido() {
    const s = this.status?.solicitacao;
    if (s) this.pedido = { nome: s.nome, email: s.email, telefone: s.telefone ?? '' };
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
