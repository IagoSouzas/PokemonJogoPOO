package JogoPokemon.service;

import JogoPokemon.repository.PokemonRepository;

import java.sql.SQLException;
import java.util.Map;

/**
 * Regra de progressão após vitória na batalha.
 * <p>
 * XP necessário para subir = nivel * 100 (ex: nível 5 → 500 XP para ir ao 6).
 * Ganho por vitória = 40 + nivel * 10.
 * <p>
 * Ao subir de nível, recupera parte do HP (hpMax/4) sem ultrapassar o máximo.
 */
public class XpService {

    private final PokemonRepository repository;

    public XpService(PokemonRepository repository) {
        this.repository = repository;
    }

    /**
     * Soma XP, aplica level-ups em loop (pode subir mais de 1 nível se XP sobrar)
     * e persiste no banco via UPDATE pokemon_jogador.
     *
     * @return true se o Pokémon subiu pelo menos um nível (útil para acionar evolução depois)
     */
    public boolean processarVitoria(Map<String, Object> pokemonJogador) throws SQLException {
        int nivel = (Integer) pokemonJogador.get("nivel");
        int xp = (Integer) pokemonJogador.get("xp") + 40 + (nivel * 10);
        boolean subiu = false;

        // Enquanto XP acumulado alcança o limiar do nível atual, sobe e desconta o custo
        while (xp >= xpParaProximoNivel(nivel)) {
            xp -= xpParaProximoNivel(nivel);
            nivel++;
            subiu = true;
        }

        int hpMax = repository.calcularHpMax((Integer) pokemonJogador.get("vida_base"), nivel);
        int hpAtual = Math.min(hpMax, (Integer) pokemonJogador.get("hp_atual") + (hpMax / 4));

        repository.atualizarXpNivel((Integer) pokemonJogador.get("id"), xp, nivel, hpAtual);

        // Atualiza o Map em memória para os próximos passos (evolução, exibição) sem novo SELECT
        pokemonJogador.put("xp", xp);
        pokemonJogador.put("nivel", nivel);
        pokemonJogador.put("hp_atual", hpAtual);
        pokemonJogador.put("hp_max", hpMax);

        System.out.println("+" + (40 + nivel * 10) + " XP!");
        if (subiu) {
            System.out.println("★ Subiu para o nível " + nivel + "!");
        }
        return subiu;
    }

    public int xpParaProximoNivel(int nivel) {
        return nivel * 100;
    }
}
