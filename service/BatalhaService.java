package JogoPokemon.service;

import JogoPokemon.repository.PokemonRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

/**
 * Combate por turnos entre dois Pokémon representados como Map (dados do SQL + estado em memória).
 * <p>
 * Requisitos do projeto atendidos:
 * <ul>
 *   <li>Usar movimento — consome PP, testa precisão, aplica dano</li>
 *   <li>Usar poção — -1 poção no jogador, +50 HP (máx hp_max)</li>
 *   <li>Fugir — encerra batalha sem vitória/derrota</li>
 *   <li>Status a cada turno — nome, HP, PP atual/máx, tipo, precisão</li>
 * </ul>
 * Movimentos do jogador persistem PP no banco; inimigo só existe em memória nesta batalha.
 */
public class BatalhaService {

    public enum Resultado { VITORIA, DERROTA, FUGA }

    private static final int CURA_POCAO = 50;
    private final PokemonRepository repository;
    private final Random random = new Random();

    public BatalhaService(PokemonRepository repository) {
        this.repository = repository;
    }

    /**
     * Loop principal: jogador age → inimigo contra-ataca → repete até HP zerar ou fugir.
     */
    public Resultado executar(Scanner sc, int jogadorId, Map<String, Object> aliado,
                            Map<String, Object> inimigo) throws SQLException {
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║           BATALHA INICIADA           ║");
        System.out.println("╚══════════════════════════════════════╝");

        while ((Integer) aliado.get("hp_atual") > 0 && (Integer) inimigo.get("hp_atual") > 0) {
            exibirStatus(aliado, inimigo);

            System.out.println("\n1. Movimento | 2. Poção (" + repository.getPocoes(jogadorId) + ") | 3. Fugir");
            System.out.print("Escolha: ");
            int op;
            try { op = Integer.parseInt(sc.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.println("Opção inválida."); continue; }

            switch (op) {
                case 1 -> { if (!usarMovimento(sc, aliado, inimigo, true)) continue; }
                case 2 -> {
                    if (!usarPocao(jogadorId, aliado)) {
                        System.out.println("Não foi possível usar poção.");
                        continue;
                    }
                    System.out.println("+" + CURA_POCAO + " HP!");
                }
                case 3 -> { System.out.println("Você fugiu!"); return Resultado.FUGA; }
                default -> { System.out.println("Opção inválida."); continue; }
            }

            if ((Integer) inimigo.get("hp_atual") <= 0) break;
            turnoInimigo(inimigo, aliado);
        }

        exibirStatus(aliado, inimigo);
        if ((Integer) aliado.get("hp_atual") > 0) {
            System.out.println("\n★ Vitória!");
            repository.atualizarHpPokemonJogador((Integer) aliado.get("id"), (Integer) aliado.get("hp_atual"));
            return Resultado.VITORIA;
        }
        System.out.println("\n✖ Derrota...");
        return Resultado.DERROTA;
    }

    /**
     * Lista movimentos, valida PP, rola precisão e calcula dano.
     *
     * @param persistirPp true para jogador (UPDATE movimento_jogador); inimigo não persiste PP
     */
    @SuppressWarnings("unchecked")
    private boolean usarMovimento(Scanner sc, Map<String, Object> atacante, Map<String, Object> defensor,
                                  boolean persistirPp) throws SQLException {
        List<Map<String, Object>> movs = (List<Map<String, Object>>) atacante.get("movimentos");
        for (int i = 0; i < movs.size(); i++) {
            Map<String, Object> m = movs.get(i);
            System.out.println((i + 1) + ". " + m.get("nome") + " PP:" + m.get("pp_atual") + "/" + m.get("pp_max")
                    + " [" + m.get("tipo") + "] Prec:" + m.get("precisao") + "%");
        }
        System.out.print("Movimento: ");
        int idx;
        try { idx = Integer.parseInt(sc.nextLine().trim()) - 1; }
        catch (NumberFormatException e) { System.out.println("Inválido."); return false; }
        if (idx < 0 || idx >= movs.size()) { System.out.println("Inválido."); return false; }

        Map<String, Object> mov = movs.get(idx);
        if ((Integer) mov.get("pp_atual") <= 0) { System.out.println("Sem PP!"); return false; }

        System.out.println(atacante.get("nome") + " usou " + mov.get("nome") + "!");

        // Precisão: número aleatório 0-99 deve ser MENOR que precisão do movimento
        if (!acertou((Integer) mov.get("precisao"))) {
            System.out.println("Errou!");
        } else {
            int dano = calcularDano(atacante, defensor, mov);
            defensor.put("hp_atual", Math.max(0, (Integer) defensor.get("hp_atual") - dano));
            System.out.println("Dano: " + dano);
            double ef = efetividade((String) mov.get("tipo"), (String) defensor.get("tipo"), (String) defensor.get("tipo2"));
            if (ef > 1) System.out.println("Super efetivo!");
            else if (ef < 1 && ef > 0) System.out.println("Pouco efetivo...");
            else if (ef == 0) System.out.println("Não afeta...");
        }

        mov.put("pp_atual", (Integer) mov.get("pp_atual") - 1);
        if (persistirPp) {
            repository.atualizarPpMovimento((Integer) mov.get("id"), (Integer) mov.get("pp_atual"));
        }
        return true;
    }

    /** IA simples: primeiro movimento disponível (ou aleatório se todos sem PP). */
    @SuppressWarnings("unchecked")
    private void turnoInimigo(Map<String, Object> inimigo, Map<String, Object> aliado) throws SQLException {
        System.out.println("\n--- Turno inimigo ---");
        List<Map<String, Object>> movs = (List<Map<String, Object>>) inimigo.get("movimentos");
        Map<String, Object> mov = null;
        for (Map<String, Object> m : movs) {
            if ((Integer) m.get("pp_max") > 0) { mov = m; break; }
        }
        if (mov == null && !movs.isEmpty()) mov = movs.get(random.nextInt(movs.size()));

        if (mov != null) {
            if (!acertou((Integer) mov.get("precisao"))) {
                System.out.println(inimigo.get("nome") + " errou!");
            } else {
                int dano = calcularDano(inimigo, aliado, mov);
                aliado.put("hp_atual", Math.max(0, (Integer) aliado.get("hp_atual") - dano));
                System.out.println(inimigo.get("nome") + " causou " + dano + " de dano!");
            }
        }
        repository.atualizarHpPokemonJogador((Integer) aliado.get("id"), (Integer) aliado.get("hp_atual"));
    }

    /** Decrementa poção no SQL e cura até hp_max. */
    private boolean usarPocao(int jogadorId, Map<String, Object> aliado) throws SQLException {
        if (!repository.usarPocao(jogadorId)) return false;
        int hpMax = (Integer) aliado.get("hp_max");
        int novoHp = Math.min(hpMax, (Integer) aliado.get("hp_atual") + CURA_POCAO);
        aliado.put("hp_atual", novoHp);
        repository.atualizarHpPokemonJogador((Integer) aliado.get("id"), novoHp);
        return true;
    }

    /**
     * Fórmula inspirada nos jogos principais: nível, poder do movimento, ataque/defesa e aleatoriedade 85–100%.
     * TipoEfetividade multiplica o resultado (ex: Água x Fogo = x2).
     */
    private int calcularDano(Map<String, Object> atk, Map<String, Object> def, Map<String, Object> mov) {
        int nivel = (Integer) atk.get("nivel");
        int ataque = stat(atk, "ataque_base", "ataque") + nivel * 2;
        int defesa = stat(def, "defesa_base", "defesa") + (Integer) def.get("nivel") * 2;
        int poder = (Integer) mov.get("poder");
        double base = ((2.0 * nivel / 5.0 + 2) * poder * ataque / defesa) / 50.0 + 2;
        double ef = efetividade((String) mov.get("tipo"), (String) def.get("tipo"), (String) def.get("tipo2"));
        double rand = 0.85 + random.nextDouble() * 0.15;
        return Math.max(1, (int) (base * ef * rand));
    }

    /** Dois tipos no defensor: multiplicadores se multiplicam (ex: 2 x 0.5 = 1). */
    private double efetividade(String tipoAtk, String tipo1, String tipo2) {
        double m1 = TipoEfetividade.multiplicador(tipoAtk, tipo1);
        double m2 = tipo2 != null ? TipoEfetividade.multiplicador(tipoAtk, tipo2) : 1.0;
        return m1 * m2;
    }

    /** Jogador usa ataque_base; oponente do catálogo usa chave "ataque". */
    private int stat(Map<String, Object> p, String primario, String alternativo) {
        if (p.containsKey(primario) && p.get(primario) != null) return (Integer) p.get(primario);
        return (Integer) p.get(alternativo);
    }

    private boolean acertou(int precisao) {
        return random.nextInt(100) < precisao;
    }

    private void exibirStatus(Map<String, Object> aliado, Map<String, Object> inimigo) {
        System.out.println("\n┌─────────────────────────────────────┐");
        printLinha("INIMIGO", inimigo);
        System.out.println("├─────────────────────────────────────┤");
        printLinha("SEU POKÉMON", aliado);
        System.out.println("└─────────────────────────────────────┘");
    }

    @SuppressWarnings("unchecked")
    private void printLinha(String titulo, Map<String, Object> p) {
        System.out.printf("│ %-12s %-15s Nv.%s%n", titulo + ":", p.get("nome"), p.get("nivel"));
        System.out.printf("│   HP: %s/%s | XP: %s%n", p.get("hp_atual"), p.get("hp_max"), p.get("xp"));
        System.out.printf("│   Tipo: %s%n", p.get("tipo") + (p.get("tipo2") != null ? "/" + p.get("tipo2") : ""));
        if (p.get("movimentos") != null) {
            for (Map<String, Object> m : (List<Map<String, Object>>) p.get("movimentos")) {
                System.out.printf("│   • %s PP:%s/%s Prec:%s%%%n",
                        m.get("nome"), m.get("pp_atual"), m.get("pp_max"), m.get("precisao"));
            }
        }
    }
}
