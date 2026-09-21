package com.kododake.aabrowser.web.adblock

import java.io.DataInput
import java.io.DataOutput
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Immutable parser/matcher for the commonly used subset of uBlock static filters. */
class FilterEngine private constructor(
    private val networkRules: List<NetworkRule>,
    private val cosmeticRules: List<CosmeticRule>,
    private val scriptletRules: List<ScriptletRule>
) {
    private val indexedBlockingRules: Map<String, List<NetworkRule>>
    private val unindexedBlockingRules: List<NetworkRule>
    private val indexedExceptionRules: Map<String, List<NetworkRule>>
    private val unindexedExceptionRules: List<NetworkRule>
    private val globalCosmeticRules: List<CosmeticRule>
    private val cosmeticRulesByDomain: Map<String, List<CosmeticRule>>
    private val entityCosmeticRules: List<CosmeticRule>
    private val globalScriptletRules: List<ScriptletRule>
    private val scriptletRulesByDomain: Map<String, List<ScriptletRule>>
    private val entityScriptletRules: List<ScriptletRule>

    init {
        val blocking = HashMap<String, MutableList<NetworkRule>>()
        val blockingUnindexed = ArrayList<NetworkRule>()
        val exceptions = HashMap<String, MutableList<NetworkRule>>()
        val exceptionsUnindexed = ArrayList<NetworkRule>()
        networkRules.forEach { rule ->
            val targetIndex = if (rule.exception) exceptions else blocking
            val targetUnindexed = if (rule.exception) exceptionsUnindexed else blockingUnindexed
            if (rule.token == null) targetUnindexed += rule
            else targetIndex.getOrPut(rule.token) { ArrayList() } += rule
        }
        indexedBlockingRules = blocking
        unindexedBlockingRules = blockingUnindexed
        indexedExceptionRules = exceptions
        unindexedExceptionRules = exceptionsUnindexed

        val globalCosmetic = ArrayList<CosmeticRule>()
        val cosmeticByDomain = HashMap<String, MutableList<CosmeticRule>>()
        val entityCosmetic = ArrayList<CosmeticRule>()
        cosmeticRules.forEach { rule ->
            if (rule.includedDomains.isEmpty()) globalCosmetic += rule
            else {
                var hasEntity = false
                rule.includedDomains.forEach { domain ->
                    if (domain.endsWith(".*")) hasEntity = true
                    else cosmeticByDomain.getOrPut(domain) { ArrayList() } += rule
                }
                if (hasEntity) entityCosmetic += rule
            }
        }
        globalCosmeticRules = globalCosmetic
        cosmeticRulesByDomain = cosmeticByDomain
        entityCosmeticRules = entityCosmetic

        val globalScriptlets = ArrayList<ScriptletRule>()
        val scriptletsByDomain = HashMap<String, MutableList<ScriptletRule>>()
        val entityScriptlets = ArrayList<ScriptletRule>()
        scriptletRules.forEach { rule ->
            if (rule.includedDomains.isEmpty()) globalScriptlets += rule
            else {
                var hasEntity = false
                rule.includedDomains.forEach { domain ->
                    if (domain.endsWith(".*")) hasEntity = true
                    else scriptletsByDomain.getOrPut(domain) { ArrayList() } += rule
                }
                if (hasEntity) entityScriptlets += rule
            }
        }
        globalScriptletRules = globalScriptlets
        scriptletRulesByDomain = scriptletsByDomain
        entityScriptletRules = entityScriptlets
    }

    enum class ResourceType { SCRIPT, IMAGE, STYLESHEET, FONT, MEDIA, XHR, SUBDOCUMENT, OTHER }

    data class Request(
        val url: String,
        val pageUrl: String?,
        val resourceType: ResourceType = ResourceType.OTHER,
        val requestHost: String? = null,
        val pageHost: String? = null
    )

    data class SourceLine(val text: String, val trusted: Boolean = false)

    data class ScriptletInvocation(
        val name: String,
        val arguments: List<String>,
        val trusted: Boolean
    )

    fun shouldBlock(request: Request): Boolean {
        val requestHost = request.requestHost?.let(::normalizeHost) ?: hostOf(request.url) ?: return false
        val pageHost = request.pageHost?.let(::normalizeHost) ?: hostOf(request.pageUrl)
        val context = MatchContext(
            request,
            requestHost,
            pageHost,
            pageHost != null && !sameSite(requestHost, pageHost)
        )
        if (unindexedExceptionRules.any { it.matches(context) } ||
            matchesIndexed(request.url, indexedExceptionRules, context)) return false
        if (unindexedBlockingRules.any { it.matches(context) }) return true
        return matchesIndexed(request.url, indexedBlockingRules, context)
    }

    fun cosmeticCss(pageUrl: String?): String {
        val host = hostOf(pageUrl) ?: return ""
        val hidden = LinkedHashSet<String>()
        val exceptions = HashSet<String>()
        domainCandidates(host, globalCosmeticRules, cosmeticRulesByDomain, entityCosmeticRules).forEach { rule ->
            if (rule.appliesTo(host)) {
                if (rule.exception) exceptions += rule.selector else hidden += rule.selector
            }
        }
        hidden.removeAll(exceptions)
        return hidden.joinToString(",\n") { "$it { display: none !important; }" }
    }

    fun scriptletsFor(pageUrl: String?): List<ScriptletInvocation> {
        val host = hostOf(pageUrl) ?: return emptyList()
        val candidates = domainCandidates(host, globalScriptletRules, scriptletRulesByDomain, entityScriptletRules)
        val exceptions = candidates.filter { it.exception && it.appliesTo(host) }
        if (exceptions.any { it.arguments.isEmpty() }) return emptyList()
        val excepted = exceptions.mapTo(HashSet()) { it.key }
        val selected = LinkedHashMap<Pair<String, List<String>>, ScriptletInvocation>()
        candidates.asSequence()
            .filter { !it.exception && it.appliesTo(host) && it.key !in excepted }
            .forEach { rule ->
                val existing = selected[rule.key]
                if (existing == null || (!existing.trusted && rule.trusted)) {
                    selected[rule.key] = ScriptletInvocation(rule.name, rule.arguments, rule.trusted)
                }
            }
        return selected.values.toList()
    }

    private fun <T> domainCandidates(
        host: String,
        global: List<T>,
        byDomain: Map<String, List<T>>,
        entities: List<T>
    ): List<T> {
        val result = ArrayList<T>(global.size + 16)
        result.addAll(global)
        var suffixStart = 0
        while (suffixStart < host.length) {
            byDomain[host.substring(suffixStart)]?.let(result::addAll)
            val dot = host.indexOf('.', suffixStart)
            if (dot < 0) break
            suffixStart = dot + 1
        }
        result.addAll(entities)
        return result
    }

    val ruleCount: Int get() = networkRules.size + cosmeticRules.size + scriptletRules.size

    internal fun writeSnapshot(output: DataOutput) {
        output.writeInt(networkRules.size)
        networkRules.forEach { rule ->
            when (val pattern = rule.pattern) {
                is HostPattern -> {
                    output.writeByte(PATTERN_HOST)
                    output.writeSizedString(pattern.host)
                }
                is RegexPattern -> {
                    output.writeByte(PATTERN_REGEX)
                    output.writeSizedString(pattern.regex.pattern)
                    output.writeBoolean(RegexOption.IGNORE_CASE in pattern.regex.options)
                }
                is LiteralPattern -> {
                    output.writeByte(PATTERN_LITERAL)
                    output.writeSizedString(pattern.text)
                    output.writeBoolean(pattern.matchCase)
                    output.writeBoolean(pattern.hostAnchored)
                    output.writeBoolean(pattern.startAnchored)
                    output.writeBoolean(pattern.endAnchored)
                }
            }
            output.writeBoolean(rule.exception)
            output.writeNullableString(rule.token)
            output.writeByte(when (rule.thirdParty) { null -> -1; false -> 0; true -> 1 })
            output.writeResourceTypes(rule.includeTypes)
            output.writeResourceTypes(rule.excludeTypes)
            output.writeStringSet(rule.includeDomains)
            output.writeStringSet(rule.excludeDomains)
        }
        output.writeInt(cosmeticRules.size)
        cosmeticRules.forEach { rule ->
            output.writeSizedString(rule.selector)
            output.writeBoolean(rule.exception)
            output.writeStringSet(rule.includedDomains)
            output.writeStringSet(rule.excludedDomains)
        }
        output.writeInt(scriptletRules.size)
        scriptletRules.forEach { rule ->
            output.writeSizedString(rule.name)
            output.writeStringList(rule.arguments)
            output.writeBoolean(rule.exception)
            output.writeStringSet(rule.includedDomains)
            output.writeStringSet(rule.excludedDomains)
            output.writeBoolean(rule.trusted)
        }
    }

    private fun matchesIndexed(
        value: String,
        index: Map<String, List<NetworkRule>>,
        context: MatchContext
    ): Boolean {
        var runStart = -1
        var cursor = 0
        while (cursor <= value.length) {
            val char = value.getOrNull(cursor)
            val tokenChar = char != null && (char.isAsciiLetterOrDigitIgnoreCase() || char == '%')
            if (tokenChar && runStart < 0) runStart = cursor
            if (!tokenChar && runStart >= 0) {
                if (cursor - runStart >= 4) {
                    val token = value.substring(runStart, cursor).lowercase(Locale.ROOT)
                    index[token]?.forEach { if (it.matches(context)) return true }
                }
                runStart = -1
            }
            cursor++
        }
        return false
    }

    companion object {
        private const val PATTERN_HOST = 1
        private const val PATTERN_REGEX = 2
        private const val PATTERN_LITERAL = 3
        private const val MAX_SNAPSHOT_RULES = 2_000_000
        private const val MAX_SNAPSHOT_COLLECTION_SIZE = 100_000
        private const val MAX_SNAPSHOT_STRING_BYTES = 16 * 1024 * 1024
        private val UNSUPPORTED_COSMETIC_OPERATORS = arrayOf(
            ":has-text(", ":matches-css(", ":matches-attr(", ":remove(",
            ":style(", ":upward(", ":xpath(", ":others(", ":watch-attr("
        )
        private val UNHELPFUL_TOKENS = setOf("http", "https", "html", "com", "org", "net")
        private val TYPE_OPTIONS = mapOf(
            "script" to ResourceType.SCRIPT, "image" to ResourceType.IMAGE,
            "stylesheet" to ResourceType.STYLESHEET, "font" to ResourceType.FONT,
            "css" to ResourceType.STYLESHEET,
            "media" to ResourceType.MEDIA, "xmlhttprequest" to ResourceType.XHR,
            "xhr" to ResourceType.XHR, "subdocument" to ResourceType.SUBDOCUMENT,
            "frame" to ResourceType.SUBDOCUMENT, "other" to ResourceType.OTHER,
            "object" to ResourceType.MEDIA
        )

        fun parse(lines: Sequence<String>): FilterEngine =
            parseSources(lines.map { SourceLine(it) })

        fun parseSources(lines: Sequence<SourceLine>): FilterEngine {
            // Keying by source both removes exact duplicates and makes $badfilter
            // deletion O(1), rather than rescanning all previously parsed rules.
            val network = LinkedHashMap<String, NetworkRule>()
            val cosmetic = ArrayList<CosmeticRule>()
            val scriptlets = ArrayList<ScriptletRule>()
            val disabledNetworkRules = HashSet<String>()
            lines.forEach { sourceLine ->
                val line = sourceLine.text.trim()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("# ")) return@forEach
                // Most lines are network filters. Avoid searching them repeatedly
                // for every cosmetic/scriptlet marker.
                if (line.indexOf('#') >= 0) {
                    parseScriptlet(line, sourceLine.trusted)?.let { scriptlets += it; return@forEach }
                    parseCosmetic(line)?.let { cosmetic += it; return@forEach }
                }
                badFilterTarget(line)?.let { target ->
                    disabledNetworkRules += target
                    network.remove(target)
                    return@forEach
                }
                if (line !in disabledNetworkRules) parseNetwork(line)?.let { network.putIfAbsent(line, it) }
            }
            return FilterEngine(network.values.toList(), cosmetic, scriptlets)
        }

        internal fun readSnapshot(input: DataInput): FilterEngine {
            val networkCount = input.readCount(MAX_SNAPSHOT_RULES)
            val network = ArrayList<NetworkRule>(networkCount)
            repeat(networkCount) {
                val pattern = when (val type = input.readUnsignedByte()) {
                    PATTERN_HOST -> HostPattern(input.readSizedString())
                    PATTERN_REGEX -> RegexPattern(Regex(
                        input.readSizedString(),
                        if (input.readBoolean()) setOf(RegexOption.IGNORE_CASE) else emptySet()
                    ))
                    PATTERN_LITERAL -> LiteralPattern(
                        input.readSizedString(), input.readBoolean(), input.readBoolean(),
                        input.readBoolean(), input.readBoolean()
                    )
                    else -> throw IllegalArgumentException("Unknown cached pattern type $type")
                }
                val exception = input.readBoolean()
                val token = input.readNullableString()
                val thirdParty = when (input.readByte().toInt()) {
                    -1 -> null
                    0 -> false
                    1 -> true
                    else -> throw IllegalArgumentException("Invalid cached party option")
                }
                network += NetworkRule(
                    pattern, "", exception, token, thirdParty,
                    input.readResourceTypes(), input.readResourceTypes(),
                    input.readStringSet(), input.readStringSet()
                )
            }
            val cosmetic = ArrayList<CosmeticRule>()
            repeat(input.readCount(MAX_SNAPSHOT_RULES)) {
                cosmetic += CosmeticRule(
                    input.readSizedString(), input.readBoolean(),
                    input.readStringSet(), input.readStringSet()
                )
            }
            val scriptlets = ArrayList<ScriptletRule>()
            repeat(input.readCount(MAX_SNAPSHOT_RULES)) {
                scriptlets += ScriptletRule(
                    input.readSizedString(), input.readStringList(), input.readBoolean(),
                    input.readStringSet(), input.readStringSet(), input.readBoolean()
                )
            }
            return FilterEngine(network, cosmetic, scriptlets)
        }

        private fun DataOutput.writeSizedString(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }

        private fun DataInput.readSizedString(): String {
            val size = readCount(MAX_SNAPSHOT_STRING_BYTES)
            val bytes = ByteArray(size)
            readFully(bytes)
            return String(bytes, StandardCharsets.UTF_8)
        }

        private fun DataOutput.writeNullableString(value: String?) {
            writeBoolean(value != null)
            if (value != null) writeSizedString(value)
        }

        private fun DataInput.readNullableString(): String? =
            if (readBoolean()) readSizedString() else null

        private fun DataOutput.writeStringSet(values: Set<String>) {
            writeInt(values.size)
            values.forEach { writeSizedString(it) }
        }

        private fun DataInput.readStringSet(): Set<String> {
            val count = readCount(MAX_SNAPSHOT_COLLECTION_SIZE)
            return LinkedHashSet<String>(count).apply { repeat(count) { add(readSizedString()) } }
        }

        private fun DataOutput.writeStringList(values: List<String>) {
            writeInt(values.size)
            values.forEach { writeSizedString(it) }
        }

        private fun DataInput.readStringList(): List<String> {
            val count = readCount(MAX_SNAPSHOT_COLLECTION_SIZE)
            return ArrayList<String>(count).apply { repeat(count) { add(readSizedString()) } }
        }

        private fun DataOutput.writeResourceTypes(values: Set<ResourceType>) {
            var mask = 0
            values.forEach { mask = mask or (1 shl it.ordinal) }
            writeInt(mask)
        }

        private fun DataInput.readResourceTypes(): Set<ResourceType> {
            val mask = readInt()
            return ResourceType.entries.filterTo(LinkedHashSet()) { mask and (1 shl it.ordinal) != 0 }
        }

        private fun DataInput.readCount(max: Int): Int {
            val count = readInt()
            require(count in 0..max) { "Invalid cached collection size $count" }
            return count
        }

        private fun parseScriptlet(line: String, trusted: Boolean): ScriptletRule? {
            val marker = when {
                "#@#+js(" in line -> "#@#+js("
                "##+js(" in line -> "##+js("
                else -> return null
            }
            if (!line.endsWith(')')) return null
            val split = line.indexOf(marker)
            val arguments = parseScriptletArguments(line.substring(split + marker.length, line.length - 1))
                ?: return null
            if (arguments.isEmpty() && marker == "##+js(") return null
            val (included, excluded) = parseDomainList(line, 0, split, ',')
            if (included.isEmpty() && marker == "##+js(") return null
            return ScriptletRule(
                name = canonicalScriptletName(arguments.firstOrNull().orEmpty()),
                arguments = arguments.drop(1),
                exception = marker == "#@#+js(",
                includedDomains = included,
                excludedDomains = excluded,
                trusted = trusted
            )
        }

        internal fun parseScriptletArguments(source: String): List<String>? {
            if (source.isBlank()) return emptyList()
            val output = ArrayList<String>()
            val current = StringBuilder()
            var quote: Char? = null
            var atArgumentStart = true
            var index = 0
            while (index < source.length) {
                val char = source[index]
                val next = source.getOrNull(index + 1)
                if (char == '\\' && ((quote != null && next == quote) || (quote == null && next == ','))) {
                    current.append(next)
                    index += 2
                    continue
                }
                if (quote != null) {
                    if (char == quote) quote = null else current.append(char)
                } else if (atArgumentStart && char.isWhitespace()) {
                    // uBO ignores whitespace before the optional opening quote.
                } else if (atArgumentStart && char in charArrayOf('\'', '"', '`')) {
                    quote = char
                    atArgumentStart = false
                } else if (char == ',') {
                    output += current.toString().trim()
                    current.clear()
                    atArgumentStart = true
                } else {
                    current.append(char)
                    atArgumentStart = false
                }
                index++
            }
            if (quote != null) return null
            output += current.toString().trim()
            return output
        }

        private fun canonicalScriptletName(rawName: String): String {
            val name = rawName.removeSuffix(".js")
            return SCRIPTLET_ALIASES[name] ?: name
        }

        private val SCRIPTLET_ALIASES = mapOf(
            "abort-current-inline-script" to "abort-current-script",
            "acis" to "abort-current-script", "acs" to "abort-current-script",
            "ra" to "remove-attr", "urlskip" to "href-sanitizer",
            "aost" to "abort-on-stack-trace", "prevent-eval-if" to "noeval-if",
            "addEventListener-defuser" to "prevent-addEventListener",
            "aeld" to "prevent-addEventListener", "bab-defuser" to "prevent-bab",
            "nobab" to "prevent-bab", "no-fetch-if" to "prevent-fetch",
            "no-setTimeout-if" to "prevent-setTimeout", "nostif" to "prevent-setTimeout",
            "setTimeout-defuser" to "prevent-setTimeout",
            "no-setInterval-if" to "prevent-setInterval", "nosiif" to "prevent-setInterval",
            "setInterval-defuser" to "prevent-setInterval",
            "no-requestAnimationFrame-if" to "prevent-requestAnimationFrame",
            "norafif" to "prevent-requestAnimationFrame", "set" to "set-constant",
            "trusted-set" to "trusted-set-constant", "no-xhr-if" to "prevent-xhr",
            "cookie-remover" to "remove-cookie", "aopr" to "abort-on-property-read",
            "aopw" to "abort-on-property-write",
            "nano-setInterval-booster" to "adjust-setInterval", "nano-sib" to "adjust-setInterval",
            "nano-setTimeout-booster" to "adjust-setTimeout", "nano-stb" to "adjust-setTimeout",
            "refresh-defuser" to "prevent-refresh", "rc" to "remove-class",
            "nowoif" to "prevent-window-open", "no-window-open-if" to "prevent-window-open",
            "window.open-defuser" to "prevent-window-open", "window-close-if" to "close-window",
            "rmnt" to "remove-node-text", "trusted-rpnt" to "trusted-replace-node-text",
            "replace-node-text" to "trusted-replace-node-text", "rpnt" to "trusted-replace-node-text",
            "trusted-rpfr" to "trusted-replace-fetch-response"
        )

        private fun badFilterTarget(line: String): String? {
            val split = optionSeparator(line)
            if (split < 0) return null
            if (line.indexOf("badfilter", split + 1, ignoreCase = true) < 0) return null
            val retained = ArrayList<String>()
            var found = false
            forEachPart(line, split + 1, line.length, ',') { start, end ->
                val option = line.substring(start, end)
                if (option.removePrefix("~").equals("badfilter", ignoreCase = true)) found = true
                else retained += option
            }
            if (!found) return null
            val optionsWithoutBadFilter = retained.joinToString(",")
            return line.substring(0, split) + if (optionsWithoutBadFilter.isEmpty()) {
                ""
            } else {
                "${'$'}$optionsWithoutBadFilter"
            }
        }

        private fun parseCosmetic(line: String): CosmeticRule? {
            val marker = when { "#@#" in line -> "#@#"; "##" in line -> "##"; else -> return null }
            val split = line.indexOf(marker)
            val selector = line.substring(split + marker.length).trim()
            if (selector.isEmpty() || selector.startsWith("+") || selector.startsWith("^") ||
                UNSUPPORTED_COSMETIC_OPERATORS.any(selector::contains)) return null
            val (included, excluded) = parseDomainList(line, 0, split, ',')
            return CosmeticRule(selector, marker == "#@#", included, excluded)
        }

        private fun parseNetwork(source: String): NetworkRule? {
            var line = source
            hostsEntry(line)?.let { line = it }
            val exception = line.startsWith("@@")
            if (exception) line = line.drop(2)
            if (line.isBlank() || line.startsWith("#")) return null
            val optionAt = optionSeparator(line)
            val patternText = if (optionAt >= 0) line.substring(0, optionAt) else line
            val optionText = if (optionAt >= 0) line.substring(optionAt + 1) else ""
            if (patternText.isBlank()) return null

            var thirdParty: Boolean? = null
            var matchCase = false
            val includeTypes = mutableSetOf<ResourceType>()
            val excludeTypes = mutableSetOf<ResourceType>()
            val includeDomains = mutableSetOf<String>()
            val excludeDomains = mutableSetOf<String>()
            var unsupported = false
            forEachPart(optionText, 0, optionText.length, ',') { start, end ->
                val raw = optionText.substring(start, end)
                val negated = raw.startsWith("~")
                val option = raw.removePrefix("~").lowercase(Locale.ROOT)
                when {
                    option == "third-party" || option == "3p" -> thirdParty = !negated
                    option == "first-party" || option == "1p" -> thirdParty = negated
                    option == "match-case" -> matchCase = !negated
                    option == "all" && !negated -> Unit
                    option == "important" && !negated -> Unit
                    option.startsWith("domain=") -> {
                        val valueStart = option.indexOf('=') + 1
                        forEachPart(option, valueStart, option.length, '|') { domainStart, domainEnd ->
                            val excluded = option[domainStart] == '~'
                            val actualStart = if (excluded) domainStart + 1 else domainStart
                            if (actualStart < domainEnd) {
                                val domain = normalizeHost(option.substring(actualStart, domainEnd))
                                if (excluded) excludeDomains += domain else includeDomains += domain
                            }
                        }
                    }
                    option in TYPE_OPTIONS -> {
                        val type = TYPE_OPTIONS.getValue(option)
                        if (negated) excludeTypes += type else includeTypes += type
                    }
                    else -> unsupported = true
                }
            }
            // Silently ignoring an action option such as removeparam or redirect
            // would turn it into a much broader blocking rule, so skip the rule.
            if (unsupported) return null
            return NetworkRule(
                compilePattern(patternText, matchCase) ?: return null, source, exception, extractToken(patternText), thirdParty,
                includeTypes, excludeTypes, includeDomains, excludeDomains
            )
        }

        private fun extractToken(pattern: String): String? {
            if (pattern.length > 2 && pattern.startsWith('/') && pattern.endsWith('/')) return null
            var best: String? = null
            var runStart = -1
            var index = 0
            while (index <= pattern.length) {
                val char = pattern.getOrNull(index)
                val tokenChar = char != null && (char.isAsciiLetterOrDigitIgnoreCase() || char == '%')
                if (tokenChar && runStart < 0) runStart = index
                if (!tokenChar && runStart >= 0) {
                    val length = index - runStart
                    if (length >= 4 && (best == null || length > best.length)) {
                        val candidate = pattern.substring(runStart, index).lowercase(Locale.ROOT)
                        if (candidate !in UNHELPFUL_TOKENS) best = candidate
                    }
                    runStart = -1
                }
                index++
            }
            return best
        }

        private fun optionSeparator(line: String): Int {
            if (line.startsWith('/') && line.lastIndexOf('/') > 0) return line.indexOf('$', line.lastIndexOf('/') + 1)
            return line.indexOf('$')
        }

        private fun compilePattern(text: String, matchCase: Boolean): UrlPattern? {
            val flags = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
            if (text.length > 2 && text.startsWith('/') && text.endsWith('/')) {
                return runCatching { RegexPattern(Regex(text.drop(1).dropLast(1), flags)) }.getOrNull()
            }
            val normalized = text.lowercase(Locale.ROOT)
            if (isDomainOnly(normalized)) {
                return HostPattern(normalizeHost(normalized))
            }
            val hostOnly = hostAnchoredDomain(text)
            if (hostOnly != null) {
                return HostPattern(normalizeHost(hostOnly))
            }
            var pattern = text
            val hostAnchored = pattern.startsWith("||")
            val startAnchored = !hostAnchored && pattern.startsWith('|')
            val endAnchored = pattern.endsWith('|')
            pattern = when { hostAnchored -> pattern.drop(2); startAnchored -> pattern.drop(1); else -> pattern }
            if (endAnchored && pattern.isNotEmpty()) pattern = pattern.dropLast(1)
            if ('*' !in pattern && '^' !in pattern) {
                return LiteralPattern(pattern, matchCase, hostAnchored, startAnchored, endAnchored)
            }
            val regex = StringBuilder()
            when { hostAnchored -> regex.append("^[a-z][a-z0-9+.-]*://(?:[^/?#]*\\.)?"); startAnchored -> regex.append('^') }
            pattern.forEach { char ->
                when (char) {
                    '*' -> regex.append(".*")
                    '^' -> regex.append("(?:[^A-Za-z0-9_.%-]|$)")
                    else -> appendRegexLiteral(regex, char)
                }
            }
            if (endAnchored) regex.append('$')
            return runCatching { RegexPattern(Regex(regex.toString(), flags)) }.getOrNull()
        }

        private fun isDomainOnly(value: String): Boolean {
            if (value.isEmpty() || !value.first().isAsciiLetterOrDigit() ||
                !value.last().isAsciiLetterOrDigit()) return false
            return value.all { it.isAsciiLetterOrDigit() || it == '.' || it == '-' }
        }

        private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in '0'..'9'

        private fun Char.isAsciiLetterOrDigitIgnoreCase(): Boolean =
            this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

        private fun hostAnchoredDomain(value: String): String? {
            if (value.length <= 3 || !value.startsWith("||") || !value.endsWith('^')) return null
            val start = 2
            val end = value.length - 1
            if (start >= end || !value[start].isAsciiLetterOrDigitIgnoreCase() ||
                !value[end - 1].isAsciiLetterOrDigitIgnoreCase()) return null
            if ((start until end).any {
                    val char = value[it]
                    !char.isAsciiLetterOrDigitIgnoreCase() && char != '.' && char != '-'
                }) return null
            return value.substring(start, end)
        }

        private fun hostsEntry(value: String): String? {
            val prefixLength = when {
                value.startsWith("0.0.0.0") -> 7
                value.startsWith("127.0.0.1") -> 9
                value.startsWith("::1") -> 3
                else -> return null
            }
            if (value.getOrNull(prefixLength)?.isWhitespace() != true) return null
            var start = prefixLength
            while (start < value.length && value[start].isWhitespace()) start++
            if (start == value.length || value[start] == '#') return null
            var end = start
            while (end < value.length && !value[end].isWhitespace() && value[end] != '#') end++
            return value.substring(start, end)
        }

        private fun parseDomainList(
            value: String,
            start: Int,
            end: Int,
            delimiter: Char
        ): Pair<Set<String>, Set<String>> {
            val included = LinkedHashSet<String>()
            val excluded = LinkedHashSet<String>()
            forEachPart(value, start, end, delimiter) { partStart, partEnd ->
                val isExcluded = value[partStart] == '~'
                val actualStart = if (isExcluded) partStart + 1 else partStart
                if (actualStart < partEnd) {
                    val domain = normalizeHost(value.substring(actualStart, partEnd))
                    if (isExcluded) excluded += domain else included += domain
                }
            }
            return included to excluded
        }

        private inline fun forEachPart(
            value: String,
            start: Int,
            end: Int,
            delimiter: Char,
            action: (start: Int, end: Int) -> Unit
        ) {
            var partStart = start
            var index = start
            while (index <= end) {
                if (index == end || value[index] == delimiter) {
                    var trimmedStart = partStart
                    var trimmedEnd = index
                    while (trimmedStart < trimmedEnd && value[trimmedStart].isWhitespace()) trimmedStart++
                    while (trimmedEnd > trimmedStart && value[trimmedEnd - 1].isWhitespace()) trimmedEnd--
                    if (trimmedStart < trimmedEnd) action(trimmedStart, trimmedEnd)
                    partStart = index + 1
                }
                index++
            }
        }

        private fun appendRegexLiteral(output: StringBuilder, char: Char) {
            if (char == '\\' || char == '.' || char == '[' || char == ']' || char == '{' ||
                char == '}' || char == '(' || char == ')' || char == '+' || char == '?' ||
                char == '$' || char == '|') {
                output.append('\\')
            }
            output.append(char)
        }

        internal fun hostOf(url: String?): String? = runCatching { url?.let(::URI)?.host?.let(::normalizeHost) }.getOrNull()
        private fun normalizeHost(host: String) = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        private fun hostMatches(host: String, domain: String): Boolean {
            if (domain.startsWith('/') && domain.endsWith('/') && domain.length > 2) {
                return runCatching { Regex(domain.drop(1).dropLast(1)).containsMatchIn(host) }.getOrDefault(false)
            }
            if (domain.endsWith(".*")) {
                val entity = domain.substring(0, domain.length - 2).substringAfterLast('.')
                val siteStart = siteKeyStart(host)
                val siteLabelEnd = host.indexOf('.', siteStart).let { if (it < 0) host.length else it }
                return siteLabelEnd - siteStart == entity.length &&
                    host.regionMatches(siteStart, entity, 0, entity.length, ignoreCase = true)
            }
            return host == domain || host.endsWith(".$domain")
        }
        private val COMMON_SECOND_LEVEL_SUFFIXES = setOf("co.uk", "org.uk", "com.au", "net.au", "co.jp", "co.nz", "com.br", "com.cn", "com.sg", "co.in")
        private fun sameSite(first: String, second: String): Boolean {
            val firstStart = siteKeyStart(first)
            val secondStart = siteKeyStart(second)
            val length = first.length - firstStart
            return second.length - secondStart == length &&
                first.regionMatches(firstStart, second, secondStart, length, ignoreCase = true)
        }

        private fun siteKeyStart(host: String): Int {
            val lastDot = host.lastIndexOf('.')
            if (lastDot < 0) return 0
            val secondLastDot = host.lastIndexOf('.', lastDot - 1)
            if (secondLastDot < 0) return 0
            val suffixStart = secondLastDot + 1
            val suffixLength = host.length - suffixStart
            val hasCommonSecondLevelSuffix = COMMON_SECOND_LEVEL_SUFFIXES.any { suffix ->
                suffix.length == suffixLength &&
                    host.regionMatches(suffixStart, suffix, 0, suffixLength, ignoreCase = true)
            }
            if (!hasCommonSecondLevelSuffix) return suffixStart
            val thirdLastDot = host.lastIndexOf('.', secondLastDot - 1)
            return if (thirdLastDot < 0) 0 else thirdLastDot + 1
        }
    }

    private data class MatchContext(
        val request: Request,
        val requestHost: String,
        val pageHost: String?,
        val thirdParty: Boolean
    )

    private sealed interface UrlPattern {
        fun matches(url: String, requestHost: String): Boolean
    }

    private data class HostPattern(val host: String) : UrlPattern {
        override fun matches(url: String, requestHost: String): Boolean = hostMatches(requestHost, host)
    }

    private data class RegexPattern(val regex: Regex) : UrlPattern {
        override fun matches(url: String, requestHost: String): Boolean = regex.containsMatchIn(url)
    }

    private data class LiteralPattern(
        val text: String,
        val matchCase: Boolean,
        val hostAnchored: Boolean,
        val startAnchored: Boolean,
        val endAnchored: Boolean
    ) : UrlPattern {
        override fun matches(url: String, requestHost: String): Boolean {
            if (hostAnchored) {
                val schemeEnd = url.indexOf("://")
                if (schemeEnd < 1) return false
                val authorityStart = schemeEnd + 3
                val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
                    .let { if (it < 0) url.length else it }
                if (matchesAt(url, authorityStart)) return true
                var index = authorityStart
                while (index < authorityEnd) {
                    if (url[index] == '.' && matchesAt(url, index + 1)) return true
                    index++
                }
                return false
            }
            if (startAnchored) return matchesAt(url, 0)
            if (endAnchored) return matchesAt(url, url.length - text.length)
            return url.indexOf(text, ignoreCase = !matchCase) >= 0
        }

        private fun matchesAt(url: String, start: Int): Boolean {
            if (start < 0 || start + text.length > url.length) return false
            if (endAnchored && start + text.length != url.length) return false
            return url.regionMatches(start, text, 0, text.length, ignoreCase = !matchCase)
        }
    }

    private data class NetworkRule(
        val pattern: UrlPattern, val source: String, val exception: Boolean, val token: String?, val thirdParty: Boolean?,
        val includeTypes: Set<ResourceType>, val excludeTypes: Set<ResourceType>,
        val includeDomains: Set<String>, val excludeDomains: Set<String>
    ) {
        fun matches(context: MatchContext): Boolean {
            if (thirdParty != null && context.thirdParty != thirdParty) return false
            if (includeTypes.isNotEmpty() && context.request.resourceType !in includeTypes) return false
            if (context.request.resourceType in excludeTypes) return false
            val host = context.pageHost
            if (includeDomains.isNotEmpty() && (host == null || includeDomains.none { hostMatches(host, it) })) return false
            if (host != null && excludeDomains.any { hostMatches(host, it) }) return false
            return pattern.matches(context.request.url, context.requestHost)
        }
    }

    private data class CosmeticRule(
        val selector: String, val exception: Boolean,
        val includedDomains: Set<String>, val excludedDomains: Set<String>
    ) {
        fun appliesTo(host: String): Boolean {
            if (excludedDomains.any { hostMatches(host, it) }) return false
            return includedDomains.isEmpty() || includedDomains.any { hostMatches(host, it) }
        }
    }

    private data class ScriptletRule(
        val name: String,
        val arguments: List<String>,
        val exception: Boolean,
        val includedDomains: Set<String>,
        val excludedDomains: Set<String>,
        val trusted: Boolean
    ) {
        val key: Pair<String, List<String>> get() = name to arguments

        fun appliesTo(host: String): Boolean {
            if (excludedDomains.any { hostMatches(host, it) }) return false
            return includedDomains.isEmpty() || includedDomains.any { hostMatches(host, it) }
        }
    }
}
