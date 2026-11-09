import { ChangeDetectorRef, Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Categoria } from '../../models/categoria';
import { Produto } from '../../models/produto';
import { CategoriaService } from '../../services/categoria.service';
import { ProdutoService } from '../../services/produto.service';
import { agruparPorCategoria, GrupoCategoria, reduzirImagem, secoesDoFiltro } from '../../utils/utils';
import { ConfirmDialog } from '../clientes/clientes';

/** imagem: undefined = não mexeu, null = remover, Blob = nova imagem */
interface ResultadoFormulario {
  produto: Produto;
  imagem?: Blob | null;
}

// ── Diálogo de novo/editar produto ────────────────────────────────────────────
@Component({
  selector: 'app-produto-form-dialog',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  templateUrl: './produto-form-dialog.html',
  styleUrl: './produto-form.css',
})
export class ProdutoFormDialog implements OnDestroy {
  dados: Produto;
  categoriaId: number | null;
  modoEdicao: boolean;
  erro = '';
  imagemPreview: string | null;
  private imagemNova?: Blob | null;
  private urlTemporaria: string | null = null;

  constructor(
    private dialogRef: MatDialogRef<ProdutoFormDialog>,
    private produtoService: ProdutoService,
    private cdr: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) public data: { produto?: Produto; categorias: Categoria[] } | null
  ) {
    this.modoEdicao = !!data?.produto;
    this.dados = data?.produto
      ? { ...data.produto }
      : { nome: '', preco: 0, preco_custo: 0, estoque: 0 };
    this.categoriaId = this.dados.categoria?.id ?? null;
    this.imagemPreview = this.produtoService.urlImagem(this.dados);
  }

  async selecionarImagem(input: HTMLInputElement) {
    const arquivo = input.files?.[0];
    input.value = '';
    if (!arquivo) return;
    if (!arquivo.type.startsWith('image/')) {
      this.erro = 'Escolha um arquivo de imagem (JPG, PNG...).';
      return;
    }
    try {
      this.imagemNova = await reduzirImagem(arquivo);
      this.trocarPreview(URL.createObjectURL(this.imagemNova));
      this.erro = '';
    } catch {
      this.erro = 'Não foi possível ler esta imagem.';
    }
    this.cdr.detectChanges();
  }

  removerImagem() {
    this.imagemNova = null;
    this.trocarPreview(null);
  }

  private trocarPreview(url: string | null) {
    if (this.urlTemporaria) URL.revokeObjectURL(this.urlTemporaria);
    this.urlTemporaria = url;
    this.imagemPreview = url;
  }

  ngOnDestroy() {
    if (this.urlTemporaria) URL.revokeObjectURL(this.urlTemporaria);
  }

  confirmar() {
    if (this.dados.preco <= 0) {
      this.erro = 'O preço deve ser maior que zero.';
      return;
    }
    const categoria = this.data?.categorias.find(c => c.id === this.categoriaId) ?? null;
    const resultado: ResultadoFormulario = { produto: { ...this.dados, categoria }, imagem: this.imagemNova };
    this.dialogRef.close(resultado);
  }

  fechar() { this.dialogRef.close(); }
}

// ── Página de produtos ─────────────────────────────────────────────────────────
@Component({
  selector: 'app-produto-form',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    FormsModule,
    RouterLink,
  ],
  templateUrl: './produto-form.html',
  styleUrl: './produto-form.css',
})
export class ProdutoForm implements OnInit {
  produtos: Produto[] = [];
  categorias: Categoria[] = [];
  /** Mesmo agrupamento da tela de vendas: filtro "Todas" ou uma categoria, uma seção por categoria */
  grupos: GrupoCategoria[] = [];
  grupoSelecionado: GrupoCategoria | null = null;
  /** A categoria já aparece no título da seção, por isso não há coluna para ela */
  colunas = ['semEstoque', 'imagem', 'nome', 'preco', 'estoque', 'acoes'];

  constructor(
    public produtoService: ProdutoService,
    private categoriaService: CategoriaService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit() {
    this.carregar();
    this.categoriaService.listar().subscribe({
      next: dados => this.categorias = dados,
      error: () => this.snackBar.open('Erro ao carregar categorias.', 'Fechar', { duration: 3000 })
    });
  }

  carregar() {
    this.produtoService.listar().subscribe({
      next: dados => {
        // Recarregar (após salvar, excluir...) mantém a categoria que estava no filtro
        const categoriaAtual = this.grupoSelecionado?.nome;
        this.produtos = dados;
        this.grupos = agruparPorCategoria(dados);
        this.grupoSelecionado = this.grupos.find(g => g.nome === categoriaAtual) ?? this.grupos[0];
        this.cdr.detectChanges();
      },
      error: () => this.snackBar.open('Erro ao carregar produtos.', 'Fechar', { duration: 3000 })
    });
  }

  secoesVisiveis(): GrupoCategoria[] {
    return secoesDoFiltro(this.grupos, this.grupoSelecionado);
  }

  abrirFormulario() {
    const ref = this.dialog.open(ProdutoFormDialog, { width: '400px', data: { categorias: this.categorias } });
    ref.afterClosed().subscribe((resultado: ResultadoFormulario | undefined) => {
      if (!resultado) return;
      this.produtoService.salvar(resultado.produto).subscribe({
        next: salvo => this.gravarImagem(salvo.id!, resultado.imagem, 'Produto cadastrado com sucesso!'),
        error: () => this.snackBar.open('Erro ao cadastrar produto.', 'Fechar', { duration: 3000 })
      });
    });
  }

  abrirEdicao(produto: Produto) {
    const ref = this.dialog.open(ProdutoFormDialog, { width: '400px', data: { produto, categorias: this.categorias } });
    ref.afterClosed().subscribe((resultado: ResultadoFormulario | undefined) => {
      const id = resultado?.produto.id;
      if (!resultado || !id) return;
      this.produtoService.atualizar(id, resultado.produto).subscribe({
        next: () => this.gravarImagem(id, resultado.imagem, 'Produto atualizado!'),
        error: () => this.snackBar.open('Erro ao atualizar produto.', 'Fechar', { duration: 3000 })
      });
    });
  }

  /** Depois de salvar os dados do produto, envia ou remove a imagem (se ela mudou) */
  private gravarImagem(id: number, imagem: Blob | null | undefined, mensagem: string) {
    const concluir = (msg: string) => {
      this.snackBar.open(msg, 'Fechar', { duration: 3000 });
      this.carregar();
    };
    if (imagem === undefined) {
      concluir(mensagem);
      return;
    }
    const requisicao = imagem ? this.produtoService.salvarImagem(id, imagem) : this.produtoService.removerImagem(id);
    requisicao.subscribe({
      next: () => concluir(mensagem),
      error: () => concluir('Produto salvo, mas houve erro ao gravar a imagem.')
    });
  }

  marcarSemEstoque(produto: Produto, checked: boolean) {
    if (!produto.id || !checked) return;
    const atualizado = { ...produto, estoque: 0 };
    this.produtoService.atualizar(produto.id, atualizado).subscribe({
      next: () => {
        this.snackBar.open('Produto marcado como sem estoque.', 'Fechar', { duration: 3000 });
        this.carregar();
      },
      error: () => this.snackBar.open('Erro ao atualizar estoque.', 'Fechar', { duration: 3000 })
    });
  }

  excluir(produto: Produto) {
    const id = produto.id;
    if (!id) return;
    const ref = this.dialog.open(ConfirmDialog, {
      width: '380px',
      data: { titulo: 'Excluir produto', mensagem: `Deseja excluir o produto "${produto.nome}"? Produtos que já aparecem em vendas não podem ser excluídos.` },
      panelClass: 'confirm-dialog-panel'
    });
    ref.afterClosed().subscribe((confirmado: boolean) => {
      if (!confirmado) return;
      this.produtoService.excluir(id).subscribe({
        next: () => {
          this.snackBar.open('Produto excluído.', 'Fechar', { duration: 3000 });
          this.carregar();
        },
        error: () => this.snackBar.open('Erro ao excluir produto. Se ele já foi vendido, zere o estoque em vez de excluir.', 'Fechar', { duration: 5000 })
      });
    });
  }
}
