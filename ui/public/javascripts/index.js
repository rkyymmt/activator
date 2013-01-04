$(function() {
    
    if (getURLParameter("location")) {
        // try to open the app
        $.ajax({
            url: "/api/app/details?location=" + getURLParameter("location"),
            dataType: "json",
            success: function(data) {
                showAppScreen(data)
            }, 
            error: function(jqXHR, textStatus, errorThrown) {
                // todo: display error
                showLaunchScreen()
            }
        })
    }
    else {
        showLaunchScreen()
    }
    
    // event handlers
    $("#newAppLink").click(function(event) {
        event.preventDefault()
        showLaunchScreen()
        history.pushState(null, null, "/")
    })

    $("#inputNewAppName").keyup(function(event) {
        updateAppLocation()
    })

    $("#buttonBrowseAppLocation").click(function(event) {
        // todo
    })

    $("#buttonBrowseTemplate").click(function(event) {
        // todo
    })

    $("#inputNewAppName").keyup(function(event) {
        updateCreateAppButton()
    })
    $("#inputNewAppName").bind('paste', function(event) {
        updateCreateAppButton()
    })
    $("#inputNewAppLocation").keyup(function(event) {
        updateCreateAppButton()
    })
    $("#inputNewAppLocation").bind('paste', function(event) {
        updateCreateAppButton()
    })

    $("#createAppForm").submit(function(event) {
        event.preventDefault()

        if (updateCreateAppButton()) {
            snap_templates.clone_template({
                location: $("#inputNewAppLocation").val(),
                id: $("#selectNewAppTemplate").val(),
                name: $("#inputNewAppName").val(),
                success:  function(data) {
                    history.pushState(null, null, "/app?location=" + data.location)
                    showAppScreen(data)
                },
                error: function(data) {
                    // todo: need a general displayer
                }
            })
        }
    })

})

function showAppScreen(appDetails) {
    $("#launchScreen").hide()
    $("#appScreen").show()
    $("#appName").text(appDetails.name)
    
    loadAppPlugins(getURLParameter("location"))
}

function showLaunchScreen() {
    $.get("/api/local/env", function(data) {
        $("#inputNewAppLocation").data("local-dir", data.desktopDir)
        $("#inputNewAppLocation").data("local-separator", data.separator)
        updateAppLocation()
    })

    snap_templates.get_templates({
        success: function(data) {
            $.each(data, function(index, item) {
                $("#selectNewAppTemplate").append($("<option>").text(item.name).attr("value", item.id))
            })
        }
    })
    
    $("#launchScreen").show()
    $("#appScreen").hide()
}

function getURLParameter(name) {
    return decodeURIComponent((RegExp(name + '=' + '(.+?)(&|$)').exec(location.search)||[,""])[1])
}

function updateAppLocation() {
    var baseLocalDir = $("#inputNewAppLocation").data("local-dir")
    var separator = $("#inputNewAppLocation").data("local-separator")
    var appDir = $("#inputNewAppName").val().toLowerCase().replace(/\s/g, "-").replace(/[^a-z0-9\\-]/g,"")
    $("#inputNewAppLocation").val(baseLocalDir + separator + appDir)
}

function updateCreateAppButton() {
    if ( ($("#inputNewAppName").val().length > 0) &&
        ($("#inputNewAppLocation").val().length > 0) &&
        ($("#selectNewAppTemplate").val().length > 0) ) {
        $("#buttonCreateApp").removeClass("disabled")
        return true;
    }
    else {
        $("#buttonCreateApp").addClass("disabled")
        return false;
    }
}