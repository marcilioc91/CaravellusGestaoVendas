import { Component } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AtualizacaoService } from '../../services/atualizacao.service';

/** Barra flutuante com o progresso do download da atualização (o sistema continua utilizável) */
@Component({
  selector: 'app-atualizacao-progresso',
  standalone: true,
  imports: [MatProgressBarModule],
  templateUrl: './atualizacao-progresso.html',
  styleUrl: './atualizacao-progresso.css',
})
export class AtualizacaoProgresso {
  constructor(protected atualizacao: AtualizacaoService) {}
}
