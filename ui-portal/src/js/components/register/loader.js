define([
    "ojs/ojcomposite",
    "text!./register.html",
    "./register",
    "text!./component.json"
], function(Composite, view, viewModel, metadata) {
    "use strict";

    Composite.register("user-register", {
        view: view,
        viewModel: viewModel,
        metadata: JSON.parse(metadata)
    });
});
