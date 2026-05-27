package JogoPokemon.service;

import JogoPokemon.repository.PokemonRepository;

import java.sql.SQLException;
import java.util.Map;
import java.util.Scanner;

/**
 * Sistema de rotas do jogo: seleção progressiva, 3 tentativas por rota e batalha em faixa de nível.
 * <p>
 * Esta classe substitui a antiga implementação em `boni/Rota.java` e opera no mesmo modelo do
 * restante do projeto: H2 + `Map<String, Object>` + services.
 */
public class Rota {

    private static final int ROTA_1 = 1;
    private static final int ROTA_2 = 2;
    private static final int ROTA_3 = 3;
    private static final int TENTATIVAS_INICIAIS = 3;

    private final PokemonRepository repository;
    private final BatalhaService batalhaService;
    private final XpService xpService;
    private final EvolucaoService evolucaoService;
    private final Scanner scanner;

    public Rota(PokemonRepository repository,
                BatalhaService batalhaService,
                XpService xpService,
                EvolucaoService evolucaoService,
                Scanner scanner) {
        this.repository = repository;
        this.batalhaService = batalhaService;
        this.xpService = xpService;
        this.evolucaoService = evolucaoService;
        this.scanner = scanner;
    }

    public void escolherRota(int jogadorId) throws SQLException {
        Map<String, Object> estado = repository.buscarEstadoRotaJogador(jogadorId);
        int rotaConcluida = numero(estado, "rota_concluida");
        int rotaAtual = numero(estado, "rota_atual");
        int tentativas = numero(estado, "rota_tentativas");
        int liberada = Math.min(ROTA_3, rotaConcluida + 1);

        System.out.println("\n╔══════════════════════╗");
        System.out.println("║      ESCOLHER ROTA   ║");
        System.out.println("╚══════════════════════╝");
        System.out.println("Rotas liberadas: 1 até " + liberada);
        if (rotaAtual > 0) {
            System.out.println("Rota atual: " + rotaAtual + " | Tentativas restantes: " + tentativas);
        }

        System.out.print("Escolha a rota (1-3): ");
        int escolha = lerInteiro();
        if (escolha < ROTA_1 || escolha > ROTA_3) {
            System.out.println("Opção inválida.");
            return;
        }
        if (escolha > liberada) {
            System.out.println("Essa rota ainda está bloqueada.");
            return;
        }

        if (escolha == rotaAtual && tentativas <= 0) {
            System.out.println("Esta rota está bloqueada. Escolha outra ou avance na progressão.");
            return;
        }

        int tentativasNovas = escolha == rotaAtual ? tentativas : TENTATIVAS_INICIAIS;
        repository.atualizarEstadoRotaJogador(jogadorId, escolha, tentativasNovas, rotaConcluida);
        System.out.println("Rota " + escolha + " selecionada. Tentativas: " + tentativasNovas);
    }

    public boolean executarRota(int jogadorId, Map<String, Object> pokemonJogador) throws SQLException {
        Map<String, Object> estado = repository.buscarEstadoRotaJogador(jogadorId);
        int rotaAtual = numero(estado, "rota_atual");
        int tentativas = numero(estado, "rota_tentativas");

        if (rotaAtual <= 0) {
            System.out.println("Selecione uma rota antes de batalhar.");
            return false;
        }
        if (tentativas <= 0) {
            System.out.println("Sem tentativas restantes para esta rota.");
            return false;
        }

        Map<String, Object> inimigo = repository.buscarOponenteAleatorioPorRota(rotaAtual);
        if (inimigo == null) {
            System.out.println("Nenhum oponente disponível para esta rota.");
            return false;
        }

        System.out.println("\n══ Rota " + rotaAtual + " | Tentativas: " + tentativas + " ══");
        BatalhaService.Resultado resultado = batalhaService.executar(scanner, jogadorId, pokemonJogador, inimigo);

        if (resultado == BatalhaService.Resultado.VITORIA) {
            xpService.processarVitoria(pokemonJogador);
            evolucaoService.verificarEvolucao(pokemonJogador);
            repository.incrementarInimigosDerrotados(jogadorId);
            repository.concluirRotaJogador(jogadorId, rotaAtual);
            System.out.println("Rota concluída com sucesso!");
            return true;
        }

        if (resultado == BatalhaService.Resultado.DERROTA) {
            int restantes = repository.diminuirTentativaRotaJogador(jogadorId);
            if (restantes <= 0) {
                System.out.println("As 3 tentativas acabaram. Esta rota ficou bloqueada.");
            } else {
                System.out.println("Tentativas restantes na rota: " + restantes);
            }
        }

        return false;
    }

    private int numero(Map<String, Object> estado, String chave) {
        Object valor = estado != null ? estado.get(chave) : null;
        return valor instanceof Number ? ((Number) valor).intValue() : 0;
    }

    private int lerInteiro() {
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}