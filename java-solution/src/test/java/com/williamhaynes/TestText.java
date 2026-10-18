package com.williamhaynes;

/**
 * Awkward characters for tests, built from numeric code units so the source file itself stays plain ASCII
 * (invisible and control characters are unreadable, and easily lost, when written literally).
 */
final class TestText {

    private TestText() {
    }

    /** Builds a string from UTF-16 code units, so lone surrogates are possible too. */
    static String units(int... codeUnits) {
        StringBuilder text = new StringBuilder();
        for (int unit : codeUnits) {
            text.append((char) unit);
        }
        return text.toString();
    }

    static final String NUL = units(0x00);
    static final String BEL = units(0x07);
    static final String ESC = units(0x1B);
    static final String DEL = units(0x7F);
    static final String NEL = units(0x85);
    static final String NBSP = units(0xA0);
    static final String EM_SPACE = units(0x2003);
    static final String THIN_SPACE = units(0x2009);
    static final String IDEOGRAPHIC_SPACE = units(0x3000);
    static final String ZERO_WIDTH_SPACE = units(0x200B);
    static final String ZERO_WIDTH_JOINER = units(0x200D);
    static final String LEFT_TO_RIGHT_MARK = units(0x200E);
    static final String RIGHT_TO_LEFT_MARK = units(0x200F);
    static final String BYTE_ORDER_MARK = units(0xFEFF);
    static final String COMBINING_ACUTE = units(0x301);
    static final String E_ACUTE = units(0xE9);
    static final String SHARP_S = units(0xDF);
    static final String DOTTED_CAPITAL_I = units(0x130);
    static final String GRINNING_EMOJI = units(0xD83D, 0xDE00);
    static final String WORKER_EMOJI = units(0xD83D, 0xDC77);
    static final String LONE_HIGH_SURROGATE = units(0xD800);
    static final String LONE_LOW_SURROGATE = units(0xDC00);
    static final String ARABIC = units(0x645, 0x647, 0x646, 0x62F, 0x633, 0x20, 0x645, 0x639, 0x645, 0x627, 0x631, 0x64A);
    static final String HEBREW = units(0x5DE, 0x5D4, 0x5E0, 0x5D3, 0x5E1);
    static final String GREEK_SIGMAS_UPPER = units(0x3A3, 0x391, 0x3A3);
    static final String GREEK_SIGMAS_LOWER = units(0x3C3, 0x3B1, 0x3C2);

    /** Full-width form of an ASCII letter string, e.g. "architect" -> "ARCHITECT" in full-width capitals. */
    static String fullWidth(String ascii) {
        StringBuilder text = new StringBuilder();
        for (char c : ascii.toCharArray()) {
            text.append((char) (c + 0xFEE0));
        }
        return text.toString();
    }
}
