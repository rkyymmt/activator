var registeredPlugins = {}

function loadAppPlugins(location) {
    $.get("/api/app/plugins?location=" + location, function(plugins) {
        $("#pluginList").empty()
        
        $.each(plugins, function(index, plugin) {
            // load the plugin
            $.getScript("/public/plugins/" + plugin.id + "/plugin.js", function(data) {
                
                // initialize the plugin
                registeredPlugins[plugin.id].appOpened()
                
                // render the plugin
                // todo: position is hard to deal with because we are async
                var pluginDiv = $("<div class='plugin-summary'>").attr("id", plugin.id).append($("<h1>").append($("<a>").text(plugin.name).attr("href", "#").data("plugin-id", plugin.id).click(function(event) {
                    event.preventDefault()
                    showPluginDetail($(this).data("plugin-id"))
                })))
                $("#pluginList").append(pluginDiv)
                
                // display the summary view
                registeredPlugins[plugin.id].summaryView(pluginDiv, {location: location})
                
                // if this is the first plugin loaded then select it and display it's details
                if (plugin.position == 0) {
                    pluginDiv.addClass("active")
                    showPluginDetail(plugin.id)
                }
            })
        })
    })
}

function registerPlugin(name, body) {
    registeredPlugins[name] = body
}

function showPluginDetail(id) {
    // make the selected plugin active
    $("#pluginList").children().removeClass("active")
    $("#pluginList #" + id).addClass("active")
    
    // display the details
    $("#pluginDetail").empty()
    registeredPlugins[id].detailView($("#pluginDetail"))
}