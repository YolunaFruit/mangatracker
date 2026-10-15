import React, { useState } from "react";
import api, { errorMessage } from "./api";

export default function LoginPage({ onLogin }) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");

    const submit = async (action) => {
        setBusy(true); setError(""); setMessage("");
        try {
            const body = new URLSearchParams({ username: username.trim(), password });
            const { data } = await api.post(`/user/${action}`, body);
            if (action === "login") onLogin(data);
            else setMessage("Account created. You can now sign in.");
        } catch (err) {
            setError(errorMessage(err));
        } finally { setBusy(false); }
    };

    return <div className="login-panel">
        <h1 className="brand">Manga Tracker</h1>
        <p className="subtitle">Discover your next read. Pick up where you left off.</p>
        <h2>Login / Register</h2>
        <form onSubmit={event => { event.preventDefault(); submit("login"); }}>
            <label htmlFor="username">Username</label>
            <input id="username" autoComplete="username" required maxLength={40} value={username}
                onChange={event => setUsername(event.target.value)} placeholder="Your username" />
            <label htmlFor="password">Password</label>
            <input id="password" type="password" autoComplete="current-password" required value={password}
                onChange={event => setPassword(event.target.value)} placeholder="Your password" />
            <p className="subtitle">New account? Use 3–40 letters, numbers, underscores or hyphens and a password of at least 8 characters.</p>
            {error && <p role="alert">{error}</p>}
            {message && <p role="status">{message}</p>}
            <div className="login-actions">
                <button className="primary" disabled={busy} type="submit">{busy ? "Please wait…" : "Login"}</button>
                <button disabled={busy || !username.trim() || !password} type="button" onClick={() => submit("register")}>Register</button>
            </div>
        </form>
    </div>;
}
