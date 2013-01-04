registerPlugin("code", {

    pluginAdded: function() {

    },

    appOpened: function() {
        // I don't need to do anything here
    },

    summaryView: function(jQueryNodeToRenderIn, config) {
        // todo: global controller or global config or config reference?
        jQueryNodeToRenderIn.append($("<a>").text(config.location).attr("href", "/api/app/open?location=" + config.location).click(function(event) {
            event.preventDefault()
            $.get($(this).attr("href"))
        }))
        
        jQueryNodeToRenderIn.append($("<br>"))
        
        jQueryNodeToRenderIn.append($("<span>").text("Compiling... (5 Java, 3 Scala, 6 Other)"))
    },

    detailView: function(jQueryNodeToRenderIn) {
        jQueryNodeToRenderIn.append($("<span>").text("I'm the detail view for the Code Plugin"))
    }
})