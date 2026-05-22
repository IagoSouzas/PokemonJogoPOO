package JogoPokemon.service;

import JogoPokemon.repository.PokemonRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Fase 2: prova que o catálogo no H2 está correto — nenhuma chamada HTTP aqui.
 * <p>
 * Usado pelo menu opção 2 para demonstrar os métodos SELECT do Repository.
 */
public class ConsultaService {

    private final PokemonRepository repository;

    public ConsultaService(PokemonRepository repository) {
        this.repository = repository;
    }

    /**
     * Roteiro de testes no console: contagens, iniciais, busca por id/nome, evoluções e movimentos.
     */
    public void executarDemonstracao() throws SQLException {
        System.out.println("\n═══ FASE 2 — Consultas SQL (sem API) ═══");
        System.out.println("Total Pokémon: " + repository.contarPokemon());
        System.out.println("Iniciais: " + repository.contarIniciais());
        System.out.println("Evoluções: " + repository.contarEvolucoes());
        System.out.println("Movimentos: " + repository.contarMovimentos());

        System.out.println("\n--- Iniciais (eh_inicial = TRUE) ---");
        for (Map<String, Object> p : repository.listarIniciais()) {
            imprimirResumo(p);
        }

        // Bulbasaur = id interno 1, útil para ver evoluções e movimentos encadeados
        Map<String, Object> primeiro = repository.buscarPokemonPorId(1);
        if (primeiro != null) {
            System.out.println("\n--- buscarPokemonPorId(1) ---");
            imprimirResumo(primeiro);
            System.out.println("Evoluções:");
            for (Map<String, Object> evo : repository.listarEvolucoes((Integer) primeiro.get("id"))) {
                System.out.println("  " + evo.get("ordem") + " → " + evo.get("nome_evolucao")
                        + " (nv " + evo.get("nivel_minimo") + ")");
            }
            System.out.println("Movimentos:");
            for (Map<String, Object> mov : repository.listarMovimentos((Integer) primeiro.get("id"))) {
                System.out.println("  " + mov.get("nome") + " | PP:" + mov.get("pp_max")
                        + " | " + mov.get("tipo") + " | Prec:" + mov.get("precisao") + "%");
            }
        }

        Map<String, Object> porNome = repository.buscarPokemonPorNome("Pikachu");
        if (porNome != null) {
            System.out.println("\n--- buscarPokemonPorNome('Pikachu') ---");
            imprimirResumo(porNome);
        }
    }

    private void imprimirResumo(Map<String, Object> p) {
        System.out.printf("  #%s %s | %s/%s | HP:%s ATK:%s DEF:%s%n",
                p.get("id_pokemon_api"), p.get("nome"), p.get("tipo"),
                p.get("tipo2") != null ? p.get("tipo2") : "-",
                p.get("vida"), p.get("ataque"), p.get("defesa"));
    }
}
