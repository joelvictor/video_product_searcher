package ai.videosearch.links

/** Detecta o navegador interno do Instagram/TikTok pelo User-Agent. */
enum class InAppBrowser {
    INSTAGRAM, TIKTOK, FACEBOOK;

    companion object {
        fun detect(userAgent: String?): InAppBrowser? {
            val ua = userAgent ?: return null
            return when {
                "Instagram" in ua -> INSTAGRAM
                "musical_ly" in ua || "BytedanceWebview" in ua || "TikTok" in ua -> TIKTOK
                "FBAN" in ua || "FBAV" in ua -> FACEBOOK
                else -> null
            }
        }
    }
}
