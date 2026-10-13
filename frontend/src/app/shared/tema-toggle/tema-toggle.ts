import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TemaService } from '../../services/tema.service';

/** Chave de tema claro/escuro (usada na tela de login e na tela inicial). */
@Component({
  selector: 'app-tema-toggle',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './tema-toggle.html',
  styleUrl: './tema-toggle.css',
})
export class TemaToggle {
  constructor(protected tema: TemaService) {}
}
