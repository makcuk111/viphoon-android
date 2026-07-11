package io.nekohasekai.sfa.compose.model

// Раскладка нод по смысловым секциям, как в десктоп-клиенте ViPhooN
// (VPN / Hysteria2 / Обходы белых списков / Автовыбор).
enum class NodeSection(val title: String, val hint: String? = null) {
    AUTO("Автовыбор"),
    VPN("VPN — локации"),
    HYSTERIA("Hysteria2", "QUIC-протокол, устойчив к блокировкам"),
    BYPASS("Обходы белых списков", "трафик считается из лимита"),
}

private val BYPASS_REGEX = Regex("обход|bypass|white|бел[ыо]|lte", RegexOption.IGNORE_CASE)

fun nodeSection(item: GroupItem): NodeSection = when {
    item.type == "urltest" || item.type == "URLTest" -> NodeSection.AUTO
    BYPASS_REGEX.containsMatchIn(item.tag) -> NodeSection.BYPASS
    item.type.startsWith("hysteria", ignoreCase = true) -> NodeSection.HYSTERIA
    else -> NodeSection.VPN
}

// Секции в порядке отображения; пустые пропускаются.
fun sectionize(items: List<GroupItem>): List<Pair<NodeSection, List<GroupItem>>> {
    val bySection = items.groupBy(::nodeSection)
    return listOf(NodeSection.AUTO, NodeSection.VPN, NodeSection.HYSTERIA, NodeSection.BYPASS)
        .mapNotNull { s -> bySection[s]?.let { s to it } }
}
