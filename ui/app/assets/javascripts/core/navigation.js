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
			$(this).toggleClass("open");
			if(e.target.href && (window.location.href != e.target.href)) {
				// We clicked a link, let's go to it.
				window.location.href = e.target.href;
			}
		})
		$("body > aside").click(function(){
			$("body").toggleClass("right-open").trigger("resize")
		})
		// -------------------------
	}

	return {
		init: init
	}

});