package JogoPokemon.Application;

import JogoPokemon.API.PokeAPILoader;
import JogoPokemon.repository.PokemonRepository;
import JogoPokemon.service.ConsultaService;
import JogoPokemon.service.JogoService;

import java.util.Scanner;

/**
 * Ponto de entrada do programa.
 * <p>
 * Organiza as 3 fases do PLANEJAMENTO.md em um menu:
 * <ol>
 *   <li>Popular catálogo (API + Gson + SQL) — só na primeira vez</li>
 *   <li>Demonstrar consultas (somente banco)</li>
 *   <li>Jogar (batalha, XP, evolução)</li>
 * </ol>
 * try-with-resources em PokemonRepository garante fechamento da conexão H2 ao sair.
 */
public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Uma conexão por execução do menu; AUTO_SERVER=TRUE no JDBC evita "database locked"
        try (PokemonRepository repository = new PokemonRepository()) {
            boolean rodando = true;

            while (rodando) {
                System.out.println("\n╔══════════════════════════════════════╗");
                System.out.println("║         JOGO POKÉMON — POO           ║");
                System.out.println("╚══════════════════════════════════════╝");
                System.out.println("1. Popular catálogo (Fase 1 — PokeAPI)");
                System.out.println("2. Demonstrar consultas SQL (Fase 2)");
                System.out.println("3. Jogar (Fase 3 — batalha, XP, evolução, rotas)");
                System.out.println("4. Sair");
                System.out.print("Opção: ");

                String op = scanner.nextLine().trim();
                switch (op) {
                    case "1" -> new PokeAPILoader(repository).carregarCatalogo();
                    case "2" -> {
                        if (!repository.catalogoJaPopulado()) {
                            System.out.println("Popule o catálogo primeiro (opção 1).");
                        } else {
                            new ConsultaService(repository).executarDemonstracao();
                            repository.imprimirPrimeiros20();
                        }
                    }
                    case "3" -> new JogoService(repository, scanner).iniciar();
                    case "4" -> rodando = false;
                    default -> System.out.println("Opção inválida.");
                }
            }
        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
}
