# Planejamento — Jogo Pokémon (POO)

Documento oficial do projeto. **Não utiliza pacote `model/`** — os dados ficam no banco H2 e o acesso é feito com **SQL direto** no `PokemonRepository`, como no modelo original do projeto.

---

## Decisões de arquitetura

| Decisão | Escolha |
|---------|---------|
| Persistência | H2 (`jdbc:h2:./pokemon_db`) |
| Acesso a dados | `PokemonRepository` com `CREATE TABLE`, `INSERT`, `SELECT`, `UPDATE` |
| API externa | [PokeAPI](https://pokeapi.co/) — apenas na **carga inicial** (Fase 1) |
| Parse JSON | **Gson** (`lib/gson-2.11.0.jar`) — sem parser manual/recursivo |
| Pacote `model/` | **Não usar** — remover/ignorar classes `Pokemon`, `Movimento`, `Jogador`, `Tipo` |
| Rotas de oponentes | **Outro integrante** — fora do escopo atual |

---

## Dependências

| Biblioteca | Arquivo | Uso |
|------------|---------|-----|
| H2 Database | `lib/h2-2.4.240.jar` | Banco embarcado |
| Gson | `lib/gson-2.11.0.jar` | Ler JSON da PokeAPI |

### Configuração no IntelliJ

1. **File → Project Structure → Libraries → +**
2. Selecionar `lib/gson-2.11.0.jar`
3. Aplicar em `JogoPokemon` (junto com o H2)

### Exemplo de uso do Gson (sem recursão manual)

```java
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

String json = pokemonAPI.buscarPokemonPorId(152);
JsonObject root = JsonParser.parseString(json).getAsJsonObject();

String nome = root.get("name").getAsString();
int hp = root.getAsJsonArray("stats").asList().stream()
    .filter(s -> s.getAsJsonObject().get("stat").getAsJsonObject()
        .get("name").getAsString().equals("hp"))
    .findFirst().get().getAsJsonObject().get("base_stat").getAsInt();
```

> **Resposta à dúvida:** Sim, dá para buscar no JSON com biblioteca Java. As opções mais usadas são **Gson** (simples) e **Jackson** (mais robusto). Este projeto adota **Gson** para não escrever funções recursivas de parse em `JsonHelper` / `PokeAPIParser`.

---

## Fases do projeto

| Fase | Objetivo | Quem implementa | Status |
|------|----------|-----------------|--------|
| **1** | Criar tabelas (SQL), buscar 150 Pokémon + 3 iniciais na API, cadastrar evoluções e movimentos | Este grupo | **Concluída** |
| **2** | Consultas SQL para leitura do catálogo (sem chamar API) | Este grupo | **Concluída** |
| **3** | Básico do jogo: **batalha**, **evolução**, **cálculo de XP** (+ poção e fuga) | Este grupo | **Concluída** |
| **4** | Rotas pré-definidas com oponentes aleatórios no nível do jogador | **Outro integrante** | Pendente |

---

## Fase 1 — Cadastro no banco (AGORA)

### Escopo

1. Executar `CREATE TABLE` no `PokemonRepository.criarTabela()`.
2. Buscar na PokeAPI:
   - **150 Pokémon** — IDs nacionais **1 a 150**
   - **3 iniciais** da geração de Chikorita (Johto): **152, 155, 158**
3. Para cada um: gravar Pokémon, evoluções e movimentos via `INSERT`.
4. **Não** implementar batalha, XP nem rotas.

### Iniciais (Geração II — Johto)

| Pokémon | ID API | Evoluções |
|---------|--------|-----------|
| Chikorita | 152 | Bayleef → Meganium |
| Cyndaquil | 155 | Quilava → Typhlosion |
| Totodile | 158 | Croconaw → Feraligatr |

Marcar com `eh_inicial = TRUE` apenas nesses três.

---

## SQL — Criação das tabelas

Tudo em `PokemonRepository.criarTabela()` com `Statement.execute(...)`.

### Catálogo (dados da API)

```sql
CREATE TABLE IF NOT EXISTS pokemon (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    nome            VARCHAR(200) NOT NULL UNIQUE,
    tipo            VARCHAR(100),
    tipo2           VARCHAR(100),
    fraqueza        VARCHAR(500),
    id_pokemon_api  INT NOT NULL UNIQUE,
    vida            INT,
    ataque          INT,
    defesa          INT,
    eh_inicial      BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS movimento_pkm (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    pokemon_id        INT NOT NULL,
    nome              VARCHAR(200) NOT NULL,
    pp_max            INT NOT NULL,
    tipo              VARCHAR(50),
    precisao          INT,
    poder             INT,
    nivel_aprendizado INT,
    FOREIGN KEY (pokemon_id) REFERENCES pokemon(id)
);

CREATE TABLE IF NOT EXISTS evolucao_pkm (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    pokemon_id       INT NOT NULL,
    ordem            INT NOT NULL,
    nome_evolucao    VARCHAR(200) NOT NULL,
    id_api_evolucao  INT,
    nivel_minimo     INT DEFAULT 6,
    FOREIGN KEY (pokemon_id) REFERENCES pokemon(id)
);
```

### Jogo — jogador e progresso (Fases 2 e 3)

```sql
CREATE TABLE IF NOT EXISTS jogador (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    nome                 VARCHAR(200) NOT NULL,
    pocao                INT DEFAULT 5,
    inimigos_derrotados  INT DEFAULT 0,
    rota_atual           INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pokemon_jogador (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    id_jogador   INT NOT NULL,
    id_pokemon   INT NOT NULL,
    nivel        INT DEFAULT 5,
    xp           INT DEFAULT 0,
    hp_atual     INT,
    FOREIGN KEY (id_jogador) REFERENCES jogador(id),
    FOREIGN KEY (id_pokemon) REFERENCES pokemon(id)
);

CREATE TABLE IF NOT EXISTS movimento_jogador (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    id_pokemon_jogador  INT NOT NULL,
    id_movimento        INT NOT NULL,
    pp_atual            INT NOT NULL,
    FOREIGN KEY (id_pokemon_jogador) REFERENCES pokemon_jogador(id),
    FOREIGN KEY (id_movimento) REFERENCES movimento_pkm(id)
);
```

### Rotas (outro integrante — Fase 4)

```sql
-- Estrutura sugerida; implementação ficará com outro membro do grupo
CREATE TABLE IF NOT EXISTS rota (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    nome      VARCHAR(200),
    ordem     INT NOT NULL
);

CREATE TABLE IF NOT EXISTS rota_pokemon (
    id       INT AUTO_INCREMENT PRIMARY KEY,
    rota_id  INT NOT NULL,
    FOREIGN KEY (rota_id) REFERENCES rota(id)
);
```

> Na Fase 4, cada rota sorteia um Pokémon do catálogo (regras de nível a cargo do integrante de rotas). **Não implementar agora.**

---

## SQL — Consultas necessárias

### Fase 1 — Escrita (cadastro)

| Método no Repository | SQL |
|---------------------|-----|
| `inserirPokemon(...)` | `INSERT INTO pokemon (...) VALUES (...)` |
| `inserirMovimento(...)` | `INSERT INTO movimento_pkm (...) VALUES (...)` |
| `inserirEvolucao(...)` | `INSERT INTO evolucao_pkm (...) VALUES (...)` |
| `limparCatalogo()` | `DELETE FROM movimento_pkm; DELETE FROM evolucao_pkm; DELETE FROM pokemon;` (opcional, recarga) |

### Fase 2 — Leitura (catálogo)

| Método | SQL resumido |
|--------|--------------|
| `buscarPokemonPorId(id)` | `SELECT * FROM pokemon WHERE id = ?` |
| `buscarPokemonPorNome(nome)` | `SELECT * FROM pokemon WHERE nome = ?` |
| `listarIniciais()` | `SELECT * FROM pokemon WHERE eh_inicial = TRUE` |
| `listarTodos()` | `SELECT * FROM pokemon ORDER BY id_pokemon_api` |
| `listarEvolucoes(pokemonId)` | `SELECT * FROM evolucao_pkm WHERE pokemon_id = ? ORDER BY ordem` |
| `listarMovimentos(pokemonId)` | `SELECT * FROM movimento_pkm WHERE pokemon_id = ?` |

### Fase 3 — Jogo (básico)

| Método | SQL resumido |
|--------|--------------|
| `criarJogador(nome)` | `INSERT INTO jogador (nome, pocao) VALUES (?, 5)` |
| `associarPokemonJogador(...)` | `INSERT INTO pokemon_jogador (...)` |
| `atualizarHp(id, hp)` | `UPDATE pokemon_jogador SET hp_atual = ? WHERE id = ?` |
| `atualizarXpNivel(...)` | `UPDATE pokemon_jogador SET xp = ?, nivel = ? WHERE id = ?` |
| `usarPocao(jogadorId)` | `UPDATE jogador SET pocao = pocao - 1 WHERE id = ? AND pocao > 0` |
| `atualizarPpMovimento(...)` | `UPDATE movimento_jogador SET pp_atual = ? WHERE id = ?` |
| `salvarJornada(...)` / `carregarJornada()` | `SELECT` + `UPDATE` combinados nas tabelas de jogador |

---

## Estrutura de classes (sem `model/`)

```
JogoPokemon/
├── API/
│   ├── PokemonAPI.java       # HTTP → retorna String JSON
│   └── PokeAPILoader.java    # Gson: JSON → dados para INSERT
├── repository/
│   └── PokemonRepository.java  # SQL: criar tabelas, INSERT, SELECT, UPDATE
├── service/                  # (Fase 3)
│   ├── BatalhaService.java   # turnos, dano, precisão, PP, poção, fuga
│   ├── EvolucaoService.java  # nível 6 e 14, consulta evolucao_pkm
│   └── XpService.java        # ganho de XP e subida de nível
└── Application/
    └── Main.java             # Fase 1: carga | Fase 2: consultas | Fase 3: jogo
```

### O que remover / não usar

- `model/Pokemon.java`, `model/Movimento.java`, `model/Jogador.java`, `model/Tipo.java`, `model/EstadoJogo.java`
- `API/JsonHelper.java`, `API/PokeAPIParser.java` (substituídos pelo **Gson**)
- `service/JogoService.java` (versão com model — reescrever na Fase 3 com SQL)
- `API/PokemonAPIService.java` (lógica migra para `PokeAPILoader` + Repository)

---

## Fluxo Fase 1 — Carga com Gson + SQL

```
Main
  └─ PokeAPILoader.carregarCatalogo(repository)

        SE repository.catalogoJaPopulado() == true:
            imprimir "Tabela já possui dados. População ignorada."
            repository.imprimirPrimeiros20()
            ENCERRAR (sem chamar a API)

        SENÃO:
            para id = 1..150 e id in (152, 155, 158):
              json = PokemonAPI.buscarPokemonPorId(id)
              Gson → INSERT pokemon, evolucao_pkm, movimento_pkm
              Thread.sleep(150)

            repository.imprimirPrimeiros20()
```

### Verificação antes de popular

- Método `catalogoJaPopulado()`: `SELECT COUNT(*) FROM pokemon` — se **> 0**, pula toda a carga.
- Ao reabrir o projeto, **não refaz** as requisições à PokeAPI.
- No final (carga nova ou pulada): **`imprimirPrimeiros20()`** no console com os 20 primeiros registros ordenados por `id_pokemon_api`.

---

## Fase 3 — Básico do jogo (próxima etapa deste grupo)

Implementar **apenas** o núcleo abaixo. Rotas e ordem dos oponentes ficam para o outro integrante.

### BatalhaService

- Exibir status a cada turno: nome, HP, PP atual/máximo, tipo.
- Opções: **usar movimento**, **usar poção** (+50 HP, 5 iniciais), **fugir**.
- Precisão: `random < precisao` (dado de `movimento_pkm`).
- PP: decrementar em `movimento_jogador.pp_atual`.
- Dano: fórmula usando `ataque`, `defesa`, `poder`, `nivel` (lidos do banco).

### XpService

- Vitória → `xp += valor`.
- Se `xp >= nivel * 100` → `nivel++`, persistir com `UPDATE pokemon_jogador`.

### EvolucaoService

- Ao atingir **nível 6** → consultar `evolucao_pkm` ordem 1 → trocar `id_pokemon` do jogador.
- Ao atingir **nível 14** → consultar `evolucao_pkm` ordem 2.
- Stats e movimentos vêm do catálogo já cadastrado.

### Fora do escopo (outro integrante)

- Tabela `rota` / `rota_pokemon`
- Progressão por rotas pré-definidas
- Sorteio de oponente aleatório por rota (nível compatível com o jogador — regra definida pelo integrante de rotas)

---

## Regras de negócio (referência para Fase 3)

| Regra | Valor |
|-------|-------|
| Poções iniciais | 5 |
| Cura por poção | 50 HP |
| XP para subir | `nivel * 100` |
| 1ª evolução | nível 6 |
| 2ª evolução | nível 14 |
| Movimento | nome, PP atual, PP máx, tipo, precisão |

---

## Ordem de implementação

```
AGORA — Fase 1
  1. Adicionar Gson em lib/ e no módulo IntelliJ
  2. Reescrever PokemonRepository → SQL do catálogo (CREATE + INSERT)
  3. Criar PokeAPILoader com Gson
  4. Verificar catalogoJaPopulado() antes de chamar a API
  5. Main: executar carga dos 150 + 3 iniciais
  6. imprimirPrimeiros20() ao final
  7. Validar com SELECT COUNT(*)

Depois — Fase 2
  6. Métodos SELECT no Repository
  7. Main de testes de consulta

Depois — Fase 3 (este grupo)
  8. BatalhaService
  9. XpService
  10. EvolucaoService
  11. SQL de save/load do jogador

Depois — Fase 4 (outro integrante)
  12. Rotas pré-definidas + oponente aleatório por nível
```

---

## Critério de aceite — Fase 1

```sql
SELECT COUNT(*) FROM pokemon;                          -- 153
SELECT COUNT(*) FROM pokemon WHERE eh_inicial = TRUE;  -- 3
SELECT COUNT(*) FROM evolucao_pkm;                     -- > 0
SELECT COUNT(*) FROM movimento_pkm;                    -- > 0
```

---

## Referências

- PokeAPI: https://pokeapi.co/docs/v2
- Gson: https://github.com/google/gson
- H2: https://www.h2database.com/

---

## Observações

- `planejamento.txt` é rascunho; **`PLANEJAMENTO.md` é o documento oficial**.
- Não usar pacote `model/`; dados trafegam via `Map<String,Object>`, SQL e primitivos.
- Banco H2 com `AUTO_SERVER=TRUE` para evitar lock ao reexecutar.

---

## Resumo da implementação

Execução concluída em **21/05/2026**. Validação no banco: **153 Pokémon**, **3 iniciais**, **127 evoluções**, **594 movimentos**.

### Fase 1 — Cadastro (PokeAPI + SQL)

- [x] Tabelas `pokemon`, `movimento_pkm`, `evolucao_pkm`, `jogador`, `pokemon_jogador`, `movimento_jogador` via SQL
- [x] `PokemonAPI.java` — requisições HTTP à PokeAPI
- [x] `PokeAPILoader.java` — parse com **Gson** (sem parser manual)
- [x] Cadastro de 150 Pokémon (IDs 1–150) + 3 iniciais Johto (152, 155, 158)
- [x] Evoluções gravadas em `evolucao_pkm` (nível 6 e 14)
- [x] Movimentos gravados em `movimento_pkm` (nome, PP, precisão, tipo, poder)
- [x] `catalogoJaPopulado()` — não repopula se já houver dados
- [x] `imprimirPrimeiros20()` ao final da carga ou ao pular

### Fase 2 — Consultas SQL (sem API)

- [x] `buscarPokemonPorId(id)`
- [x] `buscarPokemonPorNome(nome)`
- [x] `listarIniciais()`
- [x] `listarTodos()`
- [x] `listarEvolucoes(pokemonId)`
- [x] `listarMovimentos(pokemonId)`
- [x] `ConsultaService` — demonstração no menu (opção 2)

### Fase 3 — Jogo básico

- [x] `BatalhaService` — movimento, poção (+50 HP), fuga
- [x] Status a cada turno (nome, HP, PP atual/máx, tipo, precisão)
- [x] Precisão e PP considerados na batalha
- [x] Dano com tipo (efetividade via `TipoEfetividade`)
- [x] `XpService` — ganho de XP e level up (`nivel * 100`)
- [x] `EvolucaoService` — evolução automática nos níveis 6 e 14
- [x] Escolha dos 3 iniciais (Chikorita, Cyndaquil, Totodile)
- [x] Oponente aleatório do catálogo (temporário até Fase 4 de rotas)
- [x] Save implícito no banco (jogador + pokemon_jogador + movimento_jogador)
- [x] `Main` com menu: 1=Carga, 2=Consultas, 3=Jogar, 4=Sair

### Fase 4 — Rotas (outro integrante)

- [ ] Tabela `rota` / `rota_pokemon`
- [ ] Progressão por rotas pré-definidas
- [ ] Oponente por rota com nível do jogador

### Arquivos do projeto

| Arquivo | Função |
|---------|--------|
| `Application/Main.java` | Menu principal |
| `API/PokemonAPI.java` | HTTP PokeAPI |
| `API/PokeAPILoader.java` | Carga Gson → INSERT |
| `repository/PokemonRepository.java` | Todo SQL (CREATE, INSERT, SELECT, UPDATE) |
| `service/ConsultaService.java` | Demo Fase 2 |
| `service/BatalhaService.java` | Combate |
| `service/XpService.java` | Experiência |
| `service/EvolucaoService.java` | Evolução |
| `service/TipoEfetividade.java` | Multiplicador de tipos |
| `service/JogoService.java` | Fluxo do jogador |
| `lib/h2-2.4.240.jar` | Banco H2 |
| `lib/gson-2.11.0.jar` | JSON |

### Como executar

```
java -cp "out:lib/h2-2.4.240.jar:lib/gson-2.11.0.jar" JogoPokemon.Application.Main
```

1. **Opção 1** — popula na primeira vez; nas seguintes apenas exibe os 20 primeiros.
2. **Opção 2** — testa consultas SQL.
3. **Opção 3** — jogo (escolher inicial, batalhar, ganhar XP, evoluir).
