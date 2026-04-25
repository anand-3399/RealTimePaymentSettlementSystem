define([
    "./model",
    "knockout"
], function(Model, ko) {
    "use strict";

    return function() {
        let self, params;

        function registerCall(payload) {
            return Model.register(payload);
        }

        function onClickRegister() {
            const payload = {
                username: self.username(),
                email: self.email(),
                password: self.password()
            };

            registerCall(payload).then(function() {
                console.log("Registered!");
            });
        }

        function init(bindingContext, rootParams) {
            self = bindingContext;
            params = rootParams;
            return true;
        }

        return {
            onClickRegister: onClickRegister,
            init: init
        };
    };
});
