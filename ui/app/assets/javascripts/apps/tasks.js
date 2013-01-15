var Tasks = {
		name: "Tasks",
		tpl: _.template( $('#tasks_tpl').text() ),

		init: function(v){
		}

	},

	Task = {
		name: "Tasks",
		tpl: _.template( $('#task_tpl').text() ),

		init: function(v){
		},

		get: function(module, ƒ){
			$.getJSON( '/' + module.url, ƒ)
		}
	}
