snap.registerPlugin({
  id: 'test',
  detailView: 'test-detail-view',
  summaryView: 'test-summary-view',
  model: function() {},
  templates: [{
    id: 'test-detail-view',
    content: '<span>TESTING, TESTING, 1, 2, TESTING</span>'
  },{
   id: 'test-summary-view',
   content: 'i think it might pass this time'
  }]
});