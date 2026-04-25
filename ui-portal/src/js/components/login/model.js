define([], function() {
    "use strict";

    return {
        authenticate: function(username, password) {
            return fetch('http://localhost:8081/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            }).then(response => {
                if (!response.ok) throw new Error('Auth failed');
                return response.json();
            });
        }
    };
});
