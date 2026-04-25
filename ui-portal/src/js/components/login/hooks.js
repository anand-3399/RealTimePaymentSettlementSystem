define([
    "./model",
    "knockout"
], function(Model, ko) {
    "use strict";

    return function() {
        let self, params;

        function authenticateCall(username, password) {
            return Model.authenticate(username, password);
        }

        function onClickLogin() {
            authenticateCall(self.username(), self.password()).then(function(response) {
                self.message("Login successful!");
                if (params.onLoginSuccess) {
                    params.onLoginSuccess(response.token);
                }
            }).catch(function() {
                self.message("Invalid credentials");
            });
        }

        function init(bindingContext, rootParams) {
            self = bindingContext;
            params = rootParams;

            return true;
        }

        return {
            onClickLogin: onClickLogin,
            init: init
        };
    };
});
