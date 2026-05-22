package JogoPokemon.service;

/**
 * Tabela de vantagem/desvantagem entre tipos (nomes em inglês como na PokeAPI).
 * <p>
 * Usado em BatalhaService.calcularDano: retorna 2.0 (super efetivo), 0.5 (fraco), 0.0 (imune) ou 1.0.
 * <p>
 * Classe utilitária (construtor privado) — não instanciamos, só chamamos multiplicador().
 */
public final class TipoEfetividade {

    private TipoEfetividade() {}

    /**
     * @param tipoAtaque tipo do movimento (ex: "water")
     * @param tipoDefesa tipo do Pokémon defensor (ex: "fire")
     */
    public static double multiplicador(String tipoAtaque, String tipoDefesa) {
        if (tipoAtaque == null || tipoDefesa == null) return 1.0;
        return switch (tipoAtaque.toLowerCase()) {
            case "fire" -> switch (tipoDefesa.toLowerCase()) {
                case "grass", "ice", "bug" -> 2.0;
                case "water", "rock", "dragon" -> 0.5;
                default -> 1.0;
            };
            case "water" -> switch (tipoDefesa.toLowerCase()) {
                case "fire", "ground", "rock" -> 2.0;
                case "water", "grass", "dragon" -> 0.5;
                default -> 1.0;
            };
            case "grass" -> switch (tipoDefesa.toLowerCase()) {
                case "water", "ground", "rock" -> 2.0;
                case "fire", "grass", "poison", "flying", "bug", "dragon" -> 0.5;
                default -> 1.0;
            };
            case "electric" -> switch (tipoDefesa.toLowerCase()) {
                case "water", "flying" -> 2.0;
                case "electric", "grass", "dragon" -> 0.5;
                default -> 1.0;
            };
            case "ice" -> switch (tipoDefesa.toLowerCase()) {
                case "grass", "ground", "flying", "dragon" -> 2.0;
                case "fire", "water", "ice" -> 0.5;
                default -> 1.0;
            };
            case "fighting" -> switch (tipoDefesa.toLowerCase()) {
                case "normal", "ice", "rock", "dark", "steel" -> 2.0;
                case "poison", "flying", "psychic", "bug", "fairy" -> 0.5;
                default -> 1.0;
            };
            case "poison" -> switch (tipoDefesa.toLowerCase()) {
                case "grass", "fairy" -> 2.0;
                case "poison", "ground", "rock", "ghost" -> 0.5;
                default -> 1.0;
            };
            case "ground" -> switch (tipoDefesa.toLowerCase()) {
                case "fire", "electric", "poison", "rock", "steel" -> 2.0;
                case "grass", "bug" -> 0.5;
                case "flying" -> 0.0;
                default -> 1.0;
            };
            case "flying" -> switch (tipoDefesa.toLowerCase()) {
                case "grass", "fighting", "bug" -> 2.0;
                case "electric", "rock", "steel" -> 0.5;
                default -> 1.0;
            };
            case "psychic" -> switch (tipoDefesa.toLowerCase()) {
                case "fighting", "poison" -> 2.0;
                case "psychic", "steel" -> 0.5;
                default -> 1.0;
            };
            case "bug" -> switch (tipoDefesa.toLowerCase()) {
                case "grass", "psychic", "dark" -> 2.0;
                case "fire", "fighting", "poison", "flying", "ghost", "steel", "fairy" -> 0.5;
                default -> 1.0;
            };
            case "rock" -> switch (tipoDefesa.toLowerCase()) {
                case "fire", "ice", "flying", "bug" -> 2.0;
                case "fighting", "ground", "steel" -> 0.5;
                default -> 1.0;
            };
            case "ghost" -> switch (tipoDefesa.toLowerCase()) {
                case "psychic", "ghost" -> 2.0;
                case "dark" -> 0.5;
                case "normal" -> 0.0;
                default -> 1.0;
            };
            case "dragon" -> switch (tipoDefesa.toLowerCase()) {
                case "dragon" -> 2.0;
                case "steel" -> 0.5;
                case "fairy" -> 0.0;
                default -> 1.0;
            };
            case "dark" -> switch (tipoDefesa.toLowerCase()) {
                case "psychic", "ghost" -> 2.0;
                case "fighting", "dark", "fairy" -> 0.5;
                default -> 1.0;
            };
            case "steel" -> switch (tipoDefesa.toLowerCase()) {
                case "ice", "rock", "fairy" -> 2.0;
                case "fire", "water", "electric", "steel" -> 0.5;
                default -> 1.0;
            };
            case "fairy" -> switch (tipoDefesa.toLowerCase()) {
                case "fighting", "dragon", "dark" -> 2.0;
                case "fire", "poison", "steel" -> 0.5;
                default -> 1.0;
            };
            default -> 1.0;
        };
    }
}
