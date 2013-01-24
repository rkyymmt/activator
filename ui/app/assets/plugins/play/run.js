define(['css!./play.css','text!./run.html'], function(css, template){

	var ko = req('vendors/knockout-2.2.1.debug'),
		key = req('vendors/keymage.min');

	var Run = Class({
		init: function(parameters){
			this.title = ko.observable("Play monitor")
		},
		render: function(parameters){
			var view = $(template + "");
			return view;
		},
		update: function(parameters){
		}
	});

	return Run;

});
