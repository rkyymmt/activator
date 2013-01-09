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
}

snap.registerPlugin({
  id: 'code',
  detailView: 'code-detail-view',
  summaryView: 'code-summary-view',
  model: function() {
    this.details = ko.observable("Our Code details");
    this.projectDir = ko.observable(new FileModel({
      name: snap.name(),
      location: snap.location(),
      isDirectory: true
    }));
    // Load our values...
    this.projectDir().load();
  },
  templates: [{
    id: 'code-detail-view',
    content: '<div class="file-tree" data-bind="template: { name: '+"'"+'file-tree-view'+"'"+', data: model().projectDir() }"></div>'
  },{
    id: 'code-summary-view',
    content: '<strong>Look at that dang code!</strong>'
  },{
    id: 'file-tree-view',
    content:'<span data-bind="text: name, click: load"></span>'+
               '<div data-bind="if: isDirectory">' +
                 '<ul data-bind="foreach: children()">'+
                    // TODO - Recursive template here.
                    '<li><span data-bind="template: { name: '+"'"+'file-tree-view'+"'"+'}"></span></li>'+
                 '</ul>' +
               '</div>' 
    //url: '/api/plugin/code/file-tree-view.html'
  }]
});

})();
