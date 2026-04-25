define([
    "ojs/ojcomposite",
    "text!./payment.html",
    "./payment",
    "text!./component.json"
], function(Composite, view, viewModel, metadata) {
    "use strict";

    Composite.register("payment-module", {
        view: view,
        viewModel: viewModel,
        metadata: JSON.parse(metadata)
    });
});
