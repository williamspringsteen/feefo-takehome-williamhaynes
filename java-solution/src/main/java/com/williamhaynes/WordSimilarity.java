package com.williamhaynes;

/** Decides how well one input word matches one candidate word. */
interface WordSimilarity {

    /** @return match quality from 0 (no match) to 1 (perfect match). */
    double similarity(String inputWord, String candidateWord);
}
