// TODO - how do we expose stuff and all that?  Probably use require.js in the future.

 
function getURLParameter(name) {
  return decodeURIComponent((RegExp(name + '=' + '(.+?)(&|$)').exec(location.search)||[,""])[1])
}
 
function PluginModel(config) {
  this.id = ko.observable(config.id);
  this.name = ko.observable(config.name);
  this.summary = ko.observable();
  this.details = ko.observable();
  this.load = function() {
    // TODO - Figure out how to load a plugin *and* point it at this object, so we can set its functions
    // as observables and render from them....
    $.getScript("/api/plugin/" + this.id() + "/plugin.js")
    return this;
  }
}
 
function ApplicationModel() {
  this.location = ko.observable(getURLParameter(name));  
  this.name = ko.observable();
  this.plugins = ko.observableArray([]);
  // TODO - create a new model for history...
  this.history = ko.observableArray([]);
  
  // Current plugin support.
  this.currentPluginId = ko.observable();
  this.currentPlugin = ko.computed(function(){
    var id = this.currentPluginId();
    var plugins = this.plugins();
    for(var i=0; i < plugins.length; ++i) {
      if(plugins[i].id() == id) {
        return plugins[i];
      }
    }
    return null;
  }.bind(this));
  this.setCurrentPlugin = function(plugin) {
    this.currentPluginId(plugin.id());
  }.bind(this);
  
  // Can we assume this never runs without having plugins loaded?
  this.registerPlugin = function(config) {
    $.each(this.plugins(), function(idx, plugin) {
      // TODO - Copy all properties?
      if(plugin.id() == config.id) {
        plugin.summary(config.summary);
        plugin.details(config.details);
      }
    });
  };
  
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
       // Create Plugin Models and load plugins after we're sure they're
       // specified correctly.
       this.plugins($.map(data.plugins, function(item) { return new PluginModel(item); }));
       $.each(this.plugins(), function(i,p) { p.load(); });
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
