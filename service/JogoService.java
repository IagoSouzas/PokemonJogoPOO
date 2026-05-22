package JogoPokemon.service;

import JogoPokemon.repository.PokemonRepository;
import JogoPokemon.service.BatalhaService.Resultado;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Orquestra a Fase 3: escolha do inicial, loop de menu, batalhas e pós-batalha (XP + evolução).
 * <p>
 * Não implementa rotas (Fase 4 — outro integrante). Oponente é sorteado do catálogo por enquanto.
 * <p>
 * Save do jogo: dados ficam nas tabelas jogador / pokemon_jogador; ao reiniciar, carregarJornada()
 * pergunta se deseja continuar.
 */
public class JogoService {

    private final PokemonRepository repository;
    private final BatalhaService batalhaService;
    private final XpService xpService;
    private final EvolucaoService evolucaoService;
    private final Scanner scanner;

    public JogoService(PokemonRepository repository, Scanner scanner) {
        this.repository = repository;
        this.scanner = scanner;
        this.batalhaService = new BatalhaService(repository);
        this.xpService = new XpService(repository);
        this.evolucaoService = new EvolucaoService(repository);
    }

    public void iniciar() throws SQLException {
        if (!repository.catalogoJaPopulado()) {
            System.out.println("Catálogo vazio! Execute a opção 1 primeiro.");
            return;
        }

        Map<String, Object> jornada = repository.carregarJornada();
        int jogadorId;
        Map<String, Object> pokemon;

        if (jornada != null) {
            System.out.print("Jornada encontrada. Continuar? (s/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                jogadorId = (Integer) jornada.get("jogador_id");
                pokemon = (Map<String, Object>) jornada.get("pokemon_jogador");
                loopJogo(jogadorId, pokemon);
                return;
            }
        }

        jogadorId = criarNovoJogador();
        pokemon = repository.buscarPokemonJogadorAtivo(jogadorId);
        loopJogo(jogadorId, pokemon);
    }

    /**
     * Novo treinador: INSERT jogador + escolha entre os 3 iniciais (eh_inicial no banco).
     */
    private int criarNovoJogador() throws SQLException {
        System.out.print("Nome do treinador: ");
        String nome = scanner.nextLine().trim();
        if (nome.isEmpty()) nome = "Treinador";
        int jogadorId = repository.criarJogador(nome);

        List<Map<String, Object>> iniciais = repository.listarIniciais();
        System.out.println("\nEscolha seu inicial:\n");
        for (int i = 0; i < iniciais.size(); i++) {
            Map<String, Object> p = iniciais.get(i);
            System.out.printf("[%d] %s | %s | HP:%s ATK:%s DEF:%s%n",
                    i + 1, p.get("nome"), p.get("tipo"), p.get("vida"), p.get("ataque"), p.get("defesa"));
        }
        int escolha;
        do {
            System.out.print("Escolha: ");
            try { escolha = Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException e) { escolha = 0; }
        } while (escolha < 1 || escolha > iniciais.size());

        // Nível 5 inicial — regra comum em jogos Pokémon de demonstração
        repository.associarPokemonJogador(jogadorId, (Integer) iniciais.get(escolha - 1).get("id"), 5);
        System.out.println("Você escolheu " + iniciais.get(escolha - 1).get("nome") + "!");
        return jogadorId;
    }

    private void loopJogo(int jogadorId, Map<String, Object> pokemon) throws SQLException {
        boolean ativo = true;
        while (ativo && pokemon != null && (Integer) pokemon.get("hp_atual") > 0) {
            pokemon = repository.buscarPokemonJogadorAtivo(jogadorId);
            System.out.println("\n══ " + pokemon.get("nome") + " Nv." + pokemon.get("nivel")
                    + " | HP:" + pokemon.get("hp_atual") + "/" + pokemon.get("hp_max")
                    + " | Poções:" + repository.getPocoes(jogadorId) + " ══");
            System.out.println("1. Batalhar (oponente aleatório)");
            System.out.println("2. Ver status");
            System.out.println("3. Sair");
            System.out.print("Opção: ");
            int op;
            try { op = Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException e) { op = 0; }

            switch (op) {
                case 1 -> pokemon = batalhar(jogadorId, pokemon);
                case 2 -> imprimirStatus(pokemon);
                case 3 -> ativo = false;
                default -> System.out.println("Opção inválida.");
            }
        }
        System.out.println("Até a próxima!");
    }

    /**
     * Sequência após batalha: vitória → XP → evolução; derrota → cura total; fuga → nada.
     */
    private Map<String, Object> batalhar(int jogadorId, Map<String, Object> pokemon) throws SQLException {
        int nivel = (Integer) pokemon.get("nivel");
        Map<String, Object> inimigo = repository.buscarOponenteAleatorio(nivel);
        if (inimigo == null) {
            System.out.println("Nenhum oponente disponível.");
            return pokemon;
        }
        System.out.println("Um " + inimigo.get("nome") + " selvagem (Nv." + nivel + ") apareceu!");

        try {
            Resultado r = batalhaService.executar(scanner, jogadorId, pokemon, inimigo);
            pokemon = repository.buscarPokemonJogadorAtivo(jogadorId);

            if (r == Resultado.VITORIA) {
                xpService.processarVitoria(pokemon);
                evolucaoService.verificarEvolucao(pokemon);
                repository.incrementarInimigosDerrotados(jogadorId);
                pokemon = repository.buscarPokemonJogadorAtivo(jogadorId);
            } else if (r == Resultado.DERROTA) {
                int hpMax = (Integer) pokemon.get("hp_max");
                repository.atualizarHpPokemonJogador((Integer) pokemon.get("id"), hpMax);
                pokemon.put("hp_atual", hpMax);
                System.out.println("Recuperou no Centro Pokémon.");
            }
        } catch (SQLException e) {
            System.out.println("Erro na batalha: " + e.getMessage());
        }
        return repository.buscarPokemonJogadorAtivo(jogadorId);
    }

    @SuppressWarnings("unchecked")
    private void imprimirStatus(Map<String, Object> pokemon) {
        System.out.println("\n--- " + pokemon.get("nome") + " ---");
        System.out.println("Nv." + pokemon.get("nivel") + " | XP:" + pokemon.get("xp")
                + "/" + xpService.xpParaProximoNivel((Integer) pokemon.get("nivel")));
        System.out.println("HP:" + pokemon.get("hp_atual") + "/" + pokemon.get("hp_max"));
        for (Map<String, Object> m : (List<Map<String, Object>>) pokemon.get("movimentos")) {
            System.out.println("  " + m.get("nome") + " PP:" + m.get("pp_atual") + "/" + m.get("pp_max")
                    + " [" + m.get("tipo") + "] Prec:" + m.get("precisao") + "%");
        }
    }
}
