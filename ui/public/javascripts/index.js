// TODO - how do we expose stuff and all that?  Probably use require.js in the future.
 
 
function getURLParameter(name) {
  return decodeURIComponent((RegExp(name + '=' + '(.+?)(&|$)').exec(location.search)||[,""])[1])
}
 
function PluginModel(config) {
  this.id = ko.observable(config.id);
  this.name = ko.observable(config.name);
  this.load = function() {
    // TODO - Figure out how to load a plugin *and* point it at this object, so we can set its functions
    // as observables and render from them....
    return this;
  }
}
 
function ApplicationModel() {

  this.location = ko.observable(getURLParameter(name));  
  this.name = ko.observable();
  this.plugins = ko.observableArray([]);
  this.history = ko.observableArray([]);
  
  // Load initial state
  $.ajax({
     url: '/api/app/details',
     type: 'GET',
     dataType: 'json',
     data: { location: this.location() }, 
     context: this,
     success: function(data) {
       // TODO - Find a way to be lazy about this perhaps.....
       this.name(data.name);
       // TODO - Plugins as a model...
       this.plugins($.map(data.plugins, function(item) { return new PluginModel(item); }));
     }
  });
  
  // Load history
  $.ajax({
     url: '/api/app/history',
     type: 'GET',
     dataType: 'json',
     context: this,
     success: function(data) {
       // TODO - Wrap these in observable objects?
       this.history(data);
     }
  });
};
 

snap = new ApplicationModel();

// Apply bindings after we've loaded.
$(function() { ko.applyBindings(snap) });
  

/*
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
}*/