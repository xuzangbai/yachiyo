package dev.yachiyoc.combat;

/**
 * 自动切换武器的时机。
 */
public enum WeaponSwitchMode {
    /** 静默切换：本地手持不动，只给服务端发「换到武器 → 攻击 → 换回来」，命中带武器附魔。 */
    SILENT,
    /** 每次攻击切到武器并保持（原来默认的行为）。 */
    PER_ATTACK,
    /** 攻击时本地切到武器，出完手立刻切回原来那一格。 */
    SWAP_BACK
}
