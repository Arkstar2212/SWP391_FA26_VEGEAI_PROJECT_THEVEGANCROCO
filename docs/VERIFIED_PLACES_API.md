# Verified vegetarian restaurant feed

## Cause of the missing map results

The current frontend renders Leaflet/OpenStreetMap and calls `/api/places/verified/*`.
Previously the backend only implemented legacy Google Places `/nearby` and `/search`,
so the frontend received 404. The legacy service also supplied `mock_` locations
when its Google key was missing; these must not be relabeled as actual restaurants.

## Endpoints (Guest, Customer and Admin may read)

- `GET /api/places/verified/nearby?lat=10.7769&lng=106.7009&radius=5000`
- `GET /api/places/verified/search?query=Ho%20Chi%20Minh%20City&radius=5000`
- `GET /api/places/verified/search?query=restaurant-name&lat=10.7769&lng=106.7009&radius=5000`

Coordinates must be finite and within geographic bounds; radius is 500–50000 m.
When searching with coordinates, query filters restaurant names/addresses. Without
coordinates, Photon resolves the requested area or named place, then Overpass
searches around that point. Unknown areas return an empty list, never a default
Saigon result. Coordinates, query text and cache values are not application-logged.

Responses keep the existing `ApiResponse<List<PlaceResponse>>` structure. Each
result has a stable `osm_node_ID`, `osm_way_ID` or `osm_relation_ID`, actual
coordinates, precise straight-line distance in km, `openStreetMapUrl`, attribution,
and original diet/cuisine tags in `types`. Legacy `verified` is retained and
`isVerified` is exposed for frontend compatibility. Unknown ratings, opening status
and photos remain null rather than invented. This verification means matching
community tags, NOT certification that a restaurant is entirely vegetarian today.

Only eateries tagged `diet:vegetarian=only`, `diet:vegan=only`, or a cuisine made
exclusively of `vegetarian`/`vegan` qualify. `diet:vegetarian=yes` alone, mixed
seafood cuisine, stores, missing coordinates and results outside the radius are
excluded. Ways and relations use returned center coordinates. Results are sorted
by distance and deduplicated by OSM object ID, not by restaurant name.

## Configuration and public-service limits

No Google Maps key is required for these endpoints. Existing Google endpoints
remain for compatibility, but are not used by the current restaurant page.

```dotenv
OSM_OVERPASS_URLS=https://overpass.private.coffee/api/interpreter,https://overpass-api.de/api/interpreter
OSM_PHOTON_URL=https://photon.komoot.io/api/
OSM_USER_AGENT=VEGEAI/1.0 (+https://github.com/cudatbusua/SWP391_VEGEAI_FRONTEND)
```

Restart the backend after changing runtime variables. Requests have bounded
connect/read timeouts and at most one configured alternate Overpass server. Redis
caches nearby results (including empty lists) for 15 minutes, resolved areas for
24 hours, and unknown areas for 5 minutes. Geocoding is limited to one new request
per second across instances when Redis is available, with a local fallback limit.
Provider failures return JSON 503/code 2601, not success with sample places.
Each unavailable Overpass endpoint is paused for 30 seconds using Redis, shared
across coordinates and backend instances. Existing successful result caches are
still readable during the pause. If Redis is unavailable this shared pause cannot
be enforced; bounded provider timeouts still apply. This avoids repeated requests
to overloaded public infrastructure, but does not guarantee provider availability.

The public [Photon demo policy](https://github.com/komoot/photon#demo-server)
permits reasonable small-project usage but does not guarantee availability. Search
is user-triggered, not autocomplete. Overpass is also shared infrastructure; use
configurable private/commercial providers for heavier production traffic. Do not
send private addresses or sensitive information. This implementation does not use
the public Nominatim service. Attribution remains visible on the frontend map.

Sources: [Overpass service](https://overpass.kumi.systems/),
[OSM diet tags](https://wiki.openstreetmap.org/wiki/Key:diet:*),
[Overpass geometries](https://dev.overpass-api.de/overpass-doc/en/full_data/osm_types.html).

## Verification

`VerifiedPlacesApiTest` uses a local HTTP fixture, not live-provider success claims.
It covers the Guest API contract, provider failover, no mock substitution, strict
tag filtering, coordinates/center parsing, sorting, exact radius bounds, cache,
geocoding and unknown-area handling. Live public-service calls are a separate
connectivity check and may fail when a provider is overloaded or unreachable.
