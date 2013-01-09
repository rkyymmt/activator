// TODO - how do we expose stuff and all that?  Probably use require.js in the future.
 
function getURLParameter(name) {
  return decodeURIComponent((RegExp(name + '=' + '(.+?)(&|$)').exec(location.search)||[,""])[1])
}
// TODO - namespace on SNAP
// Or put into a library somewhere....
function loadTemplate(template, next) {
  // TODO - Synch these somehow?
  if($('#'+template.id).length == 0) {
    var s = document.createElement("script");
    s.id = template.id; 
    s.type = "text/html";
    if(template.content) {
      s.text = template.content;
    } else if(template.url) {
      // TODO - load the dang thing up and write it directly...
      s.src = template.url;
    }
    $("body").append(s);
  }
  next();
}
// Todo hide in a namespace somewhere....
function loadTemplates(templates, continuation) {
  var idx = templates.length-1;
  
  var next = function(next) {
    if(idx >= 0) {
      var template = templates[idx];
      idx--;
      loadTemplate(template, function() { next(next) });
    } else {
      if(continuation) continuation();
    }
  };
  // Tail recursion and CPS, yippie.
  next(next);
}
// Initializes a plugin from registered config...
function initPlugin(plugin, config) {
  plugin.model(new config.model());
  $.each(config.css || [], function(idx, item) {
     if(item.url) {
       $('head').append( $('<link rel="stylesheet" type="text/css" />').attr('href', item.url) );
     }
  });

  // Ensure odd ordering issues are correct here.
  loadTemplates(config.templates, function() {
    plugin.detailView(config.detailView);
    plugin.summaryView(config.summaryView);
  });
}

 
function PluginModel(config) {
  this.id = ko.observable(config.id);
  this.name = ko.observable(config.name);
  this.model = ko.observable();
  this.summaryView = ko.observable('default-plugin-template');
  this.detailView = ko.observable('default-plugin-template');
  this.load = function() {
    // TODO - Figure out how to load a plugin *and* point it at this object, so we can set its functions
    // as observables and render from them....
    $.getScript("/api/plugin/" + this.id() + "/plugin.js").fail(function(jqxhr, settings, exception) { 
      alert('Failed to load plugin: ' + config.id + '\n Exception: ' + exception);
    });
  }
}
 
function ApplicationModel() {
  var self = this;
  self.location = ko.observable(getURLParameter(name));  
  self.name = ko.observable();
  self.plugins = ko.observableArray([]);
  // TODO - create a new model for history...
  self.history = ko.observableArray([]);
  
  // Current plugin support.
  self.currentPluginId = ko.observable();
  self.currentPlugin = ko.computed(function(){
    var id = this.currentPluginId();
    var plugins = this.plugins();
    for(var i=0; i < plugins.length; ++i) {
      if(plugins[i].id() == id) {
        return plugins[i];
      }
    }
    return null;
  }.bind(this));
  self.setCurrentPlugin = function(plugin) {
    // Controllers should only modify the location hash to move the app through the routes...
    location.hash = plugin.id();
  }.bind(this);
  
  // Can we assume this never runs without having plugins loaded?
  self.registerPlugin = function(config) {
    $.each(this.plugins(), function(idx, plugin) {
      // TODO - Copy all properties?
      if(plugin.id() == config.id) {
        initPlugin(plugin, config);
      }
    });
  };
  
  // Load initial state
  $.ajax({
     url: '/api/app/details',
     type: 'GET',
     dataType: 'json',
     data: { location: self.location() }, 
     success: function(data) {
       // TODO - Find a way to be lazy about this perhaps.....
       self.name(data.name);
       // Create Plugin Models and load plugins after we're sure they're
       // specified correctly.
       self.plugins($.map(data.plugins, function(item) { return new PluginModel(item); }));
       $.each(self.plugins(), function(i,p) { p.load(); });
     }
  });
  
  // Load history
  $.ajax({
     url: '/api/app/history',
     type: 'GET',
     dataType: 'json',
     success: function(data) {
       // TODO - Wrap these in observable objects?
       self.history(data);
     }
  });

  self.subpath = ko.observable();

  self.routes = $.sammy(function() {
    this.get(/#([^\/]+)\/?(.*)/, function() {
      self.currentPluginId(this.params.splat[0]); 
      self.subpath(this.params.splat[1]);
    });
    this.get('', function() { self.currentPluginId(''); });
  });
  $(function() { self.routes.run(); });
};


snap = new ApplicationModel();

// Apply bindings after we've loaded.
$(function() { ko.applyBindings(snap) });
