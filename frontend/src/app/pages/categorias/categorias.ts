import { ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConfirmDialog } from '../clientes/clientes';
import { MatTableModule } from '@angular/material/table';
import { Categoria } from '../../models/categoria';
import { CategoriaService } from '../../services/categoria.service';

// ── Diálogo de nova/editar categoria ──────────────────────────────────────────
@Component({
  selector: 'app-categoria-form-dialog',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './categoria-form-dialog.html',
})
export class CategoriaFormDialog {
  dados: Categoria;
  modoEdicao: boolean;

  constructor(
    private dialogRef: MatDialogRef<CategoriaFormDialog>,
    @Inject(MAT_DIALOG_DATA) public data: { categoria?: Categoria } | null
  ) {
    this.modoEdicao = !!data?.categoria;
    this.dados = data?.categoria ? { ...data.categoria } : { nome: '' };
  }

  confirmar() {
    if (!this.dados.nome.trim()) return;
    this.dialogRef.close({ ...this.dados, nome: this.dados.nome.trim() });
  }

  fechar() { this.dialogRef.close(); }
}

// ── Página de categorias ──────────────────────────────────────────────────────
@Component({
  selector: 'app-categorias',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    RouterLink,
  ],
  templateUrl: './categorias.html',
  styleUrl: './categorias.css',
})
export class Categorias implements OnInit {
  categorias: Categoria[] = [];
  colunas = ['nome', 'acoes'];

  constructor(
    private categoriaService: CategoriaService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit() { this.carregar(); }

  carregar() {
    this.categoriaService.listar().subscribe({
      next: dados => {
        this.categorias = dados;
        this.cdr.detectChanges();
      },
      error: () => this.snackBar.open('Erro ao carregar categorias.', 'Fechar', { duration: 3000 })
    });
  }

  private mensagemErro(err: any, padrao: string): string {
    return typeof err?.error === 'string' && err.error ? err.error : padrao;
  }

  abrirFormulario() {
    const ref = this.dialog.open(CategoriaFormDialog, { width: '400px', data: null });
    ref.afterClosed().subscribe((dados: Categoria | undefined) => {
      if (!dados) return;
      this.categoriaService.salvar(dados).subscribe({
        next: () => {
          this.snackBar.open('Categoria cadastrada com sucesso!', 'Fechar', { duration: 3000 });
          this.carregar();
        },
        error: err => this.snackBar.open(this.mensagemErro(err, 'Erro ao cadastrar categoria.'), 'Fechar', { duration: 4000 })
      });
    });
  }

  abrirEdicao(categoria: Categoria) {
    const ref = this.dialog.open(CategoriaFormDialog, { width: '400px', data: { categoria } });
    ref.afterClosed().subscribe((dados: Categoria | undefined) => {
      if (!dados || !dados.id) return;
      this.categoriaService.atualizar(dados.id, dados).subscribe({
        next: () => {
          this.snackBar.open('Categoria atualizada!', 'Fechar', { duration: 3000 });
          this.carregar();
        },
        error: err => this.snackBar.open(this.mensagemErro(err, 'Erro ao atualizar categoria.'), 'Fechar', { duration: 4000 })
      });
    });
  }

  excluir(categoria: Categoria) {
    const id = categoria.id;
    if (!id) return;
    const ref = this.dialog.open(ConfirmDialog, {
      width: '380px',
      data: { titulo: 'Excluir categoria', mensagem: `Deseja excluir a categoria "${categoria.nome}"?` },
      panelClass: 'confirm-dialog-panel'
    });
    ref.afterClosed().subscribe((confirmado: boolean) => {
      if (!confirmado) return;
      this.categoriaService.excluir(id).subscribe({
        next: () => {
          this.snackBar.open('Categoria excluída.', 'Fechar', { duration: 3000 });
          this.carregar();
        },
        error: err => this.snackBar.open(this.mensagemErro(err, 'Erro ao excluir categoria.'), 'Fechar', { duration: 4000 })
      });
    });
  }
}
