# Normaliser: things to revisit

Status: first working version. Placeholder algorithms, light tests. Nothing below is done yet unless marked.

## 1. Open design decisions

- [x] No match returns `"Unknown"` (private constant `UNKNOWN` in `Normaliser`).
- [x] **Minimum score threshold.** Best score must be at least `MIN_SCORE` (0.5, inclusive so a single word of a two-word output still matches; private constant in `Normaliser`) or we return `"Unknown"`. Not yet configurable. Still needs tests at and around the boundary.
- [x] **Partial matches of multi-word outputs.** Is one word out of two a valid match, or only a weak one? This is tied to the threshold above.
- [x] **Greedy word alignment.** Each output word takes its best unused input word, in output order. This is order-dependent and not optimal when words are similar to each other. Options: optimal assignment (Hungarian / best-pair-first), or accept the limitation and document it.
- [x] **Tie-breaking.** Order is score, then stage priority; a tie on both now returns `"Unknown"` (was: position in the output list). Confirm priority numbers should stay explicit and not use enum ordinals (current choice: explicit, so reordering the enum can't change behaviour).
- [x] **`StageScore.NONE` sentinel** uses `RELATED_WORD` as a dummy stage. Consider `Optional<StageScore>` or a nullable stage so a "no score" can't be mistaken for a real one.
- [x] **Outputs list.** Currently a hardcoded placeholder list. Decide where the real list lives (constant, file, injected) and whether it can change at runtime. Decide how to handle duplicate, blank or null entries. Decision: leave hardcoded for now, potential improvement is to make everything configurable, so we can do automated testing with lots of different sets of output job titles, input job titles, thresholds, typo allowance, and order penalty (and even different algorithms for working out typos), to see what tweaks work best. 
- [x] **Return value for matches.** Return the output in its original casing (current behaviour). Confirm.

## 2. Match stage algorithms (needs a proper walkthrough)

Each stage scores `q = C * S * O`. Go through each stage and each factor and agree the intended behaviour, then write it down.

### Shared
- [x] **C (coverage).** Proportion of *output* words matched. Should unmatched *input* words (extra noise) lower the score at all? Currently they don't ("senior architect at acme" is a perfect `Architect` match).
- [x] **S (quality).** Average over matched words only. Check it behaves sensibly alongside C (a single perfect word and a half-matched phrase of typos).
- [x] **O (order).** Currently `1 - 0.1 * inversions/pairs`. Check the penalty size, and how it behaves with 1 matched word (always 1) and with repeated words.
- [x] **Stage ordering/dominance.** Is `RELATED_WORD >= WORD >= EXACT_PHRASE` in raw score acceptable, with priority only breaking ties? Or should a later stage be capped below an earlier one?

### Exact phrase
- [x] Currently: best window of the input as wide as the output, using the same alignment as word matching. This means a reordered phrase still scores close to 1 here, which blurs it with `WORD`. Decide whether this stage should require strict in-order contiguity, with order handled only by `O`.
- [x] What happens when the input is shorter than the output phrase (currently the whole input is the window).

### Word matching
- [x] Words matched anywhere in the input. Decide on stop words ("of", "the", "at") and whether they count towards C.
- [x] Repeated words in input or output.

### Related word matching
- [x] **Related-word false positives.** Fixed: suffix stripping is single-pass and no longer strips -er/-or (so "engineer" no longer stems to "engine"), and a shared prefix must be at least 5 characters and cover at least 65% of the longer word ("archive" no longer matches `Architect`). Side effect: a lone "surveying" no longer matches `Quantity surveyor` (scores 0.27); "quantity surveying" does.
- [x] **Typo tolerance on 8+ letter words.** "survivor" -> `Quantity surveyor` and "engine" -> `Software engineer` score 0.35, so the 0.5 threshold now rejects them (tests in `NormaliserTest`). Still possible when the typo'd word is the whole candidate, e.g. a one-word output.
- [x] Replaced the substring placeholder with suffix-aware stemming plus a shared-prefix rule (min 5 chars, max quality 0.8) in `RelatedWordSimilarity`. Still open: a synonym table ("developer" / "engineer"), and measuring false positives.
- [x] Decide whether synonyms are data (a map/file) or code, and who maintains them.
- [x] Substring matching is risky ("art" in "architect", "ant" in "accountant"). Check the false positive rate, and consider a higher minimum length or a prefix-only rule.

### Typo handling
- [x] Typo algorithm is pluggable (`StringSimilarity`); the default is Damerau-Levenshtein (swapped letters count as one typo), chosen from `TypoAlgorithmComparisonTest`. Levenshtein and Jaro-Winkler stay in the code for comparison. Still open: phonetic matching, and sweeping the shared cut-off (`DEFAULT_MIN_SIMILARITY`, 0.75) per algorithm.
- [x] Tune the typo allowance and penalty. Short words (3 chars or fewer) allow no typos at all.
- [x] Should the penalty scale by word length (percentage of word wrong) rather than a flat amount per typo?

## 3. Code quality: readable, scalable

- [x] **Injection and testability.** `STAGES` is a static list inside `Normaliser`, and the outputs-list constructor is package-private. Make stages injectable (constructor or builder) so tests can swap in mocks. Add an interface for a stage scorer (`StageScorer` is currently a concrete class). Done: `StageScorer` is now an interface (concrete class renamed `AlignmentStageScorer`) and `Normaliser` has a package-private constructor taking the stages.
- [x] **`StageScorer` flag argument.** The `boolean phraseWindow` makes call sites unreadable. Replace with separate stage classes, or a strategy for "which input words are eligible".
- [x] **Split `StageScorer.scoreAgainst`.** It does alignment, C, S and O in one method. Extract alignment, and the three factors, into separately testable pieces.
- [x] **Named constants and tuning.** Group magic numbers (`0.15`, `0.1`, `0.8`, `3`, `4`, `8`) into one config object, so tuning doesn't need hunting through classes.
- [ ] **Scalability.** Cost is roughly `outputs × stages × windows × words² × editDistance`. Fine for a few outputs, but check it with hundreds or thousands. Ideas: pre-tokenise outputs once at construction (currently re-split every call), cache word similarities, early exit on a perfect score, prefilter outputs.
- [x] **Package layout and visibility.** Decide what is public API (only `Normaliser` + `normalise`) and what is package-private. Consider sub-packages if it grows.
- [x] **Thread safety.** Currently stateless, so safe. Keep it that way, and document it.
- [x] **Javadoc / README.** Document the scoring model once the algorithm decisions above are settled. Let's add this once the tests are done. Javadoc docmentation for every appropriate function is ideal, preferably giving an overview of what the function does, and @param and @return tags briefly explaining what each is. We can give a basic overview of what the code is doing, then give more detail on each step (how for each stage we take in to account a certain criteria, penalising for typos and ordering, then give detail on each stage, and detail on how we detect typos, and how we calculate ordering penalty). We can also talk about how we tested different typo algorithms. We can talk about how we can tweak various parts of the normaliser with MatchConfig variables. We can have a section covering the rest of the tests, and what we try to cover. We can then talk about possible improvements we can make (I have a list of these noted down, that I think are the important ones I want to add). Let's make sure to try to document all the edge cases we cover.
- [x] **Static analysis / formatting.** Consider adding Checkstyle or Spotless, and `-Xlint:all -Werror` to the compiler plugin.

## 4. Edge cases and hostile input

Decide the intended behaviour for each, then pin it with tests.

- [x] `null` input. Currently throws `NullPointerException`. It should return `"Unknown"` instead. Done: returns `"Unknown"` (`Normaliser.normalise`, tested in `EdgeCaseInputTest`).
- [x] Empty and whitespace-only input. Currently `"Unknown"` - this is correct.
- [x] **Very long input** (megabytes of text, millions of words). Add a maximum input length / word count, and decide whether to truncate, reject, or return `"Unknown"`. Edit distance on one huge word is quadratic. Cap word length before comparing. Decision is to have max input character length of 400, max word count of 30, and if either limit is exceeded then immediately return "Unknown". These limits should be configurable in MatchConfig. We should also make sure that, where relevant, nothing breaks/errors/exceptions/runs out of memory when given a very long string.
- [x] **Unicode.** Normalise with `java.text.Normalizer` (NFKC), strip accents ("é" / "e"), handle full-width characters, zero-width characters, right-to-left text, emoji, and unpaired surrogates. `toLowerCase(Locale.ROOT)` is used already (avoids the Turkish-i problem), but check special cases like "ß" and final sigma.
- [x] **Whitespace.** Only `\s+` splitting is used. Non-breaking spaces and other Unicode whitespace are not covered by `\s` by default. Make sure to handle any kind of whitespace. Make sure to handle repeated whitespace. Done: `InputCleaner` splits on any Unicode whitespace (`[\p{Z}\s\p{javaWhitespace}]+`), repeats collapse.
- [x] **Punctuation and symbols.** Commas, hyphens, slashes, brackets, "&", digits ("Software Engineer II", "C++ Developer"), apostrophes. Decide whether to strip, split, or keep them. Decision: Do not strip from input, treat as part of the word.
- [x] **Control characters and nulls** (`\u0000`, newlines, tabs, ANSI escapes). Strip or reject. Decision: treat tabs and newlines as whitespace, reject \u0000 (NUL), reject ANSI escape sequences,  reject other kinds of control characters not mentioned. Can do this rejection somewhere at the start of the process.
- [x] **Injection-style strings** (SQL, HTML, format strings, regex metacharacters). The code doesn't interpret input, so these should just be non-matches, but pin that with tests. Make sure the input is never logged or echoed unsafely if logging is added.
- [x] **Denial of service.** Pathological inputs: one enormous word, many repeated words, inputs engineered to maximise edit-distance work. No regex is used on input except `split("\\s+")`, which is linear, but verify with timing tests. Should be fine as we will have max character / word limits on input.
- [x] **Repeated words** ("architect architect architect"). Should not score above a single match.
- [x] **Input equal to several outputs equally** (tie behaviour), and an output list with duplicates. Output Unknown if there are two equally good best outputs (tied on score and match stage). We should test this and test ties where the tie gets decided on match stage. Done: `Normaliser` returns `"Unknown"` on a tie for best on both score and stage (exact equality); a better stage still wins.
- [x] **Output list edge cases.** Empty list, single entry, blank entry, entries differing only by case. Done: constructor rejects a null/empty list and null/blank/wordless entries (`IllegalArgumentException`); duplicates (ignoring case, accents, spacing) are merged keeping the first spelling.
- [x] **Case and locale.** Run the tests under a different default locale (e.g. Turkish) to prove the result doesn't depend on it. Done: `EdgeCaseInputTest.LocaleIndependence` runs under tr/az/lt/de/el default locales.

## 5. Tests

Current state: one test class, 9 simple tests. Target: broad, parameterised, with mocking.

- [x] **Parameterised tests** (`junit-jupiter-params` is already included via the `junit-jupiter` aggregate dependency). Use `@CsvSource` / `@MethodSource` tables for: input → expected output, typo cases, ordering cases, related-word cases, and edge-case inputs. Done: `NormaliserTest` (input → output and Unknown tables), `TypoTolerantSimilarityTest`, `ScoreFactorsTest`, `StageScoreTest`, `MatchStageTest`, `StageScorerTest` (per-stage score ranges), `StringSimilarityContractTest` (all algorithms), plus `EdgeCaseInputTest` and `RelatedWordSimilarityTest`.
- [x] **Unit tests per class,** not just through `Normaliser`:
  - `TypoTolerantSimilarity`: distances, thresholds, symmetry (`sim(a,b) == sim(b,a)`), identity, range `[0,1]`.
  - `RelatedWordSimilarity`: substring rules, minimum length, scaling.
  - `StageScorer`: C, S and O in isolation (needs the refactor above to make them separable), including windowing.
  - `StageScore` comparator: value, then stage priority, then ties.
  - `MatchStage`: priorities are unique and in the intended order. Done: `TypoTolerantSimilarityTest`, `RelatedWordSimilarityTest`, `ScoreFactorsTest`, `WordAlignerTest`, `AlignmentStageScorerTest` (incl. windowing), `StageScoreTest`, `MatchStageTest`, `StringSimilarityContractTest`.
- [x] **Mocking** (add Mockito as a test dependency). Mock `WordSimilarity` and the stage scorer interface so `Normaliser` ranking and tie-breaking can be tested without depending on real scoring numbers. Verify calls too (e.g. every stage is called for every output, or stages are skipped on a perfect score if we add early exit). Done: Mockito added to the pom. `NormaliserRankingTest` mocks `StageScorer` (ranking, tie-breaks, threshold, call verification); `TypoTolerantSimilarityTest` and `RelatedWordSimilarityTest` mock `StringSimilarity` / `WordSimilarity`. Early exit is not implemented, so there is no skip-on-perfect-score verification.
- [ ] **Property-style tests.** Scores always in `[0,1]`. The result is always one of the outputs or `"Unknown"`. The result is deterministic. Exact output text (any casing, extra whitespace) always returns that output. Never throws for arbitrary strings (consider jqwik, or a simple random-string fuzz loop with a fixed seed).
- [ ] **Edge-case / hostile-input tests** for everything in section 4, including timeouts (`assertTimeoutPreemptively`) on large inputs.
- [ ] **Tie-break tests** for every pair of stages, and for equal-score outputs (equal score outputs should return 'Unknown'). 
- [ ] **Regression table.** A list of real-world inputs and expected outputs that grows over time, kept in a CSV under `src/test/resources`.
- [ ] **Coverage.** Add JaCoCo and set a threshold, including branch coverage.
- [ ] **Locale test.** Run key tests with `Locale.setDefault(new Locale("tr"))`.
- [ ] **Greediness testing.** Will Engineeering Engineer and Engineer Engineering both return the same, for example?
- [ ] **Other characters.** For example brackets, like if the input was '(Software) engineer'. Should this be treated like a typo? Or should we be stripping out any non-letters, unless they also appear in a possible candidate output?
- [ ] **Stop word only inputs.** Shouldn't really match with anything.

## 6. Task expectations checklist

Taken from the brief notes. Each item is something a reviewer should be able to see directly in the repo.

### Java knowledge and design
- [ ] **SOLID, with interfaces.** Show it deliberately:
  - Single responsibility: split tokenising/cleaning, scoring, and ranking out of `Normaliser`.
  - Open/closed: new stages or similarity algorithms are added without editing `Normaliser`.
  - Liskov: any `WordSimilarity` / stage implementation is interchangeable (test this with a shared contract test).
  - Interface segregation: small interfaces (`WordSimilarity`, stage scorer, input cleaner).
  - Dependency inversion: `Normaliser` depends on interfaces, with dependencies passed in through the constructor.
- [ ] Use modern Java where it helps and is readable (records, `List.copyOf`, `Optional`, switch expressions, `var`, sealed types if useful). Don't overdo it.
- [ ] Immutability: `final` fields and classes, unmodifiable collections, no shared mutable state.
- [ ] Sensible names for every package, class, method and variable. Check the existing ones (`BEST_LAST`, `StageScorer`, `NONE`, `q`) read clearly to someone new.
- [ ] Clean directory structure: standard Maven layout, `.gitignore` in place, no IDE or build output committed (`.idea/` and `target/` are already ignored), test packages mirror main packages.

### Defensive coding
- [ ] **Everything null-safe.** Not only `normalise(null)`: also constructor arguments (null list, null elements), null similarity/stage dependencies, and any returned values. Decide the policy once (fail fast with a clear message for programmer errors, `"Unknown"` for bad user input) and document it. Consider `@NullMarked` / JSpecify annotations.
- [ ] Validate constructor arguments (reject an empty outputs list or blank entries with `IllegalArgumentException`).
- [ ] Never let an arbitrary input string cause an unchecked exception, hang or out-of-memory error (see section 4).
- [ ] Decide whether to log, and make sure input is never logged unsanitised.

### Tests required by the brief
Make sure each of these has a clearly named test, and shows up in the test documentation:
- [ ] Mixed case
- [ ] Leading/trailing/internal whitespace
- [ ] Tie-break (score ties, stage-priority ties, equal-output ties)
- [ ] Blank / empty
- [ ] No match
- [ ] Null safety
- [ ] Edge cases (section 4)
- [ ] Parameterised tests
- [ ] Mocking
- [ ] Use `@DisplayName` and `@Nested` so the test report reads as a specification.

### Build
- [ ] **No deprecated Maven syntax.** Review the pom:
  - Use `maven.compiler.release` (already done) and not `source`/`target`.
  - Check every plugin and dependency version is current. Dependabot or `mvn versions:display-plugin-updates` can help.
  - Pin plugin versions for the clean/resources/jar/install lifecycle too (Maven warns about unpinned ones in some setups).
  - Add the Maven wrapper (`mvnw`) so the build doesn't depend on a local Maven install.
  - Check `mvn -q verify` runs with no warnings from our own build.
  - Consider the Maven Enforcer plugin to require Java 25 and a minimum Maven version.
- [ ] Add JaCoCo for coverage evidence (also listed in section 5).

### Documentation
- [ ] **README.md** with: what it does, how to build and run, how to use `Normaliser` from other code, the scoring model (C, S, O and the stages) explained simply, an example table of inputs and outputs, design decisions and the reasoning behind them.
- [ ] **Testing document** (e.g. `docs/TESTING.md`): what is tested and why, how to run the tests, a description of each test category, and evidence (test run output, coverage report summary or screenshot).
- [ ] **Design / decisions document** (e.g. `docs/DESIGN.md`): the thought process, alternatives considered (greedy vs optimal alignment, Levenshtein vs others), trade-offs, and why each choice was made.
- [ ] **Improvements document** (or a section in the README): how it could be improved with more time. Draw on this file, e.g. configurable outputs, synonym data, better algorithms, performance for large output lists, caching, observability, packaging as a library.
- [ ] Javadoc on every public class and method, and comments explaining *why* for non-obvious code. Keep comments accurate as the code changes.
- [ ] Keep this `TODO.md` current or fold it into the docs above before submitting, so no stale items remain.

## 7. Additions from second set of notes

Only items not already covered above.

### Design questions
- [ ] **Qualifiers ("junior", "senior", "chief", "head of", "in training", "trainee").** Today they are just unmatched extra input words, which cost nothing, so "Senior Accountant" and "Accountant" both score 1.0 for `Accountant`. Decide: ignore them (current behaviour), keep a configurable qualifier list that is stripped before matching, or use them only as a tie-breaker. Note that "Accountant in Training" arguably is still an accountant, but "Chief Accountant" vs "Architect" shows qualifiers don't change the role itself. There is no reliable way to *detect* a qualifier from the three stages alone, so a list is needed (see Improvements).
- [ ] **Whole-string match = perfect score.** Add an explicit fast path: if the cleaned input equals an output (case-insensitive), return it immediately with score 1.0. Also gives the early-exit benefit below.
- [ ] **Early exit on a very high score.** Define "very high" (e.g. >= 0.95), and document the risk: a later stage or later output could in principle score higher, or win a tie by priority. Only safe for an exact whole-string match, where 1.0 can't be beaten.
- [ ] **Running stages together / merging passes.** An optimisation to compute all three stages in one pass over the words. Document the trade-off: faster, but couples the stages and risks missing a better match if stage logic is shared incorrectly. Measure first before doing it.
- [ ] **Two outputs both match very well (ambiguity).** Currently the higher score wins, then stage priority, then list order. Consider a minimum margin: if the top two are within a small epsilon, is the answer really known? Options: keep the tie-break rules, or return `"Unknown"`. Pick one and document why.
- [ ] **Threshold wording.** The notes mention an "N/A" output. We chose `"Unknown"` (the private constant), so keep this consistent in the docs and tests.
- [ ] **Predefined related words per output.** Allow each output to carry its own related words (e.g. `Accountant` -> bookkeeper, accounts, finance). Likely a documented improvement (README) rather than part of the first version, but the design should leave room for it (e.g. an output type richer than a plain `String`).
- [x] **Related-word detection limits.** "Head of Accounts" now matches `Accountant` via the shared stem (named test in `NormaliserTest`, pairs in `RelatedWordSimilarityTest`).

### Configurability and comparing algorithms
- [ ] Make the outputs list and the scoring parameters (weights, thresholds, typo allowance, order penalty) settable when constructing `Normaliser`. Public constructor or builder, with sensible defaults, so the default `new Normaliser()` still works for callers.
- [x] Allow different algorithm sets to be plugged in, then run the same test table against each and compare. Done for the typo algorithm via `TypoAlgorithmComparisonTest`; other stage algorithms are not pluggable yet.
- [ ] Using different outputs lists in tests is what demonstrates scalability: add a test with a few hundred generated outputs and check correctness and time.
- [ ] Write up how scores are tweaked: for each constant, what it does, what changes when it is raised or lowered, with a worked example. Walk through this together with the algorithm review in section 2.

### Test data and cases
- [ ] **Descriptive text before and/or after:** "Accountant in Training", "Chief Accountant", "Senior Accountant of B2B", "Junior Software Engineer (Contract)".
- [ ] **No word matches exactly:** "Head of Accounts", "Software Developer" (no shared word with `Software engineer` except "software"), "Building Designer".
- [ ] **Nothing matches much:** expect `"Unknown"`. Include inputs that share only a short or incidental fragment with an output.
- [ ] **Near-matches to several outputs:** e.g. a title that is close to two outputs, to exercise ranking, margin and tie-break rules.
- [ ] **Numbers and symbols:** "Accountant 2", "C++ Engineer", "12345", "Software Engineer - L5".
- [ ] **Misspellings** across every output, including first-letter errors and transpositions.
- [ ] **Real job titles.** A list of common titles from a public source (check the licence first, and record the source in `docs/TESTING.md`) can be turned into a CSV under `src/test/resources`, with an expected output for each. Keep a small hand-verified set so expected values are trustworthy.
- [ ] **Reuse:** one parameterised test per behaviour group that takes (input, expected) rows from a CSV or `@MethodSource`, rather than copying test bodies.
- [ ] **Per-stage tests:** each stage scorer tested on its own with a table of (input, output, expected score range).
- [x] **Forcing scores to test ranking.** With the stage scorer behind an interface and injected, use mocks/stubs to return fixed scores for each (output, stage), then assert the right output is chosen. Cases: highest score wins, tie goes to earlier stage, full tie goes to list order, all-zero returns `"Unknown"`. This tests the ranking logic without depending on real scoring numbers. Done in `NormaliserRankingTest`.
- [ ] Optionally expose the per-output best score and stage (package-private or a debug method) so tests and docs can show *why* an output was picked.

### Documentation notes
- [ ] README: explicitly list predefined related words per output and a qualifier list as future improvements.
- [ ] DESIGN: record the early-exit and merged-pass risk discussion above, and the ambiguity decision.
- [ ] TESTING: record the source and licence of any external job title list.
