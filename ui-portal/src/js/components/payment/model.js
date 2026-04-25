define([], function() {
    "use strict";

    return {
        fetchRecentPayments: function(token) {
            return fetch('http://localhost:8081/api/orders', {
                headers: { 'Authorization': 'Bearer ' + token }
            }).then(r => r.json());
        },
        submitPayment: function(amount, currency, idempotencyKey, token) {
            return fetch('http://localhost:8081/api/orders', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + token 
                },
                body: JSON.stringify({ amount, currency, idempotencyKey })
            }).then(r => r.json());
        }
    };
});
