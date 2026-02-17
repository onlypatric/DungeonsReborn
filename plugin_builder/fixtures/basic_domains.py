"""Fixtures for tests: minimal content for each domain."""

from dungeonsreborn_builder import (
    AbilityBuilder,
    ContentPack,
    Item,
    Material,
    Mob,
    EntityType,
    MobAttack,
    MobAttackTrigger,
    Quest,
    Shop,
    damage,
)


def build(output_dir: str) -> list[str]:
    ping = AbilityBuilder("fx_ping").name("Ping").action(damage(1.0))
    item = Item("fixture_item").name("Fixture Item").material(Material.STONE).bind_use("fx_ping")
    mob = (
        Mob("fixture_mob")
        .name("Fixture Mob")
        .mob_type(EntityType.ZOMBIE)
        .main_attack(MobAttack(ability="fx_ping", trigger=MobAttackTrigger.MELEE))
    )
    quest = Quest("fixture_quest", "Fixture Quest").kill_mob("fixture_mob", count=1).reward_tokens(1)
    shop = Shop("fixture_shop", "Fixture Shop").trade(item, cost_tokens=1)
    pack = ContentPack().add(ping, item, mob, quest, shop)
    return pack.export(output_dir)


if __name__ == "__main__":
    build("./out")
