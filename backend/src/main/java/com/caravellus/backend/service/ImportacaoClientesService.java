package com.caravellus.backend.service;

import com.caravellus.backend.dto.ImportacaoClientesDTO;
import com.caravellus.backend.dto.ImportacaoClientesDTO.Linha;
import com.caravellus.backend.dto.ImportacaoClientesDTO.Situacao;
import com.caravellus.backend.entity.Cliente;
import com.caravellus.backend.entity.Pessoa;
import com.caravellus.backend.repository.ClienteRepository;
import com.caravellus.backend.repository.PessoaRepository;
import com.caravellus.backend.util.LeitorCsv;
import com.caravellus.backend.util.ValidacaoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Importação de clientes a partir de CSV. As colunas são reconhecidas pelo nome do cabeçalho, então serve
 * tanto o CSV do Google Forms ("Nome completo", "Celular", "Nome do responsável"...) quanto o modelo
 * do sistema (Nome; CPF; Telefone; Observações). Colunas desconhecidas são ignoradas.
 * Pessoas já cadastradas (ou repetidas no arquivo) são ignoradas, pela mesma regra do DuplicidadeService.
 */
@Service
public class ImportacaoClientesService {

    static final int TAMANHO_OBS = 255;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    // Cabeçalhos aceitos, sem acento e em minúsculas
    private static final List<String> COL_NOME = List.of("nome completo", "nome");
    private static final List<String> COL_TELEFONE = List.of("celular", "telefone", "whatsapp");
    private static final List<String> COL_CPF = List.of("cpf");
    // "Observacoes" (plural, como no modelo): o Forms tem uma "Observação" livre (pagamento etc.) que fica de fora
    private static final List<String> COL_OBS = List.of("observacoes", "obs");
    private static final List<String> COL_RESPONSAVEL = List.of("nome do responsavel", "responsavel");
    private static final List<String> COL_CELULAR_RESPONSAVEL = List.of("celular do responsavel", "telefone do responsavel");
    private static final List<String> COL_RESTRICAO = List.of("restricao alimentar");
    private static final List<String> COL_ALERGIA = List.of("alergia", "alergias");

    /** Respostas de formulário que significam "nada a informar" */
    private static final Set<String> SEM_VALOR = Set.of("", "-", ".", "NAO", "NAO TEM", "NAO POSSUI", "NAO HA",
            "NENHUM", "NENHUMA", "N/A", "NA", "NADA", "SEM", "X");

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    /** confirmar=false só monta a prévia; confirmar=true grava os novos clientes (tudo ou nada). */
    @Transactional
    public ImportacaoClientesDTO importar(byte[] conteudo, String nomeArquivo, boolean confirmar,
                                         Long usuarioId, String usuarioNome) {
        List<List<String>> registros = LeitorCsv.ler(conteudo);
        if (registros.size() < 2) {
            throw new IllegalArgumentException("O arquivo não tem clientes para importar.");
        }

        Map<String, Integer> colunas = mapearColunas(registros.get(0));
        if (!colunas.containsKey("nome")) {
            throw new IllegalArgumentException("Coluna de nome não encontrada: o cabeçalho precisa ter \"Nome\" ou \"Nome completo\".");
        }

        List<Pessoa> cadastradas = pessoaRepository.findAll();
        Set<String> cpfsCadastrados = new HashSet<>();
        cadastradas.stream().map(Pessoa::getCpf).filter(c -> c != null && !c.isBlank()).forEach(cpfsCadastrados::add);

        // Novos do próprio arquivo, para detectar repetição dentro dele
        List<Pessoa> novas = new ArrayList<>();
        Map<Pessoa, Integer> linhaDaNova = new IdentityHashMap<>();
        Set<String> cpfsNovos = new HashSet<>();

        ImportacaoClientesDTO resultado = new ImportacaoClientesDTO();
        resultado.setConfirmado(confirmar);

        for (int i = 1; i < registros.size(); i++) {
            List<String> registro = registros.get(i);
            int numeroLinha = i + 1;
            List<String> avisos = new ArrayList<>();

            String nome = formatarNome(valor(registro, colunas, "nome"));
            String telefone = normalizarTelefone(valor(registro, colunas, "telefone"));
            String obs = montarObs(registro, colunas, nome, avisos);

            Linha linha = new Linha(numeroLinha, nome, telefone, obs, Situacao.NOVO, null, avisos);
            resultado.getLinhas().add(linha);

            if (nome.isEmpty()) {
                marcar(linha, Situacao.ERRO, "Nome não informado.", resultado);
                continue;
            }
            if (telefone == null) {
                avisos.add("Sem celular.");
            } else if (telefone.length() < 10) {
                avisos.add("Celular sem DDD.");
            }

            String cpf = limparCpf(valor(registro, colunas, "cpf"), avisos);

            if (cpf != null && (cpfsCadastrados.contains(cpf) || cpfsNovos.contains(cpf))) {
                marcar(linha, Situacao.DUPLICADO, cpfsCadastrados.contains(cpf)
                        ? "CPF já cadastrado." : "CPF repetido no arquivo.", resultado);
                continue;
            }
            List<Pessoa> jaCadastradas = DuplicidadeService.semelhantes(nome, telefone, cadastradas);
            if (!jaCadastradas.isEmpty()) {
                marcar(linha, Situacao.DUPLICADO, "Já cadastrado: " + jaCadastradas.get(0).getNome() + ".", resultado);
                continue;
            }
            List<Pessoa> repetidas = DuplicidadeService.semelhantes(nome, telefone, novas);
            if (!repetidas.isEmpty()) {
                marcar(linha, Situacao.DUPLICADO, "Repetido no arquivo (linha " + linhaDaNova.get(repetidas.get(0)) + ").", resultado);
                continue;
            }

            Pessoa pessoa = new Pessoa();
            pessoa.setNome(nome);
            pessoa.setCpf(cpf);
            pessoa.setTelefone(telefone);
            novas.add(pessoa);
            linhaDaNova.put(pessoa, numeroLinha);
            if (cpf != null) {
                cpfsNovos.add(cpf);
            }
            resultado.setIncluidos(resultado.getIncluidos() + 1);

            if (confirmar) {
                Cliente cliente = new Cliente();
                cliente.setPessoa(pessoaRepository.save(pessoa));
                cliente.setObs(obs);
                clienteRepository.save(cliente);
            }
        }

        if (confirmar && resultado.getIncluidos() > 0) {
            auditoriaService.registrar(usuarioId, usuarioNome, "IMPORTACAO_CLIENTES",
                    "Importação do arquivo '" + (nomeArquivo == null ? "" : nomeArquivo) + "': "
                            + resultado.getIncluidos() + " cliente(s) incluído(s), "
                            + resultado.getIgnorados() + " ignorado(s) por já estarem cadastrados, "
                            + resultado.getErros() + " com erro.");
        }
        return resultado;
    }

    private static void marcar(Linha linha, Situacao situacao, String motivo, ImportacaoClientesDTO resultado) {
        linha.setSituacao(situacao);
        linha.setMotivo(motivo);
        if (situacao == Situacao.DUPLICADO) {
            resultado.setIgnorados(resultado.getIgnorados() + 1);
        } else {
            resultado.setErros(resultado.getErros() + 1);
        }
    }

    /** Campo lógico -> índice da coluna; vale o primeiro cabeçalho que bater com um dos nomes aceitos. */
    private static Map<String, Integer> mapearColunas(List<String> cabecalho) {
        List<String> normalizado = cabecalho.stream().map(ImportacaoClientesService::normalizarCabecalho).toList();
        Map<String, List<String>> campos = Map.of(
                "nome", COL_NOME, "telefone", COL_TELEFONE, "cpf", COL_CPF, "obs", COL_OBS,
                "responsavel", COL_RESPONSAVEL, "celularResponsavel", COL_CELULAR_RESPONSAVEL,
                "restricao", COL_RESTRICAO, "alergia", COL_ALERGIA);
        Map<String, Integer> colunas = new HashMap<>();
        campos.forEach((campo, aceitos) -> {
            for (String aceito : aceitos) {
                int indice = normalizado.indexOf(aceito);
                if (indice >= 0) {
                    colunas.put(campo, indice);
                    return;
                }
            }
        });
        return colunas;
    }

    private static String normalizarCabecalho(String texto) {
        return DuplicidadeService.normalizarNome(texto).toLowerCase(Locale.ROOT);
    }

    private static String valor(List<String> registro, Map<String, Integer> colunas, String campo) {
        Integer indice = colunas.get(campo);
        if (indice == null || indice >= registro.size()) {
            return "";
        }
        return registro.get(indice).trim().replaceAll("\\s+", " ");
    }

    private static boolean temValor(String texto) {
        String normalizado = DuplicidadeService.normalizarNome(texto).replaceAll("[.!]+$", "");
        // "Sem restrições", "Sem alergias"
        return !SEM_VALOR.contains(normalizado) && !normalizado.startsWith("SEM ");
    }

    static String formatarNome(String nome) {
        return nome.trim().replaceAll("\\s+", " ").toUpperCase(PT_BR);
    }

    /**
     * Só dígitos, no padrão DDD + número: tira o 55 do país e põe o 9 em celular antigo de 10 dígitos.
     * Número sem DDD fica como veio. Vazio: null.
     */
    static String normalizarTelefone(String telefone) {
        String digitos = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (digitos.startsWith("55") && (digitos.length() == 12 || digitos.length() == 13)) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() == 10 && digitos.charAt(2) >= '6') {
            digitos = digitos.substring(0, 2) + "9" + digitos.substring(2);
        }
        return digitos.isEmpty() ? null : digitos;
    }

    private static String limparCpf(String cpf, List<String> avisos) {
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return null;
        }
        if (!ValidacaoUtil.cpfValido(digitos)) {
            avisos.add("CPF inválido: importado sem CPF.");
            return null;
        }
        return digitos;
    }

    /** Observação do arquivo + responsável + restrição alimentar + alergia, separados por " | ". */
    private static String montarObs(List<String> registro, Map<String, Integer> colunas, String nome, List<String> avisos) {
        List<String> partes = new ArrayList<>();

        String obs = valor(registro, colunas, "obs");
        if (temValor(obs)) {
            partes.add(obs);
        }

        String responsavel = valor(registro, colunas, "responsavel");
        // Adulto que se colocou como o próprio responsável
        boolean eleMesmo = DuplicidadeService.mesmaPessoa(responsavel, null, nome, null)
                || DuplicidadeService.normalizarNome(responsavel).equals(DuplicidadeService.primeiroNome(DuplicidadeService.normalizarNome(nome)));
        if (temValor(responsavel) && !eleMesmo) {
            String celular = formatarTelefones(valor(registro, colunas, "celularResponsavel"));
            partes.add("Resp.: " + responsavel + (celular.isEmpty() ? "" : " " + celular));
        }

        String restricao = valor(registro, colunas, "restricao");
        if (temValor(restricao)) {
            partes.add("Restrição alimentar: " + restricao);
        }
        String alergia = valor(registro, colunas, "alergia");
        if (temValor(alergia)) {
            partes.add("Alergia: " + alergia);
        }

        if (partes.isEmpty()) {
            return null;
        }
        String texto = String.join(" | ", partes);
        if (texto.length() > TAMANHO_OBS) {
            avisos.add("Observação cortada em " + TAMANHO_OBS + " caracteres.");
            texto = texto.substring(0, TAMANHO_OBS - 1) + "…";
        }
        return texto;
    }

    /** "(81) 99350-4481"; dois celulares colados no mesmo campo viram "(81) 9... / (81) 9..."; o resto fica como veio. */
    private static String formatarTelefones(String texto) {
        String digitos = texto.replaceAll("\\D", "");
        if (digitos.length() == 22) {
            return formatarTelefone(digitos.substring(0, 11)) + " / " + formatarTelefone(digitos.substring(11));
        }
        String normalizado = normalizarTelefone(digitos);
        return normalizado != null && normalizado.length() == 11 ? formatarTelefone(normalizado) : texto;
    }

    private static String formatarTelefone(String onzeDigitos) {
        return "(" + onzeDigitos.substring(0, 2) + ") " + onzeDigitos.substring(2, 7) + "-" + onzeDigitos.substring(7);
    }
}
