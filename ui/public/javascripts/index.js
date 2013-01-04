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

})

function showAppScreen(appDetails) {
    $("#launchScreen").hide()
    $("#appScreen").show()
    $("#appName").text(appDetails.name)
    
    loadAppPlugins(getURLParameter("location"))
}

function showLaunchScreen() {
    $("#launchScreen").show()
    $("#appScreen").hide()
}

function getURLParameter(name) {
    return decodeURIComponent((RegExp(name + '=' + '(.+?)(&|$)').exec(location.search)||[,""])[1])
}