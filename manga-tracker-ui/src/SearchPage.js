import React, { useEffect, useMemo, useRef, useState } from "react";
import api from "./api";

const NO_COVER = "/no-cover.svg";
const safeCover = (url) => (url && url.trim().length > 0 ? url : NO_COVER);

// Warning tag styling helpers
const WARN_ORANGE = new Set(["Suggestive", "Ecchi"]);
const WARN_RED = new Set([
    "Erotica",
    "Gore",
    "Violence",
    "Sexual Violence",
    "Abuse",
    "Suicide",
    "Self-Harm",
]);

const warningWeight = (t) => (WARN_RED.has(t) ? 2 : WARN_ORANGE.has(t) ? 1 : 0);

const tagClass = (t) => {
    const w = warningWeight(t);
    if (w === 2) return "tag warn-red";
    if (w === 1) return "tag warn-orange";
    return "tag";
};

const sortTagsForDisplay = (tags) => {
    const arr = [...(tags || [])];
    arr.sort((a, b) => warningWeight(b) - warningWeight(a) || a.localeCompare(b));
    return arr;
};

// tri-state for tag picker
function triNext(state) {
    // 0 = neutral, 1 = include, -1 = exclude
    if (state === 0) return 1;
    if (state === 1) return -1;
    return 0;
}

// Robust author/artist formatting (supports several possible backend shapes)
function formatCredits(m) {
    if (!m) return "";

    const authors =
        (Array.isArray(m.authors) && m.authors.filter(Boolean).join(", ")) ||
        (typeof m.author === "string" ? m.author : "") ||
        "";
    const artists =
        (Array.isArray(m.artists) && m.artists.filter(Boolean).join(", ")) ||
        (typeof m.artist === "string" ? m.artist : "") ||
        "";

    if (authors && artists && authors !== artists) return `By ${authors} • Art ${artists}`;
    if (authors) return `By ${authors}`;
    if (artists) return `Art ${artists}`;
    return "";
}

export default function SearchPage({ user }) {
    const [mode, setMode] = useState("home"); // home | search | advanced
    const [view, setView] = useState("home"); // home | list | details

    // home data
    const [home, setHome] = useState({ trending: [], new: [] });
    const [homeLoading, setHomeLoading] = useState(false);

    // shared
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState(null);

    // search
    const [query, setQuery] = useState("");
    const [suggestions, setSuggestions] = useState([]);
    const [listResults, setListResults] = useState([]);

    // advanced
    const [tagCatalog, setTagCatalog] = useState(null);
    const [tagState, setTagState] = useState({}); // tagId -> -1/0/1
    const [sortBy, setSortBy] = useState("relevance");
    const [sortDir, setSortDir] = useState("desc");

    const typingTimerRef = useRef(null);
    const suggestionRequestRef = useRef(0);
    useEffect(() => () => {
        clearTimeout(typingTimerRef.current);
        suggestionRequestRef.current++;
    }, []);

    const includeTags = useMemo(
        () => Object.entries(tagState).filter(([, v]) => v === 1).map(([k]) => k),
        [tagState]
    );
    const excludeTags = useMemo(
        () => Object.entries(tagState).filter(([, v]) => v === -1).map(([k]) => k),
        [tagState]
    );

    // load home
    useEffect(() => {
        if (mode !== "home") return;
        (async () => {
            setHomeLoading(true);
            try {
                const res = await api.get("/search/home");
                setHome(res.data);
                setView("home");
            } catch (_) {
                // The shared API handler displays the error with a retryable message.
            } finally {
                setHomeLoading(false);
            }
        })();
    }, [mode]);

    // load tags when advanced opens
    useEffect(() => {
        if (mode !== "advanced") return;
        (async () => {
            if (tagCatalog) return;
            try {
                const res = await api.get("/search/tags");
                setTagCatalog(res.data);
            } catch (_) {}
        })();
    }, [mode, tagCatalog]);

    const fetchSuggestions = async (text) => {
        const q = text.trim();
        if (q.length < 2) {
            setSuggestions([]);
            return;
        }
        // Backend will be updated so "title" searches title OR author
        const request = ++suggestionRequestRef.current;
        try {
            const res = await api.get(`/search/list?title=${encodeURIComponent(q)}&limit=10`);
            if (request === suggestionRequestRef.current)
                setSuggestions(Array.isArray(res.data) ? res.data.slice(0, 6) : []);
        } catch (_) {}
    };

    const runSearch = async () => {
        const q = query.trim();
        if (!q) {
            // If user hits search with empty query, just go to browse/home-ish behavior
            setSuggestions([]);
            setListResults([]);
            setResult(null);
            setMode("home");
            setView("home");
            return;
        }

        clearTimeout(typingTimerRef.current);
        suggestionRequestRef.current++;
        setLoading(true);
        try {
            const res = await api.get(`/search/list?title=${encodeURIComponent(q)}&limit=25`);
            setListResults(Array.isArray(res.data) ? res.data : []);
            setResult(null);
            setSuggestions([]);
            setMode("search");
            setView("list");
            window.scrollTo({ top: 0, behavior: "smooth" });
        } catch (_) {
            // Errors are shown by the shared API handler.
        } finally {
            setLoading(false);
        }
    };

    const runAdvanced = async () => {
        clearTimeout(typingTimerRef.current);
        suggestionRequestRef.current++;
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (query.trim().length > 0) params.set("title", query.trim());
            includeTags.forEach((t) => params.append("includeTags", t));
            excludeTags.forEach((t) => params.append("excludeTags", t));
            params.set("sortBy", sortBy);
            params.set("sortDir", sortDir);
            params.set("limit", "25");

            const res = await api.get(`/search/advanced?${params.toString()}`);
            setListResults(Array.isArray(res.data) ? res.data : []);
            setResult(null);
            setSuggestions([]);
            setMode("advanced");
            setView("list");
            window.scrollTo({ top: 0, behavior: "smooth" });
        } catch (_) {
            // Errors are shown by the shared API handler.
        } finally {
            setLoading(false);
        }
    };

    const runRandomWithFilters = async () => {
        clearTimeout(typingTimerRef.current);
        suggestionRequestRef.current++;
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (query.trim().length > 0) params.set("title", query.trim());
            includeTags.forEach((t) => params.append("includeTags", t));
            excludeTags.forEach((t) => params.append("excludeTags", t));
            params.set("sortBy", sortBy);
            params.set("sortDir", sortDir);

            const res = await api.get(`/search/random?${params.toString()}`);
            const data = res.data;

            if (!data || !data.id) {
                alert("No random result found for those filters.");
                return;
            }

            // show details directly
            setResult(data);
            setSuggestions([]);
            setListResults([]);
            setMode("advanced");
            setView("details");
            window.scrollTo({ top: 0, behavior: "smooth" });
        } catch (_) {
            // Errors are shown by the shared API handler.
        } finally {
            setLoading(false);
        }
    };

    const runPrimarySearch = async () => {
        if (mode === "advanced") return runAdvanced();
        return runSearch();
    };

    const loadDetails = async (id) => {
        clearTimeout(typingTimerRef.current);
        suggestionRequestRef.current++;
        setLoading(true);
        try {
            const res = await api.get(`/search/details?id=${encodeURIComponent(id)}`);
            setResult(res.data);
            setSuggestions([]);
            setListResults([]);
            setMode((prev) => (prev === "home" ? "search" : prev));
            setView("details");
            window.scrollTo({ top: 0, behavior: "smooth" });
        } catch (_) {
            // Errors are shown by the shared API handler.
        } finally {
            setLoading(false);
        }
    };

    const addToLibrary = async () => {
        if (!result) return;

        try {
            const payload = {
                mangaId: result.id,
                title: result.title,
                coverUrl: result.coverUrl,
            };

            await api.post(`/user/${encodeURIComponent(user.username)}/add-manga`, payload, {
                headers: { "Content-Type": "application/json" },
            });

            alert("Added to your library!");
        } catch (_) {
            // Errors are shown by the shared API handler.
        }
    };

    return (
        <div>
            {/* Mode pills */}
            <div className="pillbar">
                <button
                    className={`pill ${mode === "home" ? "active" : ""}`}
                    onClick={() => {
                        setMode("home");
                        setView("home");
                        setResult(null);
                        setListResults([]);
                    }}
                >
                    Browse
                </button>
                <button
                    className={`pill ${mode === "search" ? "active" : ""}`}
                    onClick={() => {
                        setMode("search");
                        setView("list");
                    }}
                >
                    Search
                </button>
                <button
                    className={`pill ${mode === "advanced" ? "active" : ""}`}
                    onClick={() => {
                        setMode("advanced");
                        setView("list");
                    }}
                >
                    Advanced Search
                </button>
            </div>

            {/* Search box */}
            <div className="search-wrapper">
                <div className="search-box">
                    <input
                        type="text"
                        placeholder="Search by title or author..."
                        value={query}
                        onChange={(e) => {
                            const text = e.target.value;
                            setQuery(text);
                            suggestionRequestRef.current++;
                            if (text.trim().length < 2) setSuggestions([]);

                            // In Advanced mode, suggestions aren't useful (and can confuse the workflow)
                            if (mode === "advanced") {
                                setSuggestions([]);
                                return;
                            }

                            if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
                            typingTimerRef.current = setTimeout(() => fetchSuggestions(text), 250);
                        }}
                        onKeyDown={(e) => e.key === "Enter" && runPrimarySearch()}
                    />
                    <button onClick={runPrimarySearch}>
                        {mode === "advanced" ? "Search (with filters)" : "Search"}
                    </button>

                    {suggestions.length > 0 && (
                        <div className="suggestions-box">
                            {suggestions.map((s) => {
                                const credits = formatCredits(s);
                                return (
                                    <div
                                        key={s.id}
                                        className="suggestion-item"
                                        onClick={() => loadDetails(s.id)}
                                    >
                                        <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
                                            <span className="suggestion-title">{s.title}</span>
                                            {credits && (
                                                <span
                                                    style={{
                                                        fontSize: 12,
                                                        opacity: 0.75,
                                                    }}
                                                >
                          {credits}
                        </span>
                                            )}
                                        </div>

                                        <span className="suggestion-meta">
                      {s.chapterCount > 0 ? `${s.chapterCount} ch` : "—"}
                    </span>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>

            {/* Home */}
            {mode === "home" && view === "home" && (
                <div className="home">
                    {homeLoading ? (
                        <div className="loader"></div>
                    ) : (
                        <>
                            <section className="home-section">
                                <div className="home-head">
                                    <h2>Trending</h2>
                                    <div className="home-sub">Popular right now</div>
                                </div>
                                <div className="home-row">
                                    {home.trending?.map((m) => (
                                        <button
                                            key={m.id}
                                            className="home-card"
                                            onClick={() => loadDetails(m.id)}
                                        >
                                            <img referrerPolicy="no-referrer" src={safeCover(m.coverUrl)} alt={m.title + " cover"} loading="lazy" onError={event => { event.currentTarget.onerror = null; event.currentTarget.src = NO_COVER; }} />
                                            <div className="home-card-title">{m.title}</div>
                                        </button>
                                    ))}
                                </div>
                            </section>

                            <section className="home-section">
                                <div className="home-head">
                                    <h2>New / Recently Updated</h2>
                                    <div className="home-sub">Fresh uploads</div>
                                </div>
                                <div className="home-row">
                                    {home.new?.map((m) => (
                                        <button
                                            key={m.id}
                                            className="home-card"
                                            onClick={() => loadDetails(m.id)}
                                        >
                                            <img referrerPolicy="no-referrer" src={safeCover(m.coverUrl)} alt={m.title + " cover"} loading="lazy" onError={event => { event.currentTarget.onerror = null; event.currentTarget.src = NO_COVER; }} />
                                            <div className="home-card-title">{m.title}</div>
                                        </button>
                                    ))}
                                </div>
                            </section>
                        </>
                    )}
                </div>
            )}

            {/* Advanced */}
            {mode === "advanced" && (
                <div className="advanced">
                    <div className="advanced-panel">
                        <div className="advanced-row">
                            <div className="advanced-field">
                                <label>Sort By</label>
                                <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
                                    <option value="relevance">Relevance</option>
                                    <option value="trending">Follows (Trending)</option>
                                    <option value="latest">Latest Updated</option>
                                    <option value="rating">Rating</option>
                                    <option value="title">Title</option>
                                </select>
                            </div>

                            <div className="advanced-field">
                                <label>Direction</label>
                                <select value={sortDir} onChange={(e) => setSortDir(e.target.value)}>
                                    <option value="desc">Descending</option>
                                    <option value="asc">Ascending</option>
                                </select>
                            </div>

                            <button className="primary" onClick={runAdvanced} disabled={loading}>
                                Apply Filters
                            </button>

                            <button className="primary" onClick={runRandomWithFilters} disabled={loading}>
                                Random Search (with filters)
                            </button>

                            <button
                                className="ghost"
                                onClick={() => {
                                    setTagState({});
                                    setSortBy("relevance");
                                    setSortDir("desc");
                                }}
                                disabled={loading}
                            >
                                Clear
                            </button>
                        </div>

                        <div className="trihelp">
                            Click a tag to cycle: <span className="tri tri-in">Include</span> →{" "}
                            <span className="tri tri-ex">Exclude</span> → Neutral
                        </div>

                        {!tagCatalog ? (
                            <div className="loader"></div>
                        ) : (
                            <div className="tag-groups">
                                {Object.entries(tagCatalog).map(([groupName, tags]) => (
                                    <div key={groupName} className="tag-group">
                                        <h3>{groupName}</h3>
                                        <div className="tag-grid">
                                            {tags.map((t) => {
                                                const state = tagState[t.id] ?? 0;
                                                return (
                                                    <button
                                                        key={t.id}
                                                        className={`tagpick ${state === 1 ? "in" : state === -1 ? "ex" : ""}`}
                                                        onClick={() =>
                                                            setTagState((prev) => ({
                                                                ...prev,
                                                                [t.id]: triNext(prev[t.id] ?? 0),
                                                            }))
                                                        }
                                                        disabled={loading}
                                                    >
                                                        <span className="tagdot" />
                                                        {t.name}
                                                    </button>
                                                );
                                            })}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            )}

            {loading && <div className="loader"></div>}

            {/* Results list */}
            {!loading &&
                (mode === "search" || mode === "advanced") &&
                view === "list" &&
                listResults.length > 0 && (
                    <div className="search-results-list">
                        {listResults.map((item) => {
                            const credits = formatCredits(item);
                            return (
                                <div
                                    key={item.id}
                                    className="search-result-item"
                                    onClick={() => loadDetails(item.id)}
                                >
                                    <img referrerPolicy="no-referrer"
                                        className="search-result-cover"
                                        src={safeCover(item.coverUrl)}
                                        alt="cover"
                                        loading="lazy"
                                        onError={(e) => (e.currentTarget.src = NO_COVER)}
                                    />

                                    <div className="result-info">
                                        <div className="result-title-row">
                                            <h3 className="result-title">{item.title}</h3>
                                            <div className="result-chapters">
                                                {item.chapterCount > 0 ? `${item.chapterCount} ch` : ""}
                                            </div>
                                        </div>

                                        {credits && (
                                            <div
                                                className="result-credits"
                                                style={{
                                                    marginTop: 2,
                                                    marginBottom: 6,
                                                    fontSize: 13,
                                                    opacity: 0.8,
                                                }}
                                            >
                                                {credits}
                                            </div>
                                        )}

                                        <p className="result-description">
                                            {item.description || "No description available."}
                                        </p>

                                        {item.tags && item.tags.length > 0 && (
                                            <div className="tag-list">
                                                {sortTagsForDisplay(item.tags)
                                                    .slice(0, 8)
                                                    .map((t) => (
                                                        <span key={t} className={tagClass(t)}>
                              {t}
                            </span>
                                                    ))}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}

            {/* Details */}
            {!loading && (mode === "search" || mode === "advanced") && view === "details" && result && (
                <div className="result-card-large">
                    <button
                        className="ghost"
                        onClick={() => {
                            setResult(null);
                            setView("list");
                        }}
                        style={{ marginBottom: 10 }}
                    >
                        ← Back to results
                    </button>

                    <img referrerPolicy="no-referrer" src={safeCover(result.coverUrl)} alt="cover" />

                    <div className="detail-meta">
                        <h2>{result.title}</h2>

                        {formatCredits(result) && (
                            <div style={{ marginTop: 4, opacity: 0.85, fontSize: 14 }}>
                                {formatCredits(result)}
                            </div>
                        )}

                        <div className="detail-sub" style={{ marginTop: 6 }}>
                            {result.chapterCount > 0 ? `${result.source === "MyAnimeList" ? "Chapters reported" : "Latest English chapter"}: ${result.chapterCount}` : ""}
                        </div>
                    </div>

                    <p>{result.description}</p>

                    {result.altTitles && result.altTitles.length > 0 && (
                        <div className="alt-titles">
                            <b>Alternative titles:</b> {result.altTitles.join(" • ")}
                        </div>
                    )}

                    {result.tags && result.tags.length > 0 && (
                        <div className="tag-list" style={{ marginTop: 10 }}>
                            {sortTagsForDisplay(result.tags)
                                .slice(0, 12)
                                .map((t) => (
                                    <span key={t} className={tagClass(t)}>
                    {t}
                  </span>
                                ))}
                        </div>
                    )}

                    <button onClick={addToLibrary}>Add to Library</button>
                </div>
            )}
        </div>
    );
}
