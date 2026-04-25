define([
    "./model",
    "knockout"
], function(Model, ko) {
    "use strict";

    return function() {
        let self, params;

        function fetchRecentPaymentsCall(token) {
            return Model.fetchRecentPayments(token);
        }

        function submitPaymentCall(amount, currency, idempotencyKey, token) {
            return Model.submitPayment(amount, currency, idempotencyKey, token);
        }

        function onClickSubmit() {
            const token = sessionStorage.getItem('jwt_token');
            const idempotencyKey = 'key-' + Date.now();

            submitPaymentCall(self.amount(), self.currency(), idempotencyKey, token).then(function(newOrder) {
                self.payments.unshift(newOrder);
            });
        }

        function init(bindingContext, rootParams) {
            self = bindingContext;
            params = rootParams;

            const token = sessionStorage.getItem('jwt_token');
            if (token) {
                fetchRecentPaymentsCall(token).then(function(data) {
                    self.payments(data);
                });
            }

            return true;
        }

        return {
            onClickSubmit: onClickSubmit,
            init: init
        };
    };
});
