package com.caravellus.backend.service;

import com.caravellus.backend.entity.Categoria;
import com.caravellus.backend.repository.CategoriaRepository;
import com.caravellus.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
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

    public List<Categoria> listar() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "nome"));
    }

    public Categoria salvar(Categoria categoria, Long usuarioId, String usuarioNome) {
        String nome = categoria.getNome() == null ? "" : categoria.getNome().trim().toUpperCase();
        if (nome.isEmpty()) {
            throw new IllegalArgumentException("O nome da categoria é obrigatório.");
        }
        boolean isNovo = categoria.getId() == null;
        boolean duplicada = isNovo
                ? repository.existsByNomeIgnoreCase(nome)
                : repository.existsByNomeIgnoreCaseAndIdNot(nome, categoria.getId());
        if (duplicada) {
            throw new IllegalArgumentException("Já existe uma categoria com o nome '" + nome + "'.");
        }
        categoria.setNome(nome);
        Categoria salva = repository.save(categoria);

        String tipo = isNovo ? "INCLUSAO_CATEGORIA" : "ALTERACAO_CATEGORIA";
        String acao = isNovo ? "incluída" : "alterada";
        auditoriaService.registrar(usuarioId, usuarioNome, tipo,
                "Categoria '" + salva.getNome() + "' (ID " + salva.getId() + ") " + acao + ".");

        return salva;
    }

    public void excluir(Long id, Long usuarioId, String usuarioNome) {
        if (produtoRepository.existsByCategoriaId(id)) {
            throw new IllegalArgumentException("Não é possível excluir: existem produtos vinculados a esta categoria.");
        }
        repository.findById(id).ifPresent(c ->
                auditoriaService.registrar(usuarioId, usuarioNome, "EXCLUSAO_CATEGORIA",
                        "Categoria '" + c.getNome() + "' (ID " + id + ") excluída.")
        );
        repository.deleteById(id);
    }
}
