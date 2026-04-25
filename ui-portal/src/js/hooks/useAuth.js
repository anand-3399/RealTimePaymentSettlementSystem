define(['knockout'], function(ko) {
    /**
     * Hook for authentication management
     */
    function useAuth() {
        const token = ko.observable(sessionStorage.getItem('jwt_token'));
        const isAuthenticated = ko.pureComputed(() => !!token());

        const login = async (username, password) => {
            try {
                const response = await fetch('http://localhost:8081/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password })
                });
                
                if (!response.ok) throw new Error('Login failed');
                
                const data = await response.json();
                sessionStorage.setItem('jwt_token', data.token);
                token(data.token);
                return true;
            } catch (error) {
                console.error('Auth Error:', error);
                return false;
            }
        };

        const logout = () => {
            sessionStorage.removeItem('jwt_token');
            token(null);
        };

        const getAuthHeader = () => {
            return token() ? { 'Authorization': 'Bearer ' + token() } : {};
        };

        return {
            token,
            isAuthenticated,
            login,
            logout,
            getAuthHeader
        };
    }

    return useAuth;
});
