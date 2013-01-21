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

		$("#switch").click(function(e){
			e.preventDefault()
			$(this).toggleClass("open")
		})
		$("body > aside").click(function(){
			$("body").toggleClass("right-open").trigger("resize")
		})
		// -------------------------
	}

/*
		// KEYBOARD NAVIGATION
		$("#wrapper").on("click", "article.list li, article.list dd", function(e){
			var el = $(this).closest("li, dd")
			window.location = el.find("a").attr("href")
			el.addClass("active").siblings().removeClass("active")
		})
*/

	return {
		init: init
	}

})
