package com.caravellus.backend.service;

import com.caravellus.backend.entity.Categoria;
import com.caravellus.backend.repository.CategoriaRepository;
import com.caravellus.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoriaService {

    @Autowired
    private CategoriaRepository repository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public List<Categoria> listar(String evento) {
        return repository.findByEventoCodigoOrderByNomeAsc(evento);
    }

    public Categoria salvar(Categoria categoria, UsuarioSessao usuario) {
        String evento = usuario.evento();
        String nome = categoria.getNome() == null ? "" : categoria.getNome().trim().toUpperCase();
        if (nome.isEmpty()) {
            throw new IllegalArgumentException("O nome da categoria é obrigatório.");
        }
        boolean isNovo = categoria.getId() == null;
        if (!isNovo && repository.findByIdAndEventoCodigo(categoria.getId(), evento).isEmpty()) {
            throw new IllegalArgumentException("Categoria não encontrada.");
        }
        boolean duplicada = isNovo
                ? repository.existsByEventoCodigoAndNomeIgnoreCase(evento, nome)
                : repository.existsByEventoCodigoAndNomeIgnoreCaseAndIdNot(evento, nome, categoria.getId());
        if (duplicada) {
            throw new IllegalArgumentException("Já existe uma categoria com o nome '" + nome + "'.");
        }
        categoria.setNome(nome);
        categoria.setEventoCodigo(evento);
        Categoria salva = repository.save(categoria);

        String tipo = isNovo ? "INCLUSAO_CATEGORIA" : "ALTERACAO_CATEGORIA";
        String acao = isNovo ? "incluída" : "alterada";
        auditoriaService.registrar(usuario, tipo,
                "Categoria '" + salva.getNome() + "' (ID " + salva.getId() + ") " + acao + ".");

        return salva;
    }

    public void excluir(Long id, UsuarioSessao usuario) {
        Categoria categoria = repository.findByIdAndEventoCodigo(id, usuario.evento()).orElse(null);
        if (categoria == null) return;
        if (produtoRepository.existsByCategoriaId(id)) {
            throw new IllegalArgumentException("Não é possível excluir: existem produtos vinculados a esta categoria.");
        }
        auditoriaService.registrar(usuario, "EXCLUSAO_CATEGORIA",
                "Categoria '" + categoria.getNome() + "' (ID " + id + ") excluída.");
        repository.delete(categoria);
    }
}
