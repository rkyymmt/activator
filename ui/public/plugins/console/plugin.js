registerPlugin("console", {
    pluginAdded: function() {

    },

    appOpened: function() {

    },

    summaryView: function(jQueryNodeToRenderIn) {
        jQueryNodeToRenderIn.append($("<span>").text("Some Sexy Chart"))
    },

    detailView: function(jQueryNodeToRenderIn) {
        jQueryNodeToRenderIn.append($("<span>").text("I'm the detail view for the Console Plugin"))
    }
})