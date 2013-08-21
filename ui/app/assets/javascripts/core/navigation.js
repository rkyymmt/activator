define(function(css, template){

	function init(){
		// TOOGLE NAV PAN ----------
		// Add effects on mouseover to toggle the pan
		$("body > nav")
			.mouseup(function(e){
				if (e.target == e.currentTarget)
					$("body").toggleClass("left-open").trigger("resize")
			})
			.mouseover(function(e){
				if (e.target == e.currentTarget)
					$(this).addClass("over")
				else
					$(this).removeClass("over")
			})
			.mouseout(function(e){
				$(this).removeClass("over")
			})

		// This allows us to select previously opened applications.
		$('#switch').click(function(e){
			e.preventDefault();
			// close the user overlay
			$("#user").removeClass("open");
			// open the app selector overlay
			$(this).toggleClass("open");
			if(e.target.href && (window.location.href != e.target.href)) {
				// We clicked a link, let's go to it.
				window.location.href = e.target.href;
			}
		})

		// Open / Close User / Login Overlay
		$('#user').click(function(e){
			e.preventDefault();
			// close the app selector overlay
			$("#switch").removeClass("open");
			// open the user overlay
			$(this).toggleClass("open");
		})
		// -------------------------
	}

	return {
		init: init
	}

});
