define([
    "knockout",
    "./hooks",
    "./model",
    "ojs/ojformlayout",
    "ojs/ojinputtext",
    "ojs/ojinputnumber",
    "ojs/ojbutton",
    "ojs/ojtable"
], function(ko, hooks, Model) {
    "use strict";

    const vm = function(Context) {
        const self = this,
            params = Context.properties ? Context.properties.params : Context;

        self.payments = ko.observableArray([]);
        self.amount = ko.observable(100.00);
        self.currency = ko.observable("USD");

        const Hooks = new hooks();

        Hooks.init(self, params);

        self.onClickSubmit = Hooks.onClickSubmit;
    };

    return vm;
});
