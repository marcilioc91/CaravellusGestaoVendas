package com.caravellus.backend.service;

import com.caravellus.backend.dto.CadastroRequestDTO;
import com.caravellus.backend.entity.Cliente;
import com.caravellus.backend.entity.Pessoa;
import com.caravellus.backend.entity.PerfilUsuario;
import com.caravellus.backend.entity.Usuario;
import java.util.List;
import java.util.Optional;
import com.caravellus.backend.repository.ClienteRepository;
import com.caravellus.backend.repository.PessoaRepository;
import com.caravellus.backend.repository.UsuarioRepository;
import com.caravellus.backend.util.ValidacaoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {
    static final String CPF_COM_USUARIO = "Este CPF já possui usuário.";

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private DuplicidadeService duplicidadeService;

    public List<Usuario> listarTodos() {
        return repository.findAll();
    }

    public void resetSenha(Long id, String novaSenha) {
        if (novaSenha == null || novaSenha.isBlank()) {
            throw new IllegalArgumentException("A nova senha não pode ser vazia.");
        }
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        repository.save(usuario);
    }

    /** Troca de senha pelo próprio usuário (exige a senha atual); usada na troca obrigatória do primeiro acesso */
    public Usuario trocarSenha(String login, String senhaAtual, String novaSenha) {
        Usuario usuario = autenticar(login, senhaAtual);
        if (usuario == null) {
            throw new IllegalArgumentException("Senha atual incorreta.");
        }
        if (novaSenha == null || novaSenha.isBlank()) {
            throw new IllegalArgumentException("A nova senha não pode ser vazia.");
        }
        if (passwordEncoder.matches(novaSenha, usuario.getSenha())) {
            throw new IllegalArgumentException("A nova senha deve ser diferente da senha atual.");
        }
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setTrocarSenha(false);
        Usuario salvo = repository.save(usuario);

        String nome = usuario.getPessoa() != null ? usuario.getPessoa().getNome() : usuario.getUsuarioLogin();
        auditoriaService.registrar(usuario.getId(), nome, "TROCA_SENHA",
                "Usuário '" + usuario.getUsuarioLogin() + "' trocou a própria senha.");
        return salvo;
    }

    public Usuario atualizarPerfil(Long id, PerfilUsuario perfil) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        usuario.setPerfil(perfil);
        return repository.save(usuario);
    }

    public Usuario autenticar(String login, String senha) {
        return repository.findByUsuarioLogin(login)
                .filter(u -> passwordEncoder.matches(senha, u.getSenha()))
                .orElse(null);
    }

    /**
     * Pessoa já cadastrada com o CPF e que ainda não é usuário (cliente a promover); vazio se o CPF é novo.
     * CPF que já pertence a um usuário: IllegalStateException.
     */
    public Optional<Pessoa> buscarPessoaParaPromover(String cpf) {
        String cpfLimpo = cpf == null ? "" : cpf.replaceAll("[^0-9]", "");
        Optional<Pessoa> pessoa = pessoaRepository.findByCpf(cpfLimpo);
        if (pessoa.isPresent() && repository.findByPessoa(pessoa.get()).isPresent()) {
            throw new IllegalStateException(CPF_COM_USUARIO);
        }
        return pessoa;
    }

    /**
     * "Sou eu": cadastro existente apontado pelo aviso de duplicidade. Só vale se continuar parecido com os
     * dados informados, ainda não tiver usuário e não tiver outro CPF.
     */
    private Pessoa pessoaEscolhida(CadastroRequestDTO dto, String cpfLimpo) {
        Pessoa pessoa = pessoaRepository.findById(dto.getPessoaIdExistente())
                .orElseThrow(() -> new IllegalArgumentException("Cadastro não encontrado."));
        if (!DuplicidadeService.mesmaPessoa(dto.getNome(), dto.getTelefone(), pessoa.getNome(), pessoa.getTelefone())) {
            throw new IllegalArgumentException("Os dados informados não conferem com o cadastro escolhido.");
        }
        if (repository.findByPessoa(pessoa).isPresent()) {
            throw new IllegalArgumentException("Este cadastro já possui usuário.");
        }
        if (cpfLimpo != null && pessoa.getCpf() != null && !cpfLimpo.equals(pessoa.getCpf())) {
            throw new IllegalArgumentException("O CPF informado é diferente do CPF do cadastro escolhido.");
        }
        return pessoa;
    }

    /** Cadastra o usuário; se o CPF já for de um cliente, reaproveita a pessoa (promoção de cliente a usuário). */
    @Transactional
    public Usuario cadastrar(CadastroRequestDTO dto) {
        String cpf = dto.getCpf();
        String cpfLimpo = null;
        Pessoa existente = null;
        if (cpf != null && !cpf.isBlank()) {
            cpfLimpo = cpf.replaceAll("[^0-9]", "");
            if (!ValidacaoUtil.cpfValido(cpfLimpo)) {
                throw new IllegalArgumentException("CPF inválido.");
            }
            try {
                existente = buscarPessoaParaPromover(cpfLimpo).orElse(null);
            } catch (IllegalStateException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }
        if (existente == null && dto.getPessoaIdExistente() != null) {
            existente = pessoaEscolhida(dto, cpfLimpo);
        }

        if (!ValidacaoUtil.emailValido(dto.getEmail())) {
            throw new IllegalArgumentException("E-mail inválido.");
        }

        boolean promocao = existente != null;
        if (!promocao && !dto.isConfirmarDuplicidade()) {
            duplicidadeService.verificar(dto.getNome(), dto.getTelefone());
        }
        Pessoa pessoa = promocao ? existente : new Pessoa();
        if (pessoa.getCpf() == null) {
            pessoa.setCpf(cpfLimpo);
        }
        // Na promoção, os dados do formulário (pré-preenchidos com os do cliente) atualizam o cadastro
        if (dto.getNome() != null && !dto.getNome().isBlank()) {
            pessoa.setNome(dto.getNome());
        }
        if (dto.getTelefone() != null && !dto.getTelefone().isBlank()) {
            pessoa.setTelefone(dto.getTelefone());
        }
        pessoa = pessoaRepository.save(pessoa);

        Usuario usuario = new Usuario();
        usuario.setPessoa(pessoa);
        usuario.setUsuarioLogin(dto.getUsuario());
        usuario.setEmail(dto.getEmail());
        usuario.setSenha(passwordEncoder.encode(dto.getSenha()));
        usuario.setPerfil(PerfilUsuario.OPERADOR);
        usuario = repository.save(usuario);

        // Todo usuário também é cliente; na promoção o cliente já existe (com o histórico de compras)
        if (!promocao || clienteRepository.findByPessoa(pessoa).isEmpty()) {
            Cliente cliente = new Cliente();
            cliente.setPessoa(pessoa);
            cliente.setObs(dto.getObs());
            clienteRepository.save(cliente);
        }

        if (promocao) {
            auditoriaService.registrar(usuario.getId(), pessoa.getNome(), "PROMOCAO_CLIENTE_USUARIO",
                    "Cliente '" + pessoa.getNome() + "' promovido a usuário com o login '" + usuario.getUsuarioLogin() + "'.");
        }

        return usuario;
    }
}
