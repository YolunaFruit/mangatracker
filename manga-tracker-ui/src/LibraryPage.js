import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import api from "./api";
import "./Library.css";

const NO_COVER = "/no-cover.svg";
const safeCover = (url) => (url && String(url).trim().length > 0 ? url : NO_COVER);

const STATUSES = [
    { key: "all", label: "All" },
    { key: "reading", label: "Reading" },
    { key: "completed", label: "Completed" },
    { key: "on_hold", label: "On Hold" },
    { key: "plan_to_read", label: "Plan to Read" },
    { key: "dropped", label: "Dropped" },
];

function fmtAgo(iso) {
    if (!iso) return "—";
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "—";
    return d.toLocaleDateString();
}

function normStatus(s) {
    if (!s) return "reading";
    const v = String(s).toLowerCase();
    if (v === "reading" || v === "completed" || v === "on_hold" || v === "plan_to_read" || v === "dropped") return v;
    return "reading";
}

export default function LibraryPage({ user }) {
    const [library, setLibrary] = useState([]);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [saveError, setSaveError] = useState("");

    // UI controls
    const [viewMode, setViewMode] = useState("expanded"); // expanded | compact
    const [statusFilter, setStatusFilter] = useState("all");
    const [sortBy, setSortBy] = useState("lastRead"); // lastRead | lastAdded | title | progress | latest
    const [sortDir, setSortDir] = useState("desc");

    // menu
    const [openMenuFor, setOpenMenuFor] = useState(null); // mangaId
    const menuRef = useRef(null);

    // update modal
    const [edit, setEdit] = useState(null); // { manga, draftChapter, manual, draftStatus, draftScore }
    const [latestMap, setLatestMap] = useState({}); // mangaId -> latestCh (number)

    const loadLibrary = useCallback(async () => {
        const res = await api.get(`/progress/${encodeURIComponent(user.username)}`);
        setLibrary(res?.data?.trackedManga || []);
    }, [user.username]);

    useEffect(() => {
        loadLibrary().catch(() => {}).finally(() => setLoading(false));
    }, [loadLibrary]);

    // Only three lightweight chapter requests at a time, rather than a burst of full details.
    const libraryIds = library.map(m => m.mangaId).join(",");
    useEffect(() => {
        let active = true;
        const ids = libraryIds ? libraryIds.split(",") : [];
        let cursor = 0;
        async function worker() {
            while (active && cursor < ids.length) {
                const id = ids[cursor++];
                try {
                    const { data } = await api.get(`/search/chapters?id=${encodeURIComponent(id)}`);
                    if (active) setLatestMap(prev => ({ ...prev, [id]: data.latestChapter }));
                } catch (_) {}
            }
        }
        Promise.all([worker(), worker(), worker()]);
        return () => { active = false; };
    }, [libraryIds]);

    // close menu on outside click
    useEffect(() => {
        const onDown = (e) => {
            if (!menuRef.current) return;
            if (menuRef.current.contains(e.target)) return;
            setOpenMenuFor(null);
        };
        window.addEventListener("mousedown", onDown);
        return () => window.removeEventListener("mousedown", onDown);
    }, []);

    const setStatus = async (manga, status) => {
        try {
            await api.post(`/progress/${encodeURIComponent(user.username)}/status`, null,
                { params: { mangaId: manga.mangaId, status } });
            setOpenMenuFor(null);
            await loadLibrary();
        } catch (_) {}
    };

    const removeManga = async (manga) => {
        try {
            await api.post(`/progress/${encodeURIComponent(user.username)}/remove`, null,
                { params: { mangaId: manga.mangaId } });
            await loadLibrary();
        } catch (_) {}
    };

    const filtered = useMemo(() => {
        const sf = statusFilter;
        return (library || []).filter((m) => {
            if (sf === "all") return true;
            return normStatus(m.status) === sf;
        });
    }, [library, statusFilter]);

    const sorted = useMemo(() => {
        const arr = [...filtered];
        const dir = sortDir === "asc" ? 1 : -1;

        const num = (v) => (typeof v === "number" ? v : -Infinity);
        const time = (v) => {
            if (!v) return -Infinity;
            const t = new Date(v).getTime();
            return Number.isNaN(t) ? -Infinity : t;
        };

        arr.sort((a, b) => {
            const aId = a.mangaId || a.id;
            const bId = b.mangaId || b.id;
            const aLatest = latestMap[aId] ?? -1;
            const bLatest = latestMap[bId] ?? -1;

            switch (sortBy) {
                case "title":
                    return dir * (String(a.title || "").localeCompare(String(b.title || "")));
                case "progress":
                    return dir * ((a.currentChapter || 0) - (b.currentChapter || 0));
                case "latest":
                    return dir * (num(aLatest) - num(bLatest));
                case "lastAdded":
                    return dir * (time(a.lastAdded) - time(b.lastAdded));
                case "lastRead":
                default:
                    return dir * (time(a.lastRead) - time(b.lastRead));
            }
        });

        return arr;
    }, [filtered, sortBy, sortDir, latestMap]);

    const openEdit = (m) => {
        const mangaId = m.mangaId || m.id;
        const latest = latestMap[mangaId] ?? -1;
        setSaveError("");
        setEdit({
            manga: m,
            draftChapter: typeof m.currentChapter === "number" ? m.currentChapter : 0,
            manual: "",
            draftStatus: normStatus(m.status),
            draftScore: typeof m.myScore === "number" ? m.myScore : null,
            latest,
        });
        setOpenMenuFor(null);
    };

    const saveEdit = async () => {
        if (!edit || saving) return;
        const chapter = edit.manual.trim() ? Number(edit.manual.trim()) : edit.draftChapter;
        if (!Number.isInteger(chapter) || chapter < 0 || chapter > 1000000) {
            setSaveError("Enter a whole chapter number between 0 and 1,000,000.");
            return;
        }
        setSaving(true); setSaveError("");
        try {
            const { data } = await api.post(`/progress/${encodeURIComponent(user.username)}/entry`, {
                mangaId: edit.manga.mangaId,
                chapter,
                status: edit.draftStatus,
                score: edit.draftScore,
            });
            setLibrary(data.trackedManga || []);
            setEdit(null);
        } catch (_) {
            setSaveError("Your changes weren't saved. Please try again.");
        } finally { setSaving(false); }
    };

    return (
        <div className="lib">
            <div className="lib-top">
                <div className="lib-title">
                    <h2>My Library</h2>
                    <div className="lib-count">{sorted.length} titles</div>
                </div>

                <div className="lib-controls">
                    <div className="lib-control">
                        <label>View</label>
                        <select value={viewMode} onChange={(e) => setViewMode(e.target.value)}>
                            <option value="expanded">Expanded</option>
                            <option value="compact">Compact</option>
                        </select>
                    </div>

                    <div className="lib-control">
                        <label>Status</label>
                        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                            {STATUSES.map((s) => (
                                <option key={s.key} value={s.key}>
                                    {s.label}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="lib-control">
                        <label>Sort By</label>
                        <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
                            <option value="lastRead">Last Read</option>
                            <option value="lastAdded">Last Added</option>
                            <option value="latest">Latest Chapter</option>
                            <option value="progress">Progress</option>
                            <option value="title">Title</option>
                        </select>
                    </div>

                    <div className="lib-control">
                        <label>Direction</label>
                        <select value={sortDir} onChange={(e) => setSortDir(e.target.value)}>
                            <option value="desc">Descending</option>
                            <option value="asc">Ascending</option>
                        </select>
                    </div>
                </div>
            </div>

            {loading ? <p role="status">Loading your library…</p> : sorted.length === 0 ? (
                <div className="lib-empty">{library.length ? "No manga matches this status." : "No manga yet. Add one from Search!"}</div>
            ) : (
                <div className={`lib-list ${viewMode}`}>
                    {sorted.map((m) => {
                        const mangaId = m.mangaId || m.id;
                        const latest = latestMap[mangaId];
                        const current = typeof m.currentChapter === "number" ? m.currentChapter : 0;
                        const hasUpdate = typeof latest === "number" && latest >= 0 && latest > current;

                        return (
                            <div key={mangaId} className={`lib-row ${hasUpdate ? "hasUpdate" : ""}`}>
                                <div className={`dot ${hasUpdate ? "on" : ""}`} title={hasUpdate ? "Updates available" : ""} />

                                {viewMode === "expanded" && (
                                    <img referrerPolicy="no-referrer"
                                        className="cover"
                                        src={safeCover(m.coverUrl)}
                                        alt="cover"
                                        loading="lazy"
                                        onError={(e) => {
                                            e.currentTarget.onerror = null;
                                            e.currentTarget.src = NO_COVER;
                                        }}
                                    />
                                )}

                                <div className="main">
                                    <div className="topline">
                                        <div className="name" title={m.title || ""}>
                                            {m.title || "(untitled)"}
                                        </div>

                                        <div className="chips">
                      <span className="chip">
                        <span className="chip-ico">📖</span> Ch. {current}
                      </span>

                                            <span className={`chip latest ${hasUpdate ? "hot" : ""}`}>
                        <span className="chip-ico">🆕</span>{" "}
                                                {typeof latest === "number" && latest >= 0 ? `Ch. ${latest}` : "Ch. —"}
                      </span>

                                            <span className={`status ${normStatus(m.status)}`}>
                        {STATUSES.find((x) => x.key === normStatus(m.status))?.label || "Reading"}
                      </span>
                                        </div>

                                        <div className="menuWrap" ref={openMenuFor === mangaId ? menuRef : null}>
                                            <button
                                                className="dots"
                                                onClick={() => setOpenMenuFor((prev) => (prev === mangaId ? null : mangaId))}
                                                aria-label="Options"
                                            >
                                                …
                                            </button>

                                            {openMenuFor === mangaId && (
                                                <div className="menu">
                                                    <button onClick={() => openEdit(m)}>Update / Edit</button>
                                                    <button onClick={() => setStatus(m, "reading")}>Set: Reading</button>
                                                    <button onClick={() => setStatus(m, "completed")}>Set: Completed</button>
                                                    <button onClick={() => setStatus(m, "on_hold")}>Set: On Hold</button>
                                                    <button onClick={() => setStatus(m, "plan_to_read")}>Set: Plan to Read</button>
                                                    <button onClick={() => setStatus(m, "dropped")}>Set: Dropped</button>
                                                    <div className="menuSep" />
                                                    <button className="danger" onClick={() => removeManga(m)}>
                                                        Remove
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    </div>

                                    <div className="subline">
                    <span className="meta">
                      <span className="meta-ico">📅</span> Read {fmtAgo(m.lastRead)}
                    </span>
                                        <span className="dotsep">•</span>
                                        <span className="meta">
                      <span className="meta-ico">⬆️</span> Added {fmtAgo(m.lastAdded)}
                    </span>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            {/* Edit Modal */}
            {edit && (
                <div className="modalBack" onMouseDown={() => setEdit(null)}>
                    <div className="modal" role="dialog" aria-modal="true" aria-label="Edit library entry" onMouseDown={(e) => e.stopPropagation()}>
                        <div className="modalHead">
                            <div className="modalTitle">{edit.manga.title || "Edit entry"}</div>
                            <button className="x" onClick={() => setEdit(null)}>
                                ✕
                            </button>
                        </div>

                        <div className="modalGrid">
                            <div className="field">
                                <label htmlFor="edit-chapter">Last Read Chapter</label>
                                <select id="edit-chapter"
                                    value={String(edit.draftChapter)}
                                    onChange={(e) => setEdit((p) => ({ ...p, draftChapter: parseInt(e.target.value, 10) }))}
                                >
                                    {Array.from(
                                        { length: Math.min(2001, Math.max(edit.draftChapter, edit.latest || 250) + 1) },
                                        (_, i) => i
                                    )
                                        .map((n) => (
                                            <option key={n} value={n}>
                                                Ch. {n}
                                            </option>
                                        ))}
                                </select>
                                <div className="hint">
                                    Or track manually:
                                    <input
                                        className="manual"
                                        value={edit.manual}
                                        onChange={(e) => setEdit((p) => ({ ...p, manual: e.target.value }))}
                                        placeholder="e.g., 293"
                                    />
                                </div>
                            </div>

                            <div className="field">
                                <label htmlFor="edit-status">Status</label>
                                <select id="edit-status"
                                    value={edit.draftStatus}
                                    onChange={(e) => setEdit((p) => ({ ...p, draftStatus: e.target.value }))}
                                >
                                    <option value="reading">Reading</option>
                                    <option value="completed">Completed</option>
                                    <option value="on_hold">On Hold</option>
                                    <option value="plan_to_read">Plan to Read</option>
                                    <option value="dropped">Dropped</option>
                                </select>
                            </div>

                            <div className="field">
                                <label htmlFor="edit-score">My Score</label>
                                <select id="edit-score"
                                    value={edit.draftScore == null ? "" : String(edit.draftScore)}
                                    onChange={(e) =>
                                        setEdit((p) => ({ ...p, draftScore: e.target.value === "" ? null : parseInt(e.target.value, 10) }))
                                    }
                                >
                                    <option value="">—</option>
                                    {Array.from({ length: 11 }, (_, i) => i).map((n) => (
                                        <option key={n} value={n}>
                                            {n}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>

                        {saveError && <p role="alert">{saveError}</p>}
                        <div className="modalActions">
                            <button className="ghost" onClick={() => setEdit(null)}>
                                Cancel
                            </button>
                            <button className="primary" disabled={saving} onClick={saveEdit}>
                                {saving ? "Saving…" : "Save"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
