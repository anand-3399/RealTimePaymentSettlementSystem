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

        self.username = ko.observable("");
        self.email = ko.observable("");
        self.password = ko.observable("");

        const Hooks = new hooks();

        Hooks.init(self, params);

        self.onClickRegister = Hooks.onClickRegister;
    };

    return vm;
});
