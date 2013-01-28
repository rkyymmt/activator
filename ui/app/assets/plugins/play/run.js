define(['css!./play.css','text!./run.html'], function(css, template){

	var ko = req('vendors/knockout-2.2.1.debug'),
		key = req('vendors/keymage.min');

        var Run = Widget({
    id: 'play-run-widget',
    template: template,
		init: function(parameters){
			this.title = ko.observable("Play monitor")
		},
		update: function(parameters){
		}
	});

	return Run;

});
