# Streaming redirects — 2026-10-22

## Click path

MovieDetailsContent now supplies a required provider callback to StreamingProvidersSection, which passes through WatchProviderRow and WatchProviderBadgeRow to the clickable logo/label. Stream, Rent and Buy keep their ProviderType. The callback reads the displayed movie's ID, canonical title/year, selected region and watch URL. Provider failures have an explicit Retry watch options button. Region changes clear previous links synchronously; request versions reject obsolete responses. DTO mapping and the ViewModel reject mismatched movie IDs, and duplicate provider IDs are removed within each category to avoid duplicate lazy keys.

## Destination policy

ProviderDestinationResolver is independent of Android Context. Its typed result is DIRECT_CONTENT, PROVIDER_SEARCH, REGIONAL_WATCH_FALLBACK or UNAVAILABLE, and retains movie/provider/category/year/region association.

1. An exact content link must come from a reviewed internal registry binding movie ID, provider ID, category and country, and pass the provider host/content-path checks. A loose WatchProvider.directLink string is not proof of a movie association and is not trusted.
2. A reviewed public provider-search contract may supply a supported HTTPS endpoint and query parameter. Stable provider IDs take priority over exact normalized names. Titles are UTF-8 encoded; year/region stay in the result but are not appended to unsupported search parameters.
3. Otherwise validate the supplied TMDB movie watch page. It must use HTTPS on themoviedb.org or www.themoviedb.org, match the current numeric movie ID (optional slug), and have no query other than the matching locale. A matching regional fragment is accepted; output uses `?locale=XX`. Another movie, country, port, user-info, encoded path, traversal, malformed URI, arbitrary scheme, unrelated host or redirect parameter is rejected.
4. Missing/invalid destinations show a clear message. There is no unrelated homepage or fabricated provider content ID fallback.

A raw JustWatch slug is not accepted because this data contract cannot independently bind it to a TMDB movie ID. The TMDB watch page is powered by JustWatch and carries the exact movie identity. Fallback messaging explicitly says regional watch options; it never claims to open the tapped provider directly.

## Verified routes and current limitation

**Production direct-link registry: empty. Production provider-search registry: empty.** No current content-specific provider link is supplied by this repository's TMDB endpoint. No public provider-search URL contract was sufficiently verified during this pass. Netflix and Prime Video candidate URL retrieval failed; Netflix help confirms title search in the product but does not establish a stable public query URL. Consequently current production behavior uses the valid regional TMDB fallback, or UNAVAILABLE. No guessed search routes were enabled.

Evidence consulted on 2026-10-22:
- [TMDB Watch Providers](https://developer.themoviedb.org/reference/movie-watch-providers) explicitly describes regional availability rather than full deep links, and recommends the supplied TMDB URL.
- [Netflix search help](https://help.netflix.com/en/node/47765) describes product search, not a verified public URL contract.
- Candidate `https://www.netflix.com/search?q=Inception` and `https://www.primevideo.com/search?phrase=Inception` could not be retrieved by the research tool. This is not evidence that they are universally invalid.

Netflix IDs 8 and Prime Video IDs 9/119 are recognized for host validation; exact normalized names are secondary. This identity support alone does not activate a provider route. Internal injected fixtures test precedence and encoding only; they are not live verified mappings.

## Launching and regression coverage

StreamingContract uses ACTION_VIEW with validated HTTPS, allowing normal App Link/browser resolution without hardcoded packages or a chooser. It reports ActivityNotFoundException, SecurityException and other synchronous launch failures. The generic watch-options button uses the same resolver.

ProviderDestinationResolverTest covers priority, trusted binding, ID/name matching, unknown providers, all categories, Unicode/punctuation encoding, null/blank titles, region propagation, movie mismatch, unsafe schemes/hosts and missing links. StreamingContractTest covers dispatch, fallback explanation, unavailable destinations and launch exceptions. StreamingProviderClickTest covers section → row → badge propagation for all three categories; it is **compiled only**, not executed.

Actual app/browser launch, authentication, territorial availability, accessibility announcements and provider playback remain unverified. A successful URI test is not evidence that Netflix/Prime opened.
