snap.registerPlugin({
  id: 'console',
  detailView: 'console-detail-view',
  summaryView: 'console-summary-view',
  model: function() {
    
  },
  templates: [{
    id: 'console-detail-view',
    content: 'This is gonna be amazing, I can tell....'
  },{
   id: 'console-summary-view',
   content: "I think it's running smooooooooth...."
  }]
});
