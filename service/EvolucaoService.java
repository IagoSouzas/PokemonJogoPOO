package JogoPokemon.service;

import JogoPokemon.repository.PokemonRepository;

import java.sql.SQLException;
import java.util.Map;

/**
 * Evolução automática nos níveis 6 e 14, usando dados gravados em evolucao_pkm na Fase 1.
 * <p>
 * Não chama API: só consulta SQL qual é o próximo nome e troca id_pokemon no pokemon_jogador.
 * <p>
 * Caso o Pokémon já tenha evoluído (ex: Bayleef), busca evolução pela cadeia via nome atual
 * (JOIN em buscarEvolucaoPorNomeAtual).
 */
public class EvolucaoService {

    private final PokemonRepository repository;

    public EvolucaoService(PokemonRepository repository) {
        this.repository = repository;
    }

    /**
     * Chamado após ganhar XP e possivelmente subir de nível.
     * Prioridade: nível 14 (2ª evo) antes de checar 6, para não evoluir duas vezes no mesmo tick.
     */
    public void verificarEvolucao(Map<String, Object> pokemonJogador) throws SQLException {
        int nivel = (Integer) pokemonJogador.get("nivel");
        String nome = (String) pokemonJogador.get("nome");
        int idCatalogo = (Integer) pokemonJogador.get("id_pokemon");
        int pokemonJogadorId = (Integer) pokemonJogador.get("id");

        if (nivel >= 14) {
            evoluirSePossivel(pokemonJogador, pokemonJogadorId, buscarEvo(idCatalogo, nome, 2), nivel);
        } else if (nivel >= 6) {
            evoluirSePossivel(pokemonJogador, pokemonJogadorId, buscarEvo(idCatalogo, nome, 1), nivel);
        }
    }

    /** Tenta pela espécie base; se falhar, pela forma intermediária (ex: Bayleef → Meganium). */
    private Map<String, Object> buscarEvo(int idCatalogo, String nomeAtual, int ordem) throws SQLException {
        Map<String, Object> evo = repository.buscarEvolucaoPorOrdem(idCatalogo, ordem);
        if (evo == null) {
            evo = repository.buscarEvolucaoPorNomeAtual(nomeAtual, ordem);
        }
        return evo;
    }

    private void evoluirSePossivel(Map<String, Object> pokemonJogador, int pokemonJogadorId,
                                   Map<String, Object> evolucao, int nivel) throws SQLException {
        if (evolucao == null) return;

        String nomeEvo = (String) evolucao.get("nome_evolucao");
        if (nomeEvo.equalsIgnoreCase((String) pokemonJogador.get("nome"))) return;

        Integer idNovo = repository.buscarIdCatalogoPorNomeEvolucao(nomeEvo);
        if (idNovo == null) return;

        String antes = (String) pokemonJogador.get("nome");
        repository.evoluirPokemonJogador(pokemonJogadorId, idNovo, nivel);

        // Recarrega do banco para o Map ter nome, stats e movimentos da nova forma
        Map<String, Object> atualizado = repository.buscarPokemonJogadorAtivo((Integer) pokemonJogador.get("id_jogador"));
        pokemonJogador.clear();
        pokemonJogador.putAll(atualizado);

        System.out.println("★★ EVOLUÇÃO! " + antes + " evoluiu para " + pokemonJogador.get("nome") + "! ★★");
    }
}
