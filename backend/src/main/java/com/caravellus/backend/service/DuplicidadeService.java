package com.caravellus.backend.service;

import com.caravellus.backend.dto.PessoaSemelhanteDTO;
import com.caravellus.backend.entity.Pessoa;
import com.caravellus.backend.repository.PessoaRepository;
import com.caravellus.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Detecta pessoas provavelmente já cadastradas, mesmo sem CPF:
 * mesmo nome completo (ignorando acentos, maiúsculas e espaços) ou
 * mesmo celular (últimos 8 dígitos, absorve DDD ou 9 faltando) com o mesmo primeiro nome.
 * O celular sozinho não basta: irmãos e casais costumam compartilhar o número.
 */
@Service
public class DuplicidadeService {

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public List<Pessoa> semelhantes(String nome, String telefone) {
        return semelhantes(nome, telefone, pessoaRepository.findAll());
    }

    public static List<Pessoa> semelhantes(String nome, String telefone, Collection<Pessoa> candidatas) {
        return candidatas.stream()
                .filter(p -> mesmaPessoa(nome, telefone, p.getNome(), p.getTelefone()))
                .toList();
    }

    public static boolean mesmaPessoa(String nomeA, String telefoneA, String nomeB, String telefoneB) {
        String a = normalizarNome(nomeA);
        String b = normalizarNome(nomeB);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        String chaveA = chaveTelefone(telefoneA);
        return chaveA != null && chaveA.equals(chaveTelefone(telefoneB)) && primeiroNome(a).equals(primeiroNome(b));
    }

    /** Lança DuplicidadeException se houver pessoa semelhante já cadastrada. */
    public void verificar(String nome, String telefone) {
        List<Pessoa> encontradas = semelhantes(nome, telefone);
        if (!encontradas.isEmpty()) {
            throw new DuplicidadeException(encontradas.stream().map(this::paraDto).toList());
        }
    }

    private PessoaSemelhanteDTO paraDto(Pessoa p) {
        return new PessoaSemelhanteDTO(p.getId(), p.getNome(), p.getTelefone(),
                usuarioRepository.findByPessoa(p).isPresent());
    }

    /** Sem acentos, maiúsculo e com espaços simples. */
    public static String normalizarNome(String nome) {
        if (nome == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    static String primeiroNome(String nomeNormalizado) {
        int espaco = nomeNormalizado.indexOf(' ');
        return espaco < 0 ? nomeNormalizado : nomeNormalizado.substring(0, espaco);
    }

    static String chaveTelefone(String telefone) {
        String digitos = telefone == null ? "" : telefone.replaceAll("\\D", "");
        return digitos.length() >= 8 ? digitos.substring(digitos.length() - 8) : null;
    }
}
