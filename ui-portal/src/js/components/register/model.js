define([], function() {
    "use strict";

    return {
        register: function(payload) {
            return Promise.resolve(payload); // Mock
        }
    };
});
