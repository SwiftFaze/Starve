package dev.starve.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TagRulesTest {
    @Test
    fun `terms are split on commas, trimmed, inner spaces collapsed and empties dropped`() =
        assertEquals(
            TermsCheck.Valid(listOf("AI", "machine learning", "GPT*")),
            TagRules.checkTerms(" AI ,, machine   learning, GPT* ,"),
        )

    @Test
    fun `a repeated term is kept once, ignoring case`() =
        assertEquals(TermsCheck.Valid(listOf("AI", "LLM")), TagRules.checkTerms("AI, LLM, ai"))

    @Test
    fun `letters, marks and digits of any script are words`() =
        assertEquals(TermsCheck.Valid(listOf("café", "日本", "GPT4o")), TagRules.checkTerms("café, 日本, GPT4o"))

    @Test
    fun `no terms at all is rejected`() {
        assertEquals(TermsCheck.Empty, TagRules.checkTerms(""))
        assertEquals(TermsCheck.Empty, TagRules.checkTerms(" , ,"))
    }

    @Test
    fun `the first term that is not words with an optional trailing star is named`() {
        assertEquals(TermsCheck.Invalid("C++"), TagRules.checkTerms("Rust, C++, \"go\""))
        assertEquals(TermsCheck.Invalid("\"go\""), TagRules.checkTerms("\"go\""))
        assertEquals(TermsCheck.Invalid("*"), TagRules.checkTerms("*"))
        assertEquals(TermsCheck.Invalid("*rust"), TagRules.checkTerms("*rust"))
        assertEquals(TermsCheck.Invalid("GPT-4"), TagRules.checkTerms("GPT-4"))
        assertEquals(TermsCheck.Invalid("ru*st"), TagRules.checkTerms("ru*st"))
        assertEquals(TermsCheck.Invalid("rust**"), TagRules.checkTerms("rust**"))
        assertEquals(TermsCheck.Invalid("title:ai"), TagRules.checkTerms("title:ai"))
    }

    @Test
    fun `twenty terms are allowed and twenty-one are not`() {
        val twenty = (1..20).joinToString(", ") { "t$it" }
        assertEquals(TermsCheck.Valid((1..20).map { "t$it" }), TagRules.checkTerms(twenty))
        assertEquals(TermsCheck.TooMany, TagRules.checkTerms("$twenty, t21"))
    }

    @Test
    fun `twenty distinct terms with repeats beyond them are allowed`() =
        assertEquals(
            TermsCheck.Valid((1..20).map { "t$it" }),
            TagRules.checkTerms((1..20).joinToString(", ") { "t$it" } + ", T1"),
        )

    @Test
    fun `the match expression ORs quoted terms over the title only`() =
        assertEquals(
            "{title} : (\"AI\" OR \"machine learning\" OR \"GPT\"*)",
            TagRules.matchExpression(listOf("AI", "machine learning", "GPT*"), matchBody = false),
        )

    @Test
    fun `matching the body too adds the body column`() =
        assertEquals("{title body} : (\"AI\")", TagRules.matchExpression(listOf("AI"), matchBody = true))

    @Test
    fun `a tag's match comes from its terms and body setting`() {
        val tag = Tag(1, "AI", listOf("AI", "LLM"), matchBody = true, groupId = 4)

        assertEquals("{title body} : (\"AI\" OR \"LLM\")", tag.match)
        assertEquals("AI, LLM", tag.termsText)
    }
}
