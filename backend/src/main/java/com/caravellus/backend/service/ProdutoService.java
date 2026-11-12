package com.caravellus.backend.service;

import com.caravellus.backend.entity.Produto;
import com.caravellus.backend.entity.ProdutoImagem;
import com.caravellus.backend.repository.CategoriaRepository;
import com.caravellus.backend.repository.ProdutoImagemRepository;
import com.caravellus.backend.repository.ProdutoRepository;
import com.caravellus.backend.repository.VendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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

    @Autowired
    private VendaRepository vendaRepository;

    public List<Produto> listar(String evento) {
        return repository.findByEventoCodigoOrderByNomeAsc(evento);
    }

    @Transactional
    public Produto salvar(Produto produto, UsuarioSessao usuario) {
        String evento = usuario.evento();
        boolean isNovo = produto.getId() == null;

        BigDecimal precoAnterior = isNovo ? null : buscarProduto(produto.getId(), evento).getPreco();

        if (produto.getCategoria() != null && produto.getCategoria().getId() != null)
            produto.setCategoria(categoriaRepository.findByIdAndEventoCodigo(produto.getCategoria().getId(), evento).orElse(null));
        else
            produto.setCategoria(null);
        produto.setEventoCodigo(evento);

        Produto salvo = repository.save(produto);
        boolean precoMudou =
                precoAnterior != null && salvo.getPreco() != null && precoAnterior.compareTo(salvo.getPreco()) != 0;

        if (precoMudou) {
            int itens = vendaRepository.atualizarPrecoItens(salvo.getId(), salvo.getPreco());
            auditoriaService.registrar(
                    usuario,
                    "ALTERACAO_PRODUTO",
                    "Preço do produto '" + salvo.getNome() + "' (ID " + salvo.getId() +
                            " alterado de :" + precoAnterior + " para: " + salvo.getPreco() +
                            "; " + itens + " item(ns) de venda atualizados"
            );
        }

        String tipo = isNovo ? "INCLUSAO_PRODUTO" : "ALTERACAO_PRODUTO";
        String acao = isNovo ? "incluído" : "alterado";
        auditoriaService.registrar(usuario, tipo,
                "Produto '" + salvo.getNome() + "' (ID " + salvo.getId() + ") " + acao + ".");

        return salvo;
    }

    /** A imagem sai junto; se o produto não puder ser excluído (já vendido), nada é apagado */
    @Transactional
    public void excluir(Long id, UsuarioSessao usuario) {
        Produto produto = repository.findByIdAndEventoCodigo(id, usuario.evento()).orElse(null);
        if (produto == null) return;
        auditoriaService.registrar(usuario, "EXCLUSAO_PRODUTO",
                "Produto '" + produto.getNome() + "' (ID " + id + ") excluído.");
        imagemRepository.deleteById(id);
        repository.delete(produto);
        repository.flush();
    }

    public Optional<ProdutoImagem> buscarImagem(Long id) {
        return imagemRepository.findById(id);
    }

    @Transactional
    public void salvarImagem(Long id, String tipo, byte[] conteudo, UsuarioSessao usuario) {
        Produto produto = buscarProduto(id, usuario.evento());
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

        auditoriaService.registrar(usuario, "ALTERACAO_PRODUTO",
                "Imagem do produto '" + produto.getNome() + "' (ID " + id + ") alterada.");
    }

    @Transactional
    public void removerImagem(Long id, UsuarioSessao usuario) {
        Produto produto = buscarProduto(id, usuario.evento());
        if (produto.getImagemVersao() == null) return;
        imagemRepository.deleteById(id);
        repository.atualizarImagemVersao(id, null);

        auditoriaService.registrar(usuario, "ALTERACAO_PRODUTO",
                "Imagem do produto '" + produto.getNome() + "' (ID " + id + ") removida.");
    }

    /** Produto de outro evento conta como inexistente */
    private Produto buscarProduto(Long id, String evento) {
        return repository.findByIdAndEventoCodigo(id, evento)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
    }
}
