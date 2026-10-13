package com.caravellus.backend.service;

import com.caravellus.backend.entity.Produto;
import com.caravellus.backend.entity.ProdutoImagem;
import com.caravellus.backend.repository.CategoriaRepository;
import com.caravellus.backend.repository.ProdutoImagemRepository;
import com.caravellus.backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class ProdutoService {

    public static final int TAMANHO_MAXIMO_IMAGEM = 2 * 1024 * 1024;

    @Autowired
    private ProdutoRepository repository;

    @Autowired
    private ProdutoImagemRepository imagemRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public List<Produto> listar() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "nome"));
    }

    public Produto salvar(Produto produto, Long usuarioId, String usuarioNome) {
        boolean isNovo = produto.getId() == null;
        if (produto.getCategoria() != null && produto.getCategoria().getId() != null) {
            produto.setCategoria(categoriaRepository.findById(produto.getCategoria().getId()).orElse(null));
        } else {
            produto.setCategoria(null);
        }
        Produto salvo = repository.save(produto);

        String tipo = isNovo ? "INCLUSAO_PRODUTO" : "ALTERACAO_PRODUTO";
        String acao = isNovo ? "incluído" : "alterado";
        auditoriaService.registrar(usuarioId, usuarioNome, tipo,
                "Produto '" + salvo.getNome() + "' (ID " + salvo.getId() + ") " + acao + ".");

        return salvo;
    }

    /** A imagem sai junto; se o produto não puder ser excluído (já vendido), nada é apagado */
    @Transactional
    public void excluir(Long id, Long usuarioId, String usuarioNome) {
        repository.findById(id).ifPresent(p ->
                auditoriaService.registrar(usuarioId, usuarioNome, "EXCLUSAO_PRODUTO",
                        "Produto '" + p.getNome() + "' (ID " + id + ") excluído.")
        );
        imagemRepository.deleteById(id);
        repository.deleteById(id);
        repository.flush();
    }

    public Optional<ProdutoImagem> buscarImagem(Long id) {
        return imagemRepository.findById(id);
    }

    @Transactional
    public void salvarImagem(Long id, String tipo, byte[] conteudo, Long usuarioId, String usuarioNome) {
        Produto produto = buscarProduto(id);
        if (tipo == null || !tipo.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo precisa ser uma imagem.");
        }
        if (conteudo == null || conteudo.length == 0 || conteudo.length > TAMANHO_MAXIMO_IMAGEM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A imagem deve ter até 2 MB.");
        }

        ProdutoImagem imagem = new ProdutoImagem();
        imagem.setProdutoId(id);
        imagem.setTipo(tipo);
        imagem.setConteudo(conteudo);
        imagemRepository.save(imagem);
        repository.atualizarImagemVersao(id, System.currentTimeMillis());

        auditoriaService.registrar(usuarioId, usuarioNome, "ALTERACAO_PRODUTO",
                "Imagem do produto '" + produto.getNome() + "' (ID " + id + ") alterada.");
    }

    @Transactional
    public void removerImagem(Long id, Long usuarioId, String usuarioNome) {
        Produto produto = buscarProduto(id);
        if (produto.getImagemVersao() == null) return;
        imagemRepository.deleteById(id);
        repository.atualizarImagemVersao(id, null);

        auditoriaService.registrar(usuarioId, usuarioNome, "ALTERACAO_PRODUTO",
                "Imagem do produto '" + produto.getNome() + "' (ID " + id + ") removida.");
    }

    private Produto buscarProduto(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
    }
}
