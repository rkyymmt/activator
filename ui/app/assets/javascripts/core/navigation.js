define(['./model'], function(model) {

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
			// this is a hack, but so is this entire navigation.js file.
		})

		// Close Pop-Overs on outside clicks
		$('body').click(function(e) {
			var d = $(event.target)

			// don't close if the user is clicking:
			// an open pop-over or the user pop-over opener or the switch pop-over opener
			if (!(d.hasClass("open") || $('#user').has(d).length || $('#user').is(d) || $('#switch').has(d).length || d.is($('#switch')))) {
				$(".open").removeClass("open")
			}
		})
		// -------------------------
	}

	return {
		init: init
	}

});
