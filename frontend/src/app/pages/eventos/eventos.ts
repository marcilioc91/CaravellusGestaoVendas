import { ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Evento, ResumoEvento } from '../../models/evento';
import { EventoService } from '../../services/evento.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialog } from '../clientes/clientes';

/** Mensagem de erro do backend (texto) ou a mensagem padrão */
function mensagemErro(err: HttpErrorResponse, padrao: string): string {
  return typeof err.error === 'string' && err.error ? err.error : padrao;
}

// ── Diálogo de renomear evento ────────────────────────────────────────────────
@Component({
  selector: 'app-renomear-evento-dialog',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Renomear evento</h2>
    <mat-dialog-content>
      <p class="eventos-dialog-sub">Código {{ data.codigo }} (não muda)</p>
      <mat-form-field appearance="outline" class="eventos-campo-nome">
        <mat-label>Nome</mat-label>
        <input matInput [(ngModel)]="nome" maxlength="100" (keyup.enter)="confirmar()" cdkFocusInitial>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="fechar()">Cancelar</button>
      <button mat-raised-button class="btn-cor-entrar" [disabled]="!nome.trim()" (click)="confirmar()">Salvar</button>
    </mat-dialog-actions>
  `,
  styleUrl: './eventos.css',
})
export class RenomearEventoDialog {
  nome: string;

  constructor(
    private dialogRef: MatDialogRef<RenomearEventoDialog>,
    @Inject(MAT_DIALOG_DATA) public data: Evento
  ) {
    this.nome = data.nome;
  }

  confirmar() {
    if (this.nome.trim()) this.dialogRef.close(this.nome.trim());
  }

  fechar() { this.dialogRef.close(); }
}

// ── Diálogo de exclusão de evento ─────────────────────────────────────────────
@Component({
  selector: 'app-excluir-evento-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule, MatIconModule],
  template: `
    <h2 mat-dialog-title class="eventos-excluir-titulo">
      <mat-icon>warning</mat-icon>
      Excluir evento
    </h2>
    <mat-dialog-content>
      <p>
        O evento <strong>{{ data.nome }}</strong> (código {{ data.codigo }}) será excluído
        <strong>junto com todos os dados dele</strong>:
      </p>
      <ul class="eventos-excluir-lista">
        <li>{{ data.clientes }} cliente(s)</li>
        <li>{{ data.categorias }} categoria(s)</li>
        <li>{{ data.produtos }} produto(s), com estoque e imagens</li>
        <li>{{ data.vendas }} venda(s), com os itens e pagamentos</li>
        <li>a auditoria do evento</li>
      </ul>
      <p class="eventos-excluir-aviso">Esta ação não pode ser desfeita.</p>
      <p class="eventos-dialog-sub">
        Os usuários do sistema não são afetados. Quem estiver usando este evento será desconectado.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false" cdkFocusInitial>Cancelar</button>
      <button mat-raised-button class="eventos-btn-excluir" [mat-dialog-close]="true">
        <mat-icon>delete_forever</mat-icon>
        Excluir tudo
      </button>
    </mat-dialog-actions>
  `,
  styleUrl: './eventos.css',
})
export class ExcluirEventoDialog {
  constructor(@Inject(MAT_DIALOG_DATA) public data: ResumoEvento) {}
}

// ── Página de eventos (ADMIN) ─────────────────────────────────────────────────
@Component({
  selector: 'app-eventos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatDialogModule,
  ],
  templateUrl: './eventos.html',
  styleUrl: './eventos.css',
})
export class Eventos implements OnInit {
  eventos: Evento[] = [];
  carregando = true;
  salvando = false;
  colunas = ['codigo', 'nome', 'criacao', 'situacao', 'acao'];
  novoNome = '';
  /** Evento em que esta sessão está (vem do login) */
  readonly eventoAtual: string | undefined;

  constructor(
    private eventoService: EventoService,
    private router: Router,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private cdRef: ChangeDetectorRef,
    auth: AuthService
  ) {
    this.eventoAtual = auth.getEventoAtual()?.codigo;
  }

  ngOnInit() {
    this.carregar();
  }

  private carregar() {
    this.eventoService.listar().subscribe({
      next: (dados) => {
        this.eventos = dados;
        this.carregando = false;
        this.cdRef.detectChanges();
      },
      error: () => {
        this.carregando = false;
        this.snackBar.open('Erro ao carregar os eventos.', 'Fechar', { duration: 3000 });
        this.cdRef.detectChanges();
      },
    });
  }

  podeIncluir(): boolean {
    return !!this.novoNome.trim() && !this.salvando;
  }

  incluir() {
    if (!this.podeIncluir()) return;
    this.salvando = true;
    this.eventoService.criar(this.novoNome.trim()).subscribe({
      next: (criado) => {
        this.salvando = false;
        this.novoNome = '';
        this.snackBar.open(`Evento "${criado.nome}" incluído com o código ${criado.codigo}.`, 'Fechar', { duration: 4000 });
        this.carregar();
      },
      error: (err: HttpErrorResponse) => {
        this.salvando = false;
        this.snackBar.open(mensagemErro(err, 'Erro ao incluir o evento.'), 'Fechar', { duration: 4000 });
        this.cdRef.detectChanges();
      },
    });
  }

  renomear(evento: Evento) {
    const ref = this.dialog.open(RenomearEventoDialog, { width: '400px', data: evento });
    ref.afterClosed().subscribe((nome: string | undefined) => {
      if (!nome || nome === evento.nome) return;
      this.atualizar(evento, { ...evento, nome }, 'Evento renomeado.');
    });
  }

  alternarAtivo(evento: Evento) {
    const desativar = evento.ativo !== false;
    const mensagem = desativar
      ? `Desativar "${evento.nome}"? Ele some da tela de login, mas os dados continuam guardados. Quem já está nele continua até sair.`
      : `Reativar "${evento.nome}"? Ele volta a aparecer na tela de login.`;
    const ref = this.dialog.open(ConfirmDialog, {
      width: '400px',
      data: { titulo: desativar ? 'Desativar evento' : 'Reativar evento', mensagem },
      panelClass: 'confirm-dialog-panel',
    });
    ref.afterClosed().subscribe((confirmado: boolean) => {
      if (!confirmado) return;
      this.atualizar(evento, { ...evento, ativo: !desativar }, desativar ? 'Evento desativado.' : 'Evento reativado.');
    });
  }

  /** Busca quanto o evento tem, mostra o aviso e só exclui depois da confirmação */
  excluir(evento: Evento) {
    this.eventoService.resumo(evento.codigo).subscribe({
      next: (resumo) => {
        const ref = this.dialog.open(ExcluirEventoDialog, { width: '440px', data: resumo });
        ref.afterClosed().subscribe((confirmado: boolean) => {
          if (!confirmado) return;
          this.eventoService.excluir(evento.codigo).subscribe({
            next: (excluido) => {
              this.snackBar.open(`Evento "${excluido.nome}" e todos os dados dele foram excluídos.`, 'Fechar', { duration: 4000 });
              this.carregar();
            },
            error: (err: HttpErrorResponse) =>
              this.snackBar.open(mensagemErro(err, 'Erro ao excluir o evento.'), 'Fechar', { duration: 5000 }),
          });
        });
      },
      error: (err: HttpErrorResponse) =>
        this.snackBar.open(mensagemErro(err, 'Erro ao consultar o evento.'), 'Fechar', { duration: 4000 }),
    });
  }

  private atualizar(evento: Evento, dados: Evento, sucesso: string) {
    this.eventoService.atualizar(evento.codigo, dados).subscribe({
      next: () => {
        this.snackBar.open(sucesso, 'Fechar', { duration: 3000 });
        this.carregar();
      },
      error: (err: HttpErrorResponse) =>
        this.snackBar.open(mensagemErro(err, 'Erro ao alterar o evento.'), 'Fechar', { duration: 4000 }),
    });
  }

  voltar() {
    this.router.navigate(['/home']);
  }
}
