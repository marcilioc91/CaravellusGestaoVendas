import { Component, Inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

export type DadosAtualizacao =
  | { modo: 'disponivel'; nova: string; atual: string }
  | { modo: 'baixada'; nova: string };

/**
 * Aviso de atualização do sistema (substitui a janela do Windows).
 * Fecha com true = "Baixar agora" / "Reiniciar e instalar agora";
 * false ou undefined (Esc, clique fora) = "Depois" / "Instalar ao fechar".
 */
@Component({
  selector: 'app-atualizacao-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  templateUrl: './atualizacao-dialog.html',
  styleUrl: './atualizacao-dialog.css',
})
export class AtualizacaoDialog {
  constructor(@Inject(MAT_DIALOG_DATA) public data: DadosAtualizacao) {}
}
