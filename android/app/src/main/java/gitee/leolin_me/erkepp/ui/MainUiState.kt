package gitee.leolin_me.erkepp.ui

/**
 * 主界面所有覆盖层（二级页）的可见性集中在这里。
 *
 * 渐进式试点：当前只收拢 `showAllOrgs`，其余 `showXxx` 仍是 `MainActivity` 里的独立状态。
 * 迁移一个、删一个，不要一次搬完——这个类还是中间态，不是最终形态。
 *
 * 约定：全部 `val`，改状态一律 `uiState = uiState.copy(...)`。
 * 同一个 lambda 里要改多个字段时合并成一次 `copy`，分两次写会丢掉前一次的结果。
 */
data class MainUiState(
    /** 「更多 → 所有组织」覆盖层 */
    val showAllOrgs: Boolean = false,
)
