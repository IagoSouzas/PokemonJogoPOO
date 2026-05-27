package JogoPokemon.service;

import JogoPokemon.repository.PokemonRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Orquestra a Fase 3: escolha do inicial, loop de menu, batalhas, rotas e pós-batalha (XP + evolução).
 * <p>
 * As rotas agora vivem em `service/Rota.java`, seguindo a mesma persistência em H2 do restante do jogo.
 * <p>
 * Save do jogo: dados ficam nas tabelas jogador / pokemon_jogador; ao reiniciar, carregarJornada()
 * pergunta se deseja continuar.
 */
public class JogoService {

    private final PokemonRepository repository;
    private final BatalhaService batalhaService;
    private final XpService xpService;
    private final EvolucaoService evolucaoService;
    private final Rota rotaService;
    private final Scanner scanner;

    public JogoService(PokemonRepository repository, Scanner scanner) {
        this.repository = repository;
        this.scanner = scanner;
        this.batalhaService = new BatalhaService(repository);
        this.xpService = new XpService(repository);
        this.evolucaoService = new EvolucaoService(repository);
        this.rotaService = new Rota(repository, batalhaService, xpService, evolucaoService, scanner);
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
            Map<String, Object> estadoRota = repository.buscarEstadoRotaJogador(jogadorId);
            System.out.println("\n══ " + pokemon.get("nome") + " Nv." + pokemon.get("nivel")
                    + " | HP:" + pokemon.get("hp_atual") + "/" + pokemon.get("hp_max")
                    + " | Poções:" + repository.getPocoes(jogadorId) + " ══");
            System.out.println("Rota atual: " + valor(estadoRota, "rota_atual")
                    + " | Tentativas: " + valor(estadoRota, "rota_tentativas")
                    + " | Concluída até: " + valor(estadoRota, "rota_concluida"));
            System.out.println("1. Batalhar na rota");
            System.out.println("2. Escolher rota");
            System.out.println("3. Ver status");
            System.out.println("4. Sair");
            System.out.print("Opção: ");
            int op;
            try { op = Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException e) { op = 0; }

            switch (op) {
                case 1 -> pokemon = batalharNaRota(jogadorId, pokemon);
                case 2 -> rotaService.escolherRota(jogadorId);
                case 3 -> imprimirStatus(pokemon);
                case 4 -> ativo = false;
                default -> System.out.println("Opção inválida.");
            }
        }
        System.out.println("Até a próxima!");
    }

    /**
     * Sequência após batalha na rota: vitória → XP → evolução + conclusão da rota; derrota → perda de tentativa.
     */
    private Map<String, Object> batalharNaRota(int jogadorId, Map<String, Object> pokemon) throws SQLException {
        Map<String, Object> estadoRota = repository.buscarEstadoRotaJogador(jogadorId);
        if (estadoRota == null || valor(estadoRota, "rota_atual") <= 0) {
            System.out.println("Escolha uma rota antes de batalhar.");
            return pokemon;
        }
        rotaService.executarRota(jogadorId, pokemon);
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

    private int valor(Map<String, Object> mapa, String chave) {
        if (mapa == null || mapa.get(chave) == null) return 0;
        return ((Number) mapa.get(chave)).intValue();
    }
}
