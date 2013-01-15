(function() {

var FileModel = function(config) {
  var self = this;
  self.name = config.name;
  self.location = config.location;
  self.isDirectory = config.isDirectory;
  self.mimeType = config.mimeType;
  // Ajaxed stuff
  self.children = ko.observableArray(config.children || []);
  self.loaded = ko.observable(false);
  self.expanded = ko.observable(false);
  self.expandedText = ko.computed(function() {
    if(self.expanded()) {
      return "[-]";
    }
    return "[+]";
  });
  self.shownChildren = ko.computed(function() {
    if(self.expanded()) {
      return self.children();
     }
     return [];
  });
}
// Because of recursive nature, we have to add this here.
FileModel.prototype.load = function() {
  var self = this;
  $.ajax({
      url: '/api/local/browse',
      type: 'GET',
      dataType: 'json',
      data: { location: self.location },
      success: function(data) {
        if(data.children) {
           var files = $.map(data.children, function(item) { return new FileModel(item); });
           self.children(files);
        }
        // TODO - Maybe we should always refresh to denote file deletions/additions.....
        self.loaded(true);
      }
    });
};

FileModel.prototype.expand = function() {
  var self = this;
  self.expanded(!self.expanded());
  if(!self.loaded()) { self.load(); }
};

FileModel.prototype.show = function() {
  var self = this;
  // TODO - update location hash....
  location.hash = 'code/'+ this.location
};


snap.registerPlugin({
  id: 'code',
  detailView: 'code-detail-view',
  summaryView: 'code-summary-view',
  css: [{ url: '/api/plugin/code/code.css' }],
  model: function() {
    var self = this;
    this.details = ko.observable("Our Code details");
    this.projectDir = ko.observable(new FileModel({
      name: snap.name(),
      location: snap.location(),
      isDirectory: true
    }));
    // Load our values...
    this.projectDir().load();
    this.currentLocation = ko.computed(function() {
      return snap.subpath();
    });
    self.currentLocationContents = ko.computed(function() {
      return "/api/local/show?location="+self.currentLocation();
    });
  },
  templates: [{
    id: 'code-detail-view',
    url: '/api/plugin/code/details.html'
  },{
    id: 'code-summary-view',
    url: '/api/plugin/code/summary.html'
  },{
    id: 'file-tree-view',
    url: '/api/plugin/code/file-tree-view.html'
  }]
});

})();
