import { Injectable, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { AtualizacaoDialog, DadosAtualizacao } from '../shared/atualizacao-dialog/atualizacao-dialog';

/** Download da atualização em andamento */
export interface DownloadAtualizacao {
  versao: string;
  percentual: number;
}

/** Liga os avisos de atualização do Electron (preload.js) ao diálogo e à barra de download */
@Injectable({ providedIn: 'root' })
export class AtualizacaoService {
  /** Download em andamento; null quando não está baixando */
  readonly download = signal<DownloadAtualizacao | null>(null);

  private iniciado = false;

  constructor(private dialog: MatDialog, private snackBar: MatSnackBar) {}

  /** Chamar uma vez, quando a primeira tela abriu */
  iniciar(): void {
    const ponte = window.caravellus;
    if (!ponte || this.iniciado) return; // no navegador (ng serve) não há atualização
    this.iniciado = true;

    ponte.aoAtualizacaoDisponivel(async dados => {
      if (await this.perguntar({ modo: 'disponivel', ...dados })) {
        this.download.set({ versao: dados.nova, percentual: 0 });
        ponte.baixarAtualizacao();
      }
    });
    ponte.aoProgressoAtualizacao(percentual =>
      this.download.update(d => (d ? { ...d, percentual } : d))
    );
    ponte.aoAtualizacaoBaixada(async dados => {
      this.download.set(null);
      if (await this.perguntar({ modo: 'baixada', ...dados })) ponte.instalarAtualizacaoAgora();
    });
    ponte.aoErroAtualizacao(() => {
      // Erro na verificação (ex.: sem internet) não incomoda; só avisa se o download falhou
      if (!this.download()) return;
      this.download.set(null);
      this.snackBar.open(
        'Não foi possível baixar a atualização. O sistema tenta de novo na próxima abertura.',
        'Fechar',
        { duration: 6000 }
      );
    });
    ponte.verificarAtualizacao();
  }

  private async perguntar(data: DadosAtualizacao): Promise<boolean> {
    const ref = this.dialog.open<AtualizacaoDialog, DadosAtualizacao, boolean>(AtualizacaoDialog, { data });
    return (await firstValueFrom(ref.afterClosed())) === true;
  }
}
