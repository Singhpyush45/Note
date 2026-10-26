package com.appname.notes.feature.voice

/**
 * Hindi in Devanagari → everyday Roman "Hinglish" ("मैं घर जा रहा हूँ" → "main ghar ja raha hun"), for the
 * Hinglish voice option: speech is recognised as Hindi, then written the way people type Hindi in English letters.
 * Rules: consonants keep their inherent "a" except at a word's end and in the usual middle position
 * ("रहता" → "rahta"); vowel signs replace it; nasal marks become "n". Spelling of Hinglish isn't standard, so
 * this follows the common chat spelling. Latin letters, digits and punctuation pass through unchanged.
 */
object Hinglish {
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "g", '\u095B' to "z", '\u095C' to "d", '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y",
    )
    private val nuktaForms = mapOf('क' to '\u0958', 'ख' to '\u0959', 'ग' to '\u095A', 'ज' to '\u095B', 'ड' to '\u095C', 'ढ' to '\u095D', 'फ' to '\u095E', 'य' to '\u095F')
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o",
    )
    private val signs = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o",
    )
    private const val VIRAMA = '्'
    private const val NUKTA = '़'
    private val nasal = setOf('ं', 'ँ')
    private const val VISARGA = 'ः'
    private val digits = "०१२३४५६७८९"

    fun romanize(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c in consonants || c in nuktaForms.keys) {
                // A whole word of Devanagari at once, so the "a" rules can look at neighbours.
                val start = i
                while (i < text.length && isDevanagari(text[i])) i++
                out.append(word(text.substring(start, i)))
                continue
            }
            if (isDevanagari(c)) {
                val start = i
                while (i < text.length && isDevanagari(text[i])) i++
                out.append(word(text.substring(start, i)))
                continue
            }
            out.append(
                when (c) {
                    '।', '॥' -> '.'
                    else -> c
                },
            )
            i++
        }
        return out.toString()
    }

    private fun isDevanagari(c: Char) = c in '\u0900'..'\u097F' && c != '\u0964' && c != '\u0965'

    /** One syllable: a consonant (with its vowel or none), or a vowel letter. */
    private class Syllable(val consonant: String?, var vowel: String?, val inherent: Boolean, var nasal: Boolean = false)

    private fun word(w: String): String {
        val syl = ArrayList<Syllable>()
        val tail = StringBuilder()
        var i = 0
        while (i < w.length) {
            var c = w[i]
            if (c in digits) {
                tail.append('0' + digits.indexOf(c))
                i++
                continue
            }
            if (i + 1 < w.length && w[i + 1] == NUKTA) nuktaForms[c]?.let { c = it; i++ }
            val cons = consonants[c]
            when {
                cons != null -> {
                    val next = w.getOrNull(i + 1)
                    when {
                        next == VIRAMA -> { syl += Syllable(cons, null, false); i += 2 }
                        next != null && next in signs -> { syl += Syllable(cons, signs[next], false); i += 2 }
                        else -> { syl += Syllable(cons, "a", true); i++ }
                    }
                }
                c in vowels -> { syl += Syllable(null, vowels[c], false); i++ }
                c in nasal -> { syl.lastOrNull()?.nasal = true; i++ }
                c == VISARGA -> { syl += Syllable("h", null, false); i++ }
                c in signs -> { syl += Syllable(null, signs[c], false); i++ } // stray sign
                else -> i++
            }
        }
        // Schwa deletion: the inherent "a" goes at the end of a word of more than one syllable, and in the middle
        // where it sits between a vowel and a consonant that has its own vowel ("रहता" → "rahta", "कमरा" → "kamra").
        if (syl.size > 1 && syl.last().inherent && !syl.last().nasal) syl.last().vowel = null
        for (k in 1 until syl.size - 1) {
            val s = syl[k]
            val next = syl[k + 1]
            val prev = syl[k - 1]
            if (s.inherent && !s.nasal && prev.vowel != null && next.consonant != null && next.vowel != null && !next.inherent) s.vowel = null
        }
        val sb = StringBuilder()
        for (s in syl) {
            s.consonant?.let(sb::append)
            s.vowel?.let(sb::append)
            if (s.nasal) sb.append('n')
        }
        return sb.toString() + tail
    }
}
