package JogoPokemon.repository;

import java.sql.*;
import java.util.List;

public class PokemonRepository {

    private static final String URL = "jdbc:h2:./pokemon_db";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    Connection connection;

    public PokemonRepository() throws SQLException {
        connection = DriverManager.getConnection(URL, USER, PASSWORD);
        System.out.println("Conectado com sucesso");
        criarTabela();
    }

    private void criarTabela() {
        try (Statement st = connection.createStatement()) {

            st.execute("""
                    CREATE TABLE IF NOT EXISTS pokemon (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        nome VARCHAR(200) NOT NULL UNIQUE,
                        tipo VARCHAR(200),
                        fraqueza VARCHAR(500)
                    )
                    """);

            System.out.println("Tabela pokemon criada com sucesso");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS movimentos_pkm (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        pokemon_id INT,
                        nome VARCHAR(200),
                        level INT,
                        FOREIGN KEY (pokemon_id) REFERENCES pokemon(id)
                    )
                    """);

            System.out.println("Tabela movimentos criada com sucesso");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS evolucao_pkm (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        pokemon_id INT,
                        evolui_para VARCHAR(200),
                        FOREIGN KEY (pokemon_id) REFERENCES pokemon(id)
                    )
                    """);

            System.out.println("Tabela evolução criada com sucesso");

        } catch (SQLException e) {
            System.out.println("Erro ao criar tabela");
            System.out.println(e.getMessage());
        }
    }

    public void salvar(String nome,
                       List<String> tipos,
                       List<String> fraquezas,
                       List<String[]> ataques) throws SQLException {

        try (PreparedStatement pstmt = connection.prepareStatement(
                "MERGE INTO pokemon(nome, tipo, fraqueza)" +
                        "KEY (nome) VALUES (?, ?, ?)")) {
            pstmt.setString(1, nome);
            pstmt.setString(2, String.join(",", tipos));
            pstmt.setString(3, String.join(",", fraquezas));
        }
        int pokemonId;
        try (PreparedStatement pstmt = connection.prepareStatement(
                "SELECT id FROM pokemon WHERE nome = ?")) {
            pstmt.setString(1, nome);
            ResultSet rs = pstmt.executeQuery();
            rs.next();
            pokemonId = rs.getInt(1);
        }

        try (PreparedStatement pstmt = connection.prepareStatement(
                "DELETE FROM ataque WHERE pokemon_id = ?")) {
            pstmt.setInt(1, pokemonId);
            pstmt.executeUpdate();
        }
        try (PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO movimento_pkm" +
                        "(pokemon_id, nome, level)" +
                        "VALUES (?, ? ,?)")) {
            for (int i = 0; i < ataques.size(); i++) {
                pstmt.setInt(1, pokemonId);
                pstmt.setString(2, nome);
                pstmt.setInt(3, Integer.parseInt(ataques.get(i)[1]));

                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    public void close() throws SQLException {
        connection.close();
    }

    /*public void buscaPorId(int id, String nome) throws SQLException {

        try (PreparedStatement pstmt = connection.prepareStatement(
                "SELECT id FROM pokemon WHERE nome = ?")) {
            pstmt.setString(1, nome);
            ResultSet rs = pstmt.executeQuery();
            rs.next();
            id = rs.getInt(1);
        }
    }*/
}