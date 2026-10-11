// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.core

/** Website home "hot" sections; each one links to its own ranking page. */
enum class RankType(val slug: String, val routeKey: String, val rankKey: String, val label: String) {
    SHORT("hot-drama", "rank_hot-drama/page", "hongguo", "热播短剧"),
    COMIC("hot-comic-drama", "rank_hot-comic-drama/page", "comic", "热播漫剧"),
    REAL("hot-real-drama", "rank_hot-real-drama/page", "real", "热播真人剧");
}
