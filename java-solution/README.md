# Job title normaliser

Maps free-text job titles ("Sr. software enginer", "head of accounts") onto a fixed list of known job titles
(outputs), or returns `"Unknown"` when nothing matches well enough.

```java
Normaliser normaliser = new Normaliser();
normaliser.normalise("acountant");                 // "Accountant"
normaliser.normalise("Senior Architect at Acme");  // "Architect"
normaliser.normalise("plumber");                   // "Unknown"
```

Build and test (Java 25, Maven): `cd java-solution && mvn verify`. `Normaliser` is the only public class.
It is immutable and stateless once built, so one instance can be shared between threads.

## 1. How it works

```
input -> InputCleaner -> words -> for each output: 3 stages -> best StageScore -> rank -> output / "Unknown"
```

1. **Clean** the input (`InputCleaner`): reject hostile input, normalise text, split into words, drop stop words.
2. **Score** the input words against every output with three stages. Each stage gives a score from 0 to 1.
3. **Pick** the output with the best score. It must reach `MIN_SCORE` (0.5) and must not be tied.

### The score: q = C x S x O

Every stage scores an output as `q = C * S * O` (`AlignmentStageScorer`, `ScoreFactors`):

| Factor | Meaning | Calculation |
|---|---|---|
| **C** coverage | How much of the *output* was found | matched output words / output words |
| **S** quality | How well the matched words matched (typos lower it) | average similarity of the matched words only |
| **O** order | Whether the words appear in the output's order | `1 - 0.1 * swapped pairs / matched pairs` |

Words are paired by `WordAligner`: output words are taken in order, and each takes the best input word not yet
used (greedy). An input word can satisfy at most one output word, so "architect architect architect" scores no
higher than "architect".

Because C is measured against the output, **extra input words cost nothing**: "senior architect at acme" is a
perfect `Architect` match. Equally, one exact word of a two-word output scores C = 0.5, which just meets the
threshold ("software" -> `Software engineer`).

### The three stages

| Stage (priority) | What counts as a word match | Which input words are eligible |
|---|---|---|
| `EXACT_PHRASE` (1) | typo-tolerant | a window of consecutive input words as wide as the output, best window kept |
| `WORD` (2) | typo-tolerant | the whole input, words anywhere |
| `RELATED_WORD` (3) | typo-tolerant, **or** same stem, **or** long shared prefix | the whole input |

The best score across the stages is used. Priority only matters on equal scores: the lower number wins.

### Detecting typos (`TypoTolerantSimilarity`)

- Identical words score 1.
- A word shorter than 4 characters (either side) must match exactly. There is too little signal to call it a typo.
- Otherwise a pluggable `StringSimilarity` reports `1 - editDistance / longerWordLength`. Anything under
  `MIN_TYPO_SIMILARITY` (0.75) is not a match; a match scores its similarity, so the penalty scales with the share of
  the word that is wrong. In practice: 1 typo in a 4-7 letter word, 2 in an 8-11 letter word.
- The default algorithm is **Damerau-Levenshtein** (optimal string alignment), where a swapped pair of letters
  ("acocunt") is one edit rather than two.

### Related words (`RelatedWordSimilarity`)

Handles different forms of the same word, scoring at most `MAX_RELATED_QUALITY` (0.8) so they never beat a direct match:

- **Stem match**: strip one common suffix (`-ing`, `-ant`, `-ment`, `-ion`, `-ed`, `-es`, `-s`...), leaving at least 5
  characters, then compare stems with typo tolerance ("accounts" / "accountant", "acounting" / "accountant").
- **Shared prefix**: at least 5 shared leading characters covering at least 65% of the longer word
  ("architect" / "architecture").
- `-er` / `-or` are deliberately **not** stripped, so "engineer" is not "engine"; "archive" is not `Architect`.

### The ordering penalty

For every pair of *matched* output words, the pair is in order if their input positions are in the same order as in
the output. `O = 1 - MAX_ORDER_PENALTY * (swapped pairs / matched pairs)`. So the worst case costs 10%:
"surveyor quantity" scores 0.9 against `Quantity surveyor`. Unmatched words say nothing about order, and with fewer
than two matched words there is nothing to judge (O = 1). Order is a gentle penalty, never a reason to reject.

### Choosing the winner

1. Outputs scoring under `MIN_SCORE` (0.5, inclusive) are discarded.
2. Highest score wins; on equal scores the better stage (lower priority number) wins.
3. If two outputs tie on **both** score and stage, the answer is ambiguous and `"Unknown"` is returned.

## 2. Tuning with `MatchConfig`

Every number and word list lives in `MatchConfig`. Raise or lower these to change behaviour:

| Constant | Default | Effect |
|---|---|---|
| `MIN_SCORE` | 0.5 | Overall threshold. Higher thresholds are more conservative: they reduce accepted matches, potentially at the cost of rejecting valid misspellings or partial titles. |
| `MAX_ORDER_PENALTY` | 0.1 | Maximum score lost when matched words are fully reversed. 0 ignores order. |
| `MIN_TYPO_WORD_LENGTH` | 4 | Words shorter than this must match exactly. |
| `MIN_TYPO_SIMILARITY` | 0.75 | Typo cut-off. Raising it to 0.8 rejects two typos in an 8-letter word. |
| `MIN_STEM_LENGTH` | 5 | Characters a stem must keep after suffix stripping. |
| `MIN_PREFIX_LENGTH` / `MIN_PREFIX_SHARE` | 5 / 0.65 | Shared-prefix rule: length, and share of the longer word. |
| `MAX_RELATED_QUALITY` | 0.8 | Cap on related-word match quality. |
| `SUFFIXES` | see code | Suffixes stripped when stemming (longest first). |
| `STOP_WORDS` | a, an, the, of, at, and, for, in, on, to | Dropped from inputs and outputs. If all words are stop words they are kept. |
| `MAX_INPUT_LENGTH` / `MAX_INPUT_WORDS` | 400 / 30 | Inputs over either limit return `"Unknown"` immediately. |
| `JARO_WINKLER_*` | 0.7 / 0.1 / 4 | Only used if the Jaro-Winkler algorithm is plugged in. |

Other parts are swappable in code through the package-private `Normaliser` constructors: the outputs list, the typo
algorithm (`StringSimilarity`) and the stage list (`StageScorer`).

## 3. Typo algorithm comparison

Three algorithms implement `StringSimilarity`, all on a 0-1 scale so the same cut-off applies.
`TypoAlgorithmComparisonTest` runs 98 cases (exact, deletions, transpositions, substitutions, plausible misspellings,
partial titles, look-alike words that must not match, other professions, extra surrounding words, one-word outputs)
through each and prints a scoreboard:

| Algorithm | Passed | Notes |
|---|---|---|
| Levenshtein | 94/98 | A swap costs two edits, so "chfe" / "nruse" fail. |
| **Damerau-Levenshtein** (default) | 96/98 | Swaps cost one edit. |
| Jaro-Winkler | 89/98 | Too lenient on short words and shared prefixes ("arch", "archive", "chess" all match). |

The two failures shared by every algorithm ("engine" -> `Engineer`, "survivor" -> `Surveyor`) are two-edit typos
in a one-word output: a 0.75 cut-off accepts them, 0.8 rejects them. In multi-word outputs the 0.5 threshold rejects
them. The cut-off is not yet swept per algorithm.

## 4. Edge cases covered

Input is never interpreted, logged or echoed. Rejected input returns `"Unknown"`; `normalise` never throws.

- **null, empty, whitespace-only** -> `"Unknown"`.
- **Very long input**: over 400 characters or 30 words is rejected before any processing; a huge single word and
  many repeated words finish in bounded time.
- **Unicode**: NFKC folding (full-width characters, ligatures), accents stripped ("e" for "e-acute"), zero-width and
  bidi characters removed, `toLowerCase(Locale.ROOT)`. Emoji, right-to-left text and unpaired surrogates do not break
  it. Results are identical under Turkish, Azeri, Lithuanian, German and Greek default locales.
- **Whitespace**: any Unicode whitespace (non-breaking spaces etc.) separates words; repeats collapse; tabs and
  newlines count as whitespace.
- **Control characters**: NUL, ANSI escapes and any control character other than whitespace reject the input.
- **Punctuation, digits, symbols** are kept as part of the word ("c++", "II"), not stripped. A bracketed word such as
  "(software)" still matches through the typo allowance when long enough.
- **Injection-style strings** (SQL, HTML, format strings, regex metacharacters) are plain text and simply don't match.
- **Repeated words** never score above a single match.
- **Stop-word-only input** ("the") keeps its words rather than becoming empty.
- **Ties**: equal score and stage -> `"Unknown"`; equal score but different stage -> better stage wins.
- **Output list**: null/empty list, and null/blank/wordless entries throw `IllegalArgumentException`; duplicates
  (ignoring case, accents, spacing) are merged keeping the first spelling; the original casing is returned.
- **Look-alike words**: "engine", "archive", "survivor", "plumber" and similar do not match.

## 5. Tests

Run with `mvn verify`. Tests use JUnit 5 (`@Nested`, `@DisplayName`, parameterised tables) and Mockito.

| Test class | Covers |
|---|---|
| `NormaliserTest` | End-to-end input -> output tables, including "Unknown" cases and the threshold. |
| `NormaliserRankingTest` | Ranking, tie-breaks and threshold with a **mocked** `StageScorer`, so no real score values are relied on; verifies calls. |
| `EdgeCaseInputTest` | Every item in section 4, including timing tests on hostile input. |
| `InputCleanerTest` | Cleaning, rejection, stop words. |
| `TypoTolerantSimilarityTest`, `RelatedWordSimilarityTest` | Thresholds, symmetry, identity, range; related-word rules and false positives; mocked collaborators. |
| `StringSimilarityContractTest` | Contract (identity, symmetry, range) for every `StringSimilarity`. |
| `TypoAlgorithmComparisonTest` | The algorithm scoreboard above. |
| `WordAlignerTest`, `ScoreFactorsTest`, `AlignmentStageScorerTest` | Pairing, C/S/O in isolation, windowing, per-stage score ranges. |
| `StageScoreTest`, `MatchStageTest` | Comparator (value, then priority) and unique, ordered priorities. |

Not yet done: property/fuzz tests, a CSV regression table, JaCoCo coverage, a few-hundred-outputs scale test.

## 6. Future improvements

- **Greediness** - Make the normaliser less greedy. Currently when we are doing a match stage for a possible candidate output word, we check each input word in order and find a best matching word in the potential output, even if a later word in the input could be a better match (currently, we would no longer try to match anything against that word in the possible output, for this match stage). An improvement would be to check all possible combinations (or use some optimal assignment algorithm), however this would make the process take longer. It would require some testing to see if the increased accuracy of the normaliser is worth the extra time taken to do the processing.
- **Configuration Value Tweaking** - We could run our automated test suite with different thresholds/configurations (see `MatchConfig`), and different algorithms, to see what works best. We already tested multiple algorithms for detecting typos, but different algorithms could be used to work out quality scores for each possible output. But sticking to the currently implemented set of algorithms, it would be interesting to run a very large test suite of possible job title inputs/outputs to see which values for each configuration variable works best.
- **Better Metrics for Testing Algorithms** - When testing our typo algorithms, we could test for better metrics such as `Precision` and `Recall`. Precision tells you how often accepted matches are correct, and recall tells you how many intended matches you successfully catch.
- **Extra Input Noise** - Think about if unmatched input words (extra noise) should lower the score. For example, should `Senior Software Engineer at Feefo` score the same as `Software Engineer`, for the output `Software Engineer`? Currently they both return a perfect score.
- **Confidence in Multiple Good Output Options** - If there is not a clear winner (e.g. if two potential outputs score high but they have a very close score), think about if we should output `Unknown`, since we are not confident which of these outputs is the best one. Currently, we output Unknown only if nothing gets a score above the minimum threshold, or if a tie-break cannot be decided.
- **Improve Related Word Stage** - For each possible job title output, we could store a list of related words (such as `Developer` for `Software Engineer`) or a synonym table, and use this in the `RELATED_WORD` match stage.
- **Scaling** - If we have a large number of possible outputs, we could exit early and return an output if we find a very strong match. We could also be make the normaliser more efficient by caching similar words found and tokenising/sanitising possible outputs once and storing it (rather than doing these things every time the class is called).
- **Visiblity** - Normaliser is the only public class, normalise and a constructor are the only public functions of this, so these are the only functions that can be called outside of the package, however currently everything is in com.williamhaynes. If we were extending this as part of a larger codebase, we could make a com.williamhaynes.normaliser sub-package.
- **CSV Test Resource** - We could keep a CSV list of test cases (job title inputs and desired output) in `src/test/resources` that can grow over time, and use this for our parameterised tests.
