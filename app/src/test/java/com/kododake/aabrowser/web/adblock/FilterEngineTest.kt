package com.kododake.aabrowser.web.adblock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    @Test
    fun `compiled snapshot preserves all rule behavior`() {
        val original = engine(
            "||ads.test^${'$'}third-party,script,domain=news.test|~paid.news.test",
            "@@||ads.test/allowed.js",
            "|https://literal.test/tracker.js|",
            "/pixel-[0-9]+\\.gif/",
            "news.test##.advert",
            "news.test#@#.sponsored",
            "news.test##+js(set, ads.visible, false)"
        )
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use(original::writeSnapshot)
        }.toByteArray()
        val restored = DataInputStream(ByteArrayInputStream(bytes)).use(FilterEngine::readSnapshot)

        assertTrue(restored.ruleCount == original.ruleCount)
        assertTrue(restored.blocks("https://ads.test/banner.js", "https://news.test", FilterEngine.ResourceType.SCRIPT))
        assertFalse(restored.blocks("https://ads.test/allowed.js", "https://news.test", FilterEngine.ResourceType.SCRIPT))
        assertTrue(restored.blocks("https://literal.test/tracker.js"))
        assertTrue(restored.blocks("https://cdn.test/pixel-42.gif"))
        assertTrue(".advert" in restored.hiddenOn("https://news.test"))
        assertTrue(restored.scriptletsFor("https://news.test").single().name == "set-constant")
    }

    @Test
    fun `preparsed hosts avoid repeated uri parsing without changing matching`() {
        val engine = engine("||cdn.test^${'$'}third-party")
        assertTrue(engine.shouldBlock(FilterEngine.Request(
            url = "https://cdn.test/ad.js",
            pageUrl = "https://publisher.test/article",
            requestHost = "cdn.test",
            pageHost = "publisher.test"
        )))
    }

    @Test
    fun `host rules include subdomains but not lookalikes`() {
        val engine = engine("||ads.example.com^")
        assertTrue(engine.blocks("https://cdn.ads.example.com/banner.js"))
        assertFalse(engine.blocks("https://ads.example.com.evil.test/banner.js"))
    }

    @Test
    fun `legacy domain lists remain supported`() {
        val engine = engine("tracker.test")
        assertTrue(engine.blocks("https://pixel.tracker.test/p.gif"))
        assertFalse(engine.blocks("https://example.test/?next=tracker.test"))
    }

    @Test
    fun `exception overrides blocking rule`() {
        val engine = engine("||ads.test^", "@@||ads.test/required.js")
        assertTrue(engine.blocks("https://ads.test/banner.js"))
        assertFalse(engine.blocks("https://ads.test/required.js"))
    }

    @Test
    fun `third party and resource options are applied`() {
        val engine = engine("||cdn.test^${'$'}third-party,script")
        assertTrue(engine.blocks("https://cdn.test/ad.js", "https://site.test", FilterEngine.ResourceType.SCRIPT))
        assertFalse(engine.blocks("https://cdn.test/ad.png", "https://site.test", FilterEngine.ResourceType.IMAGE))
        assertFalse(engine.blocks("https://static.cdn.test/app.js", "https://www.cdn.test", FilterEngine.ResourceType.SCRIPT))
    }

    @Test
    fun `registrable domain comparison handles common multi-level suffixes`() {
        val engine = engine("||cdn.publisher.co.uk^${'$'}third-party")
        assertFalse(engine.blocks("https://cdn.publisher.co.uk/ad.js", "https://www.publisher.co.uk"))
        assertTrue(engine.blocks("https://cdn.publisher.co.uk/ad.js", "https://other.co.uk"))
    }

    @Test
    fun `domain options include and exclude page hosts`() {
        val engine = engine("/advert.js${'$'}domain=news.test|~paid.news.test")
        assertTrue(engine.blocks("https://cdn.test/advert.js", "https://news.test"))
        assertFalse(engine.blocks("https://cdn.test/advert.js", "https://paid.news.test"))
        assertFalse(engine.blocks("https://cdn.test/advert.js", "https://other.test"))
    }

    @Test
    fun `wildcards separators and anchors match urls`() {
        val engine = engine("|https://ads.test/*/banner^")
        assertTrue(engine.blocks("https://ads.test/123/banner?size=2"))
        assertFalse(engine.blocks("http://ads.test/123/banner?size=2"))
    }

    @Test
    fun `literal filters preserve anchors and case behavior`() {
        val substring = engine("/Advertising/Banner.js")
        assertTrue(substring.blocks("https://cdn.test/advertising/banner.js?v=1"))

        val exactCase = engine("/Advertising/Banner.js${'$'}match-case")
        assertTrue(exactCase.blocks("https://cdn.test/Advertising/Banner.js"))
        assertFalse(exactCase.blocks("https://cdn.test/advertising/banner.js"))

        val fullyAnchored = engine("|https://exact.test/ad.js|")
        assertTrue(fullyAnchored.blocks("https://exact.test/ad.js"))
        assertFalse(fullyAnchored.blocks("https://exact.test/ad.js?v=1"))
    }

    @Test
    fun `host anchored literal paths include subdomains but not lookalikes`() {
        val engine = engine("||ads.example.test/tracker.js")
        assertTrue(engine.blocks("https://cdn.ads.example.test/tracker.js?v=1"))
        assertFalse(engine.blocks("https://ads.example.test.evil.test/tracker.js"))
    }

    @Test
    fun `cosmetic rules respect domains and exceptions`() {
        val engine = engine(
            "##.generic-ad",
            "news.test##.sponsor",
            "news.test#@#.generic-ad",
            "~shop.news.test,news.test##.newsletter"
        )
        val news = engine.hiddenOn("https://news.test/article", classes = listOf("generic-ad"))
        assertTrue(".sponsor" in news)
        assertTrue(".newsletter" in news)
        assertFalse(".generic-ad" in news)

        val shop = engine.hiddenOn("https://shop.news.test", classes = listOf("generic-ad"))
        assertFalse(".newsletter" in shop)
        assertFalse(".generic-ad" in shop) // the news.test exception covers its subdomains

        val other = engine.hiddenOn("https://other.test", classes = listOf("generic-ad"))
        assertTrue(".generic-ad" in other)
        assertFalse(".sponsor" in other)
    }

    @Test
    fun `generic rules are keyed by their leading class or id`() {
        assertTrue(FilterEngine.genericKey(".ad-banner") == FilterEngine.KEY_CLASS to "ad-banner")
        assertTrue(FilterEngine.genericKey("div.ad > a") == FilterEngine.KEY_CLASS to "ad")
        assertTrue(FilterEngine.genericKey("#sponsor .item") == FilterEngine.KEY_ID to "sponsor")
        assertTrue(FilterEngine.genericKey(".promo:not(.keep)") == FilterEngine.KEY_CLASS to "promo")
        assertTrue(FilterEngine.genericKey("[href^=\"//ads.\"]").first == FilterEngine.KEY_NONE)
        assertTrue(FilterEngine.genericKey("iframe[src*=\"doubleclick\"]").first == FilterEngine.KEY_NONE)
        assertTrue(FilterEngine.genericKey("*[data-ad]").first == FilterEngine.KEY_NONE)
        assertTrue(FilterEngine.genericKey(".ad\\:wide").first == FilterEngine.KEY_NONE)
    }

    @Test
    fun `keyed generic selectors are only returned for tokens present on the page`() {
        val engine = engine(
            "##.ad-banner",
            "##div.promo > a",
            "###sidebar-ad",
            "##iframe[src*=\"doubleclick\"]",
            "~keep.test##.tracking-pixel",
            "#@#.ad-banner",
            "site.test#@#.tracking-pixel"
        )
        val init = engine.cosmeticInit("https://site.test/page")!!
        assertTrue(init.generic)
        assertTrue(init.specific.isEmpty())
        assertTrue(init.other == listOf("iframe[src*=\"doubleclick\"]"))

        val found = engine.genericSelectorsFor(listOf("promo", "tracking-pixel", "ad-banner"), listOf("sidebar-ad"), "site.test")
        assertTrue("div.promo > a" in found)
        assertTrue("#sidebar-ad" in found)
        assertFalse(".ad-banner" in found)
        assertFalse(".tracking-pixel" in found)
        assertTrue(engine.genericSelectorsFor(listOf("tracking-pixel"), emptyList(), "other.test") == listOf(".tracking-pixel"))
        assertTrue(engine.genericSelectorsFor(listOf("tracking-pixel"), emptyList(), "keep.test").isEmpty())
        assertTrue(engine.genericSelectorsFor(listOf("nothing"), listOf("nothing"), "site.test").isEmpty())
    }

    @Test
    fun `cosmetic init can withhold generic rules and reports when nothing applies`() {
        val engine = engine("##.ad-banner", "news.test##.sponsor")
        val withoutGeneric = engine.cosmeticInit("https://news.test", allowGeneric = false)!!
        assertFalse(withoutGeneric.generic)
        assertTrue(withoutGeneric.specific == listOf(".sponsor"))
        assertTrue(withoutGeneric.other.isEmpty())
        assertTrue(engine.cosmeticInit("https://other.test", allowGeneric = false) == null)
        assertTrue(engine.cosmeticInit("file:///android_asset/error.html") == null)
        assertTrue(engine("||ads.test^").cosmeticInit("https://news.test") == null)
    }

    @Test
    fun `procedural cosmetic operators are rejected`() {
        val engine = engine("##.ad:has-text(Sponsored)", "##.ad:matches-path(/news)", "##.ad:if(.x)", "##.plain")
        assertTrue(engine.genericSelectorsFor(listOf("ad", "plain"), emptyList(), "site.test") == listOf(".plain"))
    }

    @Test
    fun `hosts file entries are accepted`() {
        val engine = engine("0.0.0.0 metrics.example.test # tracker")
        assertTrue(engine.blocks("https://metrics.example.test/collect"))
    }

    @Test
    fun `unsupported action modifiers never become broad blocking rules`() {
        val engine = engine(
            "*${'$'}removeparam=utm_source",
            "||cdn.example.test^${'$'}redirect=noopjs",
            "||known.example.test^"
        )
        assertFalse(engine.blocks("https://unrelated.test/page.js"))
        assertFalse(engine.blocks("https://cdn.example.test/app.js"))
        assertTrue(engine.blocks("https://known.example.test/app.js"))
    }

    @Test
    fun `badfilter disables its matching network rule`() {
        val engine = engine(
            "||disabled.example.test^${'$'}script",
            "||disabled.example.test^${'$'}script,badfilter"
        )
        assertFalse(engine.blocks(
            "https://disabled.example.test/app.js",
            type = FilterEngine.ResourceType.SCRIPT
        ))
    }

    @Test
    fun `badfilter works before rules and exact network duplicates are compiled once`() {
        val engine = engine(
            "||disabled-before.test^${'$'}script,badfilter",
            "||disabled-before.test^${'$'}script",
            "||deduplicated.test^",
            "||deduplicated.test^"
        )
        assertFalse(engine.blocks(
            "https://disabled-before.test/app.js",
            type = FilterEngine.ResourceType.SCRIPT
        ))
        assertTrue(engine.blocks("https://deduplicated.test/ad.js"))
        assertTrue(engine.ruleCount == 1)
    }

    @Test
    fun `option scanner preserves whitespace and domain exclusions`() {
        val engine = engine("||cdn.test^${'$'} third-party , script , domain=news.test|~paid.news.test ")
        assertTrue(engine.blocks("https://cdn.test/ad.js", "https://news.test", FilterEngine.ResourceType.SCRIPT))
        assertFalse(engine.blocks("https://cdn.test/ad.js", "https://paid.news.test", FilterEngine.ResourceType.SCRIPT))
    }

    @Test
    fun `scriptlet rules parse quoted arguments and domain exceptions`() {
        val engine = FilterEngine.parseSources(sequenceOf(
            FilterEngine.SourceLine("video.test##+js(set, 'player.ads', `undefined`)", trusted = true),
            FilterEngine.SourceLine("allowed.video.test#@#+js(set, player.ads, undefined)", trusted = true)
        ))
        val invocation = engine.scriptletsFor("https://video.test/watch").single()
        assertTrue(invocation.trusted)
        assertTrue(invocation.name == "set-constant")
        assertTrue(invocation.arguments == listOf("player.ads", "undefined"))
        assertTrue(engine.scriptletsFor("https://allowed.video.test/watch").isEmpty())
    }

    @Test
    fun `global scriptlet exception disables injection`() {
        val engine = engine(
            "video.test##+js(noeval-if, ads)",
            "video.test#@#+js()"
        )
        assertTrue(engine.scriptletsFor("https://video.test").isEmpty())
    }

    @Test
    fun `malformed scriptlet arguments are rejected`() {
        val engine = engine("video.test##+js(set, 'unterminated)")
        assertTrue(engine.scriptletsFor("https://video.test").isEmpty())
    }

    @Test
    fun `scriptlet argument escaping matches ubo separators`() {
        val args = FilterEngine.parseScriptletArguments("trusted-replace, /ad\\.js/, one\\,two")
        assertTrue(args == listOf("trusted-replace", "/ad\\.js/", "one,two"))
    }

    @Test
    fun `entity scriptlet domains match registrable sites`() {
        val engine = engine("example.*##+js(set, ads, false)")
        assertTrue(engine.scriptletsFor("https://www.example.co.uk/page").isNotEmpty())
        assertTrue(engine.scriptletsFor("https://example.com/page").isNotEmpty())
        assertTrue(engine.scriptletsFor("https://example.evil.com/page").isEmpty())
    }

    @Test
    fun `scriptlet aliases are canonicalized for exceptions`() {
        val engine = engine(
            "video.test##+js(set, player.ads, false)",
            "video.test#@#+js(set-constant, player.ads, false)"
        )
        assertTrue(engine.scriptletsFor("https://video.test").isEmpty())
    }

    @Test
    fun `trusted duplicate wins without allowing custom lists to escalate`() {
        val engine = FilterEngine.parseSources(sequenceOf(
            FilterEngine.SourceLine("video.test##+js(trusted-set, player.ads, undefined)", trusted = false),
            FilterEngine.SourceLine("video.test##+js(trusted-set-constant, player.ads, undefined)", trusted = true)
        ))
        val invocation = engine.scriptletsFor("https://video.test").single()
        assertTrue(invocation.trusted)
        assertTrue(invocation.name == "trusted-set-constant")
    }

    @Test
    fun `important blocking rules override plain exceptions`() {
        val engine = engine("||ads.test^${'$'}important", "@@||ads.test/allowed.js")
        assertTrue(engine.blocks("https://ads.test/allowed.js"))
        assertTrue(engine.blocks("https://ads.test/other.js"))

        val plain = engine("||ads.test^", "@@||ads.test/allowed.js")
        assertFalse(plain.blocks("https://ads.test/allowed.js"))
    }

    @Test
    fun `important exceptions override important blocking rules`() {
        val engine = engine("||ads.test^${'$'}important", "@@||ads.test/allowed.js${'$'}important")
        assertFalse(engine.blocks("https://ads.test/allowed.js"))
        assertTrue(engine.blocks("https://ads.test/other.js"))
    }

    @Test
    fun `important survives the compiled snapshot`() {
        val original = engine("||ads.test^${'$'}important", "@@||ads.test^", "@@||site.test^${'$'}generichide,script")
        val bytes = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use(original::writeSnapshot) }.toByteArray()
        val restored = DataInputStream(ByteArrayInputStream(bytes)).use(FilterEngine::readSnapshot)
        assertTrue(restored.blocks("https://ads.test/x.js"))
        assertTrue(restored.pagePolicy("https://site.test/").generichide)
    }

    @Test
    fun `popup ping and websocket options stay inert instead of dropping the rule`() {
        val engine = engine("||tracker.test^${'$'}ping", "||popups.test^${'$'}popup", "||sock.test^${'$'}websocket", "||mixed.test^${'$'}ping,script")
        assertFalse(engine.blocks("https://tracker.test/beacon", type = FilterEngine.ResourceType.OTHER))
        assertFalse(engine.blocks("https://popups.test/", type = FilterEngine.ResourceType.SUBDOCUMENT))
        assertFalse(engine.blocks("https://sock.test/ws", type = FilterEngine.ResourceType.XHR))
        assertTrue(engine.blocks("https://mixed.test/a.js", type = FilterEngine.ResourceType.SCRIPT))
        assertFalse(engine.blocks("https://mixed.test/a.png", type = FilterEngine.ResourceType.IMAGE))
        assertTrue(engine("||ads.test^${'$'}~popup").blocks("https://ads.test/a.js", type = FilterEngine.ResourceType.SCRIPT))
        assertTrue(engine("||ads.test^${'$'}empty").blocks("https://ads.test/a.js"))
        assertTrue(engine("||ads.test^${'$'}from=news.test").blocks("https://ads.test/a.js", "https://news.test"))
        assertFalse(engine("||ads.test^${'$'}from=news.test").blocks("https://ads.test/a.js", "https://other.test"))
    }

    @Test
    fun `document exception disables blocking and cosmetics for the page but not elsewhere`() {
        val engine = engine("||ads.test^", "@@||trusted.test^${'$'}document", "trusted.test##.promo", "##.ad-banner")
        val policy = engine.pagePolicy("https://trusted.test/page")
        assertTrue(policy.document && policy.elemhide && policy.generichide && policy.specifichide)
        assertTrue(engine.pagePolicy("https://sub.trusted.test/") == policy)
        assertTrue(engine.pagePolicy("https://other.test/") == FilterEngine.PagePolicy.DEFAULT)
        assertTrue(engine.pagePolicy("https://nottrusted.test/") == FilterEngine.PagePolicy.DEFAULT)
        assertTrue(engine.pagePolicy(null) == FilterEngine.PagePolicy.DEFAULT)
        // The $document exception is not a subresource exception: the engine still reports the
        // match, the adapter is what skips the page (see AdBlocker.interceptOrNull).
        assertTrue(engine.blocks("https://ads.test/a.js", "https://trusted.test/page"))
    }

    @Test
    fun `generichide and specifichide exceptions are reported separately`() {
        val engine = engine("@@||news.test^${'$'}generichide", "@@||shop.test^${'$'}shide", "@@||both.test^${'$'}ehide")
        val news = engine.pagePolicy("https://news.test/")
        assertTrue(news.generichide && !news.specifichide && !news.document && !news.elemhide)
        assertTrue(news.allowSpecific && !news.allowGeneric)
        val shop = engine.pagePolicy("https://shop.test/")
        assertTrue(shop.specifichide && !shop.generichide)
        val both = engine.pagePolicy("https://both.test/")
        assertTrue(both.hidesNothing && !both.allowGeneric && !both.allowSpecific)
    }

    @Test
    fun `content options on blocking rules drop the rule and mixed exceptions keep both halves`() {
        assertFalse(engine("||ads.test^${'$'}document").blocks("https://ads.test/a.js"))
        val mixed = engine("||cdn.test^", "@@||cdn.test^${'$'}generichide,script")
        assertFalse(mixed.blocks("https://cdn.test/a.js", type = FilterEngine.ResourceType.SCRIPT))
        assertTrue(mixed.blocks("https://cdn.test/a.png", type = FilterEngine.ResourceType.IMAGE))
        assertTrue(mixed.pagePolicy("https://cdn.test/").generichide)
    }

    @Test
    fun `unknown page context blocks only unconditional rules and honours exceptions leniently`() {
        val engine = engine(
            "||ads.test^",
            "||party.test^${'$'}third-party",
            "||scoped.test^${'$'}domain=news.test",
            "||allowed.test^",
            "@@||allowed.test^${'$'}domain=news.test"
        )
        fun blocked(url: String, lenient: Boolean) = engine.shouldBlock(FilterEngine.Request(url, null), lenientExceptions = lenient)
        assertTrue(blocked("https://ads.test/a.js", lenient = true))
        assertFalse(blocked("https://party.test/a.js", lenient = true))
        assertFalse(blocked("https://scoped.test/a.js", lenient = true))
        assertFalse(blocked("https://allowed.test/a.js", lenient = true))
        assertTrue(blocked("https://allowed.test/a.js", lenient = false))
        assertTrue(engine.blocks("https://party.test/a.js", "https://news.test"))
    }

    @Test
    fun `site allowlist matches exact hosts and subdomains only`() {
        val hosts = setOf("news.test", "shop.example.test")
        assertTrue(FilterEngine.hostWithinAny("news.test", hosts))
        assertTrue(FilterEngine.hostWithinAny("www.news.test", hosts))
        assertTrue(FilterEngine.hostWithinAny("NEWS.TEST.", hosts))
        assertFalse(FilterEngine.hostWithinAny("news.test.attacker.test", hosts))
        assertFalse(FilterEngine.hostWithinAny("fakenews.test", hosts))
        assertFalse(FilterEngine.hostWithinAny("example.test", hosts))
        assertFalse(FilterEngine.hostWithinAny(null, hosts))
        assertFalse(FilterEngine.hostWithinAny("news.test", emptySet()))
    }

    @Test
    fun `host parsing survives real-world ad urls that java net uri rejects`() {
        assertTrue(FilterEngine.hostOf("https://ads.test/pixel?x=a|b{c}") == "ads.test")
        assertTrue(FilterEngine.hostOf("https://user:pw@Ads.Test.:8443/path") == "ads.test")
        assertTrue(FilterEngine.hostOf("http://ad_server.test/a b") == "ad_server.test")
        assertTrue(FilterEngine.hostOf("https://[2001:db8::1]:443/x") == "2001:db8::1")
        assertTrue(FilterEngine.hostOf("https://例え.テスト/x") == "例え.テスト")
        assertTrue(FilterEngine.hostOf("wss://sock.test#frag") == "sock.test")
        assertTrue(FilterEngine.hostOf("https://ads.test\\path") == "ads.test")
        assertTrue(FilterEngine.hostOf("file:///android_asset/error.html?failedUrl=x") == null)
        assertTrue(FilterEngine.hostOf("about:blank") == null)
        assertTrue(FilterEngine.hostOf("data:text/html,hi") == null)
        assertTrue(FilterEngine.hostOf("javascript:void(0)") == null)
        assertTrue(FilterEngine.hostOf("https:///nohost") == null)
        assertTrue(FilterEngine.hostOf("") == null)
        assertTrue(FilterEngine.hostOf(null) == null)
    }

    @Test
    fun `third party detection uses a public suffix heuristic`() {
        assertTrue(FilterEngine.registrableDomain("shop.example.co.uk") == "example.co.uk")
        assertTrue(FilterEngine.registrableDomain("a.b.example.com") == "example.com")
        assertTrue(FilterEngine.registrableDomain("alice.github.io") == "alice.github.io")
        assertTrue(FilterEngine.registrableDomain("blog.blogspot.de") == "blog.blogspot.de")
        assertTrue(FilterEngine.registrableDomain("news.example.ac.jp") == "example.ac.jp")
        assertTrue(FilterEngine.registrableDomain("example.io") == "example.io")
        val engine = engine("||cdn.test^${'$'}third-party")
        assertFalse(engine.blocks("https://cdn.test/a.js", "https://www.cdn.test/"))
        assertTrue(engine.blocks("https://cdn.test/a.js", "https://bob.github.io/"))
        val pages = engine("||alice.github.io^${'$'}third-party")
        assertTrue(pages.blocks("https://alice.github.io/a.js", "https://bob.github.io/"))
        assertFalse(pages.blocks("https://alice.github.io/a.js", "https://alice.github.io/"))
    }

    @Test
    fun `glob patterns match wildcards separators and anchors without regexes`() {
        val engine = engine(
            "||ads.test/*.js^",
            "/banner_*_wide.",
            "|https://start.test/*/ad^",
            "||end.test/x*|",
            "||sep.test/a^b"
        )
        assertTrue(engine.blocks("https://cdn.ads.test/lib/a.js?x=1"))
        assertTrue(engine.blocks("https://cdn.ads.test/lib/a.js"))
        assertFalse(engine.blocks("https://cdn.ads.test/lib/a.json"))
        assertFalse(engine.blocks("https://notads.test/lib/a.js"))
        assertTrue(engine.blocks("https://any.test/img/banner_300_wide.png"))
        assertFalse(engine.blocks("https://any.test/img/banner_300_narrow.png"))
        assertTrue(engine.blocks("https://start.test/one/two/ad?x"))
        assertFalse(engine.blocks("https://other.test/https://start.test/one/ad"))
        assertTrue(engine.blocks("https://end.test/xyz"))
        assertTrue(engine.blocks("https://end.test/xyz/more")) // `*` spans slashes, as in uBO
        assertFalse(engine.blocks("https://end.test/y"))
        assertFalse(engine("||end.test/x^|").blocks("https://end.test/xyz"))
        assertTrue(engine("||end.test/x^|").blocks("https://end.test/x"))
        assertTrue(engine("||end.test/x^|").blocks("https://end.test/x/"))
        assertTrue(engine.blocks("https://sep.test/a/b"))
        assertFalse(engine.blocks("https://sep.test/a-b"))
        assertTrue(engine("||case.test/*Ad^${'$'}match-case").blocks("https://case.test/x/Ad/"))
        assertFalse(engine("||case.test/*Ad^${'$'}match-case").blocks("https://case.test/x/ad/"))
        assertTrue(engine("***").ruleCount == 0)
    }

    private fun engine(vararg rules: String) = FilterEngine.parse(rules.asSequence())

    /** Every selector the document-start script would hide on [url] given the page's [classes]/[ids]. */
    private fun FilterEngine.hiddenOn(
        url: String,
        classes: List<String> = emptyList(),
        ids: List<String> = emptyList()
    ): List<String> {
        val init = cosmeticInit(url) ?: return emptyList()
        val generic = if (init.generic) genericSelectorsFor(classes, ids, FilterEngine.hostOf(url)) else emptyList()
        return init.specific + init.other + generic
    }

    private fun FilterEngine.blocks(
        url: String,
        pageUrl: String = "https://publisher.test/article",
        type: FilterEngine.ResourceType = FilterEngine.ResourceType.OTHER
    ) = shouldBlock(FilterEngine.Request(url, pageUrl, type))
}
