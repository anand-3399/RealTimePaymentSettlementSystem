define([
    "knockout",
    "./hooks",
    "./model",
    "ojs/ojformlayout",
    "ojs/ojinputtext",
    "ojs/ojbutton"
], function(ko, hooks, Model) {
    "use strict";

    const vm = function(Context) {
        const self = this,
            params = Context.properties ? Context.properties.params : Context;

        self.username = ko.observable("admin");
        self.password = ko.observable("admin123");
        self.message = ko.observable("");

        const Hooks = new hooks();

        Hooks.init(self, params);

        self.onClickLogin = Hooks.onClickLogin;
    };

    return vm;
});
