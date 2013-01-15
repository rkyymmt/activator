snap.registerPlugin({
  id: 'run',
  detailView: 'run-detail-view',
  summaryView: 'run-summary-view',
  model: function() {},
  templates: [{
    id: 'run-detail-view',
    content: '<span>DETAILS FOR RUN</span>'
  },{
   id: 'run-summary-view',
   content: '<strong>QUICK</strong> Catch the code, man!'
  }]
});