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
        assertTrue(".advert" in restored.cosmeticCss("https://news.test"))
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
        val newsCss = engine.cosmeticCss("https://news.test/article")
        assertTrue(".sponsor" in newsCss)
        assertTrue(".newsletter" in newsCss)
        assertFalse(".generic-ad" in newsCss)

        val shopCss = engine.cosmeticCss("https://shop.news.test")
        assertFalse(".newsletter" in shopCss)
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

    private fun engine(vararg rules: String) = FilterEngine.parse(rules.asSequence())

    private fun FilterEngine.blocks(
        url: String,
        pageUrl: String = "https://publisher.test/article",
        type: FilterEngine.ResourceType = FilterEngine.ResourceType.OTHER
    ) = shouldBlock(FilterEngine.Request(url, pageUrl, type))
}
