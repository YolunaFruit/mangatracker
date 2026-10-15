import axios from "axios";

// Same-origin requests work locally through the dev proxy and on Vercel.
const api = axios.create({ baseURL: "", withCredentials: true });
let csrfToken;
let sessionRequest;

export const errorMessage = (error) =>
    error?.response?.data?.message ||
    (typeof error?.response?.data === "string" && error.response.data.length < 240 && !error.response.data.includes("<") ? error.response.data : "") ||
    "Couldn't connect. Please try again.";

export async function getSession() {
    if (!sessionRequest) {
        sessionRequest = api.get("/user/session").then(({ data }) => {
            csrfToken = data.csrfToken;
            return data.user;
        }).finally(() => { sessionRequest = null; });
    }
    return sessionRequest;
}

api.interceptors.request.use(async (config) => {
    if (!["get", "head", "options"].includes((config.method || "get").toLowerCase())) {
        if (!csrfToken) await getSession();
        config.headers["X-CSRF-TOKEN"] = csrfToken;
    }
    return config;
});

api.interceptors.response.use(response => response, error => {
    const path = error.config?.url || "";
    if (!path.startsWith("/user/login") && !path.startsWith("/user/register")) {
        window.dispatchEvent(new CustomEvent("api-error", { detail: errorMessage(error) }));
        if (error.response?.status === 401)
            window.dispatchEvent(new Event("session-expired"));
    }
    return Promise.reject(error);
});

export default api;
