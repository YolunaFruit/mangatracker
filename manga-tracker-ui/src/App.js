import React, { useEffect, useState } from "react";
import LoginPage from "./LoginPage";
import LibraryPage from "./LibraryPage";
import SearchPage from "./SearchPage";
import api, { getSession } from "./api";
import "./App.css";

function App() {
    const [user, setUser] = useState(null);
    const [ready, setReady] = useState(false);
    const [error, setError] = useState("");
    const [page, setPage] = useState("search");

    useEffect(() => {
        let active = true;
        // Old cached profiles never prove that a visitor is signed in.
        localStorage.removeItem("user");
        const onError = (event) => setError(event.detail);
        const onExpired = () => setUser(null);
        window.addEventListener("api-error", onError);
        window.addEventListener("session-expired", onExpired);
        getSession().then(value => { if (active) setUser(value); })
            .catch(() => {}).finally(() => { if (active) setReady(true); });
        return () => {
            active = false;
            window.removeEventListener("api-error", onError);
            window.removeEventListener("session-expired", onExpired);
        };
    }, []);

    const logout = async () => {
        try {
            await api.post("/user/logout");
            setUser(null);
            setPage("search");
            await getSession();
        } catch (_) {}
    };

    return <div className="container">
        {error && <div className="error-message" role="alert">
            {error} <button onClick={() => setError("")} aria-label="Dismiss error">×</button>
        </div>}
        {!ready ? <p role="status">Loading your session…</p> : !user ?
            <LoginPage onLogin={value => { setUser(value); setError(""); }} /> : <>
                <div className="topbar">
                    <div><h1 className="brand">Manga Tracker</h1><div className="subtitle">Welcome, {user.username}</div></div>
                    <div className="topbar-actions">
                        <button className={`tabbtn ${page === "search" ? "active" : ""}`} onClick={() => setPage("search")}>Search</button>
                        <button className={`tabbtn ${page === "library" ? "active" : ""}`} onClick={() => setPage("library")}>My Library</button>
                        <button className="tabbtn" onClick={logout}>Logout</button>
                    </div>
                </div>
                <hr />
                {page === "search" ? <SearchPage user={user} /> : <LibraryPage user={user} />}
            </>}
    </div>;
}
export default App;
