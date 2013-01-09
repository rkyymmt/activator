(function() {

var FileModel = function(config) {
  var self = this;
  self.name = config.name;
  self.location = config.location;
  self.isDirectory = config.isDirectory;
  // Ajaxed stuff
  self.contents = ko.observable();
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
        if(data.contents) { self.contents(data.contents); }
        if(data.children) {
           var files = $.map(data.children, function(item) { return new FileModel(item); });
           self.children(files);
        }
        self.loaded(false);
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
  },
  templates: [{
    id: 'code-detail-view',
    content: '<div class="file-tree" data-bind="template: { name: '+"'"+'file-tree-view'+"'"+', data: model().projectDir() }"></div>'+
             '<div class="file-contents"><p>Would be showing:</p><span data-bind="text: model().currentLocation()"></span></div>'
  },{
    id: 'code-summary-view',
    content: '<strong>Look at that dang code!</strong>'
  },{
    id: 'file-tree-view',
    content:'<span data-bind="click: expand, text: expandedText, if: isDirectory"></span>' +
            '  <span data-bind="text: name"></span>'+
            '  <a data-bind="click: show">(Show)</a>' +
               '<div data-bind="if: isDirectory">' +
                 
                 '<ul data-bind="foreach: shownChildren()">'+
                    // TODO - Recursive template here.
                    '<li><span data-bind="template: { name: '+"'"+'file-tree-view'+"'"+'}"></span></li>'+
                 '</ul>' +
               '</div>' 
    //url: '/api/plugin/code/file-tree-view.html'
  }]
});

})();
