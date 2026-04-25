define([
    "ojs/ojcomposite",
    "text!./login.html",
    "./login",
    "text!./component.json"
], function(Composite, view, viewModel, metadata) {
    "use strict";

    Composite.register("login-form", {
        view: view,
        viewModel: viewModel,
        metadata: JSON.parse(metadata)
    });
});
