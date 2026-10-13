import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

export interface Periodo {
  inicio: string; // yyyy-MM-dd
  fim: string;    // yyyy-MM-dd
}

export type TelaPeriodo = 'relatorio' | 'historico';

/** Ultimo periodo informado em cada tela (gravado em periodos.ini na pasta de dados). */
@Injectable({ providedIn: 'root' })
export class PeriodoService {
  private api = 'http://localhost:8080/preferencias/periodo';

  constructor(private http: HttpClient) {}

  /** Ultimo periodo salvo; se nao houver, do dia 1 do mes atual ate hoje. */
  obter(tela: TelaPeriodo): Observable<Periodo> {
    return this.http.get<Partial<Periodo>>(`${this.api}/${tela}`).pipe(
      map(p => (p.inicio && p.fim ? { inicio: p.inicio, fim: p.fim } : PeriodoService.mesAtual()))
    );
  }

  salvar(tela: TelaPeriodo, periodo: Periodo) {
    return this.http.put(`${this.api}/${tela}`, periodo);
  }

  static mesAtual(): Periodo {
    const hoje = new Date();
    return {
      inicio: PeriodoService.iso(new Date(hoje.getFullYear(), hoje.getMonth(), 1)),
      fim: PeriodoService.iso(hoje),
    };
  }

  static iso(d: Date): string {
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  }

  static formatar(iso: string): string {
    const [a, m, d] = iso.split('-');
    return `${d}/${m}/${a}`;
  }
}
