registerPlugin("test", {
    pluginAdded: function() {

    },

    appOpened: function() {
        // todo: start the tests
    },

    summaryView: function(jQueryNodeToRenderIn) {
        jQueryNodeToRenderIn.append($("<span>").text("Test Chart and Red, Yellow, Green Light"))
    },

    detailView: function(jQueryNodeToRenderIn) {
        jQueryNodeToRenderIn.append($("<span>").text("I'm the detail view for the Test Plugin"))
    }
})