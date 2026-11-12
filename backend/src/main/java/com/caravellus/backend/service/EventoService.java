package com.caravellus.backend.service;

import com.caravellus.backend.dto.ResumoEventoDTO;
import com.caravellus.backend.entity.Evento;
import com.caravellus.backend.entity.Pessoa;
import com.caravellus.backend.repository.AuditoriaLogRepository;
import com.caravellus.backend.repository.CategoriaRepository;
import com.caravellus.backend.repository.ClienteRepository;
import com.caravellus.backend.repository.EventoRepository;
import com.caravellus.backend.repository.PessoaRepository;
import com.caravellus.backend.repository.ProdutoImagemRepository;
import com.caravellus.backend.repository.ProdutoRepository;
import com.caravellus.backend.repository.UsuarioRepository;
import com.caravellus.backend.repository.VendaRepository;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class EventoService {

    /** Maior código possível: a coluna CODIGO é VARCHAR(4) */
    static final int CODIGO_MAXIMO = 9999;

    @Autowired
    private EventoRepository repository;

    @Autowired
    private AuditoriaService auditoriaService;

    // Usados só na exclusão do evento
    @Autowired private VendaRepository vendaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ProdutoImagemRepository produtoImagemRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private AuditoriaLogRepository auditoriaLogRepository;
    @Autowired private PessoaRepository pessoaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    /** Lista da tela de login */
    public List<Evento> listarAtivos() {
        return repository.findByAtivoTrueOrderByNomeAsc();
    }

    public List<Evento> listarTodos() {
        return repository.findAllByOrderByNomeAsc();
    }

    /** Evento escolhido no login: precisa existir e estar ativo */
    public Evento buscarAtivo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Selecione o evento.");
        }
        return repository.findById(codigo.trim().toUpperCase(Locale.ROOT))
                .filter(Evento::getAtivo)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado ou inativo."));
    }

    @Transactional
    /** O código é gerado aqui (próximo número, com 4 dígitos); o enviado pela tela é ignorado */
    public Evento criar(Evento dados, UsuarioSessao usuario) {
        String nome = validarNome(dados.getNome());
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new IllegalArgumentException("Já existe um evento com o nome '" + nome + "'.");
        }
        String codigo = proximoCodigo();

        Evento evento = new Evento();
        evento.setCodigo(codigo);
        evento.setNome(nome);
        evento.setAtivo(true);
        evento.setDataCriacao(LocalDateTime.now());
        Evento salvo = repository.save(evento);

        auditoriaService.registrar(usuario.id(), usuario.nome(), "INCLUSAO_EVENTO",
                "Evento '" + nome + "' (código " + codigo + ") incluído.");
        return salvo;
    }

    /** Muda o nome e ativa ou desativa. O código não muda. */
    @Transactional
    public Evento atualizar(String codigo, Evento dados, UsuarioSessao usuario) {
        Evento evento = repository.findById(codigo.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado."));
        String nome = validarNome(dados.getNome());
        if (repository.existsByNomeIgnoreCaseAndCodigoNot(nome, evento.getCodigo())) {
            throw new IllegalArgumentException("Já existe um evento com o nome '" + nome + "'.");
        }
        boolean ativo = dados.getAtivo() == null || dados.getAtivo();
        // Sem nenhum evento ativo, ninguém conseguiria entrar no sistema
        if (!ativo && evento.getAtivo() && repository.countByAtivoTrue() <= 1) {
            throw new IllegalArgumentException("Mantenha pelo menos um evento ativo.");
        }

        String antes = descrever(evento.getNome(), evento.getAtivo());
        evento.setNome(nome);
        evento.setAtivo(ativo);
        Evento salvo = repository.save(evento);

        auditoriaService.registrar(usuario.id(), usuario.nome(), "ALTERACAO_EVENTO",
                "Evento " + salvo.getCodigo() + " alterado de '" + antes + "' para '" + descrever(nome, ativo) + "'.");
        return salvo;
    }

    /** Quanto o evento tem: a tela mostra antes de confirmar a exclusão */
    public ResumoEventoDTO resumir(String codigo) {
        return resumir(buscar(codigo));
    }

    /**
     * Exclui o evento e tudo o que é dele: vendas (com os itens), clientes, produtos (com as imagens),
     * categorias e a auditoria do evento. Usuários não são afetados. Os dados pessoais (PESSOA) de quem
     * era cliente só saem se a pessoa não for usuário nem cliente em outro evento.
     * Não exclui o evento em que o próprio usuário está nem o último evento ativo.
     * Quem estiver logado no evento é desconectado pelo controller, depois da exclusão confirmada.
     */
    @Transactional
    public ResumoEventoDTO excluir(String codigo, UsuarioSessao usuario) {
        Evento evento = buscar(codigo);
        String c = evento.getCodigo();
        if (c.equals(usuario.evento())) {
            throw new IllegalArgumentException("Você está neste evento agora. Para excluí-lo, saia e entre em outro evento.");
        }
        if (evento.getAtivo() && repository.countByAtivoTrue() <= 1) {
            throw new IllegalArgumentException("Mantenha pelo menos um evento ativo.");
        }

        ResumoEventoDTO resumo = resumir(evento);
        List<Integer> pessoaIds = clienteRepository.findByEventoCodigo(c).stream()
                .map(cliente -> cliente.getPessoa().getId()).distinct().toList();

        // Ordem das chaves estrangeiras: o que aponta para outra tabela sai antes
        vendaRepository.excluirItensDoEvento(c);
        vendaRepository.excluirDoEvento(c);
        clienteRepository.excluirDoEvento(c);
        produtoImagemRepository.excluirDoEvento(c);
        produtoRepository.excluirDoEvento(c);
        categoriaRepository.excluirDoEvento(c);
        auditoriaLogRepository.excluirDoEvento(c);
        for (Integer pessoaId : pessoaIds) {
            pessoaRepository.findById(pessoaId)
                    .filter(p -> usuarioRepository.findByPessoa(p).isEmpty() && !clienteRepository.existsByPessoa(p))
                    .ifPresent(pessoaRepository::delete);
        }
        repository.deleteById(c);

        auditoriaService.registrar(usuario.id(), usuario.nome(), "EXCLUSAO_EVENTO",
                "Evento '" + resumo.nome() + "' (código " + c + ") excluído com " + resumo.clientes() + " cliente(s), "
                        + resumo.categorias() + " categoria(s), " + resumo.produtos() + " produto(s) e "
                        + resumo.vendas() + " venda(s).");
        return resumo;
    }

    private Evento buscar(String codigo) {
        return repository.findById(codigo == null ? "" : codigo.trim())
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado."));
    }

    private ResumoEventoDTO resumir(Evento evento) {
        String c = evento.getCodigo();
        return new ResumoEventoDTO(c, evento.getNome(), clienteRepository.countByEventoCodigo(c),
                categoriaRepository.countByEventoCodigo(c), produtoRepository.countByEventoCodigo(c),
                vendaRepository.countByEventoCodigo(c));
    }

    /** Maior código numérico + 1, com zeros à esquerda: 0001, 0002... */
    private String proximoCodigo() {
        int maior = repository.findAll().stream()
                .map(Evento::getCodigo)
                .filter(c -> c != null && c.matches("\\d{1,4}"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        if (maior >= CODIGO_MAXIMO) {
            throw new IllegalArgumentException("Limite de " + CODIGO_MAXIMO + " eventos atingido.");
        }
        return String.format("%04d", maior + 1);
    }

    private static String descrever(String nome, boolean ativo) {
        return ativo ? nome : nome + " (inativo)";
    }

    private static String validarNome(String nome) {
        String limpo = nome == null ? "" : nome.trim().replaceAll("\\s+", " ").toUpperCase(Locale.forLanguageTag("pt-BR"));
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("O nome do evento é obrigatório.");
        }
        if (limpo.length() > 100) {
            throw new IllegalArgumentException("O nome do evento deve ter até 100 caracteres.");
        }
        return limpo;
    }
}
