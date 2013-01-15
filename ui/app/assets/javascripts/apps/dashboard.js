var Dashboard = {
		name: "Dashboard",
		tpl: _.template( $('#dashboard_tpl').text() ),

		init: function(v){
			$(v.view).on("click", ".widget .list > li, .widget .list > dt", function(){
				$(this).toggleClass("open")
			})
		}

	}

