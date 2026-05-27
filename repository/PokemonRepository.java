package JogoPokemon.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Única classe de acesso ao banco H2 — todo SQL do projeto está aqui (sem pacote model).
 * <p>
 * Por que Map&lt;String, Object&gt; em vez de classes Pokemon/Jogador?
 * - Alinhado ao planejamento: dados vêm do ResultSet e vão direto para os services.
 * - Evita duplicar estrutura que já existe nas tabelas SQL.
 * <p>
 * Três grupos de responsabilidade:
 * <ol>
 *   <li>Catálogo (pokemon, movimento_pkm, evolucao_pkm) — populado pela API na Fase 1</li>
 *   <li>Consultas de leitura — Fase 2</li>
 *   <li>Jogador em partida (jogador, pokemon_jogador, movimento_jogador) — Fase 3</li>
 * </ol>
 */
public class PokemonRepository implements AutoCloseable {

    /** H2 em arquivo local; AUTO_SERVER=TRUE permite reabrir o programa sem erro de lock. */
    private static final String URL = "jdbc:h2:./pokemon_db;AUTO_SERVER=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private final Connection connection;

    public PokemonRepository() throws SQLException {
        connection = DriverManager.getConnection(URL, USER, PASSWORD);
        criarTabelas();
    }

    /**
     * Cria tabelas se não existirem.
     * Se existir schema antigo vazio (migração), dropa catálogo e recria — evita erro de coluna faltando.
     */
    public void criarTabelas() throws SQLException {
        if (!catalogoJaPopulado() && tabelaExiste("pokemon")) {
            droparTabelasCatalogo();
        }
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS pokemon (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        nome VARCHAR(200) NOT NULL UNIQUE,
                        tipo VARCHAR(100), tipo2 VARCHAR(100), fraqueza VARCHAR(500),
                        id_pokemon_api INT NOT NULL UNIQUE,
                        vida INT, ataque INT, defesa INT,
                        eh_inicial BOOLEAN DEFAULT FALSE)
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS movimento_pkm (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        pokemon_id INT NOT NULL, nome VARCHAR(200) NOT NULL,
                        pp_max INT NOT NULL, tipo VARCHAR(50), precisao INT, poder INT,
                        nivel_aprendizado INT,
                        FOREIGN KEY (pokemon_id) REFERENCES pokemon(id))
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS evolucao_pkm (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        pokemon_id INT NOT NULL, ordem INT NOT NULL,
                        nome_evolucao VARCHAR(200) NOT NULL, id_api_evolucao INT,
                        nivel_minimo INT DEFAULT 6,
                        FOREIGN KEY (pokemon_id) REFERENCES pokemon(id))
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS jogador (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        nome VARCHAR(200) NOT NULL, pocao INT DEFAULT 5,
                        inimigos_derrotados INT DEFAULT 0, rota_atual INT DEFAULT 0,
                        rota_tentativas INT DEFAULT 3, rota_concluida INT DEFAULT 0)
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS pokemon_jogador (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        id_jogador INT NOT NULL, id_pokemon INT NOT NULL,
                        nivel INT DEFAULT 5, xp INT DEFAULT 0, hp_atual INT,
                        FOREIGN KEY (id_jogador) REFERENCES jogador(id),
                        FOREIGN KEY (id_pokemon) REFERENCES pokemon(id))
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS movimento_jogador (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        id_pokemon_jogador INT NOT NULL, id_movimento INT NOT NULL,
                        pp_atual INT NOT NULL,
                        FOREIGN KEY (id_pokemon_jogador) REFERENCES pokemon_jogador(id),
                        FOREIGN KEY (id_movimento) REFERENCES movimento_pkm(id))
                    """);
            st.execute("ALTER TABLE jogador ADD COLUMN IF NOT EXISTS rota_atual INT DEFAULT 0");
            st.execute("ALTER TABLE jogador ADD COLUMN IF NOT EXISTS rota_tentativas INT DEFAULT 3");
            st.execute("ALTER TABLE jogador ADD COLUMN IF NOT EXISTS rota_concluida INT DEFAULT 0");
        }
    }

    private boolean tabelaExiste(String nomeTabela) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(null, null, nomeTabela.toUpperCase(), null)) {
            return rs.next();
        }
    }

    private void droparTabelasCatalogo() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS movimento_jogador");
            st.execute("DROP TABLE IF EXISTS pokemon_jogador");
            st.execute("DROP TABLE IF EXISTS movimento_pkm");
            st.execute("DROP TABLE IF EXISTS movimentos_pkm");
            st.execute("DROP TABLE IF EXISTS evolucao_pkm");
            st.execute("DROP TABLE IF EXISTS ataque");
            st.execute("DROP TABLE IF EXISTS pokemon");
        }
    }

    /**
     * Verificação da Fase 1: se já há linhas em pokemon, PokeAPILoader não chama a API de novo.
     */
    public boolean catalogoJaPopulado() throws SQLException {
        if (!tabelaExiste("pokemon")) return false;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS total FROM pokemon")) {
            return rs.next() && rs.getInt("total") > 0;
        }
    }

    public int contarPokemon() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS total FROM pokemon")) {
            return rs.next() ? rs.getInt("total") : 0;
        }
    }

    public int contarEvolucoes() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS total FROM evolucao_pkm")) {
            return rs.next() ? rs.getInt("total") : 0;
        }
    }

    public int contarMovimentos() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS total FROM movimento_pkm")) {
            return rs.next() ? rs.getInt("total") : 0;
        }
    }

    public int contarIniciais() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS total FROM pokemon WHERE eh_inicial = TRUE")) {
            return rs.next() ? rs.getInt("total") : 0;
        }
    }

    // --- Fase 1: INSERT ---

    // ─────────── Fase 1: INSERT no catálogo ───────────

    public int inserirPokemon(String nome, String tipo, String tipo2, int idApi,
                              int vida, int ataque, int defesa, boolean ehInicial) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pokemon (nome, tipo, tipo2, id_pokemon_api, vida, ataque, defesa, eh_inicial) VALUES (?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome); ps.setString(2, tipo); ps.setString(3, tipo2);
            ps.setInt(4, idApi); ps.setInt(5, vida); ps.setInt(6, ataque); ps.setInt(7, defesa);
            ps.setBoolean(8, ehInicial);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getInt(1);
        }
    }

    public void inserirMovimento(int pokemonId, String nome, int ppMax, String tipo,
                                 int precisao, int poder, int nivelAprendizado) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO movimento_pkm (pokemon_id, nome, pp_max, tipo, precisao, poder, nivel_aprendizado) VALUES (?,?,?,?,?,?,?)")) {
            ps.setInt(1, pokemonId); ps.setString(2, nome); ps.setInt(3, ppMax);
            ps.setString(4, tipo); ps.setInt(5, precisao); ps.setInt(6, poder); ps.setInt(7, nivelAprendizado);
            ps.executeUpdate();
        }
    }

    public void inserirEvolucao(int pokemonId, int ordem, String nomeEvolucao,
                                int idApiEvolucao, int nivelMinimo) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO evolucao_pkm (pokemon_id, ordem, nome_evolucao, id_api_evolucao, nivel_minimo) VALUES (?,?,?,?,?)")) {
            ps.setInt(1, pokemonId); ps.setInt(2, ordem); ps.setString(3, nomeEvolucao);
            ps.setInt(4, idApiEvolucao); ps.setInt(5, nivelMinimo);
            ps.executeUpdate();
        }
    }

    // --- Fase 2: SELECT catálogo ---

    // ─────────── Fase 2: SELECT no catálogo (sem API) ───────────

    public Map<String, Object> buscarPokemonPorId(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM pokemon WHERE id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapPokemon(rs) : null;
        }
    }

    public Map<String, Object> buscarPokemonPorNome(String nome) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM pokemon WHERE LOWER(nome) = LOWER(?)")) {
            ps.setString(1, nome);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapPokemon(rs) : null;
        }
    }

    public List<Map<String, Object>> listarIniciais() throws SQLException {
        return listarComFiltro("SELECT * FROM pokemon WHERE eh_inicial = TRUE ORDER BY id_pokemon_api");
    }

    public List<Map<String, Object>> listarTodos() throws SQLException {
        return listarComFiltro("SELECT * FROM pokemon ORDER BY id_pokemon_api");
    }

    public List<Map<String, Object>> listarEvolucoes(int pokemonId) throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM evolucao_pkm WHERE pokemon_id = ? ORDER BY ordem")) {
            ps.setInt(1, pokemonId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("ordem", rs.getInt("ordem"));
                m.put("nome_evolucao", rs.getString("nome_evolucao"));
                m.put("nivel_minimo", rs.getInt("nivel_minimo"));
                lista.add(m);
            }
        }
        return lista;
    }

    public List<Map<String, Object>> listarMovimentos(int pokemonId) throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM movimento_pkm WHERE pokemon_id = ?")) {
            ps.setInt(1, pokemonId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("nome", rs.getString("nome"));
                m.put("pp_max", rs.getInt("pp_max"));
                m.put("tipo", rs.getString("tipo"));
                m.put("precisao", rs.getInt("precisao"));
                m.put("poder", rs.getInt("poder"));
                m.put("nivel_aprendizado", rs.getInt("nivel_aprendizado"));
                lista.add(m);
            }
        }
        return lista;
    }

    private List<Map<String, Object>> listarComFiltro(String sql) throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) lista.add(mapPokemon(rs));
        }
        return lista;
    }

    private Map<String, Object> mapPokemon(ResultSet rs) throws SQLException {
        Map<String, Object> m = new HashMap<>();
        m.put("id", rs.getInt("id"));
        m.put("nome", rs.getString("nome"));
        m.put("tipo", rs.getString("tipo"));
        m.put("tipo2", rs.getString("tipo2"));
        m.put("id_pokemon_api", rs.getInt("id_pokemon_api"));
        m.put("vida", rs.getInt("vida"));
        m.put("ataque", rs.getInt("ataque"));
        m.put("defesa", rs.getInt("defesa"));
        m.put("eh_inicial", rs.getBoolean("eh_inicial"));
        return m;
    }

    /** Exibe tabela formatada no console — usado ao fim da carga ou quando carga é pulada. */
    public void imprimirPrimeiros20() throws SQLException {
        System.out.println("\n══════════ 20 PRIMEIROS POKÉMON NO BANCO ══════════");
        System.out.printf("%-4s %-6s %-15s %-10s %-10s %-5s %-5s %-5s %-8s%n",
                "ID", "API", "NOME", "TIPO1", "TIPO2", "HP", "ATK", "DEF", "INICIAL");
        System.out.println("─".repeat(80));
        for (Map<String, Object> p : listarComFiltro("SELECT * FROM pokemon ORDER BY id_pokemon_api LIMIT 20")) {
            System.out.printf("%-4s %-6s %-15s %-10s %-10s %-5s %-5s %-5s %-8s%n",
                    p.get("id"), p.get("id_pokemon_api"), p.get("nome"), p.get("tipo"),
                    p.get("tipo2") != null ? p.get("tipo2") : "-",
                    p.get("vida"), p.get("ataque"), p.get("defesa"),
                    (Boolean) p.get("eh_inicial") ? "SIM" : "NAO");
        }
        System.out.println("═".repeat(80));
        System.out.println("Total no banco: " + contarPokemon() + " Pokémon");
    }

    // --- Fase 3: Jogador ---

    // ─────────── Fase 3: jogador e partida ───────────

    public int criarJogador(String nome) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO jogador (nome, pocao) VALUES (?, 5)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Liga treinador ao Pokémon do catálogo e copia movimentos para movimento_jogador (com PP cheio).
     */
    public int associarPokemonJogador(int jogadorId, int idPokemonCatalogo, int nivel) throws SQLException {
        Map<String, Object> pkm = buscarPokemonPorId(idPokemonCatalogo);
        int hpMax = calcularHpMax((Integer) pkm.get("vida"), nivel);
        int pokemonJogadorId;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pokemon_jogador (id_jogador, id_pokemon, nivel, xp, hp_atual) VALUES (?,?,?,0,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, jogadorId); ps.setInt(2, idPokemonCatalogo);
            ps.setInt(3, nivel); ps.setInt(4, hpMax);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            pokemonJogadorId = rs.getInt(1);
        }
        copiarMovimentosParaJogador(pokemonJogadorId, idPokemonCatalogo);
        return pokemonJogadorId;
    }

    private void copiarMovimentosParaJogador(int pokemonJogadorId, int idPokemonCatalogo) throws SQLException {
        for (Map<String, Object> mov : listarMovimentos(idPokemonCatalogo)) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO movimento_jogador (id_pokemon_jogador, id_movimento, pp_atual) VALUES (?,?,?)")) {
                ps.setInt(1, pokemonJogadorId);
                ps.setInt(2, (Integer) mov.get("id"));
                ps.setInt(3, (Integer) mov.get("pp_max"));
                ps.executeUpdate();
            }
        }
    }

    public Map<String, Object> carregarJornada() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM jogador ORDER BY id DESC LIMIT 1")) {
            if (!rs.next()) return null;
            Map<String, Object> jornada = new HashMap<>();
            int jogadorId = rs.getInt("id");
            jornada.put("jogador_id", jogadorId);
            jornada.put("nome", rs.getString("nome"));
            jornada.put("pocao", rs.getInt("pocao"));
            jornada.put("inimigos_derrotados", rs.getInt("inimigos_derrotados"));
            jornada.put("rota_atual", rs.getInt("rota_atual"));
            jornada.put("rota_tentativas", rs.getInt("rota_tentativas"));
            jornada.put("rota_concluida", rs.getInt("rota_concluida"));
            jornada.put("pokemon_jogador", buscarPokemonJogadorAtivo(jogadorId));
            return jornada;
        }
    }

    public Map<String, Object> buscarEstadoRotaJogador(int jogadorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT rota_atual, rota_tentativas, rota_concluida FROM jogador WHERE id = ?")) {
            ps.setInt(1, jogadorId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Map<String, Object> estado = new HashMap<>();
            estado.put("rota_atual", rs.getInt("rota_atual"));
            estado.put("rota_tentativas", rs.getInt("rota_tentativas"));
            estado.put("rota_concluida", rs.getInt("rota_concluida"));
            return estado;
        }
    }

    public Map<String, Object> buscarPokemonJogadorAtivo(int jogadorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pj.id AS pj_id, pj.id_jogador, pj.id_pokemon, pj.nivel, pj.xp, pj.hp_atual, " +
                        "p.nome, p.tipo, p.tipo2, p.vida, p.ataque, p.defesa " +
                        "FROM pokemon_jogador pj JOIN pokemon p ON pj.id_pokemon = p.id " +
                        "WHERE pj.id_jogador = ? ORDER BY pj.id DESC LIMIT 1")) {
            ps.setInt(1, jogadorId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Map<String, Object> m = new HashMap<>();
            int pjId = rs.getInt("pj_id");
            m.put("id", pjId);
            m.put("id_jogador", jogadorId);
            m.put("id_pokemon", rs.getInt("id_pokemon"));
            m.put("nome", rs.getString("nome"));
            m.put("tipo", rs.getString("tipo"));
            m.put("tipo2", rs.getString("tipo2"));
            m.put("vida_base", rs.getInt("vida"));
            m.put("ataque_base", rs.getInt("ataque"));
            m.put("defesa_base", rs.getInt("defesa"));
            m.put("nivel", rs.getInt("nivel"));
            m.put("xp", rs.getInt("xp"));
            m.put("hp_atual", rs.getInt("hp_atual"));
            m.put("hp_max", calcularHpMax(rs.getInt("vida"), rs.getInt("nivel")));
            m.put("movimentos", listarMovimentosJogador(pjId));
            return m;
        }
    }

    public List<Map<String, Object>> listarMovimentosJogador(int pokemonJogadorId) throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT mj.id, mj.pp_atual, m.nome, m.pp_max, m.tipo, m.precisao, m.poder " +
                        "FROM movimento_jogador mj JOIN movimento_pkm m ON mj.id_movimento = m.id " +
                        "WHERE mj.id_pokemon_jogador = ?")) {
            ps.setInt(1, pokemonJogadorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("nome", rs.getString("nome"));
                m.put("pp_atual", rs.getInt("pp_atual"));
                m.put("pp_max", rs.getInt("pp_max"));
                m.put("tipo", rs.getString("tipo"));
                m.put("precisao", rs.getInt("precisao"));
                m.put("poder", rs.getInt("poder"));
                lista.add(m);
            }
        }
        return lista;
    }

    /**
     * Oponente temporário até a Fase 4 (rotas): sorteia do catálogo, mesmo nível do jogador.
     * ORDER BY RAND() é específico do H2/MySQL.
     */
    public Map<String, Object> buscarOponenteAleatorio(int nivel) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM pokemon WHERE eh_inicial = FALSE ORDER BY RAND() LIMIT 1")) {
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Map<String, Object> oponente = mapPokemon(rs);
            oponente.put("nivel", nivel);
            oponente.put("hp_max", calcularHpMax((Integer) oponente.get("vida"), nivel));
            oponente.put("hp_atual", oponente.get("hp_max"));
            List<Map<String, Object>> movs = listarMovimentos((Integer) oponente.get("id"));
            for (Map<String, Object> mov : movs) {
                mov.put("pp_atual", mov.get("pp_max"));
            }
            oponente.put("movimentos", movs);
            oponente.put("ataque_base", oponente.get("ataque"));
            oponente.put("defesa_base", oponente.get("defesa"));
            return oponente;
        }
    }

    public Map<String, Object> buscarOponenteAleatorioPorRota(int rota) throws SQLException {
        int nivelMinimo;
        int nivelMaximo;
        switch (rota) {
            case 1 -> {
                nivelMinimo = 1;
                nivelMaximo = 7;
            }
            case 2 -> {
                nivelMinimo = 8;
                nivelMaximo = 15;
            }
            case 3 -> {
                nivelMinimo = 16;
                nivelMaximo = 25;
            }
            default -> {
                return null;
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM pokemon WHERE eh_inicial = FALSE ORDER BY RAND() LIMIT 1")) {
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Map<String, Object> oponente = mapPokemon(rs);
            int nivelEscolhido = nivelMinimo + (int) (Math.random() * (nivelMaximo - nivelMinimo + 1));
            oponente.put("nivel", nivelEscolhido);
            oponente.put("hp_max", calcularHpMax((Integer) oponente.get("vida"), nivelEscolhido));
            oponente.put("hp_atual", oponente.get("hp_max"));
            List<Map<String, Object>> movs = listarMovimentos((Integer) oponente.get("id"));
            for (Map<String, Object> mov : movs) {
                mov.put("pp_atual", mov.get("pp_max"));
            }
            oponente.put("movimentos", movs);
            oponente.put("ataque_base", oponente.get("ataque"));
            oponente.put("defesa_base", oponente.get("defesa"));
            return oponente;
        }
    }

    public void atualizarHpPokemonJogador(int pokemonJogadorId, int hp) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pokemon_jogador SET hp_atual = ? WHERE id = ?")) {
            ps.setInt(1, hp); ps.setInt(2, pokemonJogadorId);
            ps.executeUpdate();
        }
    }

    public void atualizarXpNivel(int pokemonJogadorId, int xp, int nivel, int hpAtual) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pokemon_jogador SET xp = ?, nivel = ?, hp_atual = ? WHERE id = ?")) {
            ps.setInt(1, xp); ps.setInt(2, nivel); ps.setInt(3, hpAtual); ps.setInt(4, pokemonJogadorId);
            ps.executeUpdate();
        }
    }

    public boolean usarPocao(int jogadorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE jogador SET pocao = pocao - 1 WHERE id = ? AND pocao > 0")) {
            ps.setInt(1, jogadorId);
            return ps.executeUpdate() > 0;
        }
    }

    public int getPocoes(int jogadorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT pocao FROM jogador WHERE id = ?")) {
            ps.setInt(1, jogadorId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("pocao") : 0;
        }
    }

    public void atualizarPpMovimento(int movimentoJogadorId, int ppAtual) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE movimento_jogador SET pp_atual = ? WHERE id = ?")) {
            ps.setInt(1, ppAtual); ps.setInt(2, movimentoJogadorId);
            ps.executeUpdate();
        }
    }

    public void incrementarInimigosDerrotados(int jogadorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE jogador SET inimigos_derrotados = inimigos_derrotados + 1 WHERE id = ?")) {
            ps.setInt(1, jogadorId);
            ps.executeUpdate();
        }
    }

    public void atualizarEstadoRotaJogador(int jogadorId, int rotaAtual, int tentativas, int rotaConcluida) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE jogador SET rota_atual = ?, rota_tentativas = ?, rota_concluida = ? WHERE id = ?")) {
            ps.setInt(1, rotaAtual);
            ps.setInt(2, tentativas);
            ps.setInt(3, rotaConcluida);
            ps.setInt(4, jogadorId);
            ps.executeUpdate();
        }
    }

    public int diminuirTentativaRotaJogador(int jogadorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE jogador SET rota_tentativas = CASE WHEN rota_tentativas > 0 THEN rota_tentativas - 1 ELSE 0 END WHERE id = ?")) {
            ps.setInt(1, jogadorId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT rota_tentativas FROM jogador WHERE id = ?")) {
            ps.setInt(1, jogadorId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("rota_tentativas") : 0;
        }
    }

    public void concluirRotaJogador(int jogadorId, int rotaConcluida) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE jogador SET rota_concluida = GREATEST(rota_concluida, ?), rota_atual = 0, rota_tentativas = 3 WHERE id = ?")) {
            ps.setInt(1, rotaConcluida);
            ps.setInt(2, jogadorId);
            ps.executeUpdate();
        }
    }

    /**
     * Troca id_pokemon para a forma evoluída, recalcula HP e recria movimentos do catálogo novo.
     */
    public void evoluirPokemonJogador(int pokemonJogadorId, int novoIdPokemonCatalogo, int nivel) throws SQLException {
        Map<String, Object> pkm = buscarPokemonPorId(novoIdPokemonCatalogo);
        int hpMax = calcularHpMax((Integer) pkm.get("vida"), nivel);
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pokemon_jogador SET id_pokemon = ?, hp_atual = ? WHERE id = ?")) {
            ps.setInt(1, novoIdPokemonCatalogo);
            ps.setInt(2, hpMax);
            ps.setInt(3, pokemonJogadorId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM movimento_jogador WHERE id_pokemon_jogador = ?")) {
            ps.setInt(1, pokemonJogadorId);
            ps.executeUpdate();
        }
        copiarMovimentosParaJogador(pokemonJogadorId, novoIdPokemonCatalogo);
    }

    public Integer buscarIdCatalogoPorNomeEvolucao(String nomeEvolucao) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM pokemon WHERE LOWER(nome) = LOWER(?)")) {
            ps.setString(1, nomeEvolucao);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : null;
        }
    }

    public Map<String, Object> buscarEvolucaoPorOrdem(int pokemonCatalogId, int ordem) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM evolucao_pkm WHERE pokemon_id = ? AND ordem = ?")) {
            ps.setInt(1, pokemonCatalogId);
            ps.setInt(2, ordem);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Map<String, Object> m = new HashMap<>();
            m.put("nome_evolucao", rs.getString("nome_evolucao"));
            m.put("nivel_minimo", rs.getInt("nivel_minimo"));
            return m;
        }
    }

    public Map<String, Object> buscarEvolucaoPorNomeAtual(String nomeAtual, int ordem) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT e2.nome_evolucao, e2.nivel_minimo FROM evolucao_pkm e1 " +
                        "JOIN evolucao_pkm e2 ON e1.pokemon_id = e2.pokemon_id " +
                        "WHERE LOWER(e1.nome_evolucao) = LOWER(?) AND e2.ordem = ?")) {
            ps.setString(1, nomeAtual);
            ps.setInt(2, ordem);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Map<String, Object> m = new HashMap<>();
            m.put("nome_evolucao", rs.getString("nome_evolucao"));
            m.put("nivel_minimo", rs.getInt("nivel_minimo"));
            return m;
        }
    }

    /** Fórmula simples de HP máximo: base da espécie + bônus por nível. */
    public int calcularHpMax(int vidaBase, int nivel) {
        return vidaBase + (nivel * 8);
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
