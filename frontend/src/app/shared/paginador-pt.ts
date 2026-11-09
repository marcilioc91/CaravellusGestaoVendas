import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/**
 * Textos do mat-paginator em português. Registre no componente que usa o paginador:
 * providers: [{ provide: MatPaginatorIntl, useClass: PaginadorPt }]
 * (no app.config ele entraria no carregamento inicial do app, que tem limite de 1 MB).
 */
@Injectable()
export class PaginadorPt extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Itens por página:';
  override nextPageLabel = 'Próxima página';
  override previousPageLabel = 'Página anterior';
  override firstPageLabel = 'Primeira página';
  override lastPageLabel = 'Última página';

  /** "1 – 10 de 57" */
  override getRangeLabel = (pagina: number, tamanho: number, total: number): string => {
    if (total === 0 || tamanho === 0) return `0 de ${total}`;
    const inicio = pagina * tamanho;
    const fim = Math.min(inicio + tamanho, total);
    return `${inicio + 1} – ${fim} de ${total}`;
  };
}
