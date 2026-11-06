import { Component } from '@angular/core';
import { version } from '../../../../package.json';

/** Rodapé da tela inicial: período dos direitos autorais e versão (lida do package.json no build). */
@Component({
  selector: 'app-rodape-sistema',
  standalone: true,
  templateUrl: './rodape-sistema.html',
  styleUrl: './rodape-sistema.css',
})
export class RodapeSistema {
  /** Ano de lançamento do sistema */
  static readonly ANO_INICIAL = 2026;

  readonly versao = version;
  readonly periodo = RodapeSistema.periodo(new Date().getFullYear());

  /** "2026" no primeiro ano; "2026 - 2027" a partir do ano seguinte */
  static periodo(anoAtual: number): string {
    return anoAtual > RodapeSistema.ANO_INICIAL ? `${RodapeSistema.ANO_INICIAL} - ${anoAtual}` : `${RodapeSistema.ANO_INICIAL}`;
  }
}
