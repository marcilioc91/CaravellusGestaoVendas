import { Component, Inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Periodo } from '../../services/periodo.service';

/** Tela de contexto: pede periodo inicial e final. Fecha com o Periodo ou null se cancelada. */
@Component({
  selector: 'app-periodo-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.titulo }}</h2>
    <mat-dialog-content>
      <p class="dica">Informe o período que deseja visualizar.</p>
      <div class="campos">
        <mat-form-field appearance="outline">
          <mat-label>Período inicial</mat-label>
          <input matInput type="date" [(ngModel)]="inicio" name="inicio">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Período final</mat-label>
          <input matInput type="date" [(ngModel)]="fim" name="fim">
        </mat-form-field>
      </div>
      @if (erro) { <p class="erro">{{ erro }}</p> }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="cancelar()">Cancelar</button>
      <button mat-raised-button color="primary" (click)="confirmar()">Abrir</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .dica { color: var(--texto-suave); margin: 0 0 16px; }
    .campos { display: flex; gap: 12px; min-width: 420px; }
    .campos mat-form-field { flex: 1; }
    .erro { color: var(--cor-erro); font-size: 0.85rem; margin: 0; }
  `],
})
export class PeriodoDialog {
  inicio: string;
  fim: string;
  erro = '';

  constructor(
    private ref: MatDialogRef<PeriodoDialog, Periodo | null>,
    @Inject(MAT_DIALOG_DATA) public data: { titulo: string; periodo: Periodo }
  ) {
    this.inicio = data.periodo.inicio;
    this.fim = data.periodo.fim;
  }

  confirmar() {
    if (!this.inicio || !this.fim) {
      this.erro = 'Informe o período inicial e o final.';
    } else if (this.inicio > this.fim) {
      this.erro = 'O período inicial não pode ser maior que o final.';
    } else {
      this.ref.close({ inicio: this.inicio, fim: this.fim });
    }
  }

  cancelar() { this.ref.close(null); }
}
