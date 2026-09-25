package net.srv.eloranks.rank;

/**
 * One tier from config.yml.
 *
 * @param id the config key (tier-6); stored in the database, so renaming it resets that tier's claims
 * @param number the tier number shown to players (6 = lowest, 1 = highest by default)
 * @param elo ELO needed to reach the tier
 * @param rewardId key under rewards:
 * @param slot slot in the /kits GUI
 */
public record Tier(String id, int number, String name, String color, int elo, String rewardId, int slot) {
}
