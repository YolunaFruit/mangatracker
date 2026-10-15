import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import App from './App';
import LoginPage from './LoginPage';
import LibraryPage from './LibraryPage';
import api, { getSession } from './api';

jest.mock('./api', () => ({
    __esModule: true,
    default: { get: jest.fn(), post: jest.fn() },
    getSession: jest.fn(),
    errorMessage: () => 'Please try again.',
}));

beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
    getSession.mockResolvedValue(null);
    api.get.mockResolvedValue({ data: { trending: [], new: [] } });
});

test('ignores a stale or malformed local profile and checks the server session', async () => {
    localStorage.setItem('user', '{broken json');
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'Login / Register' })).toBeInTheDocument();
    expect(getSession).toHaveBeenCalled();
    expect(localStorage.getItem('user')).toBeNull();
});

test('restores a server session and logs out on the server', async () => {
    getSession.mockResolvedValueOnce({ username: 'reader' }).mockResolvedValue(null);
    api.post.mockResolvedValue({});
    render(<App />);
    expect(await screen.findByText('Welcome, reader')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    expect(await screen.findByRole('heading', { name: 'Login / Register' })).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith('/user/logout');
});

test('sends special characters in login credentials in the request body', async () => {
    const onLogin = jest.fn();
    api.post.mockResolvedValue({ data: { username: 'reader' } });
    render(<LoginPage onLogin={onLogin} />);
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'reader' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'books&more#123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));
    await waitFor(() => expect(onLogin).toHaveBeenCalled());
    const [url, body] = api.post.mock.calls[0];
    expect(url).toBe('/user/login');
    expect(body.get('password')).toBe('books&more#123');
});

test('saves manual progress beyond source metadata and allows clearing a score', async () => {
    const manga = { mangaId: 'mal:13', title: 'One Piece', currentChapter: 299, myScore: 8, status: 'READING' };
    api.get.mockImplementation(url => Promise.resolve({ data: url.startsWith('/progress/')
        ? { trackedManga: [manga] } : { latestChapter: 100 } }));
    api.post.mockResolvedValue({ data: { trackedManga: [{ ...manga, myScore: null }] } });
    render(<LibraryPage user={{ username: 'reader' }} />);
    await screen.findByText('One Piece');
    fireEvent.click(screen.getByRole('button', { name: 'Options' }));
    fireEvent.click(screen.getByRole('button', { name: 'Update / Edit' }));
    fireEvent.change(screen.getByLabelText('My Score'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/progress/reader/entry', {
        mangaId: 'mal:13', chapter: 299, status: 'reading', score: null,
    }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
});
