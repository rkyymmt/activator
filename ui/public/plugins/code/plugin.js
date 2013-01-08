snap.registerPlugin({
  id: 'code',
  detailView: 'code-detail-view',
  summaryView: 'code-summary-view',
  model: function() {
    this.details = ko.observable("Our Code details");
  },
  templates: [{
    id: 'code-detail-view',
    content: '<span data-bind="text: model().details"></span>'
  },{
    id: 'code-summary-view',
    content: '<strong>Look at that dang code!</strong>'
  }]
});


