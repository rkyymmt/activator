$ ->
  ws = new WebSocket $("body").data("ws-url")
  ws.onmessage = (event) ->
    message = JSON.parse event.data
    switch message.type
      when "stockhistory"
        chart = $("<div>").addClass("chart").prop("id", message.symbol)
        chartHolder = $("<div>").addClass("chart-holder").append(chart)
        chartHolder.append($("<p>").text("values are simulated"))
        detailsHolder = $("<div>").addClass("details-holder")
        flipper = $("<div>").addClass("flipper").append(chartHolder).append(detailsHolder).attr("data-content", message.symbol)
        flipContainer = $("<div>").addClass("flip-container").append(flipper).click (event) ->
          if ($(this).hasClass("flipped"))
            $(this).removeClass("flipped")
            $(this).find(".details-holder").empty()
          else
            $(this).addClass("flipped")
            # fetch stock details and tweet
            $.ajax
              url: "/sentiment/" + $(this).children(".flipper").attr("data-content")
              dataType: "json"
              context: $(this)
              success: (data) ->
                detailsHolder = $(this).find(".details-holder")
                detailsHolder.empty()
                switch data.label
                  when "pos"
                    detailsHolder.append($("<h4>").text("The tweets say BUY!"))
                    detailsHolder.append($("<img>").attr("src", "/assets/images/buy.png"))
                  when "neg"
                    detailsHolder.append($("<h4>").text("The tweets say SELL!"))
                    detailsHolder.append($("<img>").attr("src", "/assets/images/sell.png"))
                  else
                    detailsHolder.append($("<h4>").text("The tweets say HOLD!"))
                    detailsHolder.append($("<img>").attr("src", "/assets/images/hold.png"))
              error: (jqXHR, textStatus, error) ->
                detailsHolder = $(this).find(".details-holder")
                detailsHolder.empty()
                detailsHolder.append($("<h2>").text("Error: " + textStatus))
            # display loading info
            detailsHolder = $(this).find(".details-holder")
            detailsHolder.append($("<h4>").text("Determing whether you should buy or sell based on the sentiment of recent tweets..."))
            detailsHolder.append($("<div>").addClass("progress progress-striped active").append($("<div>").addClass("bar").css("width", "100%")))
        $("#stocks").prepend(flipContainer)
        plot = chart.plot([getChartArray(message.history)], getChartOptions(message.history)).data("plot")
      when "stockupdate"
        if ($("#" + message.symbol).size() > 0)
          plot = $("#" + message.symbol).data("plot")
          data = getPricesFromArray(plot.getData()[0].data)
          data.shift()
          data.push(message.price)
          plot.setData([getChartArray(data)])
          # update the yaxes if either the min or max is now out of the acceptable range
          yaxes = plot.getOptions().yaxes[0]
          if ((getAxisMin(data) < yaxes.min) || (getAxisMax(data) > yaxes.max))
            # reseting yaxes
            yaxes.min = getAxisMin(data)
            yaxes.max = getAxisMax(data)
            plot.setupGrid()
          # redraw the chart
          plot.draw()
      else
        console.log(message)

  $("#addsymbolform").submit (event) ->
    event.preventDefault()
    $.post("/watch/" + $("body").attr("data-uuid") + "/" + $("#addsymboltext").val())
    $("#addsymboltext").val("")

getPricesFromArray = (data) ->
  (v[1] for v in data)

getChartArray = (data) ->
  ([i, v] for v, i in data)

getChartOptions = (data) ->
  series:
    shadowSize: 0
  yaxis:
    min: getAxisMin(data)
    max: getAxisMax(data)
  xaxis:
    show: false

getAxisMin = (data) ->
  Math.min.apply(Math, data) * 0.9

getAxisMax = (data) ->
  Math.max.apply(Math, data) * 1.1
