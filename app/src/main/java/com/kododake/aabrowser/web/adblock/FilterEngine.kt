package com.kododake.aabrowser.web.adblock

import java.io.DataInput
import java.io.DataOutput
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Immutable parser/matcher for the commonly used subset of uBlock static filters. */
class FilterEngine private constructor(
    private val networkRules: List<NetworkRule>,
    private val cosmeticRules: List<CosmeticRule>,
    private val scriptletRules: List<ScriptletRule>
) {
    private val blocking: Bucket
    private val exceptions: Bucket
    private val importantBlocking: Bucket
    private val importantExceptions: Bucket
    /** `@@` rules carrying $document / $elemhide / $generichide / $specifichide; see [pagePolicy]. */
    private val contentExceptions: Bucket
    private val cosmeticRulesByDomain: Map<String, List<CosmeticRule>>
    private val entityCosmeticRules: List<CosmeticRule>
    /** Generic (domain-less) hide rules keyed by the class or id their leading compound selector requires. */
    private val genericByClass: Map<String, List<CosmeticRule>>
    private val genericById: Map<String, List<CosmeticRule>>
    /** Generic hide rules that cannot be keyed (tag or attribute selectors); applied to every page. */
    private val genericOther: List<CosmeticRule>
    /** Selectors excepted everywhere by a domain-less `#@#` rule. */
    private val globalCosmeticExceptions: Set<String>
    private val globalScriptletRules: List<ScriptletRule>
    private val scriptletRulesByDomain: Map<String, List<ScriptletRule>>
    private val entityScriptletRules: List<ScriptletRule>

    init {
        val builders = Array(5) { BucketBuilder() }
        networkRules.forEach { rule ->
            val target = when {
                rule.contentFlags != 0 -> 4
                rule.exception && rule.important -> 3
                rule.exception -> 1
                rule.important -> 2
                else -> 0
            }
            builders[target].add(rule)
        }
        blocking = builders[0].build()
        exceptions = builders[1].build()
        importantBlocking = builders[2].build()
        importantExceptions = builders[3].build()
        contentExceptions = builders[4].build()

        val cosmeticByDomain = HashMap<String, MutableList<CosmeticRule>>()
        val entityCosmetic = ArrayList<CosmeticRule>()
        val byClass = HashMap<String, MutableList<CosmeticRule>>()
        val byId = HashMap<String, MutableList<CosmeticRule>>()
        val other = ArrayList<CosmeticRule>()
        val globalExceptions = HashSet<String>()
        cosmeticRules.forEach { rule ->
            if (rule.includedDomains.isEmpty()) {
                when {
                    rule.exception -> globalExceptions += rule.selector
                    rule.keyKind == KEY_CLASS -> byClass.getOrPut(rule.key!!) { ArrayList() } += rule
                    rule.keyKind == KEY_ID -> byId.getOrPut(rule.key!!) { ArrayList() } += rule
                    else -> other += rule
                }
            } else {
                var hasEntity = false
                rule.includedDomains.forEach { domain ->
                    if (domain.endsWith(".*")) hasEntity = true
                    else cosmeticByDomain.getOrPut(domain) { ArrayList() } += rule
                }
                if (hasEntity) entityCosmetic += rule
            }
        }
        cosmeticRulesByDomain = cosmeticByDomain
        entityCosmeticRules = entityCosmetic
        genericByClass = byClass
        genericById = byId
        genericOther = other
        globalCosmeticExceptions = globalExceptions

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

    /** Append-only: ordinals are stored in compiled snapshots. */
    enum class ResourceType { SCRIPT, IMAGE, STYLESHEET, FONT, MEDIA, XHR, SUBDOCUMENT, OTHER, PING, WEBSOCKET, POPUP, DOCUMENT }

    /**
     * What `@@` content exceptions say about a page: [document] disables blocking and all cosmetic
     * filtering on it; [elemhide] disables all element hiding; [generichide] only the generic
     * (domain-less) hide rules; [specifichide] only the site-specific ones.
     */
    data class PagePolicy(
        val document: Boolean = false,
        val elemhide: Boolean = false,
        val generichide: Boolean = false,
        val specifichide: Boolean = false
    ) {
        val hidesNothing: Boolean get() = document || elemhide
        val allowGeneric: Boolean get() = !hidesNothing && !generichide
        val allowSpecific: Boolean get() = !hidesNothing && !specifichide

        companion object {
            val DEFAULT = PagePolicy()
        }
    }

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

    /**
     * Decides whether [request] should be blocked. `$important` blocking rules beat plain `@@`
     * exceptions, and `@@...$important` beats everything, as in uBlock Origin.
     *
     * When the page that issued the request is unknown (no page URL/host, e.g. a service worker
     * fetch) the party and `$domain=` constraints cannot be evaluated: blocking rules that depend
     * on them do not match, and with [lenientExceptions] exception rules treat them as satisfied,
     * so the engine errs towards not blocking.
     */
    fun shouldBlock(request: Request, lenientExceptions: Boolean = false): Boolean {
        val requestHost = request.requestHost?.let(::normalizeHost) ?: hostOf(request.url) ?: return false
        val pageHost = request.pageHost?.let(::normalizeHost) ?: hostOf(request.pageUrl)
        val thirdParty = pageHost?.let { !sameSite(requestHost, it) }
        val strict = MatchContext(request, requestHost, pageHost, thirdParty, lenientUnknownPage = false)
        val forExceptions = if (lenientExceptions && pageHost == null) strict.copy(lenientUnknownPage = true) else strict
        if (importantExceptions.matches(request.url, forExceptions)) return false
        if (importantBlocking.matches(request.url, strict)) return true
        if (exceptions.matches(request.url, forExceptions)) return false
        return blocking.matches(request.url, strict)
    }

    /** Content exceptions (`@@...$document`, `$elemhide`, `$generichide`, `$specifichide`) that apply to [pageUrl]. */
    fun pagePolicy(pageUrl: String?): PagePolicy {
        if (contentExceptions.isEmpty) return PagePolicy.DEFAULT
        val host = hostOf(pageUrl) ?: return PagePolicy.DEFAULT
        val request = Request(pageUrl!!, pageUrl, ResourceType.DOCUMENT, host, host)
        val context = MatchContext(request, host, host, thirdParty = false, lenientUnknownPage = false)
        var flags = 0
        contentExceptions.forEachMatch(pageUrl, context) { flags = flags or it.contentFlags }
        if (flags == 0) return PagePolicy.DEFAULT
        val document = flags and FLAG_DOCUMENT != 0
        return PagePolicy(
            document = document,
            elemhide = document || flags and FLAG_ELEMHIDE != 0,
            generichide = document || flags and FLAG_GENERICHIDE != 0,
            specifichide = document || flags and FLAG_SPECIFICHIDE != 0
        )
    }

    /**
     * Everything the document-start script needs before the page renders: the site-specific hide
     * selectors, the un-keyed generic selectors, and whether keyed generic rules should be
     * requested as the page's classes and ids are discovered (see [genericSelectorsFor]).
     */
    data class CosmeticInit(val specific: List<String>, val other: List<String>, val generic: Boolean)

    fun cosmeticInit(pageUrl: String?, allowGeneric: Boolean = true, allowSpecific: Boolean = true): CosmeticInit? {
        val host = hostOf(pageUrl) ?: return null
        val exceptions = cosmeticExceptionsFor(host)
        val specific = LinkedHashSet<String>()
        if (allowSpecific) {
            domainCandidates(host, emptyList(), cosmeticRulesByDomain, entityCosmeticRules).forEach { rule ->
                if (!rule.exception && rule.appliesTo(host) && rule.selector !in exceptions) specific += rule.selector
            }
        }
        val other = if (allowGeneric) {
            genericOther.asSequence()
                .filter { it.appliesTo(host) && it.selector !in exceptions }
                .mapTo(LinkedHashSet()) { it.selector }
                .toList()
        } else {
            emptyList()
        }
        val hasKeyedGeneric = allowGeneric && (genericByClass.isNotEmpty() || genericById.isNotEmpty())
        if (specific.isEmpty() && other.isEmpty() && !hasKeyedGeneric) return null
        return CosmeticInit(specific.toList(), other, hasKeyedGeneric)
    }

    /**
     * Generic hide selectors whose leading class or id is among the [classes] / [ids] present on the
     * page. Only rules keyed by one of those tokens are consulted, so the cost is proportional to
     * the page, not to the size of the filter lists.
     */
    fun genericSelectorsFor(classes: Collection<String>, ids: Collection<String>, pageHost: String?): List<String> {
        val host = pageHost?.let(::normalizeHost)?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val exceptions = cosmeticExceptionsFor(host)
        val out = LinkedHashSet<String>()
        fun collect(rules: List<CosmeticRule>?) {
            rules?.forEach { if (it.appliesTo(host) && it.selector !in exceptions) out += it.selector }
        }
        classes.forEach { collect(genericByClass[it]) }
        ids.forEach { collect(genericById[it]) }
        return out.toList()
    }

    /** Selectors that must not be hidden on [host]: global `#@#` rules plus the site's own exceptions. */
    private fun cosmeticExceptionsFor(host: String): Set<String> {
        var exceptions: MutableSet<String>? = null
        domainCandidates(host, emptyList(), cosmeticRulesByDomain, entityCosmeticRules).forEach { rule ->
            if (rule.exception && rule.appliesTo(host)) {
                (exceptions ?: HashSet(globalCosmeticExceptions).also { exceptions = it }) += rule.selector
            }
        }
        return exceptions ?: globalCosmeticExceptions
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
                    output.writeSizedString(pattern.source)
                    output.writeBoolean(pattern.ignoreCase)
                }
                is GlobPattern -> {
                    output.writeByte(PATTERN_GLOB)
                    output.writeSizedString(pattern.text)
                    output.writeBoolean(pattern.matchCase)
                    output.writeBoolean(pattern.hostAnchored)
                    output.writeBoolean(pattern.startAnchored)
                    output.writeBoolean(pattern.endAnchored)
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
            output.writeBoolean(rule.important)
            output.writeInt(rule.contentFlags)
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

    companion object {
        internal const val KEY_NONE = 0
        internal const val KEY_CLASS = 1
        internal const val KEY_ID = 2
        private val GENERIC_KEY = Regex("^(?:[A-Za-z][\\w-]*)?([.#])([A-Za-z_-][\\w-]*)")

        /**
         * Finds the class or id an element must carry for [selector] to match, looking only at the
         * leading compound (`.ad`, `div.ad > a`, `#banner .x`). Selectors starting with a tag,
         * attribute or wildcard, or whose identifier is cut short by a CSS escape, get [KEY_NONE].
         */
        internal fun genericKey(selector: String): Pair<Int, String?> {
            val match = GENERIC_KEY.find(selector) ?: return KEY_NONE to null
            val end = match.range.last + 1
            if (end < selector.length && selector[end] == '\\') return KEY_NONE to null
            val kind = if (match.groupValues[1] == ".") KEY_CLASS else KEY_ID
            return kind to match.groupValues[2]
        }

        private const val PATTERN_HOST = 1
        private const val PATTERN_REGEX = 2
        private const val PATTERN_LITERAL = 3
        private const val PATTERN_GLOB = 4
        private const val MAX_SNAPSHOT_RULES = 2_000_000
        private const val MAX_SNAPSHOT_COLLECTION_SIZE = 100_000
        private const val MAX_SNAPSHOT_STRING_BYTES = 16 * 1024 * 1024
        private val UNSUPPORTED_COSMETIC_OPERATORS = arrayOf(
            ":has-text(", ":matches-css(", ":matches-attr(", ":remove(",
            ":style(", ":upward(", ":xpath(", ":others(", ":watch-attr(",
            ":matches-path(", ":min-text-length(", ":nth-ancestor(", ":remove-attr(",
            ":remove-class(", ":matches-media(", ":matches-prop(", ":if(", ":if-not(", ":shadow("
        )
        private val UNHELPFUL_TOKENS = setOf("http", "https", "html", "com", "org", "net")
        internal const val FLAG_DOCUMENT = 1
        internal const val FLAG_ELEMHIDE = 2
        internal const val FLAG_GENERICHIDE = 4
        internal const val FLAG_SPECIFICHIDE = 8
        private val CONTENT_OPTIONS = mapOf(
            "document" to FLAG_DOCUMENT, "doc" to FLAG_DOCUMENT,
            "elemhide" to FLAG_ELEMHIDE, "ehide" to FLAG_ELEMHIDE,
            "generichide" to FLAG_GENERICHIDE, "ghide" to FLAG_GENERICHIDE,
            "specifichide" to FLAG_SPECIFICHIDE, "shide" to FLAG_SPECIFICHIDE
        )
        private val TYPE_OPTIONS = mapOf(
            "script" to ResourceType.SCRIPT, "image" to ResourceType.IMAGE,
            "stylesheet" to ResourceType.STYLESHEET, "font" to ResourceType.FONT,
            "css" to ResourceType.STYLESHEET,
            "media" to ResourceType.MEDIA, "xmlhttprequest" to ResourceType.XHR,
            "xhr" to ResourceType.XHR, "subdocument" to ResourceType.SUBDOCUMENT,
            "frame" to ResourceType.SUBDOCUMENT, "other" to ResourceType.OTHER,
            "object" to ResourceType.MEDIA,
            // WebView never hands these request kinds to shouldInterceptRequest, so rules that
            // only target them stay inert instead of being dropped (and `$ping,script` still
            // blocks scripts).
            "ping" to ResourceType.PING, "beacon" to ResourceType.PING,
            "websocket" to ResourceType.WEBSOCKET, "popup" to ResourceType.POPUP
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
                if (line !in disabledNetworkRules) {
                    parseNetwork(line).forEachIndexed { index, rule ->
                        network.putIfAbsent(if (index == 0) line else "$line\u0000$index", rule)
                    }
                }
            }
            return FilterEngine(network.values.toList(), cosmetic, scriptlets)
        }

        internal fun readSnapshot(input: DataInput): FilterEngine {
            val networkCount = input.readCount(MAX_SNAPSHOT_RULES)
            val network = ArrayList<NetworkRule>(networkCount)
            repeat(networkCount) {
                val pattern = when (val type = input.readUnsignedByte()) {
                    PATTERN_HOST -> HostPattern(input.readSizedString())
                    PATTERN_REGEX -> RegexPattern(input.readSizedString(), input.readBoolean())
                    PATTERN_GLOB -> GlobPattern(
                        input.readSizedString(), input.readBoolean(), input.readBoolean(),
                        input.readBoolean(), input.readBoolean()
                    )
                    PATTERN_LITERAL -> LiteralPattern(
                        input.readSizedString(), input.readBoolean(), input.readBoolean(),
                        input.readBoolean(), input.readBoolean()
                    )
                    else -> throw IllegalArgumentException("Unknown cached pattern type $type")
                }
                val exception = input.readBoolean()
                val important = input.readBoolean()
                val contentFlags = input.readInt()
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
                    input.readStringSet(), input.readStringSet(),
                    important, contentFlags
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

        /**
         * Parses one network filter. Usually yields one rule; an exception that combines content
         * options (`$document`, `$generichide`, ...) with ordinary resource types yields two, one
         * per bucket. Rules with options the engine cannot honour yield nothing.
         */
        private fun parseNetwork(source: String): List<NetworkRule> {
            var line = source
            hostsEntry(line)?.let { line = it }
            val exception = line.startsWith("@@")
            if (exception) line = line.drop(2)
            if (line.isBlank() || line.startsWith("#")) return emptyList()
            val optionAt = optionSeparator(line)
            val patternText = if (optionAt >= 0) line.substring(0, optionAt) else line
            val optionText = if (optionAt >= 0) line.substring(optionAt + 1) else ""
            if (patternText.isBlank()) return emptyList()

            var thirdParty: Boolean? = null
            var matchCase = false
            var important = false
            var contentFlags = 0
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
                    option == "empty" && !negated -> Unit
                    option == "important" && !negated -> important = true
                    option in CONTENT_OPTIONS && !negated -> {
                        // Only meaningful on exceptions. On a blocking rule `$document` would
                        // mean "block navigation", which WebView interception cannot do, so
                        // ignoring it would turn the rule into a broad subresource block.
                        if (exception) contentFlags = contentFlags or CONTENT_OPTIONS.getValue(option)
                        else unsupported = true
                    }
                    option.startsWith("domain=") || option.startsWith("from=") -> {
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
            if (unsupported) return emptyList()
            val pattern = compilePattern(patternText, matchCase) ?: return emptyList()
            val token = extractToken(patternText)
            val rules = ArrayList<NetworkRule>(2)
            if (contentFlags != 0) {
                rules += NetworkRule(
                    pattern, source, exception, token, thirdParty,
                    emptySet(), emptySet(), includeDomains, excludeDomains, important, contentFlags
                )
            }
            if (contentFlags == 0 || includeTypes.isNotEmpty()) {
                rules += NetworkRule(
                    pattern, source, exception, token, thirdParty,
                    includeTypes, excludeTypes, includeDomains, excludeDomains, important, 0
                )
            }
            return rules
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
                val source = text.drop(1).dropLast(1)
                // Validate once at parse time; snapshots re-compile lazily on first use.
                return runCatching { Regex(source, flags) }.getOrNull()?.let { RegexPattern(source, !matchCase) }
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
            // Collapse runs of wildcards; a pattern that is nothing but wildcards would match everything.
            val collapsed = pattern.replace(MULTI_STAR, "*")
            if (collapsed.all { it == '*' }) return null
            return GlobPattern(collapsed, matchCase, hostAnchored, startAnchored, endAnchored)
        }

        private val MULTI_STAR = Regex("\\*{2,}")

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

        /** True when [host] equals one of [domains] or is a subdomain of it (never a substring match). */
        fun hostWithinAny(host: String?, domains: Collection<String>): Boolean {
            val normalized = host?.let(::normalizeHost)?.takeIf { it.isNotEmpty() } ?: return false
            if (domains.isEmpty()) return false
            return domains.any { domain -> normalized == domain || normalized.endsWith(".$domain") }
        }

        /**
         * Host of an http(s)/ws(s)-style URL, lower-cased without the trailing dot, or null for
         * anything without an authority (`about:`, `data:`, `file:`, `javascript:`). Parsed by
         * hand: java.net.URI rejects many real-world ad URLs (`|`, `{}`, spaces, `_` in labels,
         * unencoded Unicode), and a rejected URL would silently escape every rule.
         */
        internal fun hostOf(url: String?): String? {
            if (url.isNullOrEmpty()) return null
            val schemeEnd = url.indexOf("://")
            if (schemeEnd <= 0) return null
            for (index in 0 until schemeEnd) {
                val char = url[index]
                if (!(char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '+' || char == '-' || char == '.')) return null
            }
            var start = schemeEnd + 3
            var end = url.length
            for (index in start until url.length) {
                val char = url[index]
                if (char == '/' || char == '?' || char == '#' || char == '\\') { end = index; break }
            }
            val userInfo = url.lastIndexOf('@', end - 1)
            if (userInfo >= start) start = userInfo + 1
            if (start >= end) return null
            val host = if (url[start] == '[') {
                val close = url.indexOf(']', start)
                if (close < 0 || close >= end) return null
                url.substring(start + 1, close)
            } else {
                val colon = url.indexOf(':', start)
                url.substring(start, if (colon in start until end) colon else end)
            }
            val normalized = normalizeHost(host)
            if (normalized.isEmpty() || normalized.any { it.isWhitespace() || it == '/' }) return null
            return normalized
        }
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
        /** Second-level labels that are public suffixes under two-letter country TLDs (co.uk, com.au, ac.jp, ...). */
        private val GENERIC_SECOND_LEVEL_LABELS = setOf(
            "co", "com", "org", "net", "gov", "edu", "ac", "ne", "or", "go", "mil", "sch", "ltd", "plc", "nom", "me", "info", "biz", "id"
        )
        /** Hosting providers whose customers' subdomains are unrelated sites. */
        private val EXPLICIT_PUBLIC_SUFFIXES = setOf(
            "github.io", "gitlab.io", "pages.dev", "web.app", "netlify.app", "vercel.app", "herokuapp.com",
            "firebaseapp.com", "azurewebsites.net", "cloudfront.net", "amazonaws.com", "wordpress.com", "tumblr.com",
            "appspot.com", "workers.dev", "surge.sh", "onrender.com", "fly.dev"
        )
        private val BRAND_SECOND_LEVEL_LABELS = setOf("blogspot")
        private fun sameSite(first: String, second: String): Boolean {
            val firstStart = siteKeyStart(first)
            val secondStart = siteKeyStart(second)
            val length = first.length - firstStart
            return second.length - secondStart == length &&
                first.regionMatches(firstStart, second, secondStart, length, ignoreCase = true)
        }

        /** Index where the registrable ("site") part of [host] starts, using a small public-suffix heuristic. */
        internal fun siteKeyStart(host: String): Int {
            val lastDot = host.lastIndexOf('.')
            if (lastDot < 0) return 0
            val secondLastDot = host.lastIndexOf('.', lastDot - 1)
            if (secondLastDot < 0) return 0
            val tld = host.substring(lastDot + 1)
            val secondLevel = host.substring(secondLastDot + 1, lastDot)
            val twoLabelSuffix = host.substring(secondLastDot + 1)
            val suffixHasTwoLabels = twoLabelSuffix in EXPLICIT_PUBLIC_SUFFIXES ||
                secondLevel in BRAND_SECOND_LEVEL_LABELS ||
                (tld.length == 2 && secondLevel in GENERIC_SECOND_LEVEL_LABELS)
            if (!suffixHasTwoLabels) return secondLastDot + 1
            val thirdLastDot = host.lastIndexOf('.', secondLastDot - 1)
            return if (thirdLastDot < 0) 0 else thirdLastDot + 1
        }

        /** Registrable domain of [host] (e.g. `shop.example.co.uk` -> `example.co.uk`). */
        internal fun registrableDomain(host: String): String = host.substring(siteKeyStart(host))
    }

    /** [thirdParty] is null when the requesting page is unknown; [lenientUnknownPage] then makes party and domain constraints count as satisfied. */
    private data class MatchContext(
        val request: Request,
        val requestHost: String,
        val pageHost: String?,
        val thirdParty: Boolean?,
        val lenientUnknownPage: Boolean
    )

    /** Network rules split into token-indexed and unindexed (regex-like) groups for fast matching. */
    private class Bucket(private val indexed: Map<String, List<NetworkRule>>, private val unindexed: List<NetworkRule>) {
        val isEmpty: Boolean get() = indexed.isEmpty() && unindexed.isEmpty()

        fun matches(url: String, context: MatchContext): Boolean {
            forEachMatch(url, context) { return true }
            return false
        }

        inline fun forEachMatch(url: String, context: MatchContext, action: (NetworkRule) -> Unit) {
            unindexed.forEach { if (it.matches(context)) action(it) }
            if (indexed.isEmpty()) return
            var runStart = -1
            var cursor = 0
            while (cursor <= url.length) {
                val char = url.getOrNull(cursor)
                val tokenChar = char != null && (char.isAsciiLetterOrDigitIgnoreCase() || char == '%')
                if (tokenChar && runStart < 0) runStart = cursor
                if (!tokenChar && runStart >= 0) {
                    if (cursor - runStart >= 4) {
                        indexed[url.substring(runStart, cursor).lowercase(Locale.ROOT)]?.forEach { if (it.matches(context)) action(it) }
                    }
                    runStart = -1
                }
                cursor++
            }
        }
    }

    private class BucketBuilder {
        private val indexed = HashMap<String, MutableList<NetworkRule>>()
        private val unindexed = ArrayList<NetworkRule>()
        fun add(rule: NetworkRule) {
            if (rule.token == null) unindexed += rule else indexed.getOrPut(rule.token) { ArrayList() } += rule
        }
        fun build() = Bucket(indexed, unindexed)
    }

    private sealed interface UrlPattern {
        fun matches(url: String, requestHost: String): Boolean
    }

    private data class HostPattern(val host: String) : UrlPattern {
        override fun matches(url: String, requestHost: String): Boolean = hostMatches(requestHost, host)
    }

    /** `/.../` rules. The Regex is compiled on first use so loading a snapshot stays cheap. */
    private data class RegexPattern(val source: String, val ignoreCase: Boolean) : UrlPattern {
        private val regex: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
            Regex(source, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        }
        override fun matches(url: String, requestHost: String): Boolean = regex.containsMatchIn(url)
    }

    /**
     * ABP-style patterns with `*` (any run) and `^` (a separator: anything but a letter, digit,
     * `_`, `-`, `.`, `%`, or the end of the URL), matched directly instead of through a regex.
     */
    private data class GlobPattern(
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
                for (index in authorityStart until authorityEnd) {
                    if (url[index] == '.' && matchesAt(url, index + 1)) return true
                }
                return false
            }
            if (startAnchored) return matchesAt(url, 0)
            for (start in 0..url.length) if (matchesAt(url, start)) return true
            return false
        }

        private fun matchesAt(url: String, start: Int): Boolean {
            var u = start
            var p = 0
            var starP = -1
            var starU = -1
            while (true) {
                if (p < text.length) {
                    val pc = text[p]
                    if (pc == '*') {
                        starP = p
                        starU = u
                        p++
                        continue
                    }
                    if (u < url.length && charMatches(pc, url[u])) {
                        p++
                        u++
                        continue
                    }
                    if (pc == '^' && u == url.length) {
                        // A separator may also match the end of the URL, consuming nothing.
                        p++
                        continue
                    }
                } else if (u == url.length || !endAnchored) {
                    return true
                }
                if (starP < 0) return false
                // Backtrack: let the last `*` absorb one more character.
                starU++
                if (starU > url.length) return false
                u = starU
                p = starP + 1
            }
        }

        private fun charMatches(patternChar: Char, urlChar: Char): Boolean = when (patternChar) {
            '^' -> !(urlChar.isLetterOrDigit() || urlChar == '_' || urlChar == '-' || urlChar == '.' || urlChar == '%')
            else -> patternChar == urlChar || (!matchCase && patternChar.equals(urlChar, ignoreCase = true))
        }
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
        val includeDomains: Set<String>, val excludeDomains: Set<String>,
        val important: Boolean = false, val contentFlags: Int = 0
    ) {
        fun matches(context: MatchContext): Boolean {
            if (thirdParty != null) {
                val actual = context.thirdParty ?: return context.lenientUnknownPage
                if (actual != thirdParty) return false
            }
            if (includeTypes.isNotEmpty() && context.request.resourceType !in includeTypes) return false
            if (context.request.resourceType in excludeTypes) return false
            val host = context.pageHost
            if (includeDomains.isNotEmpty()) {
                if (host == null) return context.lenientUnknownPage
                if (includeDomains.none { hostMatches(host, it) }) return false
            }
            if (host != null && excludeDomains.any { hostMatches(host, it) }) return false
            return pattern.matches(context.request.url, context.requestHost)
        }
    }

    private data class CosmeticRule(
        val selector: String, val exception: Boolean,
        val includedDomains: Set<String>, val excludedDomains: Set<String>
    ) {
        val keyKind: Int
        val key: String?

        init {
            val (kind, value) = if (includedDomains.isEmpty() && !exception) genericKey(selector) else KEY_NONE to null
            keyKind = kind
            key = value
        }

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
