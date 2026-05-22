package JogoPokemon.API;

import JogoPokemon.repository.PokemonRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Fase 1 do projeto: lê JSON da PokeAPI com Gson e persiste no H2 via SQL.
 * <p>
 * ─── COMO O GSON FUNCIONA NESTE PROJETO ───
 * <p>
 * 1) A API retorna TEXTO no formato JSON, por exemplo:
 * {@code {"name":"chikorita","stats":[{"base_stat":45,...}],...}}
 * <p>
 * 2) {@link JsonParser#parseString(String)} transforma esse texto em árvore na memória:
 * <ul>
 *   <li>{@link JsonObject} → chaves com objeto entre chaves {@code { }}</li>
 *   <li>{@link JsonArray} → listas entre colchetes {@code [ ]}</li>
 *   <li>valores primitivos → string, número, boolean ou null</li>
 * </ul>
 * <p>
 * 3) Navegamos com caminhos de chaves, como em um Map aninhado:
 * {@code root.get("name").getAsString()}
 * {@code root.getAsJsonArray("stats")}
 * <p>
 * 4) Por que Gson e não parser manual?
 * - Evita percorrer o texto caractere a caractere (frágil e difícil de manter).
 * - A PokeAPI tem JSON profundamente aninhado (movimentos → version_group_details → ...).
 * - Gson já trata escape, vírgulas, null e tipos.
 * <p>
 * 5) Fluxo resumido:
 * PokemonAPI (HTTP) → String json → JsonParser → JsonObject → extrair campos → INSERT SQL
 */
public class PokeAPILoader {

    /** IDs nacionais dos 3 iniciais de Johto (geração de Chikorita). */
    private static final int[] INICIAIS_API = {152, 155, 158};

    /** Só importamos movimentos aprendidos por nível até este valor (início do jogo). */
    private static final int NIVEL_MAX_MOVIMENTO = 10;

    /** Limite de movimentos gravados por Pokémon no catálogo. */
    private static final int MAX_MOVIMENTOS = 4;

    /** Pausa entre requisições para não sobrecarregar a PokeAPI (rate limit). */
    private static final int PAUSA_MS = 150;

    private final PokemonAPI api;
    private final PokemonRepository repository;

    /**
     * Cache: nome do movimento → JsonObject já baixado.
     * Vários Pokémon compartilham "tackle"; sem cache repetiríamos dezenas de GET /move/tackle.
     */
    private final Map<String, JsonObject> cacheMovimentos = new HashMap<>();

    public PokeAPILoader(PokemonRepository repository) {
        this.api = new PokemonAPI();
        this.repository = repository;
    }

    /**
     * Ponto de entrada da Fase 1.
     * Se o banco já tiver Pokémon, não chama a API de novo (economia de tempo e rede).
     */
    public void carregarCatalogo() throws Exception {
        if (repository.catalogoJaPopulado()) {
            System.out.println("Tabela 'pokemon' já possui dados. População ignorada (sem consultas à API).");
            repository.imprimirPrimeiros20();
            return;
        }

        System.out.println("Iniciando população do banco via PokeAPI...");

        // LinkedHashSet mantém ordem e evita duplicar ID se 152 estivesse também em 1..150
        Set<Integer> ids = new LinkedHashSet<>();
        for (int i = 1; i <= 150; i++) ids.add(i);
        for (int id : INICIAIS_API) ids.add(id);

        int total = ids.size(), atual = 0, erros = 0;
        for (int idApi : ids) {
            atual++;
            boolean ehInicial = Arrays.stream(INICIAIS_API).anyMatch(i -> i == idApi);
            try {
                System.out.printf("Cadastrando %d/%d (API #%d)...%n", atual, total, idApi);
                cadastrarPokemon(idApi, ehInicial);
                Thread.sleep(PAUSA_MS);
            } catch (Exception e) {
                erros++;
                System.out.println("  Erro no Pokémon #" + idApi + ": " + e.getMessage());
            }
        }
        System.out.println("\nPopulação concluída! Cadastrados: " + repository.contarPokemon() + " | Erros: " + erros);
        repository.imprimirPrimeiros20();
    }

    /**
     * Uma ida à API por Pokémon: parse Gson + 3 grupos de INSERT (pokemon, evolução, movimentos).
     */
    private void cadastrarPokemon(int idApi, boolean ehInicial) throws Exception {
        // ── Gson passo 1: String → JsonObject raiz ──
        String jsonTexto = api.buscarPokemonPorId(idApi);
        JsonObject root = JsonParser.parseString(jsonTexto).getAsJsonObject();

        // Campos simples no primeiro nível do JSON
        String nome = capitalizar(root.get("name").getAsString());
        List<String> tipos = extrairTipos(root);

        int pokemonId = repository.inserirPokemon(nome,
                tipos.isEmpty() ? "normal" : tipos.get(0),
                tipos.size() > 1 ? tipos.get(1) : null,
                idApi,
                extrairStat(root, "hp"),
                extrairStat(root, "attack"),
                extrairStat(root, "defense"),
                ehInicial);

        cadastrarEvolucoes(idApi, pokemonId, nome);
        cadastrarMovimentos(root, pokemonId);
    }

    /**
     * Evolução exige 2 endpoints:
     * 1) pokemon-species → link evolution_chain
     * 2) evolution-chain → árvore evolves_to (estrutura em árvore, percorrida recursiva)
     */
    private void cadastrarEvolucoes(int idApi, int pokemonId, String nomeBase) throws Exception {
        JsonObject species = JsonParser.parseString(api.get("pokemon-species/" + idApi)).getAsJsonObject();

        // evolution_chain é um objeto com "url", não o id direto
        String chainUrl = species.getAsJsonObject("evolution_chain").get("url").getAsString();
        int chainId = extrairIdDaUrl(chainUrl); // último segmento da URL

        JsonObject chain = JsonParser.parseString(api.buscarEvolucao(chainId))
                .getAsJsonObject()
                .getAsJsonObject("chain"); // raiz da árvore

        List<String[]> evolucoes = new ArrayList<>();
        coletarEvolucoes(chain, nomeBase.toLowerCase(), evolucoes, false);

        int ordem = 1;
        for (String[] evo : evolucoes) {
            // Regra do jogo: 1ª evolução nv 6, 2ª nv 14 (gravado para EvolucaoService usar depois)
            int nivelMin = ordem == 1 ? 6 : 14;
            repository.inserirEvolucao(pokemonId, ordem++, capitalizar(evo[0]), 0, nivelMin);
        }
    }

    /**
     * Percorre a árvore JSON de evolução.
     * {@code passouBase} indica se já passamos pelo Pokémon inicial da cadeia;
     * só após isso adicionamos nomes em {@code resultado} (evita gravar o próprio base de novo).
     */
    private void coletarEvolucoes(JsonObject no, String nomeBase, List<String[]> resultado, boolean passouBase) {
        String nomeAtual = no.getAsJsonObject("species").get("name").getAsString();
        boolean ehBase = nomeAtual.equalsIgnoreCase(nomeBase);

        if (passouBase && !ehBase) {
            resultado.add(new String[]{nomeAtual, "0"});
        }

        // evolves_to é JsonArray de filhos — cada filho é outro JsonObject
        for (JsonElement el : no.getAsJsonArray("evolves_to")) {
            coletarEvolucoes(el.getAsJsonObject(), nomeBase, resultado, passouBase || ehBase);
        }
    }

    /**
     * Para cada movimento do Pokémon, busca detalhes em /move/{nome} (outro JSON) e grava PP, precisão, etc.
     */
    private void cadastrarMovimentos(JsonObject pokemon, int pokemonId) throws Exception {
        int count = 0;
        for (MovimentoInfo info : extrairMovimentosNivel(pokemon)) {
            if (count >= MAX_MOVIMENTOS) break;

            JsonObject moveJson = buscarMovimento(info.nome());

            repository.inserirMovimento(pokemonId, capitalizar(info.nome()),
                    moveJson.get("pp").getAsInt(),
                    moveJson.getAsJsonObject("type").get("name").getAsString(),
                    extrairPrecisao(moveJson),
                    extrairPoder(moveJson),
                    info.nivel());
            count++;
        }
    }

    /**
     * O JSON de pokemon traz TODOS os movimentos da história do monstro.
     * Filtramos só level-up com level_learned_at &lt;= NIVEL_MAX_MOVIMENTO.
     * <p>
     * Estrutura simplificada:
     * moves[] → move.name + version_group_details[] → level_learned_at + move_learn_method.name
     */
    private List<MovimentoInfo> extrairMovimentosNivel(JsonObject pokemon) {
        Map<String, MovimentoInfo> unicos = new LinkedHashMap<>();

        JsonArray moves = pokemon.getAsJsonArray("moves");
        for (JsonElement moveEl : moves) {
            JsonObject moveEntry = moveEl.getAsJsonObject();
            String nome = moveEntry.getAsJsonObject("move").get("name").getAsString();

            int menorNivel = Integer.MAX_VALUE;
            JsonArray detalhes = moveEntry.getAsJsonArray("version_group_details");

            for (JsonElement detailEl : detalhes) {
                JsonObject detail = detailEl.getAsJsonObject();
                String metodo = detail.getAsJsonObject("move_learn_method").get("name").getAsString();

                if ("level-up".equals(metodo)) {
                    int nivel = detail.get("level_learned_at").getAsInt();
                    if (nivel <= NIVEL_MAX_MOVIMENTO && nivel < menorNivel) {
                        menorNivel = nivel;
                    }
                }
            }

            if (menorNivel != Integer.MAX_VALUE) {
                unicos.putIfAbsent(nome, new MovimentoInfo(nome, menorNivel));
            }
        }

        List<MovimentoInfo> lista = new ArrayList<>(unicos.values());
        lista.sort(Comparator.comparingInt(MovimentoInfo::nivel).thenComparing(MovimentoInfo::nome));
        return lista;
    }

    /** Busca /move/{nome} com cache para não repetir requisição HTTP. */
    private JsonObject buscarMovimento(String nome) throws Exception {
        return cacheMovimentos.computeIfAbsent(nome, k -> {
            try {
                return JsonParser.parseString(api.get("move/" + k)).getAsJsonObject();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * types[] na API: cada item tem slot + type.name (ex: grass, poison).
     * Percorremos o JsonArray e coletamos os nomes em ordem.
     */
    private List<String> extrairTipos(JsonObject pokemon) {
        List<String> tipos = new ArrayList<>();
        for (JsonElement el : pokemon.getAsJsonArray("types")) {
            tipos.add(el.getAsJsonObject().getAsJsonObject("type").get("name").getAsString());
        }
        return tipos;
    }

    /**
     * stats[] é uma lista; cada elemento tem stat.name (hp, attack...) e base_stat.
     * Percorremos até achar o stat pedido.
     */
    private int extrairStat(JsonObject pokemon, String nomeStat) {
        for (JsonElement el : pokemon.getAsJsonArray("stats")) {
            JsonObject stat = el.getAsJsonObject();
            if (stat.getAsJsonObject("stat").get("name").getAsString().equals(nomeStat)) {
                return stat.get("base_stat").getAsInt();
            }
        }
        return 0;
    }

    /** Na API, accuracy null = movimento que sempre acerta (ex: Swift). */
    private int extrairPrecisao(JsonObject move) {
        if (move.get("accuracy").isJsonNull()) return 100;
        int acc = move.get("accuracy").getAsInt();
        return acc == 0 ? 100 : acc;
    }

    /** power null em movimentos de status; usamos 40 como padrão de dano mínimo. */
    private int extrairPoder(JsonObject move) {
        if (move.get("power").isJsonNull()) return 40;
        int power = move.get("power").getAsInt();
        return power == 0 ? 40 : power;
    }

    /** URL da API termina com /79/ → id 79. */
    private int extrairIdDaUrl(String url) {
        String[] partes = url.split("/");
        return Integer.parseInt(partes[partes.length - 1]);
    }

    /** API usa minúsculas com hífen; exibimos "Razor-leaf" → "Razor-leaf" capitalizado. */
    private String capitalizar(String nome) {
        if (nome == null || nome.isEmpty()) return nome;
        return nome.substring(0, 1).toUpperCase() + nome.substring(1).toLowerCase();
    }

    /** Par nome + nível de aprendizado (record Java = classe de dados imutável simples). */
    private record MovimentoInfo(String nome, int nivel) {}
}
