registerPlugin("run", {
    pluginAdded: function() {

    },

    appOpened: function() {
        // todo: start the app
    },

    summaryView: function(jQueryNodeToRenderIn) {
        jQueryNodeToRenderIn.append($("<a>").text("http://localhost:9000").attr("href", "http://localhost:9000").attr("target", "_blank"))
        jQueryNodeToRenderIn.append($("<br>"))
        jQueryNodeToRenderIn.append($("<span>").text("Run / Error Indicator"))
    },

    detailView: function(jQueryNodeToRenderIn) {
        jQueryNodeToRenderIn.append($("<span>").text("I'm the detail view for the Run Plugin"))
    }
})