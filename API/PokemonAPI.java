package JogoPokemon.API;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Camada HTTP: apenas busca texto JSON na PokeAPI.
 * <p>
 * Por que existe separado do PokeAPILoader?
 * - Responsabilidade única: esta classe só sabe fazer requisições.
 * - O Loader interpreta o JSON (Gson) e grava no banco.
 * - Se a URL da API mudar, alteramos só aqui.
 */
public class PokemonAPI {

    /** URL base da PokeAPI v2 — todos os endpoints são relativos a ela. */
    private static final String URL = "https://pokeapi.co/api/v2/";

    /** Cliente HTTP do Java 11+ — reutilizado para todas as chamadas da sessão. */
    private final HttpClient client;

    public PokemonAPI() {
        this.client = HttpClient.newHttpClient();
    }

    /**
     * Faz GET e devolve o corpo da resposta como String (JSON bruto).
     *
     * @param endpoint trecho após a URL base, ex: "pokemon/25" ou "move/tackle"
     */
    public String get(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL + endpoint))
                .GET()
                .build();

        // BodyHandlers.ofString() converte bytes da resposta em String UTF-8
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro na PokeAPI (" + response.statusCode() + "): " + endpoint);
        }
        return response.body(); // ainda é texto; Gson entra depois no PokeAPILoader
    }

    /** Atalho: dados de um Pokémon (stats, tipos, lista de movimentos). */
    public String buscarPokemonPorId(int id) throws Exception {
        return get("pokemon/" + id);
    }

    /** Atalho: árvore de evolução (cadeia completa da espécie). */
    public String buscarEvolucao(int id) throws Exception {
        return get("evolution-chain/" + id);
    }
}
