import { Component } from '@angular/core';

/**
 * Logo do Caravellus (veleiro dentro do anel, com linhas de água), redesenhada em SVG a partir do
 * PNG original. Usa currentColor: a cor vem do CSS de quem usa (branca no menu, azul no login...).
 * Tamanho: defina width/height no elemento <app-logo> (padrão 48px).
 * Cópia do mesmo desenho em public/caravellus-logo.svg e, sem Angular, em index.html.
 */
@Component({
  selector: 'app-logo',
  standalone: true,
  template: `
    <svg viewBox="2 2 85 85" fill="currentColor" role="img" aria-label="Caravellus">
      <path d="M 13.37 72.23 A 39.5 41.8 0 1 1 77.44 69.57" fill="none" stroke="currentColor"
        stroke-width="2.6" stroke-linecap="round" />
      <path d="M33.6 12.5 Q28 50 14.6 62.4 Q27 61.8 33.4 70.6 Z" />
      <path d="M35 8.6 Q66.5 31.5 76.6 64 Q55 63 36.2 71.2 L37.4 30 Z" />
      <g fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round">
        <path d="M18 76.6 H62 M66 76.6 H75" />
        <path d="M22 80.6 H33.5 M39 80.6 H67" />
        <path d="M31 84.6 H45 M50 84.6 H59" />
      </g>
    </svg>
  `,
  styles: `
    :host {
      display: inline-block;
      width: 48px;
      height: 48px;
      flex-shrink: 0;
      line-height: 0;
    }
    svg {
      width: 100%;
      height: 100%;
    }
  `,
})
export class Logo {}
